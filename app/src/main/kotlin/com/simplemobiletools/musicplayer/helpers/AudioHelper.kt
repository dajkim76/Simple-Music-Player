package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import androidx.media3.common.MediaItem
import com.simplemobiletools.commons.extensions.addBit
import com.simplemobiletools.commons.extensions.getParentPath
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.*
import org.greenrobot.eventbus.EventBus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AudioHelper(private val context: Context) {

    private val config = context.config

    fun insertTracks(tracks: List<Track>) {
        context.tracksDAO.insertAll(tracks)
    }

    fun getTrack(mediaStoreId: Long): Track? {
        return context.tracksDAO.getTrackWithMediaStoreId(mediaStoreId)
    }

    fun getAllTracks(): ArrayList<Track> {
        val tracks = context.tracksDAO.getAll()
            .applyProperFilenames(config.showFilename)

        tracks.sortSafely(config.trackSorting)
        return tracks
    }

    fun getAllFolders(): ArrayList<Folder> {
        val tracks = context.audioHelper.getAllTracks()
        val foldersMap = tracks.groupBy { it.folderName }
        val folders = ArrayList<Folder>()
        val excludedFolders = config.excludedFolders
        val folderConfig = FolderConfig(context)
        for ((title, folderTracks) in foldersMap) {
            val path = (folderTracks.firstOrNull()?.path?.getParentPath() ?: "").removeSuffix("/")
            if (excludedFolders.contains(path)) {
                continue
            }

            val folder = Folder(title, folderTracks.size, path, folderConfig.getFavoriteTime(path))
            folders.add(folder)
        }

        folders.sortSafely(config.folderSorting)
        return folders
    }

    fun getFolderTracks(folder: String): ArrayList<Track> {
        val tracks = context.tracksDAO.getTracksFromFolder(folder)
            .applyProperFilenames(config.showFilename)

        tracks.sortSafely(config.getProperFolderSorting(folder))
        return tracks
    }

    fun updateTrackInfo(newPath: String, artist: String, title: String, oldPath: String) {
        context.tracksDAO.updateSongInfo(newPath, artist, title, oldPath)
    }

    fun deleteTrack(mediaStoreId: Long) {
        context.tracksDAO.removeTrack(mediaStoreId)
    }

    fun deleteTracks(tracks: List<Track>) {
        tracks.forEach {
            deleteTrack(it.mediaStoreId)
        }
    }

    fun deletePlaylistTracks(tracks: List<Track>): Int {
        return context.tracksDAO.deletePlaylistTracks(tracks)
    }

    fun removeMostPlayedListTracks(tracks: List<Track>) {
        val dao = context.tracksDAO
        tracks.forEach { dao.resetPlayCount(it.id) }
    }

    fun removeRecentlyAddedPlaylistTracks(tracks: List<Track>) {
        val dao = context.tracksDAO
        tracks.forEach { dao.resetUpdatedTime(it.id) }
    }

    fun insertArtists(artists: List<Artist>) {
        context.artistDAO.insertAll(artists)
    }

    fun getAllArtists(): ArrayList<Artist> {
        val artists = context.artistDAO.getAll() as ArrayList<Artist>
        artists.sortSafely(config.artistSorting)
        return artists
    }

    fun getArtistAlbums(artistId: Long): ArrayList<Album> {
        return context.albumsDAO.getArtistAlbums(artistId) as ArrayList<Album>
    }

    fun getArtistAlbums(artists: List<Artist>): ArrayList<Album> {
        return artists.flatMap { getArtistAlbums(it.id) } as ArrayList<Album>
    }

    fun getArtistTracks(artistId: Long): ArrayList<Track> {
        return context.tracksDAO.getTracksFromArtist(artistId)
            .applyProperFilenames(config.showFilename)
    }

    fun getArtistTracks(artists: List<Artist>): ArrayList<Track> {
        return getAlbumTracks(
            albums = getArtistAlbums(artists)
        )
    }

    fun deleteArtist(id: Long) {
        context.artistDAO.deleteArtist(id)
    }

    fun deleteArtists(artists: List<Artist>) {
        artists.forEach {
            deleteArtist(it.id)
        }
    }

    fun insertAlbums(albums: List<Album>) {
        context.albumsDAO.insertAll(albums)
    }

    fun getAlbum(albumId: Long): Album? {
        return context.albumsDAO.getAlbumWithId(albumId)
    }

    fun getAllAlbums(): ArrayList<Album> {
        val albums = context.albumsDAO.getAll() as ArrayList<Album>
        albums.sortSafely(config.albumSorting)
        return albums
    }

    fun getAlbumTracks(albumId: Long): ArrayList<Track> {
        val tracks = context.tracksDAO.getTracksFromAlbum(albumId)
            .applyProperFilenames(config.showFilename)
        tracks.sortWith(compareBy({ it.discNumber }, { it.trackId }, { it.title.lowercase() }))
        return tracks
    }

    fun getAlbumTracks(albums: List<Album>): ArrayList<Track> {
        return albums.flatMap { getAlbumTracks(it.id) }
            .applyProperFilenames(config.showFilename)
    }

    private fun deleteAlbum(id: Long) {
        context.albumsDAO.deleteAlbum(id)
    }

    fun deleteAlbums(albums: List<Album>) {
        albums.forEach {
            deleteAlbum(it.id)
        }
    }

    fun insertPlaylist(playlist: Playlist): Long {
        return context.playlistDAO.insert(playlist)
    }

    fun updatePlaylist(playlist: Playlist) {
        context.playlistDAO.update(playlist)
    }

    fun getAllPlaylists(): ArrayList<Playlist> {
        return context.playlistDAO.getAll() as ArrayList<Playlist>
    }

    fun getAllGenres(): ArrayList<Genre> {
        val genres = context.genresDAO.getAll() as ArrayList<Genre>
        genres.sortSafely(config.genreSorting)
        return genres
    }

    fun getGenreTracks(genreId: Long): ArrayList<Track> {
        val tracks = context.tracksDAO.getGenreTracks(genreId)
            .applyProperFilenames(config.showFilename)

        tracks.sortSafely(config.trackSorting)
        return tracks
    }

    fun getGenreTracks(genres: List<Genre>): ArrayList<Track> {
        val tracks = genres.flatMap { context.tracksDAO.getGenreTracks(it.id) }
            .applyProperFilenames(config.showFilename)

        tracks.sortSafely(config.trackSorting)
        return tracks
    }

    private fun deleteGenre(id: Long) {
        context.genresDAO.deleteGenre(id)
    }

    fun deleteGenres(genres: List<Genre>) {
        genres.forEach {
            deleteGenre(it.id)
        }
    }

    fun insertGenres(genres: List<Genre>) {
        genres.forEach {
            context.genresDAO.insert(it)
        }
    }

    fun getPlaylistTracks(playlistId: Int): ArrayList<Track> {
        val tracks = if (playlistId == RECENTLY_ADDED_TRACKS_PLAYLIST_ID)
            context.tracksDAO.getTracksFromPlaylistRecentlyAdded().applyProperFilenames(config.showFilename)
        else if (playlistId == MOST_PLAYED_TRACKS_PLAYLIST_ID)
            context.tracksDAO.getTracksFromPlaylistMostPlayed().applyProperFilenames(config.showFilename)
        else if (playlistId == RECENTLY_PLAYED_TRACKS_PLAYLIST_ID)
            context.tracksDAO.getTracksFromPlaylistRecentlyPlayed().applyProperFilenames(config.showFilename)
        else if (playlistId == FAVORITE_TRACKS_PLAYLIST_ID)
            context.tracksDAO.getTracksFromPlaylistFavorite().applyProperFilenames(config.showFilename)
        else
            context.tracksDAO.getTracksFromPlaylist(playlistId).applyProperFilenames(config.showFilename)

        if (playlistId == ALL_TRACKS_PLAYLIST_ID || playlistId >= SMART_PLAYLIST_ID_MAX) { // Favorite playlist sortable
            tracks.sortSafely(config.getProperPlaylistSorting(playlistId))
        }
        return tracks
    }

    fun updateRecentPlayedTrack(track: Track): Long {
        val playListId = RECENTLY_PLAYED_TRACKS_PLAYLIST_ID
        return context.tracksDAO.getPlaylistTrack(playListId, track.mediaStoreId)?.let {
            it.updatedTime = System.currentTimeMillis()
            it.playCount += 1
            context.tracksDAO.updatePlayback(it.id, it.updatedTime, it.playCount)
            it.lastPosition
        } ?: run {
            val newTrack =
                track.copy(id = 0 /*Insert new track*/, playListId = playListId, playCount = 1, updatedTime = System.currentTimeMillis())
            context.tracksDAO.insert(newTrack)
            0
        }
    }

    fun updateRecentPlayedTrackLastPosition(mediaItem: MediaItem, lastPosition: Long) {
        val playListId = RECENTLY_PLAYED_TRACKS_PLAYLIST_ID
        val mediaStoreId = mediaItem.getMediaStoreId()
        context.tracksDAO.getPlaylistTrack(playListId, mediaStoreId)?.takeIf { it.lastPosition != lastPosition }?.let {
            context.tracksDAO.updateLastPosition(it.id, lastPosition)
        } ?: run {
            // insert new entry for lastPosition
            if (lastPosition > 0) {
                val newTrack = mediaItem.toTrack()?.copy(id = 0, playListId = playListId, lastPosition = lastPosition) ?: return
                context.tracksDAO.insert(newTrack)
            }
        }
    }

    fun isFavoriteTrack(track: Track): Boolean {
        return context.tracksDAO.getPlaylistTrack(FAVORITE_TRACKS_PLAYLIST_ID, track.mediaStoreId) != null
    }

    fun toggleFavorite(track: Track): Boolean {
        val playListId = FAVORITE_TRACKS_PLAYLIST_ID
        return context.tracksDAO.getPlaylistTrack(playListId, track.mediaStoreId)?.let {
            context.tracksDAO.delete(it)
            EventBus.getDefault().post(Events.PlaylistsUpdated())
            false
        } ?: run {
            val newTrack =
                track.copy(id = 0 /*Insert new track*/, playListId = playListId, updatedTime = System.currentTimeMillis())
            context.tracksDAO.insert(newTrack)
            EventBus.getDefault().post(Events.PlaylistsUpdated())
            true
        }
    }

    fun getPlaylistTrackCount(playlistId: Int): Int {
        return context.tracksDAO.getTracksCountFromPlaylist(playlistId)
    }

    fun updateOrderInPlaylist(playlistId: Int, trackId: Long) {
        context.tracksDAO.updateOrderInPlaylist(playlistId, trackId)
    }

    fun getTrackCue(fileStableId: Long): String {
        return context.cueDAO.getCue(fileStableId)?.cuesJson ?: ""
    }

    fun updateTrackCue(track: Track, cuesJson: String) {
        if (context.cueDAO.updateCue(track.fileStableId, cuesJson) == 0) {
            val cueEntity = CueEntity(track.fileStableId, track.path, track.fileLength, track.fileLastModified, cuesJson)
            context.cueDAO.insert(cueEntity)
        }
    }

    fun deletePlaylists(playlists: ArrayList<Playlist>) {
        context.playlistDAO.deletePlaylists(playlists)
        playlists.forEach {
            context.tracksDAO.removePlaylistSongs(it.id)
        }
    }

    fun removeInvalidAlbumsArtists() {
        val tracks = context.tracksDAO.getAll()
        val albums = context.albumsDAO.getAll()
        val artists = context.artistDAO.getAll()

        val invalidAlbums = albums.filter { album -> tracks.none { it.albumId == album.id } }
        deleteAlbums(invalidAlbums)

        val invalidArtists = artists.filter { artist -> tracks.none { it.artistId == artist.id } }
        deleteArtists(invalidArtists)
    }

    fun getQueuedTracks(queueItems: List<QueueItem> = context.queueDAO.getAll()): ArrayList<Track> {
        val allTracks = getAllTracks().associateBy { it.mediaStoreId }

        // make sure we fetch the songs in the order they were displayed in
        val tracks = queueItems.mapNotNull { queueItem ->
            val track = allTracks[queueItem.trackId]
            if (track != null) {
                if (queueItem.isCurrent) {
                    track.flags = track.flags.addBit(FLAG_IS_CURRENT)
                }
                track
            } else {
                null
            }
        }

        return tracks as ArrayList<Track>
    }

    /**
     * Executes [callback] with current track as quickly as possible and then proceeds to load the complete queue with all tracks.
     */
    fun getQueuedTracksLazily(callback: (tracks: List<Track>, startIndex: Int, startPositionMs: Long, isFirstPhase: Boolean) -> Unit) {
        ensureBackgroundThread {
            var queueItems = context.queueDAO.getAll()
            if (queueItems.isEmpty()) {
                initQueue()
                queueItems = context.queueDAO.getAll()
            }

            val currentItem = context.queueDAO.getCurrent()
            if (currentItem == null) {
                callback(emptyList(), 0, 0, true)
                return@ensureBackgroundThread
            }

            val currentTrack = getTrack(currentItem.trackId)
            if (currentTrack == null) {
                callback(emptyList(), 0, 0, true)
                return@ensureBackgroundThread
            }

            // immediately return the current track.
            val startPositionMs = currentItem.lastPosition.seconds.inWholeMilliseconds
            callback(listOf(currentTrack), 0, startPositionMs, true)

            // return the rest of the queued tracks.
            val queuedTracks = getQueuedTracks(queueItems)
            val currentIndex = queuedTracks.indexOfFirstOrNull { it.mediaStoreId == currentTrack.mediaStoreId } ?: 0
            callback(queuedTracks, currentIndex, startPositionMs, false)
        }
    }

    fun initQueue(): ArrayList<Track> {
        val tracks = getAllTracks()
        val queueItems = tracks.mapIndexed { index, mediaItem ->
            QueueItem(trackId = mediaItem.mediaStoreId, trackOrder = index, isCurrent = index == 0, lastPosition = 0)
        }

        resetQueue(queueItems)
        return tracks
    }

    fun resetQueue(items: List<QueueItem>, currentTrackId: Long? = null, startPosition: Long? = null) {
        context.queueDAO.deleteAllItems()
        context.queueDAO.insertAll(items)
        if (currentTrackId != null && startPosition != null) {
            val startPositionSeconds = startPosition.milliseconds.inWholeSeconds.toInt()
            context.queueDAO.saveCurrentTrackProgress(currentTrackId, startPositionSeconds)
        } else if (currentTrackId != null) {
            context.queueDAO.saveCurrentTrack(currentTrackId)
        }
    }
}

private fun Collection<Track>.applyProperFilenames(showFilename: Int): ArrayList<Track> {
    return distinctBy { "${it.path}/${it.mediaStoreId}" }
        .onEach { it.title = it.getProperTitle(showFilename) } as ArrayList<Track>
}
