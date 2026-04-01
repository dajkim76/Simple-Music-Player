package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ItemPlaylistBinding
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.ALL_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.FAVORITE_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.SMART_PLAYLIST_ID_MAX
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import org.greenrobot.eventbus.EventBus

class PlaylistsAdapter(
    activity: BaseSimpleActivity,
    items: ArrayList<Playlist>,
    recyclerView: MyRecyclerView,
    private val toggleFavorite: (selectedAlbums: List<Playlist>) -> Unit,
    itemClick: (Any) -> Unit
) : BaseMusicAdapter<Playlist>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_playlists

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = items.getOrNull(position) ?: return
        holder.bindView(playlist, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, playlist)
        }
        bindViewHolder(holder)
    }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_rename -> showRenameDialog()
            R.id.cab_select_all -> selectAll()
            R.id.cab_favorites_toggle -> toggleFavorite.invoke(getSelectedItems())
        }
    }

    private fun askConfirmDelete() {
        val playlists = getSelectedItems().filter { it.id > SMART_PLAYLIST_ID_MAX }.toMutableList() as ArrayList<Playlist>
        if (playlists.isEmpty()) {
            context.toast(com.simplemobiletools.commons.R.string.no_items_found)
            return
        }
        RemovePlaylistDialog(context) { deleteFiles ->
            val ids = playlists.map { it.id } as ArrayList<Int>
            if (deleteFiles) {
                ensureBackgroundThread {
                    val tracksToDelete = ids.flatMap { context.audioHelper.getPlaylistTracks(it) }
                    context.deleteTracks(tracksToDelete) {
                        removePlaylists(playlists)
                    }
                }
            } else {
                removePlaylists(playlists)
            }
        }
    }

    private fun removePlaylists(playlistsToDelete: ArrayList<Playlist>) {
        val positions = playlistsToDelete.mapNotNull { playlist ->
            items.indexOfFirstOrNull { it.id == playlist.id }
        } as ArrayList<Int>

        ensureBackgroundThread {
            context.audioHelper.deletePlaylists(playlistsToDelete)
            context.runOnUiThread {
                items.removeAll(playlistsToDelete.toSet())
                removeSelectedItems(positions)
            }

            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun showRenameDialog() {
        NewPlaylistDialog(context, items[getItemKeyPosition(selectedKeys.first())]) {
            context.runOnUiThread {
                finishActMode()
            }
            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun setupView(view: View, playlist: Playlist) {
        ItemPlaylistBinding.bind(view).apply {
            root.setupViewBackground(context)
            playlistFrame.isSelected = selectedKeys.contains(playlist.hashCode())
            playlistTitle.text = if (textToHighlight.isEmpty()) playlist.title else playlist.title.highlightTextPart(textToHighlight, properPrimaryColor)
            playlistTitle.setTextColor(textColor)

            if (playlist.id == ALL_TRACKS_PLAYLIST_ID || playlist.id == FAVORITE_TRACKS_PLAYLIST_ID || playlist.id > SMART_PLAYLIST_ID_MAX) {
                val tracks = resources.getQuantityString(R.plurals.tracks_plural, playlist.trackCount, playlist.trackCount)
                playlistTracks.text = tracks
            } else {
                playlistTracks.text = resources.getString(R.string.auto)
            }
            playlistTracks.setTextColor(textColor)
            favorite.applyColorFilter(properPrimaryColor)
            favorite.isVisible = playlist.id > SMART_PLAYLIST_ID_MAX && playlist.favoriteTime > 0
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(context.config.playlistSorting) ?: ""
}
