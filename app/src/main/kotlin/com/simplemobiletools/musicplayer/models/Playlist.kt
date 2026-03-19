package com.simplemobiletools.musicplayer.models

import androidx.room.*
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.extensions.sortSafely
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_UPDATED_TIME
import com.simplemobiletools.musicplayer.helpers.SMART_PLAYLIST_ID_MAX

@Entity(tableName = "playlists", indices = [(Index(value = ["id"], unique = true))])
data class Playlist(
    @PrimaryKey(autoGenerate = true) var id: Int,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "updated_time") var updatedTime: Long = System.currentTimeMillis(),
    @Ignore var trackCount: Int = 0
) {
    constructor() : this(0, "", System.currentTimeMillis(), 0)

    companion object {
        fun getComparator(sorting: Int) = Comparator<Playlist> { first, second ->
            // 1. id가 5 이하인지 여부를 먼저 확인
            val firstIsSpecial = first.id <= SMART_PLAYLIST_ID_MAX
            val secondIsSpecial = second.id <= SMART_PLAYLIST_ID_MAX

            val result = when {
                // 둘 다 5 이하인 경우: id 순서대로 정렬
                firstIsSpecial && secondIsSpecial -> first.id.compareTo(second.id)

                // 한쪽만 5 이하인 경우: 5 이하인 쪽이 앞으로 오게 함
                firstIsSpecial -> -1
                secondIsSpecial -> 1

                // 둘 다 5보다 큰 경우: 기존 정렬 로직 수행
                sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                    val r = AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                    if (sorting and SORT_DESCENDING != 0) r * -1 else r
                }

                sorting and PLAYER_SORT_UPDATED_TIME != 0 -> {
                    val r = first.updatedTime.compareTo(second.updatedTime)
                    if (sorting and SORT_DESCENDING != 0) r * -1 else r
                }

                else -> {
                    val r = first.trackCount.compareTo(second.trackCount)
                    if (sorting and SORT_DESCENDING != 0) r * -1 else r
                }
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        else -> trackCount.toString()
    }
}

fun ArrayList<Playlist>.sortSafely(sorting: Int) = sortSafely(Playlist.getComparator(sorting))
