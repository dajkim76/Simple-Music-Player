package com.simplemobiletools.musicplayer.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import com.simplemobiletools.musicplayer.R

object ViewUtils {

    fun imageViewToBitmap(imageView: ImageView): Bitmap? {
        // Check Glide load success
        if (imageView.getTag(R.id.album_image) != true) {
            return null
        }
        val drawable = imageView.drawable
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }

        // Drawable의 크기 설정
        val width = if (drawable.intrinsicWidth <= 0) 1 else drawable.intrinsicWidth
        val height = if (drawable.intrinsicHeight <= 0) 1 else drawable.intrinsicHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas) // Canvas에 Drawable을 그려서 Bitmap에 픽셀 정보 생성

        return bitmap
    }
}
