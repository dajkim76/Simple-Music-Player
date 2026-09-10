package com.simplemobiletools.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.models.CueEntity

@Dao
interface CueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(cue: CueEntity)

    @Query("UPDATE track_cues SET cues_json = :cuesJson WHERE file_stable_id = :fileStableId")
    fun updateCue(fileStableId: Long, cuesJson: String): Int

    @Query("SELECT * FROM track_cues WHERE file_stable_id = :fileStableId")
    fun getCue(fileStableId: Long): CueEntity?

    @Query("SELECT * FROM track_cues WHERE path = :path LIMIT 1")
    fun getCueByPath(path: String): CueEntity?

    @Query("DELETE FROM track_cues WHERE file_stable_id = :fileStableId")
    fun deleteCue(fileStableId: Long)

    @Query("UPDATE track_cues SET file_stable_id = :newFileStableId, path = :newPath, file_length = :newLength, file_last_modified = :newLastModified WHERE file_stable_id = :oldFileStableId")
    fun updateCueFileStableId(oldFileStableId: Long, newFileStableId: Long, newPath: String, newLength: Long, newLastModified: Long): Int
}
