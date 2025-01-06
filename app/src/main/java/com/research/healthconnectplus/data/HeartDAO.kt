package com.research.healthconnectplus.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HeartDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(heartRecord: HeartRecord)

    @Update
    suspend fun update(heartRecord: HeartRecord)

    @Query("SELECT * FROM heart_records WHERE is_synced = 0")
    suspend fun getUnsyncedHeartRecords(): List<HeartRecord>

    @Query("SELECT * FROM heart_records WHERE is_synced = 0")
    fun fetchCursor(): Cursor
}