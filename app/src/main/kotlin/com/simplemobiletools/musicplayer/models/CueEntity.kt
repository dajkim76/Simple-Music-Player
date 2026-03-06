package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_cues")
data class CueEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_stable_id") val fileStableId: Long,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "file_length") var fileLength: Long,
    @ColumnInfo(name = "file_last_modified") var fileLastModified: Long,
    @ColumnInfo(name = "cues_json") val cuesJson: String
)
