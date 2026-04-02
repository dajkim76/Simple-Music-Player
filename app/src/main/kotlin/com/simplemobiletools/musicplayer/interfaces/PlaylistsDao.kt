package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.models.Playlist

@Dao
interface PlaylistsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(playlist: Playlist): Long

    @Delete
    fun deletePlaylists(playlists: List<Playlist>)

    @Query("SELECT * FROM playlists")
    fun getAll(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE title = :title COLLATE NOCASE")
    fun getPlaylistWithTitle(title: String): Playlist?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistWithId(id: Int): Playlist?

    @Update
    fun update(playlist: Playlist)

    @Query("UPDATE playlists SET favorite_time = :favoriteTime WHERE id = :id")
    fun updateFavorite(id: Long, favoriteTime: Long)

    @Transaction
    fun updateFavoriteData(favoriteData: List<Pair<Long, Long>>) {
        favoriteData.forEach { (id, favoriteTime) ->
            updateFavorite(id, favoriteTime)
        }
    }

    @Query("SELECT last_media_id FROM playlists WHERE id = :id")
    fun getLastMediaId(id: Int): Long?

    @Query("UPDATE playlists SET last_media_id = :lastMediaId WHERE id = :id")
    fun updateLastMediaId(id: Int, lastMediaId: Long): Int
}
