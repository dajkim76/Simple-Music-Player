package com.simplemobiletools.musicplayer.models

class Events {
    class SleepTimerChanged(val seconds: Int)
    class PlaylistsUpdated
    class FoldersUpdated
    class ArtistsUpdated
    class AlbumsUpdated
    class RefreshFragments
    class RefreshTracks
    class QueueItemsChanged
}
