package com.simplemobiletools.musicplayer.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.databinding.ItemCueBinding
import com.simplemobiletools.musicplayer.models.Cue

class CueAdapter(
    private val activity: SimpleActivity,
    var cues: List<Cue>,
    private var mediaStoreId: Long,
    private val itemClick: (Cue) -> Unit,
    private val itemUpdated: (List<Cue>) -> Unit
) : RecyclerView.Adapter<CueAdapter.ViewHolder>() {

    private var currentTimestamp: Int = -1
    private val properTextColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cue = cues[position]
        holder.bind(cue)
    }

    override fun getItemCount() = cues.size

    fun refreshList(cues: List<Cue>, mediaStoreId: Long) {
        this.cues = cues
        this.mediaStoreId = mediaStoreId
        this.currentTimestamp = -1
        notifyDataSetChanged()
    }

    fun getCurrentTitle(mediaStoreId: Long): String? {
        if (cues.isEmpty() && this.mediaStoreId != mediaStoreId) return null
        if (currentTimestamp >= 0) {
            val cue = cues.getOrNull(currentTimestamp)
            return cue?.title
        }
        return null
    }

    fun updateCurrentPosition(positionSeconds: Int): Int {
        val activeCueIndex = cues.indexOfLast { it.timestamp <= positionSeconds }
        if (activeCueIndex != currentTimestamp) {
            val oldTimestamp = currentTimestamp
            currentTimestamp = activeCueIndex
            if (oldTimestamp != -1) notifyItemChanged(oldTimestamp)
            if (currentTimestamp != -1) notifyItemChanged(currentTimestamp)
            return currentTimestamp
        }
        return -1
    }

    inner class ViewHolder(private val binding: ItemCueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cue: Cue) {
            val isActive = adapterPosition == currentTimestamp
            var textColor = if (isActive) 0xff_ffa500.toInt() else Color.WHITE
            
            if (!cue.enabled) {
                textColor = 0xff_777777.toInt()
            }

            binding.apply {
                cueTimestamp.text = cue.timestamp.getFormattedDuration()
                cueTitle.text = cue.title
                
                cueTimestamp.setTextColor(textColor)
                cueTitle.setTextColor(textColor)
                
                root.setOnClickListener { 
                    if (cue.enabled) {
                        itemClick(cue)
                    }
                }
                
                root.setOnLongClickListener {
                    showPopupMenu(cue, adapterPosition)
                    true
                }
            }
        }

        private fun showPopupMenu(cue: Cue, position: Int) {
            val popup = PopupMenu(activity, binding.root)
            val skipLabel = if (cue.enabled) "Skip" else "Play"
            popup.menu.add(0, 0, 0, skipLabel)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        val newCues = cues.toMutableList()
                        newCues[position] = cue.copy(enabled = !cue.enabled)
                        cues = newCues
                        notifyItemChanged(position)
                        itemUpdated(cues)
                    }
                }
                true
            }
            popup.show()
        }
    }
}
