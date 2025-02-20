package com.research.healthconnectplus.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HeartDAO: GenericDAO<HeartRecord> {

    @Query("SELECT * FROM heart_records WHERE is_synced = 0")
    suspend fun getUnsyncedHeartRecords(): List<HeartRecord>

    @Query("SELECT * FROM heart_records WHERE is_synced = 0")
    fun fetchCursor(): Cursor
}