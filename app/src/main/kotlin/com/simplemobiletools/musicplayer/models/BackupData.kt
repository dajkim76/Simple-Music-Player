package com.simplemobiletools.musicplayer.models

import com.google.gson.annotations.SerializedName

data class BackupData(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("queues") val queues: List<BackupQueueItem>? = null,
    @SerializedName("playlists") val playlists: List<BackupPlaylist>? = null,
    @SerializedName("favorites") val favorites: BackupFavorites? = null,
    @SerializedName("cues") val cues: List<BackupCueItem>? = null
)

data class BackupTrackIdentifier(
    @SerializedName("relative_path") val relativePath: String = "",
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("file_length") val fileLength: Long = 0L,
    @SerializedName("file_last_modified") val fileLastModified: Long = 0L,
    @SerializedName("title") val title: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("duration") val duration: Int = 0
)

data class BackupQueueItem(
    @SerializedName("queue_name") val queueName: String,
    @SerializedName("queue_id") val queueId: Long,
    @SerializedName("track") val track: BackupTrackIdentifier,
    @SerializedName("track_order") val trackOrder: Int,
    @SerializedName("is_current") val isCurrent: Boolean,
    @SerializedName("last_position") val lastPosition: Long
)

data class BackupPlaylist(
    @SerializedName("title") val title: String,
    @SerializedName("tracks") val tracks: List<BackupPlaylistTrack>
)

data class BackupPlaylistTrack(
    @SerializedName("track") val track: BackupTrackIdentifier,
    @SerializedName("order_in_playlist") val orderInPlaylist: Int = 0,
    @SerializedName("updated_time") val updatedTime: Long = 0L
)

data class BackupFavorites(
    @SerializedName("favorite_tracks") val favoriteTracks: List<BackupTrackIdentifier>? = null,
    @SerializedName("favorite_playlists") val favoritePlaylists: List<String>? = null,
    @SerializedName("favorite_albums") val favoriteAlbums: List<BackupFavoriteAlbum>? = null,
    @SerializedName("favorite_artists") val favoriteArtists: List<String>? = null,
    @SerializedName("favorite_folders") val favoriteFolders: List<String>? = null
)

data class BackupFavoriteAlbum(
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String = ""
)

data class BackupCueItem(
    @SerializedName("track") val track: BackupTrackIdentifier,
    @SerializedName("cues_json") val cuesJson: String
)
