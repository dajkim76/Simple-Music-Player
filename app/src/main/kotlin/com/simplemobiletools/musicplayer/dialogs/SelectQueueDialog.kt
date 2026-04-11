package com.simplemobiletools.musicplayer.dialogs

import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.views.MyEditText
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.ShortcutReceiverActivity.Companion.createTracklistShortcut
import com.simplemobiletools.musicplayer.activities.SimpleControllerActivity
import com.simplemobiletools.musicplayer.databinding.DialogSelectTracklistBinding
import com.simplemobiletools.musicplayer.databinding.ItemSelectQueueBinding
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.TRACKLIST_QUEUE
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.onSelectTracklist
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.queueDAO
import com.simplemobiletools.musicplayer.models.QueueData
import com.simplemobiletools.musicplayer.models.getQueueDataListFromJson
import com.simplemobiletools.musicplayer.models.toJson
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread

class SelectQueueDialog(val activity: SimpleControllerActivity) {
    private var dialog: AlertDialog? = null
    private val config = activity.config
    private val binding by activity.viewBinding(DialogSelectTracklistBinding::inflate)
    private val primaryColor = activity.getProperPrimaryColor()
    private val foregroundDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.rounded_white_border, activity.getProperPrimaryColor())
    private val lastQueueSource = config.lastQueueSource.takeIf { it.startsWith("q:") } ?: "q:0"

    init {
        val queueDataList = getQueueDataListFromJson(config.queueListJson).toMutableList()
        queueDataList.add(0, QueueData("Default", 0))
        queueDataList.forEach {
            addItemView("q:", it.name, TRACKLIST_QUEUE, it.queueId)
        }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(binding.root, this, titleId = R.string.change_queue) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun addItemView(prefix: String, title: String, type: Int, id: Long) {
        ItemSelectQueueBinding.inflate(activity.layoutInflater).apply {
            name.apply {
                text = title
                setTextColor(activity.getProperTextColor())
                setOnClickListener {
                    executeBackgroundThread {
                        activity.onSelectTracklist(type, id, "")
                    }
                    dialog?.dismiss()
                }
            }

            more.applyColorFilter(primaryColor)
            more.setOnClickListener {
                val items = if (id == 0L) {
                    arrayOf(activity.getString(com.simplemobiletools.commons.R.string.create_shortcut))
                } else {
                    arrayOf(
                        activity.getString(R.string.queue_name),
                        activity.getString(com.simplemobiletools.commons.R.string.create_shortcut),
                        activity.getString(com.simplemobiletools.commons.R.string.delete)
                    )
                }
                activity.getAlertDialogBuilder()
                    .setItems(items) { _, w ->
                        if (id == 0L) {
                            createShortcut(id, title)
                        } else {
                            when (w) {
                                0 -> changeQueueName(id, title, name)
                                1 -> createShortcut(id, title)
                                2 -> deleteQueue(id, title) { binding.dialogSelectPlaylistLinear.removeView(this.root) }
                            }
                        }
                    }
                    .show()
            }

            // last selected track list
            if ("$prefix$id" == lastQueueSource) {
                this.root.foreground = foregroundDrawable
            }

            binding.dialogSelectPlaylistLinear.addView(
                this.root,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    fun changeQueueName(id: Long, title: String, nameView: TextView) {
        activity.showQueueNameDialog(title) { name ->
            val newName = name.takeIf { it.isNotEmpty() } ?: "noname"
            nameView.text = newName

            val queueDataList = getQueueDataListFromJson(config.queueListJson)
            queueDataList.find { it.queueId == id }?.name = newName
            config.queueListJson = queueDataList.toJson()
        }
    }

    fun createShortcut(id: Long, title: String) {
        activity.createTracklistShortcut(title, "q:$id", drawableId = R.drawable.ic_shortcut_queue)
    }

    fun deleteQueue(id: Long, title: String, callback: () -> Unit) {
        activity.getAlertDialogBuilder()
            .setTitle(title)
            .setMessage(com.simplemobiletools.commons.R.string.are_you_sure_delete)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                // delete
                val queueDataList = getQueueDataListFromJson(config.queueListJson).toMutableList()
                queueDataList.removeIf { it.queueId == id }
                config.queueListJson = queueDataList.toJson()
                callback.invoke()

                executeBackgroundThread {
                    activity.queueDAO.deleteAllItems(id)
                    if (config.queueId == id) {
                        activity.onSelectTracklist(TRACKLIST_QUEUE, 0, "")
                    }
                }
            }
            .show()
    }

    companion object {
        fun SimpleControllerActivity.showQueueNameDialog(title: String, callback: (title: String) -> Unit) {
            // build layout
            val edit = MyEditText(this).apply {
                isSingleLine = true
                setText(title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            val p = resources.getDimensionPixelSize(com.simplemobiletools.commons.R.dimen.medium_margin)
            val layout = LinearLayout(this).apply {
                setPadding(p, p, p, p)
                addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            val builder = getAlertDialogBuilder()
                .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                    val name = edit.text.toString().takeIf { it.isNotEmpty() } ?: "noname"
                    callback.invoke(name)
                }

            setupDialogStuff(layout, builder, titleId = R.string.queue_name)
        }
    }
}
