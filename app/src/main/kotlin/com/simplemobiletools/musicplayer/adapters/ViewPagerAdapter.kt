package com.simplemobiletools.musicplayer.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.databinding.*
import com.simplemobiletools.musicplayer.extensions.getVisibleTabs
import com.simplemobiletools.musicplayer.fragments.*
import com.simplemobiletools.musicplayer.helpers.*

class ViewPagerAdapter(val activity: SimpleActivity) : RecyclerView.Adapter<ViewPagerAdapter.ViewHolder>() {
    private val fragments = mutableMapOf<Int, MyViewPagerFragment>()

    inner class ViewHolder(val view: MyViewPagerFragment) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val tab = activity.getVisibleTabs()[viewType]
        val layoutInflater = activity.layoutInflater
        val view = when (tab) {
            TAB_PLAYLISTS -> FragmentPlaylistsBinding.inflate(layoutInflater, parent, false).root
            TAB_FOLDERS -> FragmentFoldersBinding.inflate(layoutInflater, parent, false).root
            TAB_ARTISTS -> FragmentArtistsBinding.inflate(layoutInflater, parent, false).root
            TAB_ALBUMS -> FragmentAlbumsBinding.inflate(layoutInflater, parent, false).root
            TAB_TRACKS -> FragmentTracksBinding.inflate(layoutInflater, parent, false).root
            TAB_GENRES -> FragmentGenresBinding.inflate(layoutInflater, parent, false).root
            else -> throw IllegalArgumentException("Unknown tab: $tab")
        }
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fragment = holder.view
        fragments[position] = fragment
        fragment.setupFragment(activity)
        fragment.setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor())
    }

    override fun getItemCount() = activity.getVisibleTabs().size

    override fun getItemViewType(position: Int) = position

    fun getAllFragments() = fragments.values.toList()

    fun getFragment(position: Int) = fragments[position]

    fun getPlaylistsFragment() = fragments.values.find { it is PlaylistsFragment }

    fun getFoldersFragment() = fragments.values.find { it is FoldersFragment }

    fun getArtistsFragment() = fragments.values.find { it is ArtistsFragment }

    fun getAlbumsFragment() = fragments.values.find { it is AlbumsFragment }

    fun getTracksFragment() = fragments.values.find { it is TracksFragment }
}
