package com.simplemobiletools.musicplayer.helpers

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

class TrackFileArtCache private constructor(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "track_art_cache").apply { mkdirs() }

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
        if (bitmapCache.get(key) == null) {
            bitmapCache.put(key, bitmap)
        }

        val file = File(cacheDir, makeFilename(key))
        if (!file.exists()) {
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun get(key: Long): Bitmap? {
        var bitmap = bitmapCache.get(key)
        if (bitmap == null) {
            val file = File(cacheDir, makeFilename(key))
            if (file.exists()) {
                try {
                    bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        bitmapCache.put(key, bitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return bitmap
    }

    @Synchronized
    fun peek(key: Long): Bitmap? {
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

    private fun makeFilename(key: Long) = "track_$key.jpg"

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
