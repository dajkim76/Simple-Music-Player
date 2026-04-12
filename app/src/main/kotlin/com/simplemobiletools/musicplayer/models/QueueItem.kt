package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Users created multi queue data, saved List<QueueData> to mmkv json
@Serializable
data class QueueData(
    @SerialName("n") var name: String,
    @SerialName("i") val queueId: Long
)

fun getQueueDataListFromJson(json: String): List<QueueData> {
    return try {
        Json.decodeFromString<List<QueueData>>(json)
    } catch (_: Exception) {
        emptyList<QueueData>()
    }
}

fun List<QueueData>.toJson(): String = Json.encodeToString(this)

@Entity(tableName = "multi_queue_items", indices = [Index(value = ["queue_id", "track_id"], unique = true)])
data class QueueItem(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "queue_id") var queueId: Long,
    @ColumnInfo(name = "track_id") var trackId: Long,   // media store id
    @ColumnInfo(name = "track_order") var trackOrder: Int,
    @ColumnInfo(name = "is_current") var isCurrent: Boolean,
    @ColumnInfo(name = "last_position") var lastPosition: Long // Changed to Milliseconds
)
