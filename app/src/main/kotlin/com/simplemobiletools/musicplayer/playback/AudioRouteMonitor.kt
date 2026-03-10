package com.simplemobiletools.musicplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class AudioRouteMonitor(
    context: Context,
    private val listener: (Route) -> Unit
) {

    enum class Route {
        SPEAKER,
        WIRED_HEADSET,
        BLUETOOTH,
        USB,
        BECOMING_NOISY,
        OTHER,
    }

    private val appContext = context.applicationContext

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var currentRoute: Route? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val deviceCallback = object : AudioDeviceCallback() {

        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            scheduleUpdate()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            scheduleUpdate()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                listener(Route.BECOMING_NOISY)
            }
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        appContext.registerReceiver(noisyReceiver, filter)

        updateRoute()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)

        try {
            appContext.unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {
        }
    }

    private fun scheduleUpdate() {
        mainHandler.removeCallbacks(updateRunnable)

        // Bluetooth routing delay workaround
        mainHandler.postDelayed(updateRunnable, 300)
    }

    private val updateRunnable = Runnable {
        updateRoute()
    }

    private fun updateRoute() {
        val newRoute = detectRoute()

        if (newRoute != currentRoute) {
            currentRoute = newRoute
            listener(newRoute)
        }
    }

    private fun detectRoute(): Route {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return Route.BLUETOOTH /*MIC & HEADSET*/

                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return Route.WIRED_HEADSET

                AudioDeviceInfo.TYPE_USB_HEADSET -> return Route.WIRED_HEADSET
                AudioDeviceInfo.TYPE_USB_DEVICE -> return Route.USB
            }
        }

        return Route.SPEAKER
    }
}
