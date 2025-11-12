package com.research.healthconnectplus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inference_time_records")
data class InferenceTimeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelName: String,
    val shape: String,
    val totalTimeMs: Long,
    val averageTimeMs: Float,
    val timestamp: Long,
    val windowCount: Int = 32,
    val device: String? = null,
)
