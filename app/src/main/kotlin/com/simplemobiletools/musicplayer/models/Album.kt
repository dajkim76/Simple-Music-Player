package com.simplemobiletools.musicplayer.models

import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.extensions.sortSafely
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_ARTIST_TITLE
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_DATE_ADDED
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TRACK_COUNT

@Entity(tableName = "albums", indices = [(Index(value = ["id"], unique = true))])
data class Album(
    @PrimaryKey(autoGenerate = true) var id: Long,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "cover_art") val coverArt: String,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "track_cnt") var trackCnt: Int,
    @ColumnInfo(name = "artist_id") var artistId: Long,
    @ColumnInfo(name = "date_added") var dateAdded: Int,
    @ColumnInfo(name = "favorite_time", defaultValue = "0") var favoriteTime: Long = 0,
    @ColumnInfo(name = "last_media_id", defaultValue = "0") var lastMediaId: Long = 0,
) : ListItem() {
    companion object {
        fun getComparator(sorting: Int) = Comparator<Album> { first, second ->
            val firstIsSpecial = first.favoriteTime > 0
            val secondIsSpecial = second.favoriteTime > 0

            var result = when {
                firstIsSpecial && secondIsSpecial -> second.favoriteTime.compareTo(first.favoriteTime)

                firstIsSpecial -> -1
                secondIsSpecial -> 1

                sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                    when {
                        first.title == MediaStore.UNKNOWN_STRING && second.title != MediaStore.UNKNOWN_STRING -> 1
                        first.title != MediaStore.UNKNOWN_STRING && second.title == MediaStore.UNKNOWN_STRING -> -1
                        else -> AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                    }
                }

                sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> {
                    when {
                        first.artist == MediaStore.UNKNOWN_STRING && second.artist != MediaStore.UNKNOWN_STRING -> 1
                        first.artist != MediaStore.UNKNOWN_STRING && second.artist == MediaStore.UNKNOWN_STRING -> -1
                        else -> AlphanumericComparator().compare(first.artist.lowercase(), second.artist.lowercase())
                    }
                }

                sorting and PLAYER_SORT_BY_DATE_ADDED != 0 -> first.dateAdded.compareTo(second.dateAdded)
                sorting and PLAYER_SORT_BY_TRACK_COUNT != 0 -> first.trackCnt.compareTo(second.trackCnt)
                else -> first.year.compareTo(second.year)
            }

            if (!firstIsSpecial && !secondIsSpecial && sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> artist
        else -> year.toString()
    }
}

fun ArrayList<Album>.sortSafely(sorting: Int) = sortSafely(Album.getComparator(sorting))
