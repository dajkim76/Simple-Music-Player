package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.models.Cue

object CueListHelper {
    private var context: Context? = null
    private val cueListMap = mutableMapOf<Long, List<Cue>>()

    fun initContext(context: Context) {
        this.context = context.applicationContext
    }

    fun getCueList(mediaStoreId: Long): List<Cue> {
        return cueListMap[mediaStoreId] ?: run {
            if (context != null) {
                getCueList(context!!, mediaStoreId)
            } else {
                emptyList()
            }
        }
    }

    fun getCueList(context: Context, mediaStoreId: Long): List<Cue> {
        if (this.context == null) {
            this.context = context.applicationContext
        }
        return cueListMap[mediaStoreId] ?: run {
                val cues = loadCueList(context, mediaStoreId)
                cueListMap[mediaStoreId] = cues
                cues
        }
    }

    fun updateCueList(mediaStoreId: Long, cueJson: String) {
        val cues = getCuesFromJson(cueJson)
        cueListMap[mediaStoreId] = cues
    }

    fun updateCueList(mediaStoreId: Long, cueList: List<Cue>) {
        cueListMap[mediaStoreId] = cueList
    }

    private fun loadCueList(context: Context, mediaStoreId: Long): List<Cue> {
        val cueJson = context.audioHelper.getTrackCue(mediaStoreId)
        return getCuesFromJson(cueJson)
    }

    private fun getCuesFromJson(cueJson: String): List<Cue> {
        return try {
            val type = object : TypeToken<List<Cue>>() {}.type
            Gson().fromJson<List<Cue>>(cueJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
