package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.MenuItemCompat
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.executeBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.QueueAdapter
import com.simplemobiletools.musicplayer.databinding.ActivityQueueBinding
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.SelectQueueDialog
import com.simplemobiletools.musicplayer.dialogs.SelectQueueDialog.Companion.showQueueNameDialog
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.ACTIVITY_QUEUE
import com.simplemobiletools.musicplayer.helpers.RoomHelper
import com.simplemobiletools.musicplayer.models.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class QueueActivity : SimpleControllerActivity() {
    private var searchMenuItem: MenuItem? = null
    private var isSearchOpen = false
    private var tracksIgnoringSearch = ArrayList<Track>()

    private val binding by viewBinding(ActivityQueueBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        updateMaterialActivityViews(binding.queueCoordinator, binding.queueList, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.queueList, binding.queueToolbar)

        setupAdapter()
        setupFlingListener()
        binding.queueFastscroller.updateColors(getProperPrimaryColor())
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun queueItemsChanged(event: Events.QueueItemsChanged) {
        if (event.fromQueueActivity) {
            event.fromQueueActivity = false
            return
        }
        binding.queueList.adapter = null
        setupAdapter()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.queueToolbar, NavigationIcon.Arrow, searchMenuItem = searchMenuItem)
        getAdapter()?.updateCurrentTrack()
    }

    override fun onBackPressed() {
        if (isSearchOpen && searchMenuItem != null) {
            searchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        getAdapter()?.updateCurrentTrack()
    }

    private fun setupOptionsMenu() {
        setupSearch(binding.queueToolbar.menu)
        binding.queueToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.create_playlist_from_queue -> createPlaylistFromQueue()
                R.id.play_tracklist -> SelectTracklistDialog(this)
                R.id.sort -> changeSorting()
                R.id.create_new_queue -> createNewQueue()
                R.id.change_queue -> SelectQueueDialog(this)
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                onSearchOpened()
                isSearchOpen = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                onSearchClosed()
                isSearchOpen = false
                return true
            }
        })
    }

    private fun onSearchOpened() {
        val adapter = getAdapter() ?: return
        tracksIgnoringSearch = adapter.items
        adapter.updateItems(tracksIgnoringSearch, forceUpdate = true)
    }

    private fun onSearchClosed() {
        val adapter = getAdapter() ?: return
        adapter.updateItems(tracksIgnoringSearch, forceUpdate = true)
        binding.queuePlaceholder.beGoneIf(tracksIgnoringSearch.isNotEmpty())
    }

    private fun onSearchQueryChanged(text: String) {
        val filtered = tracksIgnoringSearch.filter { it.normalizeSearch(text, onlyTitleSearch = true) }.toMutableList() as ArrayList<Track>
        getAdapter()?.updateItems(filtered, text)
        binding.queuePlaceholder.beGoneIf(filtered.isNotEmpty())
    }

    private fun getAdapter(): QueueAdapter? {
        return binding.queueList.adapter as? QueueAdapter
    }

    private fun setupAdapter() {
        if (getAdapter() == null) {
            withPlayer {
                val tracks = currentMediaItemsShuffled.toTracks().toMutableList() as ArrayList<Track>
                binding.queueList.adapter = QueueAdapter(
                    activity = this@QueueActivity,
                    items = tracks,
                    currentTrack = currentMediaItem?.toTrack(),
                    recyclerView = binding.queueList
                ) {
                    val keepTrackLastPosition = config.keepTrackLastPosition
                    executeBackgroundThread {
                        val track = it as Track
                        var lastPosition = audioHelper.updateRecentPlayedTrack(track)
                        if (!keepTrackLastPosition) lastPosition = 0

                        withPlayer {
                            if (isPlaying) {
                                val currentItem = currentMediaItem
                                if (currentItem != null && currentItem.getMediaStoreId() != track.mediaStoreId) {
                                    val playingLastPosition = currentPosition
                                    if (keepTrackLastPosition) {
                                        executeBackgroundThread {
                                            audioHelper.updateRecentPlayedTrackLastPosition(currentItem, playingLastPosition)
                                        }
                                    }
                                } else if (currentItem != null && currentItem.getMediaStoreId() == track.mediaStoreId) {
                                    lastPosition = if (keepTrackLastPosition) currentPosition else 0
                                }
                            }

                            val startIndex = currentMediaItems.indexOfTrack(track)
                            if (startIndex >= 0) {
                                seekTo(startIndex, lastPosition)
                                if (!isReallyPlaying) {
                                    play()
                                }
                            }
                        }
                    }
                }

                if (areSystemAnimationsEnabled) {
                    binding.queueList.scheduleLayoutAnimation()
                }

                val currentPosition = shuffledMediaItemsIndices.indexOf(currentMediaItemIndex)
                if (currentPosition > 0) {
                    binding.queueList.lazySmoothScroll(currentPosition)
                }
            }
        }
    }

    private fun createPlaylistFromQueue() {
        NewPlaylistDialog(this) { newPlaylistId ->
            val tracks = ArrayList<Track>()
            getAdapter()?.items?.forEach {
                it.playListId = newPlaylistId
                tracks.add(it)
            }

            ensureBackgroundThread {
                RoomHelper(this).insertTracksWithPlaylist(tracks)
            }
            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun changeSorting() {
        ChangeSortingDialog(this, ACTIVITY_QUEUE) {
            val adapter = getAdapter() ?: return@ChangeSortingDialog
            val tracks = ArrayList(adapter.items)
            if (tracks.isEmpty()) {
                toast(R.string.no_tracks)
                return@ChangeSortingDialog
            }
            tracks.sortSafely(config.queueSorting)
            adapter.updateItems(tracks, forceUpdate = true)
            val queueId = config.queueId

            withPlayer {
                val currentTrackId = currentMediaItem?.getMediaStoreId()
                val currentPositionMs = currentPosition
                val currentIndex = tracks.indexOfFirst { it.mediaStoreId == currentTrackId }.coerceAtLeast(0)
                prepareUsingTracks(tracks, startIndex = currentIndex, startPositionMs = currentPositionMs, play = isPlaying) {
                    Events.QueueItemsChanged.setNeedToPostFromQueueActivity()
                }
            }
        }
    }

    private fun createNewQueue() {
        val adapter = getAdapter() ?: return
        val tracks = ArrayList(adapter.items)
        if (tracks.isEmpty()) {
            toast(R.string.no_tracks)
            return
        }
        showQueueNameDialog("") { queueName ->
            val queueId = config.nextQueueId
            config.queueId = queueId
            config.nextQueueId++
            // append new Queue
            val queueDataList = getQueueDataListFromJson(config.queueListJson)
            queueDataList.toMutableList().also {
                it.add(QueueData(queueName, queueId))
                config.queueListJson = it.toJson()
            }

            withPlayer {
                val currentTrackId = currentMediaItem?.getMediaStoreId()
                val currentPositionMs = currentPosition
                val queueItems = tracks.mapIndexed { index, track ->
                    val isCurrent = track.mediaStoreId == currentTrackId
                    val lastPosition = if (isCurrent) currentPositionMs else 0
                    QueueItem(id = 0, queueId = queueId, trackId = track.mediaStoreId, trackOrder = index, isCurrent = isCurrent, lastPosition = lastPosition)
                }

                executeBackgroundThread {
                    audioHelper.resetQueue(queueId, queueItems)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlingListener() {
        val flingListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false

                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                val SWIPE_THRESHOLD = 50
                val SWIPE_VELOCITY_THRESHOLD = 50

                if (Math.abs(diffX) <= Math.abs(diffY)) {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0 && !binding.queueList.canScrollVertically(-1)) {
                            onSwipeDown()
                            return true
                        }
                    }
                }
                return false
            }

            private fun onSwipeDown() {
                finish()
            }
        }

        val gestureDetector = GestureDetectorCompat(this, flingListener)

        binding.queueList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        val touchListener = View.OnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else {
                v.performClick()
                false
            }
        }

        binding.queuePlaceholder.setOnTouchListener(touchListener)
        binding.queueCoordinator.setOnTouchListener(touchListener)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, com.simplemobiletools.commons.R.anim.slide_down)
    }
}
