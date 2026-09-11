package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import com.google.gson.Gson
import com.simplemobiletools.commons.extensions.internalStoragePath
import com.simplemobiletools.commons.extensions.random
import com.simplemobiletools.commons.extensions.sdCardPath
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.models.*
import java.io.InputStream
import java.io.OutputStream

class BackupHelper(private val context: Context) {

    private fun getStorageRoots(): List<String> {
        val roots = mutableListOf<String>()
        val internal = context.internalStoragePath.trimEnd('/')
        if (internal.isNotEmpty()) {
            roots.add(internal)
        }
        val sdCard = context.sdCardPath.trimEnd('/')
        if (sdCard.isNotEmpty()) {
            roots.add(sdCard)
        }
        return roots
    }

    private fun getRelativePath(path: String, storageRoots: List<String>): String {
        for (root in storageRoots) {
            if (path.startsWith(root)) {
                return path.substring(root.length).trimStart('/')
            }
        }
        val storagePrefix = "/storage/"
        if (path.startsWith(storagePrefix)) {
            val afterStorage = path.substring(storagePrefix.length)
            val slashIndex = afterStorage.indexOf('/')
            if (slashIndex != -1) {
                return afterStorage.substring(slashIndex + 1)
            }
        }
        return path.substringAfterLast('/')
    }

    private fun Track.toIdentifier(storageRoots: List<String>): BackupTrackIdentifier {
        val fileName = path.substringAfterLast('/')
        val relPath = getRelativePath(path, storageRoots)
        return BackupTrackIdentifier(
            relativePath = relPath,
            fileName = fileName,
            fileLength = fileLength,
            fileLastModified = fileLastModified,
            title = title,
            artist = artist,
            duration = duration
        )
    }

    fun exportData(outputStream: OutputStream, callback: (success: Boolean) -> Unit) {
        ensureBackgroundThread {
            try {
                val storageRoots = getStorageRoots()
                val allTracks = context.tracksDAO.getAll()
                val trackByIdMap = allTracks.associateBy { it.mediaStoreId }

                // 1. Queues
                val queueDataList = context.audioHelper.getAllQueueList()
                val backupQueues = mutableListOf<BackupQueueItem>()
                queueDataList.forEach { queueData ->
                    val queueItems = context.queueDAO.getAll(queueData.queueId)
                    queueItems.forEach { item ->
                        val track = trackByIdMap[item.trackId]
                        if (track != null) {
                            backupQueues.add(
                                BackupQueueItem(
                                    queueName = queueData.name,
                                    queueId = queueData.queueId,
                                    track = track.toIdentifier(storageRoots),
                                    trackOrder = item.trackOrder,
                                    isCurrent = item.isCurrent,
                                    lastPosition = item.lastPosition
                                )
                            )
                        }
                    }
                }

                // 2. Playlists
                val playlists = context.playlistDAO.getAll().filter { it.id >= SMART_PLAYLIST_ID_MAX }
                val backupPlaylists = playlists.map { playlist ->
                    val tracks = context.tracksDAO.getTracksFromPlaylist(playlist.id)
                    BackupPlaylist(
                        title = playlist.title,
                        tracks = tracks.map { track ->
                            BackupPlaylistTrack(
                                track = track.toIdentifier(storageRoots),
                                orderInPlaylist = track.orderInPlaylist,
                                updatedTime = track.updatedTime
                            )
                        }
                    )
                }

                // 3. Favorites
                // Track favorites
                val favoriteTracks = context.tracksDAO.getTracksFromPlaylistFavorite().map { it.toIdentifier(storageRoots) }
                // Playlist favorites
                val favoritePlaylists = context.playlistDAO.getAll().filter { it.favoriteTime > 0 }.sortedByDescending { it.favoriteTime }.map { it.title }
                // Album favorites
                val favoriteAlbums = context.albumsDAO.getFavoriteAlbumList().map { BackupFavoriteAlbum(it.title, it.artist) }
                // Artist favorites
                val favoriteArtists = context.artistDAO.getFavoriteArtistList().map { it.title }
                // Folder favorites
                val favoriteFolders = FolderConfig.getInstance(context).getFolderFavoriteList()

                val backupFavorites = BackupFavorites(
                    favoriteTracks = favoriteTracks,
                    favoritePlaylists = favoritePlaylists,
                    favoriteAlbums = favoriteAlbums,
                    favoriteArtists = favoriteArtists,
                    favoriteFolders = favoriteFolders
                )

                // 4. Cues
                val allCues = context.cueDAO.getAllCues()
                val trackByStableIdMap = allTracks.associateBy { it.fileStableId }
                val trackByPathMap = allTracks.associateBy { it.path }
                val backupCues = allCues.map { cueEntity ->
                    val track = trackByStableIdMap[cueEntity.fileStableId] ?: trackByPathMap[cueEntity.path]
                    val identifier = track?.toIdentifier(storageRoots) ?: run {
                        val fileName = cueEntity.path.substringAfterLast('/')
                        val relPath = getRelativePath(cueEntity.path, storageRoots)
                        BackupTrackIdentifier(
                            relativePath = relPath,
                            fileName = fileName,
                            fileLength = cueEntity.fileLength,
                            fileLastModified = cueEntity.fileLastModified
                        )
                    }
                    BackupCueItem(track = identifier, cuesJson = cueEntity.cuesJson)
                }

                val backupData = BackupData(
                    version = 1,
                    queues = backupQueues,
                    playlists = backupPlaylists,
                    favorites = backupFavorites,
                    cues = backupCues
                )

                outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    Gson().toJson(backupData, writer)
                }
                callback(true)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }
    }

    fun restoreData(
        inputStream: InputStream,
        options: com.simplemobiletools.musicplayer.dialogs.RestoreOptions,
        callback: (success: Boolean) -> Unit
    ) {
        ensureBackgroundThread {
            try {
                val backupData = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    Gson().fromJson(reader, BackupData::class.java)
                }

                if (backupData == null) {
                    callback(false)
                    return@ensureBackgroundThread
                }

                val storageRoots = getStorageRoots()
                val currentTracks = context.tracksDAO.getFilteredAll()

                // Pre-index current tracks for fast matching
                // Key 1: "relPath|fileLength"
                val relMap = HashMap<String, Track>()
                // Key 2: "fileName|fileLength"
                val nameSizeMap = HashMap<String, MutableList<Track>>()
                // Key 3: "title|artist|duration"
                val metaMap = HashMap<String, Track>()

                for (track in currentTracks) {
                    val rel = getRelativePath(track.path, storageRoots)
                    relMap["$rel|${track.fileLength}"] = track

                    val fname = track.path.substringAfterLast('/')
                    nameSizeMap.getOrPut("$fname|${track.fileLength}") { ArrayList() }.add(track)

                    metaMap["${track.title.trim()}|${track.artist.trim()}|${track.duration}"] = track
                }

                fun findMatch(identifier: BackupTrackIdentifier): Track? {
                    // Match 1: Relative path + file size
                    val byRel = relMap["${identifier.relativePath}|${identifier.fileLength}"]
                    if (byRel != null) return byRel

                    // Match 2: Filename + file size
                    val byNameList = nameSizeMap["${identifier.fileName}|${identifier.fileLength}"]
                    if (!byNameList.isNullOrEmpty()) {
                        if (identifier.fileLastModified > 0) {
                            val sameDate = byNameList.find { it.fileLastModified == identifier.fileLastModified }
                            if (sameDate != null) return sameDate
                        }
                        return byNameList.first()
                    }

                    // Match 3: Title + Artist + Duration fallback
                    if (identifier.title.isNotEmpty() && identifier.duration > 0) {
                        val byMeta = metaMap["${identifier.title.trim()}|${identifier.artist.trim()}|${identifier.duration}"]
                        if (byMeta != null) return byMeta
                    }
                    return null
                }

                // 1. Restore Queuelist
                if (options.restoreQueue && !backupData.queues.isNullOrEmpty()) {
                    val queuesGrouped = backupData.queues.groupBy { it.queueId to it.queueName }
                    val existingQueues = getQueueDataListFromJson(context.config.queueListJson).toMutableList()

                    queuesGrouped.forEach { (queueKey, items) ->
                        val (backupQueueId, queueName) = queueKey
                        val targetQueueId = if (backupQueueId == 0L) {
                            0L
                        } else {
                            val existing = existingQueues.find { it.name == queueName }
                            if (existing != null) {
                                existing.queueId
                            } else {
                                val newId = System.currentTimeMillis() + (0..999).random()
                                existingQueues.add(QueueData(queueName, newId))
                                newId
                            }
                        }

                        // Clear existing items in this queue before restoring
                        context.queueDAO.deleteAllItems(targetQueueId)

                        val queueItemsToInsert = mutableListOf<QueueItem>()
                        items.forEach { backupItem ->
                            val matchedTrack = findMatch(backupItem.track)
                            if (matchedTrack != null) {
                                queueItemsToInsert.add(
                                    QueueItem(
                                        id = 0,
                                        queueId = targetQueueId,
                                        trackId = matchedTrack.mediaStoreId,
                                        trackOrder = backupItem.trackOrder,
                                        isCurrent = backupItem.isCurrent,
                                        lastPosition = backupItem.lastPosition
                                    )
                                )
                            }
                        }
                        if (queueItemsToInsert.isNotEmpty()) {
                            context.queueDAO.insertAll(queueItemsToInsert)
                        }
                    }
                    context.config.queueListJson = existingQueues.toJson()
                }

                // 2. Restore Playlists
                if (options.restorePlaylist && !backupData.playlists.isNullOrEmpty()) {
                    backupData.playlists.forEach { backupPlaylist ->
                        val existingPlaylist = context.playlistDAO.getPlaylistWithTitle(backupPlaylist.title)
                        val playlistId = existingPlaylist?.id ?: run {
                            val newPlaylist = Playlist(0, backupPlaylist.title)
                            context.playlistDAO.insert(newPlaylist).toInt()
                        }

                        if (playlistId > 0) {
                            // Remove existing tracks in playlist to avoid duplicates on restore
                            context.tracksDAO.removePlaylistSongs(playlistId)

                            val tracksToInsert = ArrayList<Track>()
                            backupPlaylist.tracks.forEach { playlistTrack ->
                                val matchedTrack = findMatch(playlistTrack.track)
                                if (matchedTrack != null) {
                                    val newTrack = matchedTrack.copy(
                                        id = 0,
                                        playListId = playlistId,
                                        orderInPlaylist = playlistTrack.orderInPlaylist,
                                        updatedTime = playlistTrack.updatedTime
                                    )
                                    tracksToInsert.add(newTrack)
                                }
                            }
                            if (tracksToInsert.isNotEmpty()) {
                                context.tracksDAO.insertAll(tracksToInsert)
                            }
                        }
                    }
                }

                // 3. Restore Favorites
                if (options.restoreFavorite && backupData.favorites != null) {
                    val favs = backupData.favorites
                    val baseTime = System.currentTimeMillis()

                    // Favorite tracks (sorted by updated_time DESC)
                    val favoriteTracksList = favs.favoriteTracks
                    if (!favoriteTracksList.isNullOrEmpty()) {
                        favoriteTracksList.forEachIndexed { index, favTrack ->
                            val matchedTrack = findMatch(favTrack)
                            if (matchedTrack != null) {
                                val favTime = baseTime - (index * 1000L)
                                val playListId = FAVORITE_TRACKS_PLAYLIST_ID
                                val existing = context.tracksDAO.getPlaylistTrack(playListId, matchedTrack.mediaStoreId)
                                if (existing != null) {
                                    context.tracksDAO.updatePlayback(existing.id, favTime, existing.playCount)
                                } else {
                                    val newTrack = matchedTrack.copy(
                                        id = 0,
                                        playListId = playListId,
                                        updatedTime = favTime
                                    )
                                    context.tracksDAO.insert(newTrack)
                                }
                            }
                        }
                    }

                    // Favorite playlists
                    favs.favoritePlaylists?.forEachIndexed { index, playlistTitle ->
                        val playlist = context.playlistDAO.getPlaylistWithTitle(playlistTitle)
                        if (playlist != null) {
                            val favTime = baseTime - (index * 1000L)
                            context.playlistDAO.updateFavorite(playlist.id.toLong(), favTime)
                        }
                    }

                    // Favorite albums
                    favs.favoriteAlbums?.forEachIndexed { index, favAlbum ->
                        val album = context.albumsDAO.getAll().find {
                            it.title.equals(favAlbum.title, ignoreCase = true) &&
                                (favAlbum.artist.isEmpty() || it.artist.equals(favAlbum.artist, ignoreCase = true))
                        }
                        if (album != null) {
                            val favTime = baseTime - (index * 1000L)
                            context.albumsDAO.updateFavorite(album.id, favTime)
                        }
                    }

                    // Favorite artists
                    favs.favoriteArtists?.forEachIndexed { index, artistTitle ->
                        val artist = context.artistDAO.getAll().find { it.title.equals(artistTitle, ignoreCase = true) }
                        if (artist != null) {
                            val favTime = baseTime - (index * 1000L)
                            context.artistDAO.updateFavorite(artist.id, favTime)
                        }
                    }

                    // Favorite folders
                    if (!favs.favoriteFolders.isNullOrEmpty()) {
                        val folderFavData = favs.favoriteFolders.mapIndexed { index, folder ->
                            folder to (baseTime - (index * 1000L))
                        }
                        FolderConfig.getInstance(context).setFolderFavoriteTime(folderFavData)
                    }
                }

                // 4. Restore Cues
                if (options.restoreCue && !backupData.cues.isNullOrEmpty()) {
                    backupData.cues.forEach { cueItem ->
                        val matchedTrack = findMatch(cueItem.track)
                        if (matchedTrack != null) {
                            context.audioHelper.updateTrackCue(matchedTrack, cueItem.cuesJson)
                            CueListCache.updateCacheByCueJson(matchedTrack.fileStableId, cueItem.cuesJson)
                        }
                    }
                }

                callback(true)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }
    }
}
