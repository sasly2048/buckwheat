package com.danilkinkin.buckwheat.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persisted dedup fingerprint, pruned to the dedup window. Additive table (DB v6). */
@Entity(tableName = "auto_capture_fingerprints")
data class AutoCaptureFingerprint(
    @PrimaryKey val fingerprint: String,
    @ColumnInfo(name = "postTime") val postTime: Long,
)
