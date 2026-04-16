package com.simplemobiletools.musicplayer.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ItemSelectTracklistBinding
import com.simplemobiletools.musicplayer.databinding.ItemSelectTracklistTitleBinding
import com.simplemobiletools.musicplayer.extensions.config

class SelectTracklistAdapter(
    val activity: BaseSimpleActivity,
    val items: List<TracklistItem>,
    val itemClick: (TracklistItem.TracklistItemData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()
    private val foregroundDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.rounded_white_border, primaryColor)
    private val lastQueueSource = activity.config.lastQueueSource

    companion object {
        private const val TYPE_TITLE = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TracklistItem.TracklistItemTitle -> TYPE_TITLE
            is TracklistItem.TracklistItemData -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_TITLE) {
            val binding = ItemSelectTracklistTitleBinding.inflate(activity.layoutInflater, parent, false)
            TitleViewHolder(binding)
        } else {
            val binding = ItemSelectTracklistBinding.inflate(activity.layoutInflater, parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is TitleViewHolder && item is TracklistItem.TracklistItemTitle) {
            holder.binding.selectTracklistTitle.apply {
                text = activity.getString(item.titleRes)
                setTextColor(primaryColor)
            }
        } else if (holder is ItemViewHolder && item is TracklistItem.TracklistItemData) {
            holder.binding.selectTracklistItemRadioButton.apply {
                text = item.title
                setTextColor(textColor)
                setOnClickListener {
                    itemClick(item)
                }
            }

            val suffix = item.data.ifEmpty { item.id.toString() }
            holder.binding.root.foreground = if ("${item.prefix}$suffix" == lastQueueSource) foregroundDrawable else null
        }
    }

    override fun getItemCount() = items.size

    inner class TitleViewHolder(val binding: ItemSelectTracklistTitleBinding) : RecyclerView.ViewHolder(binding.root)
    inner class ItemViewHolder(val binding: ItemSelectTracklistBinding) : RecyclerView.ViewHolder(binding.root)
}

sealed class TracklistItem {
    data class TracklistItemTitle(val titleRes: Int) : TracklistItem()
    data class TracklistItemData(
        val prefix: String,
        val title: String,
        val type: Int,
        val id: Long,
        val data: String
    ) : TracklistItem()
}
