package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.models.Artist

@Dao
interface ArtistsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(artist: Artist): Long

    @Query("UPDATE artists SET title = :title, album_cnt = :albumCnt, track_cnt = :trackCnt, album_art = :albumArt WHERE id = :id")
    fun update(id: Long, title: String, albumCnt: Int, trackCnt: Int, albumArt: String): Int

    @Transaction
    open fun updateAllOrInsert(artists: List<Artist>) {
        artists.forEach {
            if (update(it.id, it.title, it.albumCnt, it.trackCnt, it.albumArt) == 0) {
                insert(it)
            }
        }
    }

    @Query("UPDATE artists SET favorite_time = :favoriteTime WHERE id = :id")
    fun updateFavorite(id: Long, favoriteTime: Long)

    @Query("SELECT * FROM artists WHERE id = :id")
    fun select(id: Long): Artist?

    @Query("SELECT * FROM artists")
    fun getAll(): List<Artist>

    @Query("DELETE FROM artists WHERE id = :id")
    fun deleteArtist(id: Long)

    @Query("SELECT last_media_id FROM artists WHERE id = :id")
    fun getLastMediaId(id: Long): Long?

    @Query("UPDATE artists SET last_media_id = :lastMediaId WHERE id = :id")
    fun updateLastMediaId(id: Long, lastMediaId: Long): Int
}
