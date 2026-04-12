package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.ItemMoveCallback
import com.simplemobiletools.commons.interfaces.ItemTouchHelperContract
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.databinding.ItemTrackQueueBinding
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getTrackFileArt
import com.simplemobiletools.musicplayer.extensions.queueDAO
import com.simplemobiletools.musicplayer.extensions.swap
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.objects.executeBackgroundThread

class MultiQueueAdapter(
    activity: SimpleActivity,
    items: ArrayList<Track>,
    val queueId: Long,
    var lastMediaId: Long,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : BaseMusicAdapter<Track>(items, activity, recyclerView, itemClick), ItemTouchHelperContract, RecyclerViewFastScroller.OnPopupTextUpdate {

    private var startReorderDragListener: StartReorderDragListener
    private val foregroundDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.rounded_white_border, activity.getProperPrimaryColor())

    init {
        setupDragListener(true)

        val touchHelper = ItemTouchHelper(ItemMoveCallback(this))
        touchHelper.attachToRecyclerView(recyclerView)

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                touchHelper.startDrag(viewHolder)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_queue

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackQueueBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bindView(item, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, item, holder)
        }
        bindViewHolder(holder)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_remove_from_queue -> removeFromQueue()
            R.id.cab_delete_file -> deleteTracks()
            R.id.cab_share -> shareFiles()
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_select_all -> selectAll()
            R.id.cab_add_to_queue -> addToQueue()
        }
    }

    private fun removeFromQueue() {
        val selectedTracks = getSelectedTracks()
        val positions = ArrayList<Int>()
        selectedTracks.forEach { track ->
            val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
            if (position != -1) {
                positions.add(position)
            }
        }

        if (activity.config.queueId == queueId) {
            context.removeQueueItems(selectedTracks) {
                refreshTracksList(positions)
            }
        } else {
            executeBackgroundThread {
                selectedTracks.forEach {
                    activity.queueDAO.removeQueueItem(queueId, it.mediaStoreId)
                }
                refreshTracksList(positions)
            }
        }
    }

    private fun refreshTracksList(positions: ArrayList<Int>) {
        context.runOnUiThread {
            positions.sortDescending()
            positions.forEach {
                items.removeAt(it)
            }

            removeSelectedItems(positions)
        }
    }

    private fun deleteTracks() {
        ConfirmationDialog(
            context,
            "",
            R.string.delete_song_warning,
            com.simplemobiletools.commons.R.string.ok,
            com.simplemobiletools.commons.R.string.cancel
        ) {
            val selectedTracks = getSelectedTracks()
            val positions = ArrayList<Int>()
            selectedTracks.forEach { track ->
                val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
                if (position != -1) {
                    positions.add(position)
                }
            }

            // deleteTrakcs에서 removeQueueItem도 호출한다.
            context.deleteTracks(selectedTracks) {
                ensureBackgroundThread {
                    selectedTracks.forEach {
                        activity.queueDAO.removeQueueItem(queueId, it.mediaStoreId)
                    }
                    refreshTracksList(positions)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(view: View, track: Track, holder: ViewHolder) {
        ItemTrackQueueBinding.bind(view).apply {
            root.setupViewBackground(context)
            trackQueueFrame.isSelected = selectedKeys.contains(track.hashCode())
            trackQueueTitle.text = if (textToHighlight.isEmpty()) track.title else track.title.highlightTextPart(textToHighlight, properPrimaryColor)
            trackQueueTitle.setTextColor(textColor)
            trackQueueDuration.setTextColor(textColor)
            trackQueueDuration.text = track.duration.getFormattedDuration()
            trackQueueDragHandle.beVisibleIf(textToHighlight.isEmpty())
            trackQueueDragHandle.applyColorFilter(textColor)
            trackQueueDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }

            if (track.mediaStoreId == lastMediaId) {
                this.root.foreground = foregroundDrawable
            } else {
                this.root.foreground = null
            }

            context.getTrackFileArt(track) { coverArt ->
                if (activity.isFinishing || activity.isDestroyed) return@getTrackFileArt
                loadImage(trackQueueImage, coverArt, placeholderBig)
            }
        }
    }

    fun updateLastMediaId(lastMediaId: Long) {
        if (this.lastMediaId == lastMediaId) return
        if (items.size <= 1) return
        val beforeIndex = items.indexOfFirst { it.mediaStoreId == this.lastMediaId }
        this.lastMediaId = lastMediaId
        val afterIndex = items.indexOfFirst { it.mediaStoreId == this.lastMediaId }
        if (beforeIndex >= 0) notifyItemChanged(beforeIndex)
        if (afterIndex >= 0) notifyItemChanged(afterIndex)
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        items.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        ensureBackgroundThread {
            activity.queueDAO.updateOrder(queueId, items)
        }
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {}

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onChange(position: Int) = items.getOrNull(position)?.title ?: ""
}
