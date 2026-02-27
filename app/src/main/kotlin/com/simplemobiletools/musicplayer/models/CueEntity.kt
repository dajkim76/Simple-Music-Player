package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_cues")
data class CueEntity(
    @PrimaryKey @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "cues_json") val cuesJson: String
)
