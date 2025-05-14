package com.mshomeguardian.logger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(
    entities = [
        LocationEntity::class,
        CallLogEntity::class,
        MessageEntity::class,
        DeviceInfoEntity::class,
        AudioRecordingEntity::class  // Added new entity
    ],
    version = 2,  // Incremented version for schema change
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // DAOs
    abstract fun locationDao(): LocationDao
    abstract fun callLogDao(): CallLogDao
    abstract fun messageDao(): MessageDao
    abstract fun deviceInfoDao(): DeviceInfoDao
    abstract fun audioRecordingDao(): AudioRecordingDao  // Added new DAO

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Delete the database file first to force a clean rebuild
                // This is necessary because we're encountering schema integrity issues
                val dbFile = context.getDatabasePath("logger_database")
                if (dbFile.exists()) {
                    try {
                        context.deleteDatabase("logger_database")
                    } catch (e: Exception) {
                        // If we can't delete the database, fallback to destructive migration
                    }
                }

                // Create a new database instance
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "logger_database"
                )
                    .fallbackToDestructiveMigration()  // In case file deletion didn't work
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}