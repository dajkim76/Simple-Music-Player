package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.postDelayed
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.MediaItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import androidx.recyclerview.widget.LinearLayoutManager
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.CueAdapter
import com.simplemobiletools.musicplayer.databinding.ActivityTrackBinding
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.fragments.PlaybackSpeedFragment
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.PlaybackSpeedListener
import com.simplemobiletools.musicplayer.models.Cue
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.playback.CustomCommands
import com.simplemobiletools.musicplayer.playback.PlaybackService
import java.text.DecimalFormat
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class TrackActivity : SimpleControllerActivity(), PlaybackSpeedListener {
    private val SWIPE_DOWN_THRESHOLD = 100

    private var isThirdPartyIntent = false
    private var currentTrack: Track? = null
    private var cueAdapter: CueAdapter? = null
    private lateinit var nextTrackPlaceholder: Drawable

    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis = 500L

    private val binding by viewBinding(ActivityTrackBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        nextTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, getProperTextColor())
        setupButtons()
        setupFlingListener()

        binding.apply {
            (activityTrackAppbar.layoutParams as ConstraintLayout.LayoutParams).topMargin = statusBarHeight
            activityTrackHolder.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            activityTrackToolbar.setNavigationOnClickListener {
                finish()
            }

            activityTrackToolbar.inflateMenu(R.menu.menu_track)
            activityTrackToolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.edit_cues -> showEditCuesDialog()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }

            isThirdPartyIntent = intent.action == Intent.ACTION_VIEW
            arrayOf(activityTrackToggleShuffle, activityTrackPrevious, activityTrackNext, activityTrackPlaybackSetting).forEach {
                it.beInvisibleIf(isThirdPartyIntent)
            }

            if (isThirdPartyIntent) {
                initThirdPartyIntent()
                return
            }

            setupTrackInfo(PlaybackService.currentMediaItem)
            setupNextTrackInfo(PlaybackService.nextMediaItem)
            activityTrackPlayPause.updatePlayPauseIcon(PlaybackService.isPlaying, getProperTextColor())
            updatePlayerState()

            nextTrackHolder.background = ColorDrawable(getProperBackgroundColor())
            nextTrackHolder.setOnClickListener {
                startActivity(Intent(applicationContext, QueueActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(binding.activityTrackHolder)
        binding.activityTrackTitle.setTextColor(getProperTextColor())
        binding.activityTrackArtist.setTextColor(getProperTextColor())
        updatePlayerState()
        updateTrackInfo()
    }

    override fun onPause() {
        super.onPause()
        cancelProgressUpdate()
    }

    override fun onStop() {
        super.onStop()
        cancelProgressUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelProgressUpdate()
        if (isThirdPartyIntent && !isChangingConfigurations) {
            withPlayer {
                if (!isReallyPlaying) {
                    sendCommand(CustomCommands.CLOSE_PLAYER)
                }
            }
        }
    }

    private fun setupTrackInfo(item: MediaItem?) {
        val track = item?.toTrack() ?: return
        currentTrack = track

        setupTopArt(track)
        setupCues(track)
        binding.apply {
            activityTrackTitle.text = track.title
            activityTrackArtist.text = track.artist
            activityTrackTitle.setOnLongClickListener {
                copyToClipboard(activityTrackTitle.value)
                true
            }

            activityTrackArtist.setOnLongClickListener {
                copyToClipboard(activityTrackArtist.value)
                true
            }

            activityTrackProgressbar.max = track.duration
            activityTrackProgressMax.text = track.duration.getFormattedDuration()
        }
    }

    private fun initThirdPartyIntent() {
        binding.nextTrackHolder.beGone()
        getTrackFromUri(intent.data) { track ->
            runOnUiThread {
                if (track != null) {
                    prepareAndPlay(listOf(track), startActivity = false)
                } else {
                    toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                    finish()
                }
            }
        }
    }

    private fun setupButtons() = binding.apply {
        activityTrackToggleShuffle.setOnClickListener { withPlayer { toggleShuffle() } }
        activityTrackPrevious.setOnClickListener { withPlayer { forceSeekToPrevious() } }
        activityTrackPlayPause.setOnClickListener { togglePlayback() }
        activityTrackNext.setOnClickListener { withPlayer { forceSeekToNext() } }
        activityTrackProgressCurrent.setOnClickListener { seekBack() }
        activityTrackProgressMax.setOnClickListener { seekForward() }
        activityTrackPlaybackSetting.setOnClickListener { togglePlaybackSetting() }
        activityTrackSpeedClickArea.setOnClickListener { showPlaybackSpeedPicker() }
        setupShuffleButton()
        setupPlaybackSettingButton()
        setupSeekbar()

        arrayOf(activityTrackPrevious, activityTrackPlayPause, activityTrackNext).forEach {
            it.applyColorFilter(getProperTextColor())
        }
    }

    private fun setupNextTrackInfo(item: MediaItem?) {
        val track = item?.toTrack()
        if (track == null) {
            binding.nextTrackHolder.beGone()
            return
        }

        binding.nextTrackHolder.beVisible()
        val artist = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else {
            ""
        }

        @SuppressLint("SetTextI18n")
        binding.nextTrackLabel.text = "${getString(R.string.next_track)} ${track.title}$artist"

        getTrackCoverArt(track) { coverArt ->
            val cornerRadius = resources.getDimension(com.simplemobiletools.commons.R.dimen.rounded_corner_radius_small).toInt()
            val wantedSize = resources.getDimension(R.dimen.song_image_size).toInt()

            // change cover image manually only once loaded successfully to avoid blinking at fails and placeholders
            loadGlideResource(
                model = coverArt,
                options = RequestOptions().transform(CenterCrop(), RoundedCorners(cornerRadius)),
                size = Size(wantedSize, wantedSize),
                onLoadFailed = {
                    runOnUiThread {
                        binding.nextTrackImage.setImageDrawable(nextTrackPlaceholder)
                    }
                },
                onResourceReady = {
                    runOnUiThread {
                        binding.nextTrackImage.setImageDrawable(it)
                    }
                }
            )
        }
    }

    private fun setupTopArt(track: Track) {
        getTrackCoverArt(track) { coverArt ->
            var wantedHeight = resources.getCoverArtHeight()
            wantedHeight = min(wantedHeight, realScreenSize.y / 2)
            val wantedWidth = realScreenSize.x

            // change cover image manually only once loaded successfully to avoid blinking at fails and placeholders
            loadGlideResource(
                model = coverArt,
                options = RequestOptions().centerCrop(),
                size = Size(wantedWidth, wantedHeight),
                onLoadFailed = {
                    val drawable = resources.getDrawable(R.drawable.ic_headset)
                    val placeholder = getResizedDrawable(drawable, wantedHeight)
                    placeholder.applyColorFilter(getProperTextColor())

                    runOnUiThread {
                        binding.activityTrackImage.setImageDrawable(placeholder)
                    }
                },
                onResourceReady = {
                    val coverHeight = it.intrinsicHeight
                    if (coverHeight > 0 && binding.activityTrackImage.height != coverHeight) {
                        binding.activityTrackImage.layoutParams.height = coverHeight
                    }

                    runOnUiThread {
                        binding.activityTrackImage.setImageDrawable(it)
                    }
                }
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlingListener() {
        val flingListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null) {
                    if (velocityY > 0 && velocityY > velocityX && e2.y - e1.y > SWIPE_DOWN_THRESHOLD) {
                        finish()
                        binding.activityTrackTopShadow.animate().alpha(0f).start()
                        overridePendingTransition(0, com.simplemobiletools.commons.R.anim.slide_down)
                    }
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }
        }

        val gestureDetector = GestureDetectorCompat(this, flingListener)
        binding.activityTrackHolder.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun toggleShuffle() {
        val isShuffleEnabled = !config.isShuffleEnabled
        config.isShuffleEnabled = isShuffleEnabled
        toast(if (isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
        setupShuffleButton()
        withPlayer {
            shuffleModeEnabled = config.isShuffleEnabled
            setupNextTrackInfo(nextMediaItem)
        }
    }

    private fun setupShuffleButton(isShuffleEnabled: Boolean = config.isShuffleEnabled) {
        binding.activityTrackToggleShuffle.apply {
            applyColorFilter(if (isShuffleEnabled) getProperPrimaryColor() else getProperTextColor())
            alpha = if (isShuffleEnabled) 1f else MEDIUM_ALPHA
            contentDescription = getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        }
    }

    private fun seekBack() {
        binding.activityTrackProgressbar.progress += -SEEK_INTERVAL_S
        withPlayer { seekBack() }
    }

    private fun seekForward() {
        binding.activityTrackProgressbar.progress += SEEK_INTERVAL_S
        withPlayer { seekForward() }
    }

    private fun togglePlaybackSetting() {
        val newPlaybackSetting = config.playbackSetting.nextPlaybackOption
        config.playbackSetting = newPlaybackSetting
        toast(newPlaybackSetting.descriptionStringRes)
        setupPlaybackSettingButton()
        withPlayer {
            setRepeatMode(newPlaybackSetting)
        }
    }

    private fun maybeUpdatePlaybackSettingButton(playbackSetting: PlaybackSetting) {
        if (config.playbackSetting != PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
            setupPlaybackSettingButton(playbackSetting)
        }
    }

    private fun setupPlaybackSettingButton(playbackSetting: PlaybackSetting = config.playbackSetting) {
        binding.activityTrackPlaybackSetting.apply {
            contentDescription = getString(playbackSetting.contentDescriptionStringRes)
            setImageResource(playbackSetting.iconRes)

            val isRepeatOff = playbackSetting == PlaybackSetting.REPEAT_OFF

            alpha = if (isRepeatOff) MEDIUM_ALPHA else 1f
            applyColorFilter(if (isRepeatOff) getProperTextColor() else getProperPrimaryColor())
        }
    }

    private fun setupSeekbar() {
        binding.activityTrackSpeedIcon.applyColorFilter(getProperTextColor())
        updatePlaybackSpeed(config.playbackSpeed)

        binding.activityTrackProgressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val formattedProgress = progress.getFormattedDuration()
                binding.activityTrackProgressCurrent.text = formattedProgress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) = withPlayer {
                seekTo(seekBar.progress * 1000L)
            }
        })
    }

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        fragment.show(supportFragmentManager, PlaybackSpeedFragment::class.java.simpleName)
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        val isSlow = speed < 1f
        if (isSlow != binding.activityTrackSpeed.tag as? Boolean) {
            binding.activityTrackSpeed.tag = isSlow

            val drawableId = if (isSlow) R.drawable.ic_playback_speed_slow_vector else R.drawable.ic_playback_speed_vector
            binding.activityTrackSpeedIcon.setImageDrawable(resources.getDrawable(drawableId))
        }

        @SuppressLint("SetTextI18n")
        binding.activityTrackSpeed.text = "${DecimalFormat("#.##").format(speed)}x"
        withPlayer {
            setPlaybackSpeed(speed)
        }
    }

    private fun getResizedDrawable(drawable: Drawable, wantedHeight: Int): Drawable {
        val bitmap = (drawable as BitmapDrawable).bitmap
        val bitmapResized = Bitmap.createScaledBitmap(bitmap, wantedHeight, wantedHeight, false)
        return BitmapDrawable(resources, bitmapResized)
    }

    override fun onPlaybackStateChanged(playbackState: Int) = updatePlayerState()

    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayerState()

    override fun onRepeatModeChanged(repeatMode: Int) = maybeUpdatePlaybackSettingButton(getPlaybackSetting(repeatMode))

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = setupShuffleButton(shuffleModeEnabled)

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        if (mediaItem == null) {
            finish()
        } else {
            binding.activityTrackProgressbar.progress = 0
            updateTrackInfo()
        }
    }

    private fun updateTrackInfo() {
        withPlayer {
            setupTrackInfo(currentMediaItem)
            setupNextTrackInfo(nextMediaItem)
        }
    }

    private fun updatePlayerState() {
        withPlayer {
            val isPlaying = isReallyPlaying
            if (isPlaying) {
                scheduleProgressUpdate()
            } else {
                cancelProgressUpdate()
            }

            updateProgress(currentPosition)
            updatePlayPause(isPlaying)
            setupShuffleButton(shuffleModeEnabled)
            maybeUpdatePlaybackSettingButton(getPlaybackSetting(repeatMode))
        }
    }

    private fun scheduleProgressUpdate() {
        cancelProgressUpdate()
        withPlayer {
            val delayInMillis = (updateIntervalMillis / config.playbackSpeed).toLong()
            handler.postDelayed(delayInMillis) {
                updateProgress(currentPosition)
                scheduleProgressUpdate()
            }
        }
    }

    private fun cancelProgressUpdate() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateProgress(currentPosition: Long) {
        val seconds = currentPosition.milliseconds.inWholeSeconds.toInt()
        binding.activityTrackProgressbar.progress = seconds
        
        cueAdapter?.let { adapter ->
            adapter.updateCurrentPosition(seconds)
            
            // Skip disabled cues
            val activeCueIndex = adapter.cues.indexOfLast { it.timestamp <= seconds }
            if (activeCueIndex != -1) {
                val currentCue = adapter.cues[activeCueIndex]
                if (!currentCue.enabled) {
                    val nextEnabledCue = adapter.cues.subList(activeCueIndex + 1, adapter.cues.size).firstOrNull { it.enabled }
                    if (nextEnabledCue != null) {
                        withPlayer {
                            seekTo(nextEnabledCue.timestamp * 1000L)
                        }
                    } else {
                        // If no more enabled cues, skip to the end of the track or next song
                        // For now, let's just seek to the very end if there are no more cues at all
                        // but actually, just letting it play to the end of the file is fine if that's what's intended.
                    }
                }
            }
        }
    }

    private fun updatePlayPause(isPlaying: Boolean) {
        binding.activityTrackPlayPause.updatePlayPauseIcon(isPlaying, getProperTextColor())
    }

    private fun setupCues(track: Track) {
        ensureBackgroundThread {
            val cuesJson = audioHelper.getTrackCue(track.mediaStoreId)
            val cues = getCuesFromJson(cuesJson)
            runOnUiThread {
                if (cues.isNotEmpty()) {
                    cueAdapter = CueAdapter(this, cues, { cue ->
                        withPlayer {
                            seekTo(cue.timestamp * 1000L)
                        }
                    }, { updatedCues ->
                        ensureBackgroundThread {
                            audioHelper.updateTrackCue(track.mediaStoreId, Gson().toJson(updatedCues))
                        }
                    })
                    binding.activityTrackCuesList.apply {
                        layoutManager = LinearLayoutManager(this@TrackActivity)
                        adapter = cueAdapter
                        beVisible()
                    }

                    withPlayer {
                        val seconds = currentPosition.milliseconds.inWholeSeconds.toInt()
                        cueAdapter?.updateCurrentPosition(seconds)
                    }
                } else {
                    binding.activityTrackCuesList.beGone()
                    cueAdapter = null
                }
            }
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

    private fun showEditCuesDialog() {
        val track = currentTrack ?: return
        ensureBackgroundThread {
            val currentCuesJson = audioHelper.getTrackCue(track.mediaStoreId)
            runOnUiThread {
                val editText = androidx.appcompat.widget.AppCompatEditText(this)
                editText.setText(cuesToText(currentCuesJson))
                editText.setTextColor(getProperTextColor())
                editText.setHintTextColor(getProperTextColor().adjustAlpha(0.5f))
                editText.background = null
                editText.minLines = 5
                editText.gravity = android.view.Gravity.TOP
                val padding = resources.getDimensionPixelSize(com.simplemobiletools.commons.R.dimen.activity_margin)
                editText.setPadding(padding, padding, padding, padding)

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.edit_cues)
                    .setView(editText)
                    .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                        val newCueJson = parseCueText(editText.text.toString())
                        ensureBackgroundThread {
                            audioHelper.updateTrackCue(track.mediaStoreId, newCueJson)
                            runOnUiThread {
                                setupCues(track)
                            }
                        }
                    }
                    .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun parseCueText(text: String): String {
        val cues = mutableListOf<Cue>()
        val lines = text.split("\n")
        for (line in lines) {
            val match = Regex("""(\d{1,2}:)?(\d{1,2}):(\d{1,2})""").find(line)
            if (match != null) {
                val timeGroups = match.groupValues
                val hours = if (timeGroups[1].isNotEmpty()) timeGroups[1].replace(":", "").toInt() else 0
                val minutes = timeGroups[2].toInt()
                val seconds = timeGroups[3].toInt()
                val timestamp = hours * 3600 + minutes * 60 + seconds
                val title = line.replace(match.value, "").trim().removePrefix("-").trim()
                cues.add(Cue(timestamp, title, enabled = true))
            }
        }
        return Gson().toJson(cues.sortedBy { it.timestamp })
    }

    private fun cuesToText(cueJson: String): String {
        if (cueJson.isEmpty()) return ""
        return try {
            val type = object : TypeToken<List<Cue>>() {}.type
            val cues: List<Cue> = Gson().fromJson(cueJson, type)
            cues.joinToString("\n") { cue ->
                val h = cue.timestamp / 3600
                val m = (cue.timestamp % 3600) / 60
                val s = cue.timestamp % 60
                if (h > 0) {
                    String.format("%02d:%02d:%02d %s", h, m, s, cue.title)
                } else {
                    String.format("%02d:%02d %s", m, s, cue.title)
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
