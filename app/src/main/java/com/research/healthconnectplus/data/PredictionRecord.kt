package com.research.healthconnectplus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prediction_records")
data class PredictionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    @ColumnInfo(name = "prediction")
    val prediction: String,
    @ColumnInfo(name = "start")
    val start: Long,
    @ColumnInfo(name = "end")
    val end: Long,
)