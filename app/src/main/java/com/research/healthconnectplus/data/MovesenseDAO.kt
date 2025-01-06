package com.research.healthconnectplus.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MovesenseDAO {

    @Insert
    suspend fun insert(movesenseRecord: MovesenseRecord)

    @Update
    suspend fun update(movesenseRecord: MovesenseRecord)

    @Delete
    suspend fun delete(movesenseRecord: MovesenseRecord)

    @Query("SELECT * FROM movesense_record WHERE is_processed = 0 LIMIT 52")
    suspend fun getUnprocessedRecords(): List<MovesenseRecord>

    @Query("SELECT * FROM movesense_record WHERE is_synced = 0")
    suspend fun getUnsyncedRecords(): List<MovesenseRecord>

    @Query("SELECT * FROM movesense_record WHERE is_synced = 0")
    fun fetchCursor(): Cursor
}