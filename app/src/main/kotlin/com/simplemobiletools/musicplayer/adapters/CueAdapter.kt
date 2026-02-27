package com.simplemobiletools.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.adjustAlpha
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import com.simplemobiletools.commons.views.MyTextView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.databinding.ItemCueBinding
import com.simplemobiletools.musicplayer.models.Cue

class CueAdapter(
    val activity: SimpleActivity,
    var cues: List<Cue>,
    val itemClick: (Cue) -> Unit,
    val itemUpdated: (List<Cue>) -> Unit
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

    fun updateCurrentPosition(positionSeconds: Int) {
        val activeCueIndex = cues.indexOfLast { it.timestamp <= positionSeconds }
        if (activeCueIndex != currentTimestamp) {
            val oldTimestamp = currentTimestamp
            currentTimestamp = activeCueIndex
            if (oldTimestamp != -1) notifyItemChanged(oldTimestamp)
            if (currentTimestamp != -1) notifyItemChanged(currentTimestamp)
        }
    }

    inner class ViewHolder(private val binding: ItemCueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cue: Cue) {
            val isActive = adapterPosition == currentTimestamp
            var textColor = if (isActive) primaryColor else properTextColor
            
            if (!cue.enabled) {
                textColor = textColor.adjustAlpha(MEDIUM_ALPHA)
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
