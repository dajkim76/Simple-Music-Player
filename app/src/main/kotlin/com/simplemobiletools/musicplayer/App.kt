package com.simplemobiletools.musicplayer

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.simplemobiletools.commons.extensions.checkUseEnglish
import com.simplemobiletools.musicplayer.helpers.SimpleMediaController

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initRemoteConfig()
        checkUseEnglish()
        initController()
    }

    private fun initRemoteConfig() {
        FirebaseApp.initializeApp(this)
        // Debug mode: immediately, Release mode: 12 hours,
        if (BuildConfig.DEBUG) {
            val settings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 0
            }
            Firebase.remoteConfig.setConfigSettingsAsync(settings)
        }
        Firebase.remoteConfig.fetchAndActivate()
    }

    private fun initController() {
        SimpleMediaController.getInstance(applicationContext).createControllerAsync()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    SimpleMediaController.destroyInstance()
                }
            }
        )
    }
}
