package com.simplemobiletools.musicplayer.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleControllerActivity
import com.simplemobiletools.musicplayer.adapters.SelectTracklistAdapter
import com.simplemobiletools.musicplayer.adapters.TracklistItem
import com.simplemobiletools.musicplayer.databinding.DialogSelectTracklistBinding
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.FAVORITE_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.FolderConfig
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread

class SelectTracklistDialog(val activity: SimpleControllerActivity) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectTracklistBinding::inflate)

    init {
        ensureBackgroundThread {
            val playlists = activity.audioHelper.getAllPlaylists().filter { it.id >= FAVORITE_TRACKS_PLAYLIST_ID }
            val albumList = activity.albumsDAO.getFavoriteAlbumList()
            val artistList = activity.artistDAO.getFavoriteArtistList()
            val folderNameList = FolderConfig.getInstance(activity).getFolderFavoriteList()
            activity.runOnUiThread {
                initDialog(playlists, albumList, artistList, folderNameList)
            }
        }
    }

    private fun initDialog(playlists: List<Playlist>, albumList: List<Album>, artistList: List<Artist>, folderNameList: List<String>) {
        val items = mutableListOf<TracklistItem>()

        if (playlists.isNotEmpty()) {
            items.add(TracklistItem.TracklistItemTitle(R.string.playlists))
            playlists.forEach { playlist ->
                items.add(TracklistItem.TracklistItemData("p:", playlist.title, TRACKLIST_PLAYLIST, playlist.id.toLong(), ""))
            }
        }

        if (albumList.isNotEmpty()) {
            items.add(TracklistItem.TracklistItemTitle(R.string.albums))
            albumList.forEach { album ->
                items.add(TracklistItem.TracklistItemData("a:", album.title + " • " + album.artist, TRACKLIST_ALBUM, album.id, ""))
            }
        }

        if (folderNameList.isNotEmpty()) {
            items.add(TracklistItem.TracklistItemTitle(R.string.folders))
            folderNameList.forEach { folderName ->
                items.add(TracklistItem.TracklistItemData("f:", folderName, TRACKLIST_FOLDER, 0, folderName))
            }
        }

        if (artistList.isNotEmpty()) {
            items.add(TracklistItem.TracklistItemTitle(R.string.artists))
            artistList.forEach { artist ->
                items.add(TracklistItem.TracklistItemData("t:", artist.title, TRACKLIST_ARTIST, artist.id, ""))
            }
        }

        val adapter = SelectTracklistAdapter(activity, items) { itemData ->
            activity.withPlayer {
                val startPlay = isPlaying
                executeBackgroundThread {
                    activity.onSelectTracklist(itemData.type, itemData.id, itemData.data, startPlay = startPlay)
                }
            }
            dialog?.dismiss()
        }
        binding.dialogSelectPlaylistList.adapter = adapter

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    companion object {
        const val TRACKLIST_PLAYLIST = 0
        const val TRACKLIST_ALBUM = 1
        const val TRACKLIST_ARTIST = 2
        const val TRACKLIST_FOLDER = 3
        const val TRACKLIST_QUEUE = 4

        fun SimpleControllerActivity.onSelectTracklist(tracklistType: Int, id: Long, data: String, fromShortcut: Boolean = false, startPlay: Boolean = true) {
            val result = handleSelectTracklist(tracklistType, id, data, startPlay)
            runOnUiThread {
                if (result == 0) {
                    toast(R.string.no_tracks)
                } else if (result == -1) {
                    toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                } else if (fromShortcut) {
                    toast(com.simplemobiletools.commons.R.string.ok)
                }
            }
        }

        private fun SimpleControllerActivity.handleSelectTracklist(tracklistType: Int, id: Long, data: String, startPlay: Boolean): Int {
            if (tracklistType == TRACKLIST_PLAYLIST) {
                val playlistTracks = audioHelper.getPlaylistTracks(id.toInt())
                if (playlistTracks.isEmpty()) return 0
                val lastMediaId = playlistDAO.getLastMediaId(id.toInt()) ?: 0L
                val startIndex = playlistTracks.indexOfFirst { track -> track.mediaStoreId == lastMediaId }.coerceAtLeast(0)
                val queueSource = "p:$id"

                prepareAndPlay(playlistTracks, false, queueSource, startIndex, startPlay = startPlay)
            } else if (tracklistType == TRACKLIST_ALBUM) {
                val playlistTracks = audioHelper.getAlbumTracks(id)
                if (playlistTracks.isEmpty()) return 0
                val lastMediaId = albumsDAO.getLastMediaId(id) ?: 0L
                val startIndex = playlistTracks.indexOfFirst { track -> track.mediaStoreId == lastMediaId }.coerceAtLeast(0)
                val queueSource = "a:$id"

                prepareAndPlay(playlistTracks, false, queueSource, startIndex, startPlay = startPlay)
            } else if (tracklistType == TRACKLIST_ARTIST) {
                val albums = audioHelper.getArtistAlbums(id)
                val playlistTracks = audioHelper.getAlbumTracks(albums)
                if (playlistTracks.isEmpty()) return 0
                val lastMediaId = artistDAO.getLastMediaId(id) ?: 0L
                val startIndex = playlistTracks.indexOfFirst { track -> track.mediaStoreId == lastMediaId }.coerceAtLeast(0)
                val queueSource = "t:$id"

                prepareAndPlay(playlistTracks, false, queueSource, startIndex, startPlay = startPlay)
            } else if (tracklistType == TRACKLIST_FOLDER) {
                val playlistTracks = audioHelper.getFolderTracks(data)
                if (playlistTracks.isEmpty()) return 0
                val lastMediaId = FolderConfig.getInstance(this).getLastMediaId(data)
                val startIndex = playlistTracks.indexOfFirst { track -> track.mediaStoreId == lastMediaId }.coerceAtLeast(0)
                val queueSource = "f:$data"

                prepareAndPlay(playlistTracks, false, queueSource, startIndex, startPlay = startPlay)
            } else if (tracklistType == TRACKLIST_QUEUE) {
                val tracks = audioHelper.getQueuedTracks(id)
                if (tracks.isEmpty()) return 0
                val startIndex = tracks.indexOfFirst { it.isCurrent() }.coerceAtLeast(0)
                val currentPositionMs = tracks[startIndex].lastPosition
                config.queueId = id
                prepareAndPlay(tracks, false, "q:$id", startIndex = startIndex, startPositionMs = currentPositionMs, startPlay = startPlay)
            } else {
                return -1
            }
            return 1
        }
    }
}
