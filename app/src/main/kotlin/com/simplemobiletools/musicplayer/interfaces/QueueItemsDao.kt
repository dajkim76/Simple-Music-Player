package com.simplemobiletools.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.models.QueueItem

@Dao
interface QueueItemsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(queueItems: List<QueueItem>)

    @Query("SELECT * FROM multi_queue_items WHERE queue_id = :queueId ORDER BY track_order")
    fun getAll(queueId: Long): List<QueueItem>

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
}
