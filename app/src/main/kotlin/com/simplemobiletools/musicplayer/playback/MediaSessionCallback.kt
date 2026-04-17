package com.simplemobiletools.musicplayer.playback

import android.os.Bundle
import android.os.ConditionVariable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.EXTRA_NEXT_MEDIA_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_SHUFFLE_INDICES
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.playback.library.RESUME_PLAYBACK_ID
import com.simplemobiletools.musicplayer.playback.player.updatePlaybackState
import java.util.concurrent.Executors

@UnstableApi
internal fun PlaybackService.getMediaSessionCallback() = object : MediaLibrarySession.Callback {
    private val browsers = mutableMapOf<MediaSession.ControllerInfo, String>()
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))
    }

    private fun <T> callWhenSourceReady(action: () -> T): ListenableFuture<T> {
        val conditionVariable = ConditionVariable()
        return if (mediaItemProvider.whenReady { conditionVariable.open() }) {
            executorService.submit<T> {
                action()
            }
        } else {
            executorService.submit<T> {
                conditionVariable.block()
                action()
            }
        }
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
        for (command in customCommands) {
            availableSessionCommands.add(command)
        }

        // Search function is removed for now because the loading bar spins infinitely during searches., not working
        availableSessionCommands.remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        val customLayout = getCustomLayout()
        if (customLayout.isNotEmpty() && controller.controllerVersion != 0) {
            mediaSession.setCustomLayout(controller, customLayout)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        val command = CustomCommands.fromSessionCommand(customCommand)
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))

        when (command) {
            CustomCommands.CLOSE_PLAYER -> stopService()
            CustomCommands.RELOAD_CONTENT -> reloadContent()
            CustomCommands.TOGGLE_SLEEP_TIMER -> toggleSleepTimer()
            CustomCommands.TOGGLE_SKIP_SILENCE -> player.setSkipSilence(config.gaplessPlayback)
            CustomCommands.SET_SHUFFLE_ORDER -> setShuffleOrder(args)
            CustomCommands.SET_NEXT_ITEM -> setNextItem(args)
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (params != null && params.isRecent) {
            // The service currently does not support recent playback. Tell System UI by returning
            // an error of type 'RESULT_ERROR_NOT_SUPPORTED' for a `params.isRecent` request. See
            // https://github.com/androidx/media/issues/355
            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        return Futures.immediateFuture(
            LibraryResult.ofItem(
                mediaItemProvider.getRootItem(),
                params
            )
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ) = callWhenSourceReady {
        currentRoot = parentId
        val children = mediaItemProvider.getChildren(parentId)
            ?: return@callWhenSourceReady LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)

        LibraryResult.ofItemList(children, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ) = callWhenSourceReady {
        val item = mediaItemProvider[mediaId]
            ?: return@callWhenSourceReady LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)

        LibraryResult.ofItem(item, null)
    }

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: MediaLibraryService.LibraryParams?
    ) = callWhenSourceReady {
        val children = mediaItemProvider.getChildren(parentId)
            ?: return@callWhenSourceReady LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)

        browsers[browser] = parentId
        session.notifyChildrenChanged(browser, parentId, children.size, params)
        LibraryResult.ofVoid()
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val settableFuture = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        executorService.execute {
            mediaItemProvider.getRecentItemsLazily { it, isFirstPhase ->
                // resume playback as quickly as possible: https://github.com/androidx/media/issues/111
                if (isFirstPhase) {
                    settableFuture.set(it)
                } else {
                    player.addRemainingMediaItems(it.mediaItems, it.startIndex)
                }
            }
        }

        return settableFuture
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (controller.packageName == packageName) {
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }

        // this is to avoid single items in the queue: https://github.com/androidx/media/issues/156
        val automotiveMediaId = mediaItems[0].mediaId
        val (mediaStoreId, queueSource) = decodeAutomotiveMediaId(automotiveMediaId)

        saveCurrentMediaLastPosition(mediaSession)
        if (queueSource != null) {
            if (queueSource.startsWith("q:")) {
                val queueId = queueSource.substring(2).toLong()
                val lastMediaId = if (mediaStoreId == RESUME_PLAYBACK_ID) null else mediaStoreId
                val itemsWithStartPosition = mediaItemProvider.getQueueItemsWithStartPosition(queueId, lastMediaId)
                if (itemsWithStartPosition.mediaItems.isNotEmpty()) {
                    config.lastQueueSource = queueSource
                    config.queueId = queueId
                    // save recently played track
                    val index = itemsWithStartPosition.startIndex
                    if (index >= 0) {
                        val saveItem = itemsWithStartPosition.mediaItems[index]
                        saveItem.toTrack()?.let {
                            audioHelper.updateRecentPlayedTrack(it)
                        }
                    }
                    return Futures.immediateFuture(itemsWithStartPosition)
                }
            } else if (queueSource.startsWith("p:")) {  // playlist
                val playlistId = queueSource.substring(2).toInt()
                val lastMediaId = if (mediaStoreId == RESUME_PLAYBACK_ID) playlistDAO.getLastMediaId(playlistId)?.toString() else mediaStoreId
                val trackList = audioHelper.getPlaylistTracks(playlistId)
                if (trackList.isNotEmpty()) {
                    config.lastQueueSource = queueSource
                    config.queueId = 0
                    return Futures.immediateFuture(getMediaItemsWithStartPosition(trackList, lastMediaId))
                }
            } else if (queueSource.startsWith("a:")) { //album
                val albumId = queueSource.substring(2).toLong()
                val lastMediaId = if (mediaStoreId == RESUME_PLAYBACK_ID) albumsDAO.getLastMediaId(albumId)?.toString() else mediaStoreId
                val trackList = audioHelper.getAlbumTracks(albumId)
                if (trackList.isNotEmpty()) {
                    config.lastQueueSource = queueSource
                    config.queueId = 0
                    return Futures.immediateFuture(getMediaItemsWithStartPosition(trackList, lastMediaId))
                }
            } else if (queueSource.startsWith("f:")) { // folder
                val folderName = queueSource.substring(2)
                val lastMediaId =
                    if (mediaStoreId == RESUME_PLAYBACK_ID) folderConfig.getLastMediaId(folderName).takeIf { it != 0L }?.toString() else mediaStoreId
                val trackList = audioHelper.getFolderTracks(folderName)
                if (trackList.isNotEmpty()) {
                    config.lastQueueSource = queueSource
                    config.queueId = 0
                    return Futures.immediateFuture(getMediaItemsWithStartPosition(trackList, lastMediaId))
                }
            }

            // If you delete a list on the phone, itemList may be empty.
            // The itemList read by mediaItemProvider.getChildren(queueSource) has mediaIds encoded for automotive,
            // so passing it as Queue items can cause issues in the app. Therefore, it is better to respond with emptyList().
            return super.onSetMediaItems(mediaSession, controller, emptyList(), C.INDEX_UNSET, 0)
        } else {
            // from Tracks , Genres
            config.lastQueueSource = ""
            config.queueId = 0
        }

        val currentItems = mediaItemProvider.getChildren(currentRoot).orEmpty()

        // Tracks, Genres 's automotiveMediaId is not encoded, So there is no problem using this in the app.
        if (BuildConfig.DEBUG) {
            val allValidMediaStoreId = currentItems.all { !it.mediaId.contains(':') && !it.mediaId.startsWith("__") }
            check(allValidMediaStoreId)
        }

        val startItemIndex = currentItems.indexOfFirst { it.mediaId == automotiveMediaId }
        if (startItemIndex >= 0) {
            return super.onSetMediaItems(mediaSession, controller, currentItems, startItemIndex, startPositionMs)
        } else {
            // fallback: If currentRoot is incorrect, Use `Default queue` items,
            val queuedItemsWithStartPosition = mediaItemProvider.getQueueItemsWithStartPosition(0)
            return Futures.immediateFuture(queuedItemsWithStartPosition)
        }
    }

    // Save the media currently playing and the position.
    private fun saveCurrentMediaLastPosition(mediaSession: MediaSession) {
        with(mediaSession) {
            if (player.isPlaying) {
                val currentItem = player.currentMediaItem ?: return@with
                val playPosition = player.currentPosition
                if (config.keepTrackLastPosition) {
                    audioHelper.updateRecentPlayedTrackLastPosition(currentItem, playPosition)
                }
                audioHelper.updateQueueSourceLastMedia(config.lastQueueSource, currentItem.getMediaStoreId(), playPosition)
            }
        }
    }

    private fun getMediaItemsWithStartPosition(trackList: List<Track>, lastMediaId: String?): MediaItemsWithStartPosition {
        val mediaItemList = trackList.map { it.toMediaItem() }
        val startIndex = trackList
            .indexOfFirst { it.mediaStoreId.toString() == lastMediaId } // startIndex can be C.INDEX_UNSET(-1)
        val startPositionMs = if (startIndex >= 0) audioHelper.updateRecentPlayedTrack(trackList[startIndex]) else 0L
        return MediaItemsWithStartPosition(mediaItemList, startIndex, startPositionMs)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val items = mediaItems.map { mediaItem ->
            if (mediaItem.requestMetadata.searchQuery != null) {
                getMediaItemFromSearchQuery(mediaItem.requestMetadata.searchQuery!!)
            } else {
                mediaItemProvider[mediaItem.mediaId] ?: mediaItem
            }
        }

        return Futures.immediateFuture(items)
    }

    private fun normalize(query: String) = query.trim().lowercase()

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val searchQuery = normalize(query)
        return mediaItemProvider.makeResultsFromSearch(searchQuery)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val searchQuery = normalize(query)
        val results = mediaItemProvider.getResultsFromSearchCache(searchQuery)

        // paging 처리
        val fromIndex = page * pageSize
        val toIndex = minOf(fromIndex + pageSize, results.size)

        val paged = if (fromIndex < toIndex) {
            results.subList(fromIndex, toIndex)
        } else {
            emptyList()
        }

        return Futures.immediateFuture(
            LibraryResult.ofItemList(paged, params)
        )
    }

    private fun getMediaItemFromSearchQuery(query: String): MediaItem {
        return mediaItemProvider.getItemFromSearch(query.lowercase()) ?: mediaItemProvider.getRandomItem()
    }

    private fun reloadContent() {
        mediaItemProvider.reload()
        mediaItemProvider.whenReady {
            val rootItem = mediaItemProvider.getRootItem()
            val rootItemCount = mediaItemProvider.getChildren(rootItem.mediaId)?.size ?: 0

            executorService.execute {
                browsers.forEach { (browser, parentId) ->
                    val itemCount = mediaItemProvider.getChildren(parentId)?.size ?: 0
                    mediaSession.notifyChildrenChanged(browser, parentId, itemCount, null)
                    mediaSession.notifyChildrenChanged(browser, rootItem.mediaId, rootItemCount, null)
                }
            }
        }
    }

    private fun setShuffleOrder(args: Bundle) {
        val indices = args.getIntArray(EXTRA_SHUFFLE_INDICES) ?: return
        withPlayer {
            setShuffleIndices(indices)
        }
    }

    private fun setNextItem(args: Bundle) {
        val mediaId = args.getString(EXTRA_NEXT_MEDIA_ID) ?: return
        callWhenSourceReady {
            val mediaItem = mediaItemProvider[mediaId] ?: return@callWhenSourceReady
            withPlayer {
                setNextMediaItem(mediaItem)
                updatePlaybackState()
            }
        }
    }
}
