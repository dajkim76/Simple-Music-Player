package com.simplemobiletools.musicplayer.helpers

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import java.io.ByteArrayOutputStream

class TrackFileArtCache private constructor(private val context: Context) {
    private val bitmapCache: LruCache<Long, Bitmap> = object : LruCache<Long, Bitmap>(getCacheSize()) {
        override fun sizeOf(key: Long, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val artworkCache: LruCache<Long, ByteArray> = object : LruCache<Long, ByteArray>(getCompressedCacheSize()) {
        override fun sizeOf(key: Long, value: ByteArray): Int {
            return value.size
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

    private fun getCompressedCacheSize(): Int {
        val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
        return 1024 * 1024 * memoryClass / 16
    }

    fun getArtworkData(key: Long): ByteArray? {
        return artworkCache.get(key) ?: get(key)?.let { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val data = stream.toByteArray()
            artworkCache.put(key, data)
            data
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
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

        fun peekInstance(): TrackFileArtCache? = instance
    }
}


object GlideLoadFailChecker {
    private val failedSet = mutableSetOf<String>()

    fun isFailed(key: String): Boolean = failedSet.contains(key)

    fun markFailed(key: String) {
        failedSet.add(key)
    }
}
