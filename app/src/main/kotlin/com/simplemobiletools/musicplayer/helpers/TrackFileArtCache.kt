package com.simplemobiletools.musicplayer.helpers

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

class TrackFileArtCache private constructor(private val context: Context) {
    private val bitmapCache: LruCache<Long, Bitmap> = object : LruCache<Long, Bitmap>(getCacheSize()) {
        override fun sizeOf(key: Long, value: Bitmap): Int {
            return value.byteCount
        }
    }

    // MediaMetadataRetriever is expensive, so don't try again later.
    private val noEmbeddedPictureMap = mutableMapOf<Long, Boolean>()

    private fun getCacheSize(): Int {
        val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
        return 1024 * 1024 * memoryClass / 8
    }

    @Synchronized
    fun put(key: Long, bitmap: Bitmap) {
        bitmapCache.put(key, bitmap)
    }

    @Synchronized
    fun get(key: Long): Bitmap? {
        return bitmapCache.get(key)
    }

    @Synchronized
    fun isNoEmbeddedPicture(key: Long): Boolean {
        return noEmbeddedPictureMap.contains(key)
    }

    @Synchronized
    fun setNoEmbeddedPicture(key: Long) {
        noEmbeddedPictureMap[key] = true
    }

    companion object {
        private var instance: TrackFileArtCache? = null

        fun getInstance(context: Context): TrackFileArtCache {
            if (instance == null) {
                synchronized(TrackFileArtCache::class.java) {
                    if (instance == null) {
                        instance = TrackFileArtCache(context)
                    }
                }
            }
            return instance!!
        }
    }
}
