package com.research.healthconnectplus.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

@Dao
interface GenericDAO<T> {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(genericRecord: T)

    @Delete
    suspend fun delete(genericRecord: T)

    @Update
    suspend fun update(genericRecord: T)
}