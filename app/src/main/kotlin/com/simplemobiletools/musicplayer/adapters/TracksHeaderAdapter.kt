package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.databinding.ItemAlbumHeaderBinding
import com.simplemobiletools.musicplayer.databinding.ItemTrackBinding
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getAlbumCoverArt
import com.simplemobiletools.musicplayer.extensions.getTrackFileArt
import com.simplemobiletools.musicplayer.models.AlbumHeader
import com.simplemobiletools.musicplayer.models.ListItem
import com.simplemobiletools.musicplayer.models.Track

class TracksHeaderAdapter(activity: SimpleActivity, items: ArrayList<ListItem>, var lastMediaId: Long, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<ListItem>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private val ITEM_HEADER = 0
    private val ITEM_TRACK = 1

    override val cornerRadius = resources.getDimension(com.simplemobiletools.commons.R.dimen.rounded_corner_radius_big).toInt()
    private val foregroundDrawable = context.resources.getColoredDrawableWithColor(R.drawable.rounded_white_border, properPrimaryColor)

    override fun getActionMenuId() = R.menu.cab_tracks_header

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            ITEM_HEADER -> ItemAlbumHeaderBinding.inflate(layoutInflater, parent, false)
            else -> ItemTrackBinding.inflate(layoutInflater, parent, false)
        }

        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        val allowClicks = item !is AlbumHeader
        holder.bindView(item, allowClicks, allowClicks) { itemView, _ ->
            when (item) {
                is AlbumHeader -> setupHeader(itemView, item)
                else -> setupTrack(itemView, item as Track)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AlbumHeader -> ITEM_HEADER
            else -> ITEM_TRACK
        }
    }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = shouldShowRename()
            findItem(R.id.cab_play_next).isVisible = shouldShowPlayNext()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_properties -> showProperties()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_share -> shareFiles()
            R.id.cab_rename -> displayEditDialog()
            R.id.cab_select_all -> selectAll()
            R.id.cab_play_next -> playNextInQueue()
        }
    }

    override fun onActionModeCreated() = notifyDataChanged()

    override fun onActionModeDestroyed() = notifyDataChanged()

    override fun getSelectableItemCount() = items.size - 1

    override fun getIsItemSelectable(position: Int) = position != 0

    private fun askConfirmDelete() {
        ConfirmationDialog(context) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getSelectedTracks()
                selectedTracks.forEach { track ->
                    val position = items.indexOfFirst { it is Track && it.mediaStoreId == track.mediaStoreId }
                    if (position != -1) {
                        positions.add(position)
                    }
                }

                context.deleteTracks(selectedTracks) {
                    context.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            items.removeAt(it)
                        }

                        // finish activity if all tracks are deleted
                        if (items.none { it is Track }) {
                            context.finish()
                        }
                    }
                }
            }
        }
    }

    fun updateLastMedia(lastMediaId: Long) {
        if (items.filterIsInstance<Track>().size <= 1) return
        val beforeIndex = items.indexOfFirst { it is Track && it.mediaStoreId == this.lastMediaId }
        this.lastMediaId = lastMediaId
        val afterIndex = items.indexOfFirst { it is Track && it.mediaStoreId == this.lastMediaId }
        if (beforeIndex >= 0) notifyItemChanged(beforeIndex)
        if (afterIndex >= 0) notifyItemChanged(afterIndex)
    }

    @SuppressLint("SetTextI18n")
    private fun setupTrack(view: View, track: Track) {
        ItemTrackBinding.bind(view).apply {
            root.setupViewBackground(context)
            trackFrame.isSelected = selectedKeys.contains(track.hashCode())
            trackTitle.text = track.title
            trackInfo.beGone()

            arrayOf(trackId, trackTitle, trackDuration).forEach {
                it.setTextColor(textColor)
            }

            trackDuration.text = track.duration.getFormattedDuration()
            if (track.discNumber != null) {
                trackId.text = "${track.discNumber}.${track.trackId.toString().padStart(2, '0')}"
                trackId.beVisible()
            } else {
                if (track.trackId > 0) {
                    trackId.text = track.trackId.toString()
                    trackId.beVisible()
                } else {
                    trackId.beGone()
                }
            }
            context.getTrackFileArt(track) { coverArt ->
                if (activity.isFinishing || activity.isDestroyed) return@getTrackFileArt
                loadImage(trackImage, coverArt, placeholder)
            }
            if (actMode == null && lastMediaId == track.mediaStoreId) {
                view.foreground = foregroundDrawable
            } else {
                view.foreground = null
            }
        }
    }

    private fun setupHeader(view: View, header: AlbumHeader) {
        ItemAlbumHeaderBinding.bind(view).apply {
            albumTitle.text = header.title
            albumArtist.text = header.artist

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, header.trackCnt, header.trackCnt)
            var year = ""
            if (header.year != 0) {
                year = "${header.year} • "
            }

            @SuppressLint("SetTextI18n")
            albumMeta.text = "$year$tracks • ${header.duration.getFormattedDuration(true)}"

            arrayOf(albumTitle, albumArtist, albumMeta).forEach {
                it.setTextColor(textColor)
            }

            ensureBackgroundThread {
                val album = context.audioHelper.getAlbum(header.id)
                if (album != null) {
                    context.getAlbumCoverArt(album) { coverArt ->
                        if (activity.isFinishing || activity.isDestroyed) return@getAlbumCoverArt
                        loadImage(albumImage, coverArt, placeholderBig)
                    }
                } else {
                    context.runOnUiThread {
                        albumImage.setImageDrawable(placeholderBig)
                    }
                }
            }
        }
    }

    override fun onChange(position: Int): CharSequence {
        return when (val listItem = items.getOrNull(position)) {
            is Track -> listItem.getBubbleText(context.config.trackSorting)
            is AlbumHeader -> listItem.title
            else -> ""
        }
    }

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(context, selectedTrack) { track ->
                val trackIndex = items.indexOfFirst { (it as? Track)?.mediaStoreId == track.mediaStoreId }
                if (trackIndex != -1) {
                    items[trackIndex] = track
                    notifyItemChanged(trackIndex)
                    finishActMode()
                }

                context.refreshQueueAndTracks(track)
            }
        }
    }
}
