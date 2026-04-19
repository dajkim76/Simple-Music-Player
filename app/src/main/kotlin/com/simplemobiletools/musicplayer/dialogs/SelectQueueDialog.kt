package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.views.MyEditText
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SelectQueueAdapter
import com.simplemobiletools.musicplayer.databinding.DialogSelectQueueBinding
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.models.QueueData
import com.simplemobiletools.musicplayer.models.getQueueDataListFromJson
import com.simplemobiletools.musicplayer.models.toJson

class SelectQueueDialog(
    val activity: Activity,
    val playQueue: Boolean = true,
    val isBottomGravity: Boolean = false,
    val callback: (queueId: Long) -> Unit = {}
) {
    private var dialog: AlertDialog? = null
    private val config = activity.config
    private val binding by activity.viewBinding(DialogSelectQueueBinding::inflate)
    private val textColor = activity.getProperTextColor()
    private val queueId = config.queueId
    private var adapter: SelectQueueAdapter

    init {
        val queueDataList = getQueueDataListFromJson(config.queueListJson).toMutableList()
        queueDataList.add(0, QueueData(activity.getString(R.string.default_queue), 0))

        adapter = SelectQueueAdapter(
            activity as BaseSimpleActivity,
            queueDataList,
            playQueue,
            isBottomGravity = isBottomGravity,
            queueId,
            binding.dialogSelectQueueList
        ) { id ->
            callback.invoke(id)
            dialog?.dismiss()
        }
        binding.dialogSelectQueueList.adapter = adapter

        binding.dialogSelectQueueNew.setTextColor(textColor)
        binding.dialogSelectQueueNew.setOnClickListener { createNewQueue() }

        if (isBottomGravity) {
            binding.dialogSelectQueueDivider.isVisible = false
            binding.dialogSelectQueueNew.isVisible = false
        }

        activity.getAlertDialogBuilder().apply {
            setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            if (isBottomGravity) setNeutralButton(com.simplemobiletools.commons.R.string.cancel, null)
            activity.setupDialogStuff(binding.root, this, titleId = if (playQueue) R.string.play_queue else R.string.select_queue) { alertDialog ->
                dialog = alertDialog
                if (isBottomGravity) {
                    dialog?.window?.setGravity(Gravity.BOTTOM)
                    // limit max height, Easy to operate with one hand.
                    val container = binding.dialogSelectQueueList
                    container.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            container.viewTreeObserver.removeOnGlobalLayoutListener(this)

                            val maxHeight = context.resources.displayMetrics.heightPixels * .3f
                            if (container.height > maxHeight) {
                                container.updateLayoutParams { height = maxHeight.toInt() }
                            }
                        }
                    })
                }
            }
        }
    }

    private fun createNewQueue() {
        activity.showQueueNameDialog("") { name ->
            val newQueueId = config.nextQueueId
            config.nextQueueId++

            val newQueue = QueueData(name, newQueueId)
            adapter.items.add(newQueue)
            adapter.notifyItemInserted(adapter.items.size - 1)

            val queueDataList = getQueueDataListFromJson(config.queueListJson).toMutableList()
            queueDataList.add(newQueue)
            config.queueListJson = queueDataList.toJson()
        }
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
