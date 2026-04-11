package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Users created multi queue data, saved List<QueueData> to mmkv json
@Serializable
data class QueueData(
    @SerialName("title") val title: String,
    @SerialName("queue_id") val queueId: Long
)

@Entity(tableName = "multi_queue_items", indices = [Index(value = ["queue_id", "track_id"], unique = true)])
data class QueueItem(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "queue_id") var queueId: Long,
    @ColumnInfo(name = "track_id") var trackId: Long,
    @ColumnInfo(name = "track_order") var trackOrder: Int,
    @ColumnInfo(name = "is_current") var isCurrent: Boolean,
    @ColumnInfo(name = "last_position") var lastPosition: Long // Changed to Milliseconds
)
