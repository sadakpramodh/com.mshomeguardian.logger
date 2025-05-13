package com.mshomeguardian.logger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocationEntity::class,
        CallLogEntity::class,
        MessageEntity::class,
        DeviceInfoEntity::class
    ],
    version = 2,  // Increased from 1 to 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // DAOs
    abstract fun locationDao(): LocationDao
    abstract fun callLogDao(): CallLogDao
    abstract fun messageDao(): MessageDao
    abstract fun deviceInfoDao(): DeviceInfoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "logger_database"
                )
                    .fallbackToDestructiveMigration() // For simplicity during development
                    .build().also { INSTANCE = it }
            }
        }
    }
}