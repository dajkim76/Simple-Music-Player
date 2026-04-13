package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.models.QueueItem
import com.simplemobiletools.musicplayer.models.Track

@Dao
interface QueueItemsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(queueItems: List<QueueItem>)

    @Query("SELECT * FROM multi_queue_items WHERE queue_id = :queueId ORDER BY track_order")
    fun getAll(queueId: Long): List<QueueItem>

    @Update
    fun update(queueItems: List<QueueItem>)

    @Query("DELETE FROM multi_queue_items WHERE queue_id = :queueId AND track_id = :trackId")
    fun removeQueueItem(queueId: Long, trackId: Long)

    @Query("DELETE FROM multi_queue_items WHERE track_id = :trackId")
    fun deleteTrack(trackId: Long)

    @Transaction
    fun deleteTrackList(trackList: List<Track>) {
        trackList.forEach {
            deleteTrack(it.mediaStoreId)
        }
    }

    @Transaction
    fun updateOrder(queueId: Long, items: List<Track>) {
        val queueItems = getAll(queueId)
        val updateItems = items.mapIndexedNotNull { index, track ->
            val item = queueItems.find { it.trackId == track.mediaStoreId }
            if (item != null && item.trackOrder != index) {
                item.trackOrder = index
                item
            } else {
                null
            }
        }
        update(updateItems)
    }

    @Query("UPDATE multi_queue_items SET is_current = 0 WHERE queue_id = :queueId AND is_current = 1")
    fun resetCurrent(queueId: Long)

    @Query("SELECT * FROM multi_queue_items WHERE queue_id = :queueId AND is_current = 1")
    fun getCurrent(queueId: Long): QueueItem?

    @Query("UPDATE multi_queue_items SET is_current = 1 WHERE queue_id = :queueId AND track_id = :trackId")
    fun saveCurrentTrack(queueId: Long, trackId: Long)

    @Query("UPDATE multi_queue_items SET is_current = 1, last_position = :lastPosition WHERE queue_id = :queueId AND track_id = :trackId")
    fun saveCurrentTrackProgress(queueId: Long, trackId: Long, lastPosition: Long)

    @Query("DELETE FROM multi_queue_items WHERE queue_id = :queueId")
    fun deleteAllItems(queueId: Long)

    @Query("SELECT max(track_order) FROM multi_queue_items WHERE queue_id = :queueId")
    fun getMaxOrder(queueId: Long): Int
}
