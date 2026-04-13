package com.simplemobiletools.musicplayer.activities

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.CueListCache
import com.simplemobiletools.musicplayer.helpers.EXTRA_NEXT_MEDIA_ID
import com.simplemobiletools.musicplayer.helpers.SimpleMediaController
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.models.toMediaItems
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread
import com.simplemobiletools.musicplayer.playback.CustomCommands
import com.simplemobiletools.musicplayer.playback.PlaybackService.Companion.updatePlaybackInfo
import org.greenrobot.eventbus.EventBus
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Base class for activities that want to control the [Player].
 */
abstract class SimpleControllerActivity : SimpleActivity(), Player.Listener {
    private lateinit var controller: SimpleMediaController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = SimpleMediaController.getInstance(this)
        maybePreparePlayer()
    }

    override fun onStart() {
        super.onStart()
        controller.addListener(this)
    }

    override fun onStop() {
        super.onStop()
        controller.removeListener(this)
    }

    override fun onResume() {
        super.onResume()
        maybePreparePlayer()
    }

    open fun onPlayerPrepared(success: Boolean) {}

    fun withPlayer(callback: MediaController.() -> Unit) = controller.withController(callback)

    fun prepareAndPlay(
        tracks: List<Track>,
        showPlayback: Boolean,
        queueSource: String,
        startIndex: Int = 0,
        startPositionMs: Long = 0,
        startPlay: Boolean = true
    ) {
        val keepTrackLastPosition = config.keepTrackLastPosition
        executeBackgroundThread {
            val track = tracks[startIndex]
            var lastPosition = audioHelper.updateRecentPlayedTrack(track)
            if (!keepTrackLastPosition) lastPosition = startPositionMs

            withPlayer {
                if (isPlaying) {
                    val currentItem = currentMediaItem
                    if (currentItem != null && currentItem.getMediaStoreId() != track.mediaStoreId) {
                        val playPosition = currentPosition
                        if (keepTrackLastPosition) {
                            val lastQueueSource = config.lastQueueSource
                            executeBackgroundThread {
                                audioHelper.updateRecentPlayedTrackLastPosition(currentItem, playPosition)
                                audioHelper.updateQueueSourceLastMedia(lastQueueSource, currentItem.getMediaStoreId(), playPosition)
                            }
                        } else {
                            // The last item in the playlist is recorded before it changes.
                            val lastQueueSource = config.lastQueueSource
                            executeBackgroundThread {
                                audioHelper.updateQueueSourceLastMedia(lastQueueSource, currentItem.getMediaStoreId(), playPosition)
                            }
                        }
                    } else if (currentItem != null && currentItem.getMediaStoreId() == track.mediaStoreId) {
                        lastPosition = if (keepTrackLastPosition) currentPosition else 0
                    }
                }

                if (showPlayback) {
                    startActivity(
                        Intent(this@SimpleControllerActivity, TrackActivity::class.java)
                    )
                }

                val currentItems = currentMediaItems
                val isTracksSameWithCurrentItems = currentItems.size == tracks.size &&
                    currentItems.zip(tracks).all { (current, track) -> current.isSameMedia(track) }

                // Shortly afterwards, the media ID of the current queueSource is updated while saving queue items.
                config.lastQueueSource = queueSource
                // If not selected from multiple queues, forcibly apply to a legacy queue with ID 0.
                if (queueSource.startsWith("q:").not() && config.queueId != 0L) {
                    config.queueId = 0
                }

                prepareUsingTracks(
                    tracks = tracks,
                    startIndex = startIndex,
                    startPositionMs = lastPosition,
                    play = startPlay,
                    isTracksSameWithCurrentItems = isTracksSameWithCurrentItems
                ) { success ->
                    if (success) {
                        updatePlaybackInfo(this)
                    }
                    Events.QueueItemsChanged.setNeedToPost()
                }
            }
        }
    }

    fun maybePreparePlayer() {
        withPlayer {
            maybePreparePlayer(context = this@SimpleControllerActivity, callback = ::onPlayerPrepared)
        }
    }

    fun togglePlayback() = withPlayer { togglePlayback() }

    fun seekToNext() {
        withPlayer {
            val currentSec = currentPosition.milliseconds.inWholeSeconds.toInt()
            val fileStableId = currentMediaItem?.toTrack()?.fileStableId ?: 0
            val cues = CueListCache.getCueList(this@SimpleControllerActivity, fileStableId)
            val nextEnabledCue = cues.firstOrNull { it.enabled && it.timestamp > currentSec }
            if (nextEnabledCue != null) {
                seekTo(nextEnabledCue.timestamp * 1000L)
            } else {
                forceSeekToNext()
            }
        }
    }

    fun addTracksToQueue(tracks: List<Track>, callback: () -> Unit) {
        withPlayer {
            val currentMediaItemsIds = currentMediaItems.map { it.mediaId }
            val mediaItems = tracks.toMediaItems().filter { it.mediaId !in currentMediaItemsIds }
            addMediaItems(mediaItems)
            callback()
        }
    }

    fun removeQueueItems(tracks: List<Track>, callback: (() -> Unit)? = null) {
        withPlayer {
            var currentItemChanged = false
            tracks.forEach {
                val index = currentMediaItems.indexOfTrackOrNull(it)
                if (index != null) {
                    currentItemChanged = index == currentMediaItemIndex
                    removeMediaItem(index)
                }
            }

            if (currentItemChanged) {
                updatePlaybackInfo(this)
            }

            callback?.invoke()
        }
    }

    fun playNextInQueue(track: Track, callback: () -> Unit) {
        withPlayer {
            sendCommand(
                command = CustomCommands.SET_NEXT_ITEM,
                extras = bundleOf(EXTRA_NEXT_MEDIA_ID to track.mediaStoreId.toString())
            )
            callback()
        }
    }

    fun deleteTracks(tracks: List<Track>, callback: () -> Unit) {
        ensureBackgroundThread { // mainthread에서 호출되는 경우가 있어서 방어코드
            try {
                audioHelper.deleteTracks(tracks)
                audioHelper.removeInvalidAlbumsArtists()
                queueDAO.deleteTrackList(tracks)
            } catch (ignored: Exception) {
            }
        }

        val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        maybeRescanTrackPaths(tracks) { tracksToDelete ->
            if (tracksToDelete.isNotEmpty()) {
                if (isRPlus()) {
                    val uris = tracksToDelete.map { ContentUris.withAppendedId(contentUri, it.mediaStoreId) }
                    deleteSDK30Uris(uris) { success ->
                        if (success) {
                            removeQueueItems(tracksToDelete)
                            EventBus.getDefault().post(Events.RefreshFragments())
                            callback()
                        } else {
                            toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                        }
                    }
                } else {
                    tracksToDelete.forEach { track ->
                        try {
                            val where = "${MediaStore.Audio.Media._ID} = ?"
                            val args = arrayOf(track.mediaStoreId.toString())
                            contentResolver.delete(contentUri, where, args)
                            File(track.path).delete()
                        } catch (ignored: Exception) {
                        }
                    }

                    removeQueueItems(tracksToDelete)
                    EventBus.getDefault().post(Events.RefreshFragments())
                    callback()
                }
            }
        }
    }

    fun refreshQueueAndTracks(trackToUpdate: Track? = null) {
        ensureBackgroundThread {
            val queuedTracks = audioHelper.getQueuedTracks()
            runOnUiThread {
                withPlayer {
                    // it's not yet directly possible to update metadata without interrupting the playback: https://github.com/androidx/media/issues/33
                    if (trackToUpdate == null || currentMediaItem.isSameMedia(trackToUpdate)) {
                        prepareUsingTracks(tracks = queuedTracks, startIndex = currentMediaItemIndex, startPositionMs = currentPosition, play = isReallyPlaying)
                    } else {
                        val trackIndex = currentMediaItems.indexOfTrack(trackToUpdate)
                        if (trackIndex > 0) {
                            removeMediaItem(trackIndex)
                            addMediaItem(trackIndex, trackToUpdate.toMediaItem())
                        }
                    }
                }
            }
        }

        EventBus.getDefault().post(Events.RefreshTracks())
    }
}
