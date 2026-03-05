package com.simplemobiletools.musicplayer.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.databinding.AdjustCueTimestampBinding
import com.simplemobiletools.musicplayer.databinding.ItemCueBinding
import com.simplemobiletools.musicplayer.models.Cue

class CueAdapter(
    private val activity: SimpleActivity,
    private val itemClick: (Cue) -> Unit,
    private val itemUpdated: (mediaStoreId: Long, updatedCues: List<Cue>) -> Unit
) : RecyclerView.Adapter<CueAdapter.ViewHolder>() {

    var cues: List<Cue> = emptyList()
    private var mediaStoreId: Long = 0
    var isNoCueTitle = false
    private var currentCueIndex: Int = -1
    private var totalDuration: Int = 0
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

    fun refreshList(cues: List<Cue>, mediaStoreId: Long, totalDuration: Int) {
        this.cues = cues
        this.mediaStoreId = mediaStoreId
        this.currentCueIndex = -1
        notifyDataSetChanged()
        if (cues.size == 1) {
            val cue = cues.first()
            isNoCueTitle = cue.title == "<NO_CUE>" && cue.timestamp == 0 && !cue.enabled
        } else {
            isNoCueTitle = false
        }
        this.totalDuration = totalDuration
        makeDuration()
    }

    private fun makeDuration() {
        cues.forEachIndexed { index, cue ->
            val nextCueTimeStamp = if (index < cues.size - 1) {
                val nextCue = cues[index + 1]
                nextCue.timestamp
            } else {
                totalDuration
            }
            cue.duration = nextCueTimeStamp - cue.timestamp
        }
    }

    fun onDestroy() {
        cues = emptyList() // clear singleton cache to avoid leaks
    }

    fun getCurrentTitle(mediaStoreId: Long): String? {
        if (cues.isEmpty() || isNoCueTitle || this.mediaStoreId != mediaStoreId) return null
        if (currentCueIndex >= 0) {
            val cue = cues.getOrNull(currentCueIndex)
            return cue?.title
        }
        return null
    }

    fun updateCurrentPosition(mediaStoreId: Long, positionSeconds: Int): Int {
        if (this.mediaStoreId != mediaStoreId) return -1
        val activeCueIndex = cues.indexOfLast { it.timestamp <= positionSeconds }
        if (activeCueIndex != currentCueIndex) {
            val oldTimestamp = currentCueIndex
            currentCueIndex = activeCueIndex
            if (oldTimestamp != -1) notifyItemChanged(oldTimestamp)
            if (currentCueIndex != -1) notifyItemChanged(currentCueIndex)
            return currentCueIndex
        }
        return -1
    }

    inner class ViewHolder(private val binding: ItemCueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(cue: Cue) {
            val isActive = adapterPosition == currentCueIndex
            var textColor = if (isActive) 0xff_ffa500.toInt() else Color.WHITE

            if (!cue.enabled) {
                textColor = if (isNoCueTitle) Color.WHITE else 0xff_777777.toInt()
            }

            binding.apply {
                cueTimestamp.text = cue.timestamp.getFormattedDuration()
                cueTitle.text = if (isNoCueTitle) activity.getString(R.string.no_cue) else cue.title

                cueTimestamp.isVisible = !isNoCueTitle
                cueTimestamp.setTextColor(if (cue.favorite && cue.enabled) Color.RED else textColor)
                cueTitle.setTextColor(textColor)
                cueDuration.setTextColor(textColor)
                cueDuration.text = cue.duration.getFormattedDuration()
                cueDuration.isVisible = cue.duration > 0 && !isNoCueTitle

                root.setOnClickListener {
                    if (!isNoCueTitle && cue.enabled) {
                        itemClick(cue)
                    }
                }

                root.setOnLongClickListener {
                    if (!isNoCueTitle) {
                        showPopupMenu(cue, adapterPosition)
                    }
                    true
                }
            }
        }

        private fun showPopupMenu(cue: Cue, position: Int) {
            val popup = PopupMenu(activity, binding.root)
            val skipLabel = if (cue.enabled) activity.getString(R.string.not_playing) else activity.getString(R.string.playable)
            popup.menu.add(0, 0, 0, skipLabel)
            popup.menu.add(0, 1, 0, activity.getString(R.string.favorites_toggle))
            popup.menu.add(0, 2, 0, activity.getString(R.string.adjust_timestamp))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> {
                        val newCues = cues.toMutableList()
                        newCues[position] = cue.copy(enabled = !cue.enabled, duration = cue.duration, favorite = cue.favorite)
                        cues = newCues
                        notifyItemChanged(position)
                        itemUpdated(mediaStoreId, newCues)
                    }

                    1 -> {
                        val newCues = cues.toMutableList()
                        newCues[position] = cue.copy(enabled = cue.enabled || !cue.favorite, duration = cue.duration, favorite = !cue.favorite)
                        cues = newCues
                        notifyItemChanged(position)
                        itemUpdated(mediaStoreId, newCues)
                    }

                    2 -> {
                        val cue = cues[position]
                        val binding = AdjustCueTimestampBinding.inflate(LayoutInflater.from(activity))
                        binding.title.setText(cue.title)
                        binding.buttons.children.forEach { it.setOnClickListener { binding.editText.setText((it as? TextView)?.text) } }
                        AlertDialog.Builder(activity)
                            .setTitle(R.string.adjust_timestamp)
                            .setView(binding.root)
                            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
                            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                                val seconds = binding.editText.text.toString().toIntOrNull() ?: 0
                                val newTitle = binding.title.text.toString().replace("\n", "").trim()
                                if (seconds == 0 && cue.title == newTitle) return@setPositiveButton
                                val newCues = cues.toMutableList()
                                newCues[position] =
                                    cue.copy(
                                        timestamp = (cue.timestamp + seconds).takeIf { it >= 0 } ?: 0,
                                        title = newTitle,
                                        enabled = cue.enabled || cue.favorite,
                                        duration = cue.duration,
                                        favorite = cue.favorite
                                    )
                                newCues.sortedBy { it.timestamp }
                                cues = newCues
                                makeDuration()
                                notifyDataSetChanged() // after sortedBy
                                itemUpdated(mediaStoreId, newCues) // save and reload
                            }
                            .show()
                    }
                }
                true
            }
            popup.show()
        }
    }
}
