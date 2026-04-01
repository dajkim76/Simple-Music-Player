package com.simplemobiletools.musicplayer.playback

import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.postDelayed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.Config
import com.simplemobiletools.musicplayer.helpers.CueListCache
import com.simplemobiletools.musicplayer.helpers.NotificationHelper
import com.simplemobiletools.musicplayer.helpers.getPermissionToRequest
import com.simplemobiletools.musicplayer.playback.library.MediaItemProvider
import com.simplemobiletools.musicplayer.playback.player.SimpleMusicPlayer
import com.simplemobiletools.musicplayer.playback.player.initializeSessionAndPlayer

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), MediaSessionService.Listener {
    internal lateinit var player: SimpleMusicPlayer
    internal lateinit var playerThread: HandlerThread
    internal lateinit var playerListener: Player.Listener
    internal lateinit var playerHandler: Handler
    internal lateinit var mediaSession: MediaLibrarySession
    internal lateinit var mediaItemProvider: MediaItemProvider
    internal lateinit var config: Config

    internal var currentRoot = ""
    internal var lastCueTitle: String? = null

    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private lateinit var audioRouteMonitor: AudioRouteMonitor
    private var repeatCueData: Pair<Long, Int>? = null // fileStableId, cueIndex

    override fun onCreate() {
        super.onCreate()
        config = Config.getInstance(applicationContext)
        setListener(this)
        initializeSessionAndPlayer(handleAudioFocus = true, handleAudioBecomingNoisy = true, skipSilence = config.gaplessPlayback)
        initializeLibrary()
        // init audio route monitor
        audioRouteMonitor = AudioRouteMonitor(this) { route ->
            if (route == AudioRouteMonitor.Route.BECOMING_NOISY) {
                withPlayer { if (isPlaying) pause() }
            } else if (config.autoplayOnBluetoothConnect) {
                val isAppForeground = isAppForeground() // prevent autoplay on app background (ForegroundServiceStartNotAllowedException)
                if (isAppForeground && (route == AudioRouteMonitor.Route.BLUETOOTH || route == AudioRouteMonitor.Route.WIRED_HEADSET)) {
                    withPlayer { play() }
                }
            }
        }
        audioRouteMonitor.start()
        scheduleProgressUpdate()
        playerHandler.post {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                        .setIsGaplessSupportRequired(true)
                        .build()
                )
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }
    }

    private fun isAppForeground(): Boolean {
        return ProcessLifecycleOwner.get()
            .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        super.onDestroy()
        progressUpdateHandler.removeCallbacksAndMessages(null)
        audioRouteMonitor.stop()
        releaseMediaSession()
        clearListener()
        stopSleepTimer()
        SimpleEqualizer.release()
        currentMediaItem = null
        nextMediaItem = null
        isPlaying = false
    }

    fun stopService() {
        withPlayer {
            pause()
            stop()
        }

        stopSelf()
    }

    private fun initializeLibrary() {
        mediaItemProvider = MediaItemProvider(this)
        if (hasPermission(getPermissionToRequest())) {
            mediaItemProvider.reload()
        } else {
            showNoPermissionNotification()
        }
    }

    private fun releaseMediaSession() {
        mediaSession.release()
        withPlayer {
            removeListener(playerListener)
            release()
        }
    }

    fun scheduleProgressUpdate() {
        progressUpdateHandler.removeCallbacksAndMessages(null)
        withPlayer {
            if (isPlaying) {
                skipDisabledCueOnPlayerThread()
                if (config.keepTrackLastPosition) {
                    // 마지막 1초를 남겨두고 last position을 0으로..
                    if (player.currentPosition >= player.duration - 1000) {
                        currentMediaItem?.let { mediaItem ->
                            audioHelper.updateRecentPlayedTrackLastPosition(mediaItem, 0)
                        }
                    }
                }
            } else {
                // cancel scheduleProgressUpdate. When EVENT_IS_PLAYING_CHANGED occurred, scheduleProgressUpdate will be called again.
                return@withPlayer
            }
            progressUpdateHandler.postDelayed(PROGRESS_UPDATE_INTERVAL) {
                scheduleProgressUpdate()
            }
        }
    }

    private fun skipDisabledCueOnPlayerThread() {
        val currentItem = currentMediaItem ?: return
        val track = currentItem.toTrack() ?: return
        val currentSec = player.currentPosition / 1000

        val cues = CueListCache.getCueList(applicationContext, track.fileStableId)
        if (cues.isEmpty() || cues.all { !it.enabled }) {
            if (lastCueTitle != null) {
                lastCueTitle = null
                broadcastUpdateWidgetState(null)
            }
            return
        }
        val activeCueIndex = cues.indexOfLast { it.timestamp <= currentSec }
        if (activeCueIndex != -1) {
            val currentCue = cues[activeCueIndex]
            if (currentCue.isRepeat && currentCue.enabled) {
                if (repeatCueData == null) {
                    repeatCueData = Pair(track.fileStableId, activeCueIndex)
                }
            }

            repeatCueData?.let { pair ->
                if (pair.first == track.fileStableId) {
                    if (pair.second < cues.size) {
                        val repeatCue = cues[pair.second]
                        if (!repeatCue.isRepeat || !repeatCue.enabled) {
                            repeatCueData = null
                        }
                    } else {
                        repeatCueData = null
                    }
                } else {
                    repeatCueData = null
                }
            }

            if (!currentCue.isRepeat && repeatCueData != null && repeatCueData!!.first == track.fileStableId && repeatCueData!!.second < cues.size) {
                val repeatCue = cues[repeatCueData!!.second]
                player.seekTo(repeatCue.timestamp * 1000L)
            } else if (!currentCue.enabled) {
                val nextEnabledCue = cues.subList(activeCueIndex + 1, cues.size).firstOrNull { it.enabled }
                if (nextEnabledCue != null) {
                    player.seekTo(nextEnabledCue.timestamp * 1000L)
                }
            } else {
                if (lastCueTitle != currentCue.title) {
                    lastCueTitle = currentCue.title
                    broadcastUpdateWidgetState(currentCue.title)
                    withPlayer {
                        currentCueTitle = currentCue.title
                        invalidateMediaMetadata()
                    }
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Player is accessed on the wrong thread. Current thread from super.onTaskRemoved
        withPlayer {
            if (!isPlaying) {
                stopSelf()
            }
        }
    }

    internal fun withPlayer(callback: SimpleMusicPlayer.() -> Unit) {
        if (playerThread == Thread.currentThread()) {
            callback(player)
        } else {
            playerHandler.post { callback(player) }
        }
    }

    private fun showNoPermissionNotification() {
        Handler(Looper.getMainLooper()).postDelayed(delayInMillis = 100L) {
            try {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.createInstance(this).createNoPermissionNotification()
                )
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the {@link MediaSessionService} is in the
     * background.
     */
    override fun onForegroundServiceStartNotAllowedException() {
        showErrorToast(getString(com.simplemobiletools.commons.R.string.unknown_error_occurred))
        // todo: show a notification instead.
    }

    companion object {
        // Initializing a media controller might take a noticeable amount of time thus we expose current playback info here to keep things as quick as possible.
        const val PROGRESS_UPDATE_INTERVAL = 500L
        var isPlaying: Boolean = false
            private set
        var currentMediaItem: MediaItem? = null
            private set
        var nextMediaItem: MediaItem? = null
            private set

        fun updatePlaybackInfo(player: Player) {
            currentMediaItem = player.currentMediaItem
            nextMediaItem = player.nextMediaItem
            isPlaying = player.isReallyPlaying
        }
    }
}

