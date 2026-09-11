package com.simplemobiletools.musicplayer.dialogs

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.DialogRestoreDataBinding

data class RestoreOptions(
    val restoreQueue: Boolean,
    val restorePlaylist: Boolean,
    val restoreFavorite: Boolean,
    val restoreCue: Boolean
) {
    fun hasAnySelected() = restoreQueue || restorePlaylist || restoreFavorite || restoreCue
}

class RestoreDataDialog(
    val activity: BaseSimpleActivity,
    val callback: (options: RestoreOptions) -> Unit
) {
    private val binding by activity.viewBinding(DialogRestoreDataBinding::inflate)

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                val options = RestoreOptions(
                    restoreQueue = binding.restoreCheckboxQueuelist.isChecked,
                    restorePlaylist = binding.restoreCheckboxPlaylist.isChecked,
                    restoreFavorite = binding.restoreCheckboxFavorite.isChecked,
                    restoreCue = binding.restoreCheckboxCue.isChecked
                )
                callback(options)
            }
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.restore_data_title)
            }
    }
}
