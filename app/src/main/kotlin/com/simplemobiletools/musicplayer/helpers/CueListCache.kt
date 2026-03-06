package com.simplemobiletools.musicplayer.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.toInt
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.models.Cue
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread
import java.util.concurrent.ConcurrentHashMap

object CueListCache {
    private val cueListMap = ConcurrentHashMap<Long, List<Cue>>()

    fun peekCueList(fileStableId: Long): List<Cue>? = cueListMap[fileStableId]

    fun getCueList(context: Context, fileStableId: Long): List<Cue> {
        return cueListMap[fileStableId] ?: run {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                loadCueListAsync(context, fileStableId)
                return emptyList()
            } else {
                val cueJson = context.audioHelper.getTrackCue(fileStableId)
                return updateCacheByCueJson(fileStableId, cueJson)
            }
        }
    }

    fun updateCacheByCueJson(fileStableId: Long, cueJson: String): List<Cue> {
        val cueList = CueListHelper.getCueListFromJson(cueJson)
        cueListMap[fileStableId] = cueList
        return cueList
    }

    fun updateCacheByCueList(fileStableId: Long, cueList: List<Cue>) {
        cueListMap[fileStableId] = cueList
    }

    private fun loadCueListAsync(context: Context, fileStableId: Long) {
        executeBackgroundThread {
            val cueJson = context.audioHelper.getTrackCue(fileStableId)
            updateCacheByCueJson(fileStableId, cueJson)
        }
    }

    fun saveCueListAsync(context: Context, track: Track, newCueJson: String) {
        executeBackgroundThread {
            context.audioHelper.updateTrackCue(track, newCueJson)
        }
    }
}

object CueListHelper {

    const val CUE_DISABLED_PREFIX = "@"
    const val CUE_FAVORITE_PREFIX = "&"

    fun getCueJsonFromText(text: String): String {
        val cues = mutableListOf<Cue>()
        val lines = text.split("\n")
        for (line in lines) {
            val match = Regex("""(\d{1,2}:)?(\d{1,2}):(\d{1,2})""").find(line)
            if (match != null) {
                val timeGroups = match.groupValues
                val hours = if (timeGroups[1].isNotEmpty()) timeGroups[1].replace(":", "").toInt() else 0
                val minutes = timeGroups[2].toInt()
                val seconds = timeGroups[3].toInt()
                val timestamp = hours * 3600 + minutes * 60 + seconds
                val title = line.replace(match.value, "")
                    .trim()
                    .removePrefix("()").removeSuffix("()")
                    .removePrefix("[]").removeSuffix("[]")
                    .removePrefix("{}").removeSuffix("{}")
                    .removePrefix("<>").removeSuffix("<>")
                    .trim(' ', '-', '–', '—', '~', '•', '♪', '▶', ':', '\u200B')
                cues.add(Cue(timestamp, title, enabled = true))
            }
        }
        if (cues.isEmpty()) return ""
        return Gson().toJson(cues.sortedBy { it.timestamp })
    }

    fun getCueListFromJson(cueJson: String): List<Cue> {
        return try {
            val type = object : TypeToken<List<Cue>>() {}.type
            val cues = Gson().fromJson<List<Cue>>(cueJson, type) ?: emptyList()
            cues.forEach {
                if (it.title.startsWith(CUE_DISABLED_PREFIX)) {
                    it.title = it.title.substring(1)
                    it.enabled = false
                } else if (it.title.startsWith(CUE_FAVORITE_PREFIX)) {
                    it.title = it.title.substring(1)
                    it.favorite = true
                }
            }
            cues
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("DefaultLocale")
    fun cueJsonToText(cueJson: String): String {
        if (cueJson.isEmpty()) return ""
        return try {
            val type = object : TypeToken<List<Cue>>() {}.type
            val cues: List<Cue> = Gson().fromJson(cueJson, type)
            cues.joinToString("\n") { cue ->
                val h = cue.timestamp / 3600
                val m = (cue.timestamp % 3600) / 60
                val s = cue.timestamp % 60
                val prefix = if (!cue.enabled) CUE_DISABLED_PREFIX else if (cue.favorite) CUE_FAVORITE_PREFIX else ""
                if (h > 0) {
                    String.format("%02d:%02d:%02d %s", h, m, s, prefix + cue.title)
                } else {
                    String.format("%02d:%02d %s", m, s, prefix + cue.title)
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
