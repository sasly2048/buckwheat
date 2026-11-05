package com.danilkinkin.buckwheat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.danilkinkin.buckwheat.data.entities.AutoCaptureFingerprint

@Dao
interface AutoCaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: AutoCaptureFingerprint)

    @Query("SELECT * FROM auto_capture_fingerprints WHERE postTime >= :threshold")
    fun recent(threshold: Long): List<AutoCaptureFingerprint>

    @Query("DELETE FROM auto_capture_fingerprints WHERE postTime < :threshold")
    fun prune(threshold: Long)
}
