package com.research.healthconnectplus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StepRecord::class, HeartRecord::class, MovesenseRecord::class, PredictionRecord::class, UserInfo::class],
    version = 1,
    exportSchema = false
)
abstract class HCPlusDatabase : RoomDatabase() {
    abstract fun stepDAO(): StepDAO
    abstract fun heartDAO(): HeartDAO
    abstract fun movesenseDAO(): MovesenseDAO
    abstract fun predictionDAO(): PredictionDAO
    abstract fun userDAO(): UserDao

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
                )
                    // TODO: Remove fallbackToDestructiveMigration in production.
                    // This is a temporary fix for development to handle database version conflicts.
                    // Proper migrations should be implemented for production releases.
                    .fallbackToDestructiveMigration()
                    .build().also {
                    INSTANCE = it
                }

            }
        }
    }
}