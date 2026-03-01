package com.simplemobiletools.musicplayer.playback

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.postDelayed
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.CueListCache
import com.simplemobiletools.musicplayer.helpers.NotificationHelper
import com.simplemobiletools.musicplayer.helpers.getPermissionToRequest
import com.simplemobiletools.musicplayer.playback.library.MediaItemProvider
import com.simplemobiletools.musicplayer.playback.player.SimpleMusicPlayer
import com.simplemobiletools.musicplayer.playback.player.initializeSessionAndPlayer

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), MediaSessionService.Listener {
    internal lateinit var player: SimpleMusicPlayer
    internal lateinit var playerThread: HandlerThread
    internal lateinit var playerListener: Player.Listener
    internal lateinit var playerHandler: Handler
    internal lateinit var mediaSession: MediaLibrarySession
    internal lateinit var mediaItemProvider: MediaItemProvider

    internal var currentRoot = ""
    private var lastCueTitle: String? = null
    private val cueUpdateHandler = Handler(Looper.getMainLooper())
    private val cueUpdateRunnable = object : Runnable {
        override fun run() {
            updateCueMetadata()
            cueUpdateHandler.postDelayed(this, 1000L)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED && config.autoplayOnBluetoothConnect) {
                withPlayer {
                    play()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setListener(this)
        initializeSessionAndPlayer(handleAudioFocus = true, handleAudioBecomingNoisy = true, skipSilence = config.gaplessPlayback)
        initializeLibrary()

        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(bluetoothReceiver, filter)
        cueUpdateHandler.post(cueUpdateRunnable)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        super.onDestroy()
        cueUpdateHandler.removeCallbacks(cueUpdateRunnable)
        unregisterReceiver(bluetoothReceiver)
        releaseMediaSession()
        clearListener()
        stopSleepTimer()
        SimpleEqualizer.release()
    }

    private fun updateCueMetadata() {
        withPlayer {
            if (!isPlaying) return@withPlayer
            val currentItem = currentMediaItem ?: return@withPlayer
            val track = currentItem.toTrack() ?: return@withPlayer
            val currentSec = currentPosition / 1000

            val cues = CueListCache.getCueList(applicationContext, track.mediaStoreId)
            val currentCue = cues.lastOrNull { it.enabled && it.timestamp <= currentSec }
            val displayTitle = currentCue?.title ?: track.title

            if (displayTitle != lastCueTitle) {
                lastCueTitle = displayTitle
                val newMetadata = currentItem.mediaMetadata.buildUpon()
                    .setTitle(displayTitle)
                    .build()

                overriddenMetadata = newMetadata
                player.playlistMetadata = newMetadata
                mediaSession.setCustomLayout(getCustomLayout())
            }
        }
    }

    fun stopService() {
        withPlayer {
            pause()
            stop()
        }

        stopSelf()
    }

    private fun initializeLibrary() {
        mediaItemProvider = MediaItemProvider(this)
        if (hasPermission(getPermissionToRequest())) {
            mediaItemProvider.reload()
        } else {
            showNoPermissionNotification()
        }
    }

    private fun releaseMediaSession() {
        mediaSession.release()
        withPlayer {
            removeListener(playerListener)
            release()
        }
    }

    internal fun withPlayer(callback: SimpleMusicPlayer.() -> Unit) = playerHandler.post { callback(player) }

    private fun showNoPermissionNotification() {
        Handler(Looper.getMainLooper()).postDelayed(delayInMillis = 100L) {
            try {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.createInstance(this).createNoPermissionNotification()
                )
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the {@link MediaSessionService} is in the
     * background.
     */
    override fun onForegroundServiceStartNotAllowedException() {
        showErrorToast(getString(com.simplemobiletools.commons.R.string.unknown_error_occurred))
        // todo: show a notification instead.
    }

    companion object {
        // Initializing a media controller might take a noticeable amount of time thus we expose current playback info here to keep things as quick as possible.
        var isPlaying: Boolean = false
            private set
        var currentMediaItem: MediaItem? = null
            private set
        var nextMediaItem: MediaItem? = null
            private set

        fun updatePlaybackInfo(player: Player) {
            currentMediaItem = player.currentMediaItem
            nextMediaItem = player.nextMediaItem
            isPlaying = player.isReallyPlaying
        }
    }
}

