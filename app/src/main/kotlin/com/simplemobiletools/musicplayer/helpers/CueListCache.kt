package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
        val cues = getCuesFromJson(cueJson)
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

    private fun getCuesFromJson(cueJson: String): List<Cue> {
        return try {
            val type = object : TypeToken<List<Cue>>() {}.type
            Gson().fromJson<List<Cue>>(cueJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isWorkerThread() = Looper.myLooper() != Looper.getMainLooper()
}
