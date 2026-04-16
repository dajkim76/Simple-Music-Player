package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.adapters.SelectPlaylistAdapter
import com.simplemobiletools.musicplayer.databinding.DialogSelectPlaylistBinding
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.helpers.SMART_PLAYLIST_ID_MAX
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import org.greenrobot.eventbus.EventBus

class SelectPlaylistDialog(val activity: Activity, val callback: (playlistIds: List<Int>) -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectPlaylistBinding::inflate)
    private var adapter: SelectPlaylistAdapter? = null

    init {
        ensureBackgroundThread {
            val playlists = activity.audioHelper.getAllPlaylists().filter { it.id > SMART_PLAYLIST_ID_MAX }.toMutableList()
            activity.runOnUiThread {
                initDialog(playlists)

                if (playlists.isEmpty()) {
                    showNewPlaylistDialog()
                }
            }
        }

        binding.dialogSelectPlaylistNewText.apply {
            setTextColor(activity.getProperTextColor())
            setOnClickListener {
                showNewPlaylistDialog()
            }
        }
    }

    private fun initDialog(playlists: MutableList<Playlist>) {
        adapter = SelectPlaylistAdapter(activity as BaseSimpleActivity, playlists)
        binding.dialogSelectPlaylistList.adapter = adapter

        activity.getAlertDialogBuilder().apply {
            setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                val playlistIds = adapter?.getSelectedPlaylistIds() ?: emptyList()
                if (playlistIds.isNotEmpty()) {
                    callback(playlistIds)
                    dialog?.dismiss()
                } else {
                    activity.toast(com.simplemobiletools.commons.R.string.no_files_selected)
                }
            }
            setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)

            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun showNewPlaylistDialog() {
        NewPlaylistDialog(activity) { playlistId ->
            ensureBackgroundThread {
                val playlist = activity.audioHelper.getAllPlaylists().find { it.id == playlistId }
                if (playlist != null) {
                    activity.runOnUiThread {
                        adapter?.addPlaylist(playlist)
                        binding.dialogSelectPlaylistList.scrollToPosition(adapter?.itemCount?.minus(1) ?: 0)
                    }
                }
            }
            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }
}
