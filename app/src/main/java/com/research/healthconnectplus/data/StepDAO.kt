package com.research.healthconnectplus.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface StepDAO : GenericDAO<StepRecord> {

    @Query("SELECT * FROM step_records WHERE is_synced = 0")
    suspend fun getUnsyncedStepRecords(): List<StepRecord>

    @Query("SELECT * FROM step_records WHERE is_synced = 0")
    fun fetchCursor(): Cursor
}
