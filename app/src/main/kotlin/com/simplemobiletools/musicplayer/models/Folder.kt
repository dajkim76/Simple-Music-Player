package com.simplemobiletools.musicplayer.models

import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.extensions.sortSafely
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE

data class Folder(val title: String, val trackCount: Int, val path: String, var favoriteTime: Long = 0) {
    companion object {
        fun getComparator(sorting: Int) = Comparator<Folder> { first, second ->
            val firstIsSpecial = first.favoriteTime > 0
            val secondIsSpecial = second.favoriteTime > 0

            var result = when {
                firstIsSpecial && secondIsSpecial -> second.favoriteTime.compareTo(first.favoriteTime)

                firstIsSpecial -> -1
                secondIsSpecial -> 1

                sorting and PLAYER_SORT_BY_TITLE != 0 -> AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                else -> first.trackCount.compareTo(second.trackCount)
            }

            if (!firstIsSpecial && !secondIsSpecial && sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        else -> trackCount.toString()
    }
}

fun ArrayList<Folder>.sortSafely(sorting: Int) = sortSafely(Folder.getComparator(sorting))
