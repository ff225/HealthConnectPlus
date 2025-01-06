package com.research.healthconnectplus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StepRecord::class, HeartRecord::class, MovesenseRecord::class, PredictionRecord::class],
    version = 1,
    exportSchema = false
)
abstract class HCPlusDatabase : RoomDatabase() {
    abstract fun stepDAO(): StepDAO
    abstract fun heartDAO(): HeartDAO
    abstract fun movesenseDAO(): MovesenseDAO
    abstract fun predictionDAO(): PredictionDAO

    companion object {

        @Volatile
        private var INSTANCE: HCPlusDatabase? = null

        fun getDatabase(context: Context): HCPlusDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    HCPlusDatabase::class.java,
                    "hcplus_database"
                ).build().also {
                    INSTANCE = it
                }

            }
        }
    }
}