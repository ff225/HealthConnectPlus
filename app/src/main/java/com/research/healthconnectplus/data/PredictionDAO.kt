package com.research.healthconnectplus.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

@Dao
interface PredictionDAO {

    @Insert
    suspend fun insert(predictionRecord: PredictionRecord)

    @Update
    suspend fun update(predictionRecord: PredictionRecord)

    @Delete
    suspend fun delete(predictionRecord: PredictionRecord)

}