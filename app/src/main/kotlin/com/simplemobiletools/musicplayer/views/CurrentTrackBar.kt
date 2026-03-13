package com.simplemobiletools.musicplayer.views

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RelativeLayout
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ViewCurrentTrackBarBinding
import com.simplemobiletools.musicplayer.extensions.*

class CurrentTrackBar(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    private val binding by viewBinding(ViewCurrentTrackBarBinding::bind)
    private var activity: Activity? = null

    fun initialize(activity: Activity, togglePlayback: () -> Unit, seekToNext: () -> Unit) {
        this.activity = activity
        binding.currentTrackPlayPause.setOnClickListener {
            togglePlayback()
        }
        binding.nextTrackImage.setOnClickListener {
            seekToNext()
        }
    }

    fun updateColors() {
        background = ColorDrawable(context.getProperBackgroundColor())
        binding.currentTrackLabel.setTextColor(context.getProperTextColor())
        binding.nextTrackImage.applyColorFilter(context.getProperTextColor())
    }

    fun updateCurrentTrack(mediaItem: MediaItem?) {
        val track = mediaItem?.toTrack()
        if (track == null) {
            beGone()
            return
        } else {
            beVisible()
        }

        val artist = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else {
            ""
        }

        @SuppressLint("SetTextI18n")
        binding.currentTrackLabel.text = "${track.title}$artist"
        val cornerRadius = resources.getDimension(com.simplemobiletools.commons.R.dimen.rounded_corner_radius_small).toInt()
        val currentTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, context.getProperTextColor())
        val options = RequestOptions()
            .error(currentTrackPlaceholder)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))

        context.getTrackFileArt(track) { coverArt ->
            if (activity?.isFinishing == true || activity?.isDestroyed == true) return@getTrackFileArt
            if (coverArt is Bitmap) {
                if (binding.currentTrackImage.outlineProvider === ViewOutlineProvider.BACKGROUND) {
                    binding.currentTrackImage.apply {
                        clipToOutline = true
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius.toFloat())
                            }
                        }
                    }
                }

                binding.currentTrackImage.setImageBitmap(coverArt)
                return@getTrackFileArt
            }

            (context as? Activity)?.ensureActivityNotDestroyed {
                Glide.with(this)
                    .load(coverArt)
                    .apply(options)
                    .into(binding.currentTrackImage)
            }
        }
    }

    fun updateTrackState(isPlaying: Boolean) {
        binding.currentTrackPlayPause.updatePlayPauseIcon(isPlaying, context.getProperTextColor())
    }
}
