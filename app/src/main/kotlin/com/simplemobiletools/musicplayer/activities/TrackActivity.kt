package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.id3.UrlLinkFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.CueAdapter
import com.simplemobiletools.musicplayer.databinding.ActivityTrackBinding
import com.simplemobiletools.musicplayer.databinding.AdjustCueTimestampBinding
import com.simplemobiletools.musicplayer.databinding.InputCueTextBinding
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.fragments.PlaybackSpeedFragment
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.PlaybackSpeedListener
import com.simplemobiletools.musicplayer.models.Cue
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread
import com.simplemobiletools.musicplayer.playback.CustomCommands
import com.simplemobiletools.musicplayer.playback.PlaybackService
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class TrackActivity : SimpleControllerActivity(), PlaybackSpeedListener {
    private var isThirdPartyIntent = false
    private var currentTrack: Track? = null
    private var cueAdapter: CueAdapter? = null
    private lateinit var nextTrackPlaceholder: Drawable

    private var isNextTrack = false
    private var isPrevTrack = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis = 500L

    private val binding by viewBinding(ActivityTrackBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        nextTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset_small, getProperTextColor())
        setupImageSwitcher()
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
                    R.id.show_meta_data -> showMetaDataDialog()
                    R.id.favorite -> toggleFavorite()
                    R.id.add_to_playlist -> currentTrack?.let { addTracksToPlaylist(listOf(it)) {} }
                    R.id.goto_artist_page -> gotoArtistPage()
                    R.id.goto_album_page -> gotoAlbumPage()
                    R.id.track_property -> currentTrack?.let { showTrackProperties(listOf(it)) }
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
                overridePendingTransition(R.anim.slide_up_enter, R.anim.slide_up_exit)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(binding.activityTrackHolder)
        binding.activityTrackTitle.setTextColor(getProperTextColor())
        binding.activityCueTitle.setTextColor(getProperPrimaryColor())
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
        // avoid activity memory leak (Reset singleton bitmap, cueList cache)
        binding.activityTrackImage.setImageDrawable(null)
        binding.activityTrackNext.setImageBitmap(null)
        cueAdapter?.onDestroy()
    }

    private fun setupTrackInfo(item: MediaItem?) {
        val track = item?.toTrack() ?: return
        currentTrack = track

        setupTopArt(track)
        setupCues(track)
        updateFavorite(track)
        binding.apply {
            activityTrackTitle.text = track.title
            updateCueTitle(track.mediaStoreId, -1)
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
                    prepareAndPlay(listOf(track), showPlayback = false)
                } else {
                    toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                    finish()
                }
            }
        }
    }

    private fun setupImageSwitcher() {
        binding.activityTrackImage.setFactory {
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                adjustViewBounds = false
            }
        }
    }

    private fun setupButtons() = binding.apply {
        activityTrackToggleShuffle.setOnClickListener { withPlayer { toggleShuffle() } }
        activityTrackPrevious.setOnClickListener {
            val adapter = cueAdapter
            if (adapter != null && !adapter.isNoCueTitle && adapter.cues.isNotEmpty()) {
                withPlayer {
                    val currentSec = currentPosition.milliseconds.inWholeSeconds.toInt()
                    // Find the previous enabled cue. If we are just a few seconds into the current cue, go to the one before it.
                    val cues = adapter.cues.filter { it.enabled }
                    val activeCueIndex = cues.indexOfLast { it.timestamp <= currentSec }
                    val targetIndex = if (activeCueIndex >= 0 && currentSec - cues[activeCueIndex].timestamp < 3) {
                        activeCueIndex - 1
                    } else {
                        activeCueIndex
                    }

                    if (targetIndex >= 0) {
                        seekTo(cues[targetIndex].timestamp * 1000L)
                    } else {
                        isPrevTrack = true
                        isNextTrack = false
                        forceSeekToPrevious()
                    }
                }
            } else {
                withPlayer {
                    val positionMillis = currentPosition
                    if (positionMillis < 3000) {
                        isPrevTrack = true
                        isNextTrack = false
                        forceSeekToPrevious()
                    } else {
                        seekTo(0)
                        isPrevTrack = false
                    }
                }
            }
        }
        activityTrackSeekBack.setOnClickListener { withPlayer { seekBack() } }
        activityTrackPlayPause.setOnClickListener { togglePlayback() }
        activityTrackSeekForward.setOnClickListener { withPlayer { seekForward() } }
        activityTrackNext.setOnClickListener {
            val adapter = cueAdapter
            if (adapter != null && !adapter.isNoCueTitle && adapter.cues.isNotEmpty()) {
                withPlayer {
                    val currentSec = currentPosition.milliseconds.inWholeSeconds.toInt()
                    val nextEnabledCue = adapter.cues.firstOrNull { it.enabled && it.timestamp > currentSec }
                    if (nextEnabledCue != null) {
                        seekTo(nextEnabledCue.timestamp * 1000L)
                    } else {
                        isNextTrack = true
                        isPrevTrack = false
                        forceSeekToNext()
                    }
                }
            } else {
                withPlayer {
                    isNextTrack = true
                    isPrevTrack = false
                    forceSeekToNext()
                }
            }
        }
        activityTrackProgressCurrent.setOnClickListener { seekBack() }
        activityTrackProgressMax.setOnClickListener { seekForward() }
        activityTrackPlaybackSetting.setOnClickListener { togglePlaybackSetting() }
        activityTrackSpeedClickArea.setOnClickListener { showPlaybackSpeedPicker() }
        setupShuffleButton()
        setupPlaybackSettingButton()
        setupSeekbar()

        arrayOf(activityTrackPrevious, activityTrackSeekBack, activityTrackPlayPause, activityTrackSeekForward, activityTrackNext).forEach {
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

        getTrackFileArt(track) { coverArt ->
            if (isFinishing || isDestroyed) return@getTrackFileArt
            val cornerRadius = resources.getDimension(com.simplemobiletools.commons.R.dimen.rounded_corner_radius_small).toInt()
            val wantedSize = resources.getDimension(R.dimen.song_image_size).toInt()

            // reduce thread overhead
            if (coverArt is Bitmap) {
                if (binding.nextTrackImage.outlineProvider === ViewOutlineProvider.BACKGROUND) {
                    binding.nextTrackImage.apply {
                        clipToOutline = true
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius.toFloat())
                            }
                        }
                    }
                }

                binding.nextTrackImage.setImageBitmap(coverArt)
                return@getTrackFileArt
            }

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
        if (isNextTrack) {
            binding.activityTrackImage.setInAnimation(this, R.anim.slide_in_right)
            binding.activityTrackImage.setOutAnimation(this, R.anim.slide_out_left)
        } else if (isPrevTrack) {
            binding.activityTrackImage.setInAnimation(this, R.anim.slide_in_left)
            binding.activityTrackImage.setOutAnimation(this, R.anim.slide_out_right)
        } else {
            binding.activityTrackImage.inAnimation = null
            binding.activityTrackImage.outAnimation = null
        }

        var wantedHeight = resources.getCoverArtHeight()
        wantedHeight = min(wantedHeight, realScreenSize.y / 2)
        val wantedWidth = realScreenSize.x

        if (binding.activityTrackImage.height != wantedHeight) {
            binding.activityTrackImage.layoutParams.height = wantedHeight
        }

        getTrackFileArt(track) { coverArt ->
            if (isFinishing || isDestroyed) return@getTrackFileArt
            // reduce thread overhead
            if (coverArt is Bitmap) {
                binding.activityTrackImage.setImageDrawable(BitmapDrawable(resources, coverArt))
                return@getTrackFileArt
            }

            // change cover image manually only once loaded successfully to avoid blinking at fails and placeholders
            loadGlideResource(
                model = coverArt,
                options = RequestOptions().fitCenter(),
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
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                    }
                } else {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            onSwipeDown()
                        } else {
                            onSwipeUp()
                        }
                    }
                }
                return true
            }

            private fun onSwipeDown() {
                finish()
                binding.activityTrackTopShadow.animate().alpha(0f).start()
                overridePendingTransition(0, com.simplemobiletools.commons.R.anim.slide_down)
            }

            private fun onSwipeUp() {
                binding.nextTrackHolder.performClick()
            }

            private fun onSwipeLeft() {
                binding.activityTrackNext.performClick()
            }

            private fun onSwipeRight() {
                binding.activityTrackPrevious.performClick()
            }
        }

        val gestureDetector = GestureDetectorCompat(this, flingListener)
        binding.activityTrackHolder.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
        binding.activityTrackTitle.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
        binding.activityCueTitle.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
        binding.activityTrackArtist.setOnTouchListener { _, event ->
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
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                isNextTrack = true
                isPrevTrack = false
            }
            binding.activityTrackProgressbar.progress = 0
            updateTrackInfo()
        }
    }

    private fun updateTrackInfo() {
        withPlayer {
            setupTrackInfo(currentMediaItem)
            setupNextTrackInfo(nextMediaItem)
            isNextTrack = false
            isPrevTrack = false
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
        if (isFinishing || isDestroyed) return
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
        // Update active cue item
        if (cueAdapter?.itemCount == 0) return
        val mediaStoreId = currentTrack?.mediaStoreId ?: return
        cueAdapter?.let { adapter ->
            val newPosition = adapter.updateCurrentPosition(mediaStoreId, seconds)
            if (newPosition >= 0) {
                updateCueTitle(mediaStoreId, newPosition)
            }
        }
    }

    private fun updateCueTitle(mediaStoreId: Long, position: Int) {
        // auto scroll to cue position
        if (position >= 0) {
            binding.activityTrackCuesList.scrollToPosition(position)
        }

        // Update cue title
        cueAdapter?.getCurrentTitle(mediaStoreId)?.let {
            binding.apply {
                activityCueTitle.isVisible = true
                activityCueTitle.text = it
                activityCueTitle.setOnLongClickListener {
                    copyToClipboard(activityCueTitle.value)
                    true
                }
            }
        } ?: run {
            binding.activityCueTitle.isVisible = false
        }
    }

    private fun updatePlayPause(isPlaying: Boolean) {
        binding.activityTrackPlayPause.updatePlayPauseIcon(isPlaying, getProperTextColor())
    }

    private fun setupCues(track: Track) {
        CueListCache.peekCueList(track.fileStableId)?.let {
            updateCueList(track, it)
            return
        }

        executeBackgroundThread {
            val cues = CueListCache.getCueList(applicationContext, track.fileStableId)
            runOnUiThread {
                updateCueList(track, cues)
            }
        }
    }

    private fun updateCueList(track: Track, cues: List<Cue>) {
        if (cues.isNotEmpty()) {
            if (cueAdapter == null) {
                cueAdapter = CueAdapter(this, { cue ->
                    withPlayer {
                        if (!isPlaying) play()
                        seekTo(cue.timestamp * 1000L)
                    }
                }, { track, updatedCues ->
                    CueListCache.updateCacheByCueList(track.fileStableId, updatedCues)
                    CueListCache.saveCueListAsync(this@TrackActivity, track, Gson().toJson(updatedCues))
                }, { track -> onClickNewCue(track) })
                binding.activityTrackCuesList.apply {
                    layoutManager = LinearLayoutManager(this@TrackActivity)
                    adapter = cueAdapter
                }
            }
            cueAdapter?.refreshList(cues, track)

            withPlayer {
                val seconds = currentPosition.milliseconds.inWholeSeconds.toInt()
                runOnUiThread {
                    val position = cueAdapter?.updateCurrentPosition(track.mediaStoreId, seconds) ?: -1
                    if (position >= 0) {
                        updateCueTitle(track.mediaStoreId, position)
                    }
                }
            }
            binding.activityTrackCuesList.beVisible()
        } else {
            cueAdapter?.refreshList(cues, track)
            binding.activityTrackCuesList.beGone()
        }
    }

    private fun onClickNewCue(track: Track) {
        withPlayer {
            val currentTimestamp = currentPosition.milliseconds.inWholeSeconds.toInt()
            runOnUiThread {
                val binding = AdjustCueTimestampBinding.inflate(LayoutInflater.from(this@TrackActivity))
                binding.buttons.children.forEach { it.setOnClickListener { binding.seconds.setText((it as? TextView)?.text) } }
                binding.timestamp.text = currentTimestamp.getFormattedDuration()
                AlertDialog.Builder(this@TrackActivity)
                    .setTitle(R.string.new_timestamp)
                    .setView(binding.root)
                    .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                    .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                        val seconds = binding.seconds.text.toString().toIntOrNull() ?: 0
                        val timestamp = (currentTimestamp + seconds).takeIf { it >= 0 } ?: 0
                        val title = binding.title.text.toString().replace("\n", "").trim().takeIf { it.isNotBlank() } ?: "Untitled"
                        val cue = Cue(timestamp, title)
                        val cueList = cueAdapter?.cues?.toMutableList() ?: return@setPositiveButton
                        cueList.add(cue)
                        cueList.sortBy { it.timestamp }
                        updateCueList(track, cueList)
                        CueListCache.updateCacheByCueList(track.fileStableId, cueList)
                        CueListCache.saveCueListAsync(this@TrackActivity, track, Gson().toJson(cueList))
                    }
                    .show()
            }
        }
    }

    private fun showEditCuesDialog(cuesJson: String? = null) {
        val track = currentTrack ?: return
        executeBackgroundThread {
            val currentCuesJson = cuesJson ?: audioHelper.getTrackCue(track.fileStableId)
            runOnUiThread {
                val binding = InputCueTextBinding.inflate(LayoutInflater.from(this))
                val editText = binding.editText
                editText.setText(CueListHelper.cueJsonToText(currentCuesJson))
                editText.setTextColor(getProperTextColor())
                editText.setHintTextColor(getProperTextColor().adjustAlpha(0.5f))
                initLinkTextView(binding.description)

                AlertDialog.Builder(this)
                    .setTitle(R.string.youtube_timestamp_text)
                    .setView(binding.root)
                    .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                        val newCueJson = CueListHelper.getCueJsonFromText(editText.text.toString())
                        updateCueList(track, CueListCache.updateCacheByCueJson(track.fileStableId, newCueJson))
                        CueListCache.saveCueListAsync(this@TrackActivity, track, newCueJson)
                    }
                    .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                    .setNeutralButton(R.string.no_cue) { _, _ ->
                        val cues = listOf(Cue(0, "<NO_CUE>", false))
                        val newCueJson = Gson().toJson(cues)
                        updateCueList(track, CueListCache.updateCacheByCueJson(track.fileStableId, newCueJson))
                        CueListCache.saveCueListAsync(this@TrackActivity, track, newCueJson)
                    }
                    .show()
            }
        }
    }

    private fun initLinkTextView(textView: TextView) {
        val desc = getString(R.string.cue_not_playable_desc) + " "
        val start = desc.length
        val linkText = getString(R.string.demo_video)
        val end = start + linkText.length
        val spannable = SpannableString(desc + linkText)

        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    this@TrackActivity.launchViewIntent(DEMO_VIDEO_URL)
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = getProperPrimaryColor()
                    ds.isUnderlineText = true
                }
            },
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }

    private fun showMetaDataDialog() {
        val track = currentTrack ?: return
        val mediaItem = if (track.path.isNotEmpty()) {
            MediaItem.fromUri(track.path)
        } else {
            val uriStr = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId).toString()
            val uri = Uri.parse(uriStr)
            MediaItem.fromUri(uri)
        }

        data class MetaData(val type: String, val key: String, val value: String)

        val metadataFuture = MetadataRetriever.retrieveMetadata(this, mediaItem)
        metadataFuture.addListener({
            val metaDataList = mutableListOf<MetaData>()
            val allList = StringBuilder()
            val chapterList = mutableListOf<String>()
            var lastChapterTimeString = ""

            try {
                val trackGroupArray = metadataFuture.get()
                for (i in 0 until trackGroupArray.length) {
                    val trackGroup = trackGroupArray.get(i)
                    val metadata = trackGroup.getFormat(0).metadata

                    if (metadata != null) {
                        for (j in 0 until metadata.length()) {
                            val entry = metadata.get(j)
                            allList.append(entry.toString()).append("\n\n")
                            Log.d("Metadata", "찾은 내용: ${entry.toString()}")
                            // Opus/Ogg의 경우 VorbisComment 형태로 들어옵니다.
                            if (entry is VorbisComment) {
                                Log.d("Metadata", "찾은 내용: ${entry.value}")
                                val key = entry.key.lowercase()
                                if (key == "description" || key == "purl" || key == "comment" || key == "synopsis") {
                                    val value = entry.value
                                    if (!metaDataList.any { it.value == value }) {
                                        metaDataList.add(MetaData("VorbisComment", key, value))
                                    }
                                } else if (entry.key.startsWith("CHAPTER")) {
                                    val key = entry.key
                                    if (key.endsWith("NAME")) {
                                        if (lastChapterTimeString.isNotEmpty()) {
                                            chapterList.add(lastChapterTimeString + "  " + entry.value)
                                        }
                                        lastChapterTimeString = ""
                                    } else {
                                        lastChapterTimeString = entry.value.substringBefore('.')
                                    }
                                }
                            } else if (entry is TextInformationFrame) {
                                val key = entry.description?.lowercase()
                                if (key == "description" || key == "purl" || key == "comment" || key == "synopsis") {
                                    val value = entry.value
                                    if (!metaDataList.any { it.value == value }) {
                                        metaDataList.add(MetaData("TextInformationFrame", key, value))
                                    }
                                }
                            } else if (entry is CommentFrame) {
                                val value = entry.description
                                if (!metaDataList.any { it.value == value }) {
                                    metaDataList.add(MetaData("CommentFrame", "comment", value))
                                }
                            } else if (entry is MdtaMetadataEntry) { // apple
                                if (entry.typeIndicator == MdtaMetadataEntry.TYPE_INDICATOR_STRING) {
                                    val value = Util.fromUtf8Bytes(entry.value)
                                    metaDataList.add(MetaData("MdtaMetadataEntry", entry.key, value))
                                }
                            } else if (entry is ChapterFrame) { // mp3
                                // 1. 기본 식별자 (사용자용 제목 아님)
                                val id = entry.chapterId

                                // 2. 시간 정보
                                val start = entry.startTimeMs
                                val end = entry.endTimeMs

                                // 3. 챕터 타이틀 찾기 (서브 프레임 내부)
                                var chapterTitle = "Untitled"
                                for (index in 0..<entry.subFrameCount) {
                                    val subEntry = entry.getSubFrame(index)
                                    // MP3에서 제목은 보통 'TIT2'라는 ID를 가진 TextInformationFrame에 담깁니다.
                                    if (subEntry is TextInformationFrame && subEntry.id == "TIT2") {
                                        chapterTitle = subEntry.value
                                        break
                                    }
                                }

                                Log.d("Metadata", "챕터명: $chapterTitle (시작: ${start}ms)")
                                chapterList.add(formatMilliseconds(start.toLong()) + "  " + chapterTitle)
                            } else if (entry is UrlLinkFrame) {
                                val value = entry.url
                                if (!metaDataList.any { it.value == value }) {
                                    metaDataList.add(MetaData("UrlLinkFrame", "url", value))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var cuesJson = ""
            if (chapterList.isNotEmpty()) {
                cuesJson = CueListHelper.getCueJsonFromText(chapterList.joinToString("\n"))
            }
            val linkList = mutableListOf<String>()
            val sb = StringBuilder()
            metaDataList.forEach { (type, key, value) ->
                if (BuildConfig.DEBUG) {
                    sb.append("($type)")
                } else {
                    //sb.append("(${type[0]})")
                }
                sb.append(key).append("=").append(value).append("\n\n")
                val links = extractYoutubeLinks(value)
                links.forEach { if (!linkList.contains(it)) linkList.add(it) }
                if (cuesJson.isEmpty() && (key == "description" || key == "synopsis" || key == "comment")) {
                    cuesJson = CueListHelper.getCueJsonFromText(value)
                }
            }

            if (cuesJson.isNotEmpty()) {
                if (chapterList.isNotEmpty()) {
                    sb.append("CHAPTER:\n").append(CueListHelper.cueJsonToText(cuesJson))
                } else {
                    sb.append("CUE:\n").append(CueListHelper.cueJsonToText(cuesJson))
                }
            }

            val text = sb.toString()
            val allText = allList.toString()
            val textView = TextView(this)
            textView.text = text.ifEmpty { allText }
            val p = 30
            textView.setPadding(p, p, p, p)
            textView.setTextIsSelectable(true)

            val builder = AlertDialog.Builder(this)
                .setTitle(R.string.youtube_meta_data)
                .setView(textView)
                .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)

            if (text.isNotEmpty()) {
                builder.setNeutralButton(R.string.all_data) { _, _ ->
                    showAllMetaData(allText, linkList)
                }
            }
            val isCueListEmpty = CueListCache.getCueList(this, currentTrack?.fileStableId ?: 0).isEmpty()
            if (isCueListEmpty && cuesJson.isNotEmpty()) {
                builder.setNegativeButton(R.string.make_cue_list) { _, _ ->
                    showEditCuesDialog(cuesJson)
                }
            } else if (linkList.isNotEmpty()) {
                builder.setNegativeButton(R.string.youtube) { _, _ ->
                    openYoutubeLink(linkList)
                }
            }
            builder.show()

        }, ContextCompat.getMainExecutor(this))
    }

    private fun showAllMetaData(text: String, linkList: List<String>) {
        val textView = TextView(this)
        textView.text = text
        val p = 30
        textView.setPadding(p, p, p, p)
        textView.setTextIsSelectable(true)

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.youtube_meta_data)
            .setView(textView)
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
        if (linkList.isNotEmpty()) {
            builder.setNegativeButton(R.string.youtube) { _, _ ->
                openYoutubeLink(linkList)
            }
        }
        builder.show()
    }

    private fun openYoutubeLink(linkList: List<String>) {
        if (linkList.size == 1) {
            openUrl(linkList[0])
        } else {
            AlertDialog.Builder(this)
                .setItems(linkList.toTypedArray()) { _, w ->
                    openUrl(linkList[w])
                }
                .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
                .show()
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        //intent.setPackage("com.google.android.youtube")
        try {
            startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(this, ex.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractYoutubeLinks(text: String): List<String> {
        // YouTube 주소 패턴 정의 (다양한 포맷 대응)
        val youtubeRegex = ("(?:https?:\\/\\/)?(?:www\\.)?" +
            "(?:youtube\\.com\\/(?:(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?)\\/|\\S*?[?&]v=)|shorts\\/)|" +
            "youtu\\.be\\/)([a-zA-Z0-9_-]{11})").toRegex()

        // 텍스트 내의 모든 매칭 결과 추출
        return youtubeRegex.findAll(text).map { it.value }.toList()
    }

    private fun formatMilliseconds(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

        return if (hours > 0) {
            // 시간이 있을 경우 "HH:mm:ss"
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            // 시간이 없을 경우 "mm:ss"
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun updateFavorite(track: Track) {
        executeBackgroundThread {
            val isFavorite = audioHelper.isFavoriteTrack(track)
            runOnUiThread {
                binding.activityTrackToolbar.menu.findItem(R.id.favorite).icon =
                    if (isFavorite) resources.getDrawable(R.drawable.ic_favorite) else resources.getDrawable(R.drawable.ic_not_favorite)
            }
        }
    }

    private fun toggleFavorite() {
        val track = currentTrack ?: return
        executeBackgroundThread {
            val isFavorite = audioHelper.toggleFavorite(track)
            runOnUiThread {
                binding.activityTrackToolbar.menu.findItem(R.id.favorite).icon =
                    if (isFavorite) resources.getDrawable(R.drawable.ic_favorite) else resources.getDrawable(R.drawable.ic_not_favorite)
            }
        }
    }

    private fun gotoArtistPage() {
        val track = currentTrack ?: return
        executeBackgroundThread {
            val artist = artistDAO.select(track.artistId) ?: return@executeBackgroundThread
            runOnUiThread {
                Intent(this, AlbumsActivity::class.java).apply {
                    putExtra(ARTIST, Gson().toJson(artist))
                    startActivity(this)
                }
            }
        }
    }

    private fun gotoAlbumPage() {
        val track = currentTrack ?: return
        executeBackgroundThread {
            val album = albumsDAO.select(track.albumId) ?: return@executeBackgroundThread
            runOnUiThread {
                Intent(this, TracksActivity::class.java).apply {
                    putExtra(ALBUM, Gson().toJson(album))
                    startActivity(this)
                }
            }
        }
    }
}
