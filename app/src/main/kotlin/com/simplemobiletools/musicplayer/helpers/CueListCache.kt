package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.toInt
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.models.Cue

object CueListCache {
    private var context: Context? = null
    private val cueListMap = mutableMapOf<Long, List<Cue>>()

    fun initContext(context: Context) {
        this.context = context.applicationContext
    }

    @Synchronized
    fun getCueList(mediaStoreId: Long): List<Cue> {
        return cueListMap[mediaStoreId] ?: run {
            if (context != null && isWorkerThread()) {
                val cueJson = context!!.audioHelper.getTrackCue(mediaStoreId)
                updateCueJson(mediaStoreId, cueJson)
            } else {
                context?.let { loadCueListAsync(it, mediaStoreId) }
                emptyList()
            }
        }
    }

    @Synchronized
    fun getCueList(context: Context, mediaStoreId: Long): List<Cue> {
        if (this.context == null) {
            this.context = context.applicationContext
        }
        return cueListMap[mediaStoreId] ?: run {
                if (isWorkerThread()) {
                    val cueJson = context.audioHelper.getTrackCue(mediaStoreId)
                    updateCueJson(mediaStoreId, cueJson)
                } else {
                    loadCueListAsync(context, mediaStoreId)
                    emptyList()
                }
        }
    }

    @Synchronized
    fun updateCueJson(mediaStoreId: Long, cueJson: String) : List<Cue> {
        val cues = CueListHelper.getCueListFromJson(cueJson)
        cueListMap[mediaStoreId] = cues
        return cues
    }

    @Synchronized
    fun updateCueList(mediaStoreId: Long, cueList: List<Cue>) {
        cueListMap[mediaStoreId] = cueList
    }

    private fun loadCueListAsync(context: Context, mediaStoreId: Long) {
        ensureBackgroundThread {
            val cueJson = context.audioHelper.getTrackCue(mediaStoreId)
            updateCueJson(mediaStoreId, cueJson)
        }
    }

    private fun isWorkerThread() = Looper.myLooper() != Looper.getMainLooper()
}

object CueListHelper {

    const val CUE_DISABLED_PREFIX = "@"

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
                }
            }
            cues
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cueJsonToText(cueJson: String): String {
        if (cueJson.isEmpty()) return ""
        return try {
            val type = object : TypeToken<List<Cue>>() {}.type
            val cues: List<Cue> = Gson().fromJson(cueJson, type)
            cues.joinToString("\n") { cue ->
                val h = cue.timestamp / 3600
                val m = (cue.timestamp % 3600) / 60
                val s = cue.timestamp % 60
                val prefix = if (!cue.enabled) CUE_DISABLED_PREFIX else ""
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
