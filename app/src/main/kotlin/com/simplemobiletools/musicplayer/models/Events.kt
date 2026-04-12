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
        private var needToPost = false
        private var skipOnce = false
        var queueId = 0L
            private set
        var fromQueueActivity = false

        fun setSkipOnce() {
            skipOnce = true
        }

        fun setNeedToPostFromQueueActivity() {
            fromQueueActivity = true
            setNeedToPost()
        }

        fun setNeedToPost() {
            if (skipOnce) {
                skipOnce = false
                return
            }
            needToPost = true
        }

        fun getNeedToPost(): Boolean {
            val r = needToPost
            needToPost = false
            return r
        }

        fun setQueueId(queueId: Long) = apply {
            this.queueId = queueId
        }
    }
}
