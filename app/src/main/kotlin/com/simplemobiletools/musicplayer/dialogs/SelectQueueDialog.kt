package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
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
import com.simplemobiletools.musicplayer.databinding.DialogSelectQueueBinding
import com.simplemobiletools.musicplayer.databinding.ItemSelectQueueBinding
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.TRACKLIST_QUEUE
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.onSelectTracklist
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.queueDAO
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.QueueData
import com.simplemobiletools.musicplayer.models.getQueueDataListFromJson
import com.simplemobiletools.musicplayer.models.toJson
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread
import org.greenrobot.eventbus.EventBus

class SelectQueueDialog(val activity: Activity, val playQueue: Boolean = true, val callback: (queueId: Long) -> Unit = {}) {
    private var dialog: AlertDialog? = null
    private val config = activity.config
    private val binding by activity.viewBinding(DialogSelectQueueBinding::inflate)
    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()
    private val foregroundDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.rounded_white_border, activity.getProperPrimaryColor())
    private val queueId = config.queueId

    init {
        val queueDataList = getQueueDataListFromJson(config.queueListJson).toMutableList()
        queueDataList.add(0, QueueData(activity.getString(R.string.default_queue), 0))
        queueDataList.forEach {
            addItemView(it.name, it.queueId)
        }

        binding.dialogSelectQueueNew.setTextColor(textColor)
        binding.dialogSelectQueueNew.setOnClickListener { createNewQueue() }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(binding.root, this, titleId = if (playQueue) R.string.play_queue else R.string.select_queue) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun addItemView(title: String, id: Long) {
        ItemSelectQueueBinding.inflate(activity.layoutInflater).apply {
            name.apply {
                text = title
                setTextColor(textColor)
                setOnClickListener {
                    if (playQueue) {
                        prepareQueue(id)
                    }
                    callback.invoke(id)
                    dialog?.dismiss()
                }
            }

            more.applyColorFilter(textColor)
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
                    .setTitle(title)
                    .setItems(items) { _, w ->
                        if (id == 0L) {
                            createShortcut(id, title)
                        } else {
                            when (w) {
                                0 -> changeQueueName(id, title, name)
                                1 -> createShortcut(id, title)
                                2 -> deleteQueue(id, title) { binding.dialogSelectQueueLinear.removeView(this.root) }
                            }
                        }
                    }
                    .show()
            }

            // last selected track list
            if (queueId == id) {
                this.root.foreground = foregroundDrawable
            }

            binding.dialogSelectQueueLinear.addView(
                this.root,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    private fun prepareQueue(queueId: Long) {
        val simpleControllerActivity = (activity as? SimpleControllerActivity) ?: return
        simpleControllerActivity.withPlayer {
            val startPlay = isPlaying
            executeBackgroundThread {
                simpleControllerActivity.onSelectTracklist(TRACKLIST_QUEUE, queueId, "", startPlay = startPlay)
            }
        }
    }

    private fun createNewQueue() {
        activity.showQueueNameDialog("") { name ->
            val queueId = config.nextQueueId
            config.nextQueueId++
            addItemView(name, queueId)

            val queueDataList = getQueueDataListFromJson(config.queueListJson).toMutableList()
            queueDataList.add(QueueData(name, queueId))
            config.queueListJson = queueDataList.toJson()
        }
    }

    private fun changeQueueName(id: Long, title: String, nameView: TextView) {
        activity.showQueueNameDialog(title) { newName ->
            nameView.text = newName

            val queueDataList = getQueueDataListFromJson(config.queueListJson)
            queueDataList.find { it.queueId == id }?.name = newName
            config.queueListJson = queueDataList.toJson()
        }
    }

    private fun createShortcut(id: Long, title: String) {
        activity.createTracklistShortcut(title, "q:$id", drawableId = R.drawable.ic_shortcut_queue)
    }

    private fun deleteQueue(id: Long, title: String, callback: () -> Unit) {
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
                        prepareQueue(0)
                    }
                    if (config.tabQueueId == id) {
                        config.tabQueueId = 0
                        EventBus.getDefault().post(Events.QueueItemsChanged.setQueueId(id))
                    }
                }
            }
            .show()
    }

    companion object {
        fun Activity.showQueueNameDialog(title: String, callback: (title: String) -> Unit) {
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
