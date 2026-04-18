package com.simplemobiletools.musicplayer.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat

class SwipeGestureDetector(context: Context, val swipeFlags: Int = SWIPE_ALL, callback: (swipeWhat: Int) -> Unit) {
    private var isFlinging = false
    private val flingListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            isFlinging = false
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x

            val SWIPE_THRESHOLD = 50
            val SWIPE_VELOCITY_THRESHOLD = 50

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        if (isSwipeable(SWIPE_RIGHT)) {
                            isFlinging = true
                            callback(SWIPE_RIGHT)
                            return true
                        }
                    } else if (isSwipeable(SWIPE_LEFT)) {
                        isFlinging = true
                        callback(SWIPE_LEFT)
                        return true
                    }

                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        if (isSwipeable(SWIPE_DOWN)) {
                            isFlinging = true
                            callback(SWIPE_DOWN)
                            return true
                        }
                    } else if (isSwipeable(SWIPE_UP)) {
                        isFlinging = true
                        callback(SWIPE_UP)
                        return true
                    }
                }
            }
            return false
        }
    }

    private val gestureDetector = GestureDetectorCompat(context, flingListener).apply {
        setIsLongpressEnabled(false)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val onTouchListener = View.OnTouchListener { v, event ->
        val handled = gestureDetector.onTouchEvent(event)
        if (isFlinging && event.action == MotionEvent.ACTION_UP) {
            val cancelEvent = MotionEvent.obtain(event)
            cancelEvent.action = MotionEvent.ACTION_CANCEL
            v.onTouchEvent(cancelEvent)
            cancelEvent.recycle()
            return@OnTouchListener true
        }
        v.onTouchEvent(event) || handled
    }

    private fun isSwipeable(flag: Int): Boolean = (swipeFlags and flag) != 0

    fun attachTouchListener(view: View?) = view?.setOnTouchListener(onTouchListener)

    companion object {
        const val SWIPE_LEFT = 1
        const val SWIPE_RIGHT = 2
        const val SWIPE_UP = 4
        const val SWIPE_DOWN = 8
        const val SWIPE_ALL = SWIPE_LEFT or SWIPE_RIGHT or SWIPE_UP or SWIPE_DOWN
    }
}
