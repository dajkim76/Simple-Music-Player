package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.helpers.ALL_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.FAVORITE_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.RECENTLY_PLAYED_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.models.Track

@Dao
interface SongsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    @Delete
    fun delete(track: Track)

    @Query("SELECT * FROM tracks")
    fun getAll(): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksFromPlaylist(playlistId: Int): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = $ALL_TRACKS_PLAYLIST_ID ORDER BY date_added DESC")
    fun getTracksFromPlaylistRecentlyAdded(): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = $RECENTLY_PLAYED_TRACKS_PLAYLIST_ID AND play_count > 0 ORDER BY play_count DESC")
    fun getTracksFromPlaylistMostPlayed(): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = $RECENTLY_PLAYED_TRACKS_PLAYLIST_ID AND updated_timestamp > 0 ORDER BY updated_timestamp DESC")
    fun getTracksFromPlaylistRecentlyPlayed(): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = $FAVORITE_TRACKS_PLAYLIST_ID ORDER BY updated_timestamp DESC")
    fun getTracksFromPlaylistFavorite(): List<Track>

    @Query("SELECT * FROM tracks WHERE artist_id = :artistId")
    fun getTracksFromArtist(artistId: Long): List<Track>

    @Query("SELECT * FROM tracks WHERE album_id = :albumId")
    fun getTracksFromAlbum(albumId: Long): List<Track>

    @Query("SELECT COUNT(*) FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksCountFromPlaylist(playlistId: Int): Int

    @Query("SELECT * FROM tracks WHERE folder_name = :folderName COLLATE NOCASE GROUP BY media_store_id")
    fun getTracksFromFolder(folderName: String): List<Track>

    @Query("SELECT * FROM tracks WHERE media_store_id = :mediaStoreId")
    fun getTrackWithMediaStoreId(mediaStoreId: Long): Track?

    @Query("SELECT * FROM tracks WHERE genre_id = :genreId")
    fun getGenreTracks(genreId: Long): List<Track>

    @Query("DELETE FROM tracks WHERE media_store_id = :mediaStoreId")
    fun removeTrack(mediaStoreId: Long)

    @Delete
    fun deletePlaylistTracks(list: List<Track>): Int

    @Query("DELETE FROM tracks WHERE playlist_id = :playlistId")
    fun removePlaylistSongs(playlistId: Int)

    @Query("UPDATE tracks SET path = :newPath, artist = :artist, title = :title WHERE path = :oldPath")
    fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)

    @Query("UPDATE tracks SET cover_art = :coverArt WHERE media_store_id = :id")
    fun updateCoverArt(coverArt: String, id: Long)

    @Query("UPDATE tracks SET order_in_playlist = :index WHERE id = :id")
    fun updateOrderInPlaylist(index: Int, id: Long)

    @Query("SELECT * FROM tracks WHERE playlist_id = :playListId AND media_store_id = :mediaStoreId")
    fun getPlaylistTrack(playListId: Int, mediaStoreId: Long): Track?

    @Query("UPDATE tracks SET updated_timestamp = :updatedTimeStamp, play_count = :playCount WHERE id = :id")
    fun updatePlayback(id: Long, updatedTimeStamp: Long, playCount: Int): Int

    @Query("UPDATE tracks SET last_position = :lastPosition WHERE id = :id")
    fun updateLastPosition(id: Long, lastPosition: Long)
}
