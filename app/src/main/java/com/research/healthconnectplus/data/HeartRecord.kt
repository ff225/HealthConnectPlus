package com.research.healthconnectplus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "heart_records", indices = [Index(value = ["record_id"], unique = true)])
data class HeartRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id")
    val id: Int = 0,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "bpm")
    val bpm: String,
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    @ColumnInfo(name = "end_time")
    val endTime: Long,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false
)

fun HeartRecord.toInfluxFormat(): String {
    return "heart_records bpm=$bpm $startTime"
}
