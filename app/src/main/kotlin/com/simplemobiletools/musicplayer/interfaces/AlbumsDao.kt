package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.models.Album

@Dao
interface AlbumsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(album: Album): Long

    @Query("UPDATE albums SET artist = :artist, title = :title, cover_art = :coverArt, year = :year, track_cnt = :trackCnt, artist_id = :artistId, date_added = :dateAdded WHERE id = :id")
    fun update(id: Long, artist: String, title: String, coverArt: String, year: Int, trackCnt: Int, artistId: Long, dateAdded: Int): Int

    @Transaction
    open fun updateAllOrInsert(albums: List<Album>) {
        albums.forEach {
            if (update(it.id, it.artist, it.title, it.coverArt, it.year, it.trackCnt, it.artistId, it.dateAdded) == 0) {
                insert(it)
            }
        }
    }

    @Query("UPDATE albums SET favorite_time = :favoriteTime WHERE id = :id")
    fun updateFavorite(id: Long, favoriteTime: Long)

    @Query("SELECT * FROM albums WHERE id = :id")
    fun select(id: Long): Album?

    @Query("SELECT * FROM albums")
    fun getAll(): List<Album>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun getAlbumWithId(id: Long): Album?

    @Query("SELECT * FROM albums WHERE artist_id = :artistId")
    fun getArtistAlbums(artistId: Long): List<Album>

    @Query("DELETE FROM albums WHERE id = :id")
    fun deleteAlbum(id: Long)
}
