package com.research.healthconnectplus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movesense_record")
data class MovesenseRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    @ColumnInfo(name = "timestamp")
    val timestamp: String,
    @ColumnInfo(name = "x_acc")
    var xAcc: String,
    @ColumnInfo(name = "y_acc")
    val yAcc: String,
    @ColumnInfo(name = "z_acc")
    val zAcc: String,
    @ColumnInfo(name = "x_gyro")
    val xGyro: String,
    @ColumnInfo(name = "y_gyro")
    val yGyro: String,
    @ColumnInfo(name = "z_gyro")
    val zGyro: String,
    @ColumnInfo(name = "x_magn")
    val xMagn: String,
    @ColumnInfo(name = "y_magn")
    val yMagn: String,
    @ColumnInfo(name = "z_magn")
    val zMagn: String,
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    @ColumnInfo(name = "is_processed")
    val isProcessed: Boolean = false
)

fun MovesenseRecord.toInfluxFormat(): String {
    return "movesense_record " +
            "x_acc=$xAcc,y_acc=$yAcc,z_acc=$zAcc," +
            "x_gyro=$xGyro,y_gyro=$yGyro,z_gyro=$zGyro," +
            "x_magn=$xMagn,y_magn=$yMagn,z_magn=$zMagn " +
            timestamp
}

fun List<MovesenseRecord>.toInfluxBatch(): String {
    return this.joinToString(separator = "\n") { it.toInfluxFormat() }
}