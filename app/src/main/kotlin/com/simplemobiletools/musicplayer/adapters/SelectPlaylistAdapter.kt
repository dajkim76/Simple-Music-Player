package com.simplemobiletools.musicplayer.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getProperBackgroundColor
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.musicplayer.databinding.ItemSelectPlaylistBinding
import com.simplemobiletools.musicplayer.models.Playlist

class SelectPlaylistAdapter(
    val activity: BaseSimpleActivity,
    val playlists: MutableList<Playlist>
) : RecyclerView.Adapter<SelectPlaylistAdapter.ViewHolder>() {

    private val selectedIds = HashSet<Int>()
    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()

    inner class ViewHolder(val binding: ItemSelectPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectPlaylistBinding.inflate(activity.layoutInflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.binding.selectPlaylistItemCheckbox.apply {
            text = playlist.title
            setTextColor(textColor)
            setColors(textColor, primaryColor, activity.getProperBackgroundColor())
            isChecked = selectedIds.contains(playlist.id)
            setOnClickListener {
                if (selectedIds.contains(playlist.id)) {
                    selectedIds.remove(playlist.id)
                } else {
                    selectedIds.add(playlist.id)
                }
                isChecked = selectedIds.contains(playlist.id)
            }
        }
    }

    override fun getItemCount() = playlists.size

    fun addPlaylist(playlist: Playlist) {
        playlists.add(playlist)
        selectedIds.add(playlist.id)
        notifyItemInserted(playlists.size - 1)
    }

    fun getSelectedPlaylistIds() = selectedIds.toList()
}
