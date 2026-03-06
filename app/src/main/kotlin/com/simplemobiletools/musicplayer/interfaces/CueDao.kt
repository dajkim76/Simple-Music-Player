package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.models.CueEntity

@Dao
interface CueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(cue: CueEntity)

    @Update
    fun update(cue: CueEntity): Int

    @Query("SELECT * FROM track_cues WHERE file_stable_id = :fileStableId")
    fun getCue(fileStableId: Long): CueEntity?

    @Query("DELETE FROM track_cues WHERE file_stable_id = :fileStableId")
    fun deleteCue(fileStableId: Long)
}
