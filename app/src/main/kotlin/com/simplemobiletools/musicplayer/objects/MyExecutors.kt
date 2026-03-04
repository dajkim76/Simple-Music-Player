package com.simplemobiletools.musicplayer.objects


import android.os.Handler
import android.os.Looper
import com.simplemobiletools.commons.helpers.isOnMainThread
import java.util.concurrent.Executors


object MyExecutors {
    private val executors = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    fun execute(callback: () -> Unit) = executors.execute(callback)

    fun executeOnMainThread(callback: () -> Unit) = mainHandler.post(callback)
}

fun executeBackgroundThread(callback: () -> Unit) = MyExecutors.execute(callback)

fun executeMainThread(callback: () -> Unit) = MyExecutors.executeOnMainThread(callback)

fun ensureBackgroundThread(callback: () -> Unit) {
    if (isOnMainThread()) {
        MyExecutors.execute(callback)
    } else {
        callback()
    }
}

fun ensureMainThread(callback: () -> Unit) {
    if (isOnMainThread()) {
        callback()
    } else {
        MyExecutors.executeOnMainThread(callback)
    }
}
