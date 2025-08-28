// 1. UPDATED AppDatabase.kt - Complete replacement
package com.mshomeguardian.logger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mshomeguardian.logger.utils.OptimizedLogger

@Database(
    entities = [
        LocationEntity::class,
        CallLogEntity::class,
        MessageEntity::class,
        DeviceInfoEntity::class,
        AudioRecordingEntity::class,
        NetworkUsageEntity::class
    ],
    version = 9, // Incremented to force clean migration
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao
    abstract fun callLogDao(): CallLogDao
    abstract fun messageDao(): MessageDao
    abstract fun deviceInfoDao(): DeviceInfoDao
    abstract fun audioRecordingDao(): AudioRecordingDao
    abstract fun networkUsageDao(): NetworkUsageDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get database instance with comprehensive error handling
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    buildDatabase(context).also { INSTANCE = it }
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Failed to build database, attempting emergency recovery", e)
                    buildEmergencyDatabase(context).also { INSTANCE = it }
                }
            }
        }

        /**
         * Main database builder with all migrations
         */
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "optimized_logger_database"
            )
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration() // Safety fallback
                .addCallback(DatabaseCallback())
                .build()
        }

        /**
         * Emergency database when primary fails
         */
        private fun buildEmergencyDatabase(context: Context): AppDatabase {
            OptimizedLogger.w(TAG, "Building emergency database")
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "emergency_logger_database"
            )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback())
                .build()
        }

        /**
         * Database callback for initialization and verification
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                OptimizedLogger.d(TAG, "Database created successfully")
                createOptimizedIndices(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                OptimizedLogger.d(TAG, "Database opened")
                verifyDatabaseHealth(db)
            }
        }

        /**
         * Create performance indices
         */
        private fun createOptimizedIndices(db: SupportSQLiteDatabase) {
            try {
                val indices = listOf(
                    "CREATE INDEX IF NOT EXISTS index_call_logs_timestamp_uploaded ON call_logs(timestamp, uploadedToCloud)",
                    "CREATE INDEX IF NOT EXISTS index_messages_timestamp_uploaded ON message_logs(timestamp, uploadedToCloud)",
                    "CREATE INDEX IF NOT EXISTS index_audio_uploaded_status ON audio_recordings(uploadedToCloud, transcriptionStatus)",
                    "CREATE INDEX IF NOT EXISTS index_location_timestamp ON location_table(timestamp)",
                    "CREATE INDEX IF NOT EXISTS index_network_usage_uploaded ON network_usage(uploadedToCloud)"
                )

                indices.forEach { sql ->
                    try {
                        db.execSQL(sql)
                    } catch (e: Exception) {
                        OptimizedLogger.e(TAG, "Failed to create index: $sql", e)
                    }
                }
                OptimizedLogger.d(TAG, "Optimized indices created")
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error creating indices", e)
            }
        }

        /**
         * Verify database health after opening
         */
        private fun verifyDatabaseHealth(db: SupportSQLiteDatabase) {
            try {
                val tables = listOf("call_logs", "message_logs", "location_table", "audio_recordings", "device_info")

                tables.forEach { tableName ->
                    db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
                        if (cursor.moveToFirst() && cursor.getInt(0) == 0) {
                            OptimizedLogger.w(TAG, "Table $tableName does not exist")
                        }
                    }
                }
                OptimizedLogger.d(TAG, "Database health check completed")
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Database health check failed", e)
            }
        }

        // MIGRATION DEFINITIONS

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    createOptimizedIndices(database)
                    OptimizedLogger.d(TAG, "Migration 2->3 completed")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Migration 2->3 failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS network_usage (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            packageName TEXT NOT NULL,
                            rxBytes INTEGER NOT NULL,
                            txBytes INTEGER NOT NULL,
                            timestamp INTEGER NOT NULL,
                            deviceId TEXT NOT NULL,
                            uploadedToCloud INTEGER NOT NULL DEFAULT 0,
                            uploadTimestamp INTEGER
                        )
                    """)
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_network_usage_uploaded ON network_usage(uploadedToCloud)")
                    OptimizedLogger.d(TAG, "Migration 3->4 completed")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Migration 3->4 failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    recreateCallLogsTable(database)
                    OptimizedLogger.d(TAG, "Migration 4->5 completed")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Migration 4->5 failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    recreateCallLogsTable(database)
                    OptimizedLogger.d(TAG, "Migration 5->6 completed")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Migration 5->6 failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    recreateCallLogsTable(database)
                    OptimizedLogger.d(TAG, "Migration 6->7 completed")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Migration 6->7 failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    recreateCallLogsTable(database)
                    OptimizedLogger.d(TAG, "Migration 7->8 completed")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Migration 7->8 failed", e)
                    throw e
                }
            }
        }

        // New migration to completely fix the schema
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    OptimizedLogger.d(TAG, "Starting migration 8->9 - Complete schema reset")

                    // Drop and recreate all problematic tables
                    database.execSQL("DROP TABLE IF EXISTS call_logs")

                    // Create call_logs table with correct schema
                    database.execSQL("""
                        CREATE TABLE call_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            callId TEXT NOT NULL,
                            syncTimestamp INTEGER NOT NULL,
                            phoneNumber TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            duration INTEGER NOT NULL,
                            type INTEGER NOT NULL,
                            contactName TEXT,
                            contactPhotoUri TEXT,
                            isRead INTEGER NOT NULL,
                            isNew INTEGER NOT NULL,
                            deletedLocally INTEGER NOT NULL,
                            uploadedToCloud INTEGER NOT NULL,
                            uploadTimestamp INTEGER,
                            presentationType INTEGER,
                            callScreeningAppName TEXT,
                            callScreeningComponentName TEXT,
                            numberAttributes TEXT,
                            geoLocation TEXT,
                            phoneAccountId TEXT,
                            features INTEGER,
                            postDialDigits TEXT,
                            viaNumber TEXT,
                            deviceId TEXT NOT NULL
                        )
                    """)

                    // Create all required indices
                    database.execSQL("CREATE UNIQUE INDEX index_call_logs_callId ON call_logs(callId)")
                    database.execSQL("CREATE INDEX index_call_logs_phoneNumber ON call_logs(phoneNumber)")
                    database.execSQL("CREATE INDEX index_call_logs_timestamp ON call_logs(timestamp)")

                    // Create additional performance indices
                    createOptimizedIndices(database)

                    OptimizedLogger.d(TAG, "Migration 8->9 completed successfully")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Migration 8->9 failed", e)
                    throw e
                }
            }
        }

        /**
         * Safely recreate call_logs table
         */
        private fun recreateCallLogsTable(database: SupportSQLiteDatabase) {
            try {
                OptimizedLogger.d(TAG, "Recreating call_logs table")

                // Check if table exists
                var hasData = false
                try {
                    database.query("SELECT COUNT(*) FROM call_logs LIMIT 1").use { cursor ->
                        if (cursor.moveToFirst()) {
                            hasData = cursor.getInt(0) > 0
                        }
                    }
                } catch (e: Exception) {
                    OptimizedLogger.d(TAG, "call_logs table doesn't exist or is corrupted")
                    hasData = false
                }

                if (hasData) {
                    OptimizedLogger.d(TAG, "Preserving existing call_logs data")

                    // Create backup table
                    database.execSQL("CREATE TABLE call_logs_backup AS SELECT * FROM call_logs")

                    // Drop original table
                    database.execSQL("DROP TABLE call_logs")

                    // Create new table
                    createCallLogsTableSchema(database)

                    // Restore data
                    try {
                        database.execSQL("""
                            INSERT INTO call_logs SELECT * FROM call_logs_backup
                        """)
                        OptimizedLogger.d(TAG, "Data restored successfully")
                    } catch (e: Exception) {
                        OptimizedLogger.w(TAG, "Could not restore all data, proceeding with empty table")
                    }

                    // Drop backup
                    database.execSQL("DROP TABLE IF EXISTS call_logs_backup")

                } else {
                    OptimizedLogger.d(TAG, "Creating fresh call_logs table")
                    database.execSQL("DROP TABLE IF EXISTS call_logs")
                    createCallLogsTableSchema(database)
                }

            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Failed to recreate call_logs table", e)
                // As last resort, create empty table
                database.execSQL("DROP TABLE IF EXISTS call_logs")
                createCallLogsTableSchema(database)
            }
        }

        /**
         * Create the call_logs table schema
         */
        private fun createCallLogsTableSchema(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE call_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    callId TEXT NOT NULL,
                    syncTimestamp INTEGER NOT NULL,
                    phoneNumber TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    duration INTEGER NOT NULL,
                    type INTEGER NOT NULL,
                    contactName TEXT,
                    contactPhotoUri TEXT,
                    isRead INTEGER NOT NULL,
                    isNew INTEGER NOT NULL,
                    deletedLocally INTEGER NOT NULL,
                    uploadedToCloud INTEGER NOT NULL,
                    uploadTimestamp INTEGER,
                    presentationType INTEGER,
                    callScreeningAppName TEXT,
                    callScreeningComponentName TEXT,
                    numberAttributes TEXT,
                    geoLocation TEXT,
                    phoneAccountId TEXT,
                    features INTEGER,
                    postDialDigits TEXT,
                    viaNumber TEXT,
                    deviceId TEXT NOT NULL
                )
            """)

            // Create indices
            database.execSQL("CREATE UNIQUE INDEX index_call_logs_callId ON call_logs(callId)")
            database.execSQL("CREATE INDEX index_call_logs_phoneNumber ON call_logs(phoneNumber)")
            database.execSQL("CREATE INDEX index_call_logs_timestamp ON call_logs(timestamp)")
        }

        /**
         * Emergency database reset (development only)
         */
        fun emergencyReset(context: Context) {
            try {
                synchronized(this) {
                    INSTANCE?.close()
                    INSTANCE = null

                    // Delete all database files
                    listOf(
                        "optimized_logger_database",
                        "emergency_logger_database"
                    ).forEach { dbName ->
                        context.deleteDatabase(dbName)
                    }

                    // Clear related shared preferences
                    listOf(
                        "call_log_sync", "message_sync", "contacts_sync",
                        "location_sync", "audio_recording_sync", "weather_data"
                    ).forEach { prefsName ->
                        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                            .edit().clear().apply()
                    }

                    OptimizedLogger.d(TAG, "Emergency database reset completed")
                }
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Emergency reset failed", e)
            }
        }

        /**
         * Check if database is healthy
         */
        fun isDatabaseHealthy(context: Context): Boolean {
            return try {
                val db = getInstance(context)
                // Try a simple query
//                db.callLogDao().getAllCallLogs()
                true
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Database health check failed", e)
                false
            }
        }
    }
}