package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.SimpleControllerActivity
import com.simplemobiletools.musicplayer.adapters.MultiQueueAdapter
import com.simplemobiletools.musicplayer.databinding.FragmentMultiQueueBinding
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.SelectQueueDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.ACTIVITY_QUEUE
import com.simplemobiletools.musicplayer.models.QueueItem
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.models.getQueueDataListFromJson
import com.simplemobiletools.musicplayer.models.sortSafely
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread

class MultiQueueFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var tracks = ArrayList<Track>()
    private val config = context.config
    private var currentQueueId = config.queueId
    private val binding by viewBinding(FragmentMultiQueueBinding::bind)
    private val foregroundDrawable = context.resources.getColoredDrawableWithColor(R.drawable.rounded_white_border, context.getProperPrimaryColor())

    override fun setupFragment(activity: BaseSimpleActivity) {
        updateQueueName()

        binding.multiQueueSelectHeader.setOnClickListener {
            SelectQueueDialog(activity as SimpleActivity, playQueue = false) { queueId ->
                currentQueueId = queueId
                setupFragment(activity)
            }
        }

        binding.multiQueuePlay.setOnClickListener {
            if (tracks.isNotEmpty()) {
                val lastMediaId = getAdapter()?.lastMediaId ?: 0
                val startIndex = tracks.indexOfFirst { lastMediaId == it.mediaStoreId }.coerceAtLeast(0)
                val selectedTrack = tracks[startIndex]
                preparePlay(selectedTrack)
            }
        }

        ensureBackgroundThread {
            tracks = context.audioHelper.getQueuedTracks(currentQueueId)
            val lastMediaId = tracks.find { it.isCurrent() }?.mediaStoreId ?: 0L

            activity.runOnUiThread {
                binding.multiQueuePlaceholder.beVisibleIf(tracks.isEmpty())
                val adapter = MultiQueueAdapter(
                    activity = activity as SimpleActivity,
                    recyclerView = binding.multiQueueList,
                    items = tracks,
                    queueId = currentQueueId,
                    lastMediaId = lastMediaId
                ) {
                    preparePlay(it as Track)
                }
                binding.multiQueueList.adapter = adapter

                if (context.areSystemAnimationsEnabled) {
                    binding.multiQueueList.scheduleLayoutAnimation()
                }
            }
        }
    }

    private fun updateQueueName() {
        val queueDataList = getQueueDataListFromJson(config.queueListJson)
        val currentQueue = queueDataList.find { it.queueId == currentQueueId }
        binding.currentQueueName.text = currentQueue?.name ?: context.getString(R.string.default_queue)

        if (config.queueId == currentQueueId) {
            binding.multiQueueSelectHeader.foreground = foregroundDrawable
        } else {
            binding.multiQueueSelectHeader.foreground = null
        }
    }

    private fun preparePlay(selectedTrack: Track) {
        val startIndex = tracks.indexOf(selectedTrack).coerceAtLeast(0)
        val startPositionMs = if (selectedTrack.isCurrent()) selectedTrack.lastPosition else 0
        val queueSource = "q:$currentQueueId"
        config.queueId = currentQueueId
        prepareAndPlay(tracks, showPlayback = config.showPlaybackActivity, queueSource, startIndex, startPositionMs = startPositionMs)
        binding.multiQueueSelectHeader.foreground = foregroundDrawable
        getAdapter()?.updateLastMediaId(selectedTrack.mediaStoreId)
    }

    override fun finishActMode() {
        getAdapter()?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = tracks.filter {
            it.title.contains(text, true) || ("${it.artist} - ${it.album}").contains(text, true)
        }.toMutableList() as ArrayList<Track>
        getAdapter()?.updateItems(filtered, text)
        binding.multiQueuePlaceholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchClosed() {
        getAdapter()?.updateItems(tracks)
        binding.multiQueuePlaceholder.beGoneIf(tracks.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, ACTIVITY_QUEUE) {
            val adapter = getAdapter() ?: return@ChangeSortingDialog
            val tracks = ArrayList(adapter.items)
            if (tracks.isEmpty()) {
                activity.toast(R.string.no_tracks)
                return@ChangeSortingDialog
            }
            tracks.sortSafely(config.queueSorting)
            adapter.updateItems(tracks, forceUpdate = true)

            // If current queue is playing queue
            if (currentQueueId == config.queueId) {
                val simpleControllerActivity = activity as SimpleControllerActivity
                simpleControllerActivity.withPlayer {
                    val currentTrackId = currentMediaItem?.getMediaStoreId()
                    val currentPositionMs = currentPosition
                    val currentIndex = tracks.indexOfFirst { it.mediaStoreId == currentTrackId }.coerceAtLeast(0)
                    prepareUsingTracks(tracks, startIndex = currentIndex, startPositionMs = currentPositionMs, play = isPlaying)
                }
            } else {
                val lastMediaId = getAdapter()?.lastMediaId ?: 0
                executeBackgroundThread {
                    val queueItems = tracks.mapIndexed { index, track ->
                        val isCurrent = track.mediaStoreId == lastMediaId
                        val lastPosition = if (isCurrent) track.lastPosition else 0
                        QueueItem(
                            id = 0,
                            queueId = currentQueueId,
                            trackId = track.mediaStoreId,
                            trackOrder = index,
                            isCurrent = isCurrent,
                            lastPosition = lastPosition
                        )
                    }
                    activity.audioHelper.resetQueue(currentQueueId, queueItems)
                }
            }
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        binding.multiQueuePlaceholder.setTextColor(textColor)
        binding.currentQueueName.setTextColor(textColor)
        binding.currentQueueArrow.applyColorFilter(textColor)
        binding.multiQueueFastscroller.updateColors(adjustedPrimaryColor)
        binding.multiQueuePlay.setColorFilter(adjustedPrimaryColor)
        getAdapter()?.updateColors(textColor)
    }

    private fun getAdapter() = binding.multiQueueList.adapter as? MultiQueueAdapter
}
