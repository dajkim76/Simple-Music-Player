package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.executeBackgroundThread
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.ShortcutReceiverActivity.Companion.createTracklistShortcut
import com.simplemobiletools.musicplayer.activities.SimpleControllerActivity
import com.simplemobiletools.musicplayer.databinding.ItemSelectQueueBinding
import com.simplemobiletools.musicplayer.dialogs.SelectQueueDialog.Companion.showQueueNameDialog
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.TRACKLIST_QUEUE
import com.simplemobiletools.musicplayer.dialogs.SelectTracklistDialog.Companion.onSelectTracklist
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.queueDAO
import com.simplemobiletools.musicplayer.extensions.swap
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.QueueData
import com.simplemobiletools.musicplayer.models.toJson
import org.greenrobot.eventbus.EventBus


// copy from ItemTouchHelperContract for supporting RecyclerView.ViewHolder instead of MyRecyclerViewAdapter.ViewHolder
private interface MyItemTouchHelperContract {
    fun onRowMoved(fromPosition: Int, toPosition: Int)

    fun onRowSelected(myViewHolder: RecyclerView.ViewHolder?)

    fun onRowClear(myViewHolder: RecyclerView.ViewHolder?)
}

// copy from ItemMoveCallback for supporting RecyclerView.ViewHolder instead of MyRecyclerViewAdapter.ViewHolder
private class MyItemMoveCallback(private val mAdapter: MyItemTouchHelperContract, private val allowHorizontalDrag: Boolean = false) :
    ItemTouchHelper.Callback() {
    override fun isLongPressDragEnabled() = false

    override fun isItemViewSwipeEnabled() = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {}

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        var dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        if (allowHorizontalDrag) {
            dragFlags = dragFlags or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        }
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        mAdapter.onRowMoved(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            mAdapter.onRowSelected(viewHolder)
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        mAdapter.onRowClear(viewHolder)
    }
}

class SelectQueueAdapter(
    val activity: BaseSimpleActivity,
    val items: MutableList<QueueData>,
    val playQueue: Boolean,
    val currentQueueId: Long,
    val recyclerView: MyRecyclerView,
    val itemClick: (Long) -> Unit
) : RecyclerView.Adapter<SelectQueueAdapter.ViewHolder>(), MyItemTouchHelperContract {

    private val textColor = activity.getProperTextColor()
    private val foregroundDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.rounded_white_border, activity.getProperPrimaryColor())
    private var startReorderDragListener: StartReorderDragListener
    private var isItemOrderChanged = false

    init {
        val touchHelper = ItemTouchHelper(MyItemMoveCallback(this))
        touchHelper.attachToRecyclerView(recyclerView)

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                isItemOrderChanged = false
                touchHelper.startDrag(viewHolder)
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        @SuppressLint("ClickableViewAccessibility")
        fun bindView(queueData: QueueData): View {
            ItemSelectQueueBinding.bind(itemView).apply {
                name.text = queueData.name
                name.setTextColor(textColor)
                name.setOnClickListener {
                    if (playQueue) {
                        prepareQueue(queueData.queueId)
                    }
                    itemClick(queueData.queueId)
                }

                selectQueueDragHandle.applyColorFilter(textColor)
                selectQueueDragHandle.beVisibleIf(queueData.queueId != 0L)
                selectQueueDragHandle.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        startReorderDragListener.requestDrag(this@ViewHolder)
                    }
                    false
                }

                more.applyColorFilter(textColor)
                more.setOnClickListener {
                    showMoreMenu(queueData, this@ViewHolder)
                }

                root.foreground = if (currentQueueId == queueData.queueId) foregroundDrawable else null
            }
            return itemView
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectQueueBinding.inflate(activity.layoutInflater, parent, false)
        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(items[position])
    }

    override fun getItemCount() = items.size

    private fun showMoreMenu(queueData: QueueData, holder: ViewHolder) {
        val id = queueData.queueId
        val title = queueData.name
        val itemsMenu = if (id == 0L) {
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
            .setItems(itemsMenu) { _, w ->
                if (id == 0L) {
                    createShortcut(id, title)
                } else {
                    when (w) {
                        0 -> changeQueueName(queueData)
                        1 -> createShortcut(id, title)
                        2 -> deleteQueue(queueData)
                    }
                }
            }
            .show()
    }

    private fun prepareQueue(queueId: Long) {
        val simpleControllerActivity = (activity as? SimpleControllerActivity) ?: return
        simpleControllerActivity.withPlayer {
            val startPlay = isPlaying
            ensureBackgroundThread {
                simpleControllerActivity.onSelectTracklist(TRACKLIST_QUEUE, queueId, "", startPlay = startPlay)
            }
        }
    }

    private fun changeQueueName(queueData: QueueData) {
        activity.showQueueNameDialog(queueData.name) { newName ->
            queueData.name = newName
            notifyItemChanged(items.indexOf(queueData))
            saveQueues()
        }
    }

    private fun createShortcut(id: Long, title: String) {
        activity.createTracklistShortcut(title, "q:$id", drawableId = R.drawable.ic_shortcut_queue)
    }

    private fun deleteQueue(queueData: QueueData) {
        val id = queueData.queueId
        activity.getAlertDialogBuilder()
            .setTitle(queueData.name)
            .setMessage(com.simplemobiletools.commons.R.string.are_you_sure_delete)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                val index = items.indexOf(queueData)
                if (index != -1) {
                    items.removeAt(index)
                    notifyItemRemoved(index)
                    saveQueues()

                    executeBackgroundThread {
                        activity.queueDAO.deleteAllItems(id)
                        if (activity.config.queueId == id) {
                            prepareQueue(0)
                        }
                        if (activity.config.tabQueueId == id) {
                            activity.config.tabQueueId = 0
                            EventBus.getDefault().post(Events.QueueItemsChanged.setQueueId(id))
                        }
                    }
                }
            }
            .show()
    }

    private fun saveQueues() {
        val queuesToSave = items.filter { it.queueId != 0L }
        activity.config.queueListJson = queuesToSave.toJson()
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                items.swap(i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                items.swap(i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        isItemOrderChanged = true
    }

    override fun onRowSelected(myViewHolder: RecyclerView.ViewHolder?) = Unit

    override fun onRowClear(myViewHolder: RecyclerView.ViewHolder?) {
        if (isItemOrderChanged) saveQueues()
    }
}
