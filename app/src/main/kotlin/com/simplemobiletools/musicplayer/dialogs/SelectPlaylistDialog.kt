package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.databinding.DialogSelectPlaylistBinding
import com.simplemobiletools.musicplayer.databinding.ItemSelectPlaylistBinding
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.SMART_PLAYLIST_ID_MAX
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.sortSafely

class SelectPlaylistDialog(val activity: Activity, val callback: (playlistId: Int) -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectPlaylistBinding::inflate)

    init {
        ensureBackgroundThread {
            val playlists = activity.audioHelper.getAllPlaylists().filter { it.id > SMART_PLAYLIST_ID_MAX }
            activity.runOnUiThread {
                initDialog(ArrayList(playlists))

                if (playlists.isEmpty()) {
                    showNewPlaylistDialog()
                }
            }
        }

        binding.dialogSelectPlaylistNewRadio.setOnClickListener {
            binding.dialogSelectPlaylistNewRadio.isChecked = false
            showNewPlaylistDialog()
        }
    }

    private fun initDialog(playlists: ArrayList<Playlist>) {
        playlists.sortSafely(activity.config.playlistSorting)
        playlists.forEach {
            ItemSelectPlaylistBinding.inflate(activity.layoutInflater).apply {
                val playlist = it
                selectPlaylistItemRadioButton.apply {
                    text = playlist.title
                    isChecked = false
                    id = playlist.id

                    setOnClickListener {
                        callback(playlist.id)
                        dialog?.dismiss()
                    }
                }

                binding.dialogSelectPlaylistLinear.addView(
                    this.root,
                    RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }
        }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun showNewPlaylistDialog() {
        NewPlaylistDialog(activity) {
            callback(it)
            dialog?.dismiss()
        }
    }
}
