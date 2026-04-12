package com.simplemobiletools.musicplayer.models

class Events {
    class SleepTimerChanged(val seconds: Int)
    class PlaylistsUpdated
    class FoldersUpdated
    class ArtistsUpdated
    class AlbumsUpdated
    class RefreshFragments
    class RefreshTracks
    object QueueItemsChanged {
        private var isPost = false
        private var skipOnce = false

        fun setSkipOnce() {
            skipOnce = true
        }

        fun setIsPost() {
            if (skipOnce) {
                skipOnce = false
                return
            }
            isPost = true
        }

        fun getIsPost(): Boolean {
            val r = isPost
            isPost = false
            return r
        }
    }
}
