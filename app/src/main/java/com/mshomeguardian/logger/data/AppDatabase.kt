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
    version = 5, // Incremented for call log schema changes
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
         * Optimized database initialization with better performance
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildOptimizedDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildOptimizedDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "optimized_logger_database"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Better performance
                .enableMultiInstanceInvalidation() // For multiple processes
                .fallbackToDestructiveMigration() // Only for development
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        OptimizedLogger.d(TAG, "Database created with optimizations")

                        // Create additional indices for better performance
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_timestamp_uploaded ON call_logs(timestamp, uploadedToCloud)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_timestamp_uploaded ON message_logs(timestamp, uploadedToCloud)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_uploaded_status ON audio_recordings(uploadedToCloud, transcriptionStatus)")
                    }
                })
                .build()
        }

        /**
         * Migration from version 2 to 3 with optimizations
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add performance indices
                try {
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_timestamp_uploaded ON call_logs(timestamp, uploadedToCloud)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_timestamp_uploaded ON message_logs(timestamp, uploadedToCloud)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_audio_uploaded_status ON audio_recordings(uploadedToCloud, transcriptionStatus)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_location_timestamp ON location_table(timestamp)")

                    OptimizedLogger.d(TAG, "Database migration 2->3 completed with performance indices")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Error during database migration", e)
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS network_usage (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "packageName TEXT NOT NULL, " +
                            "rxBytes INTEGER NOT NULL, " +
                            "txBytes INTEGER NOT NULL, " +
                            "timestamp INTEGER NOT NULL, " +
                            "deviceId TEXT NOT NULL, " +
                            "uploadedToCloud INTEGER NOT NULL DEFAULT 0, " +
                            "uploadTimestamp INTEGER)"
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_network_usage_uploaded ON network_usage(uploadedToCloud)"
                    )
                    OptimizedLogger.d(TAG, "Database migration 3->4 completed")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Error during database migration 3->4", e)
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS call_logs_new (
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
                            deletedLocally INTEGER NOT NULL DEFAULT 0,
                            uploadedToCloud INTEGER NOT NULL DEFAULT 0,
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
                        """
                    )

                    try {
                        database.execSQL(
                            """
                            INSERT INTO call_logs_new (
                                id, callId, syncTimestamp, phoneNumber, timestamp, duration, type,
                                contactName, contactPhotoUri, isRead, isNew, deletedLocally,
                                uploadedToCloud, uploadTimestamp, presentationType,
                                callScreeningAppName, callScreeningComponentName, numberAttributes,
                                geoLocation, phoneAccountId, features, postDialDigits, viaNumber, deviceId
                            )
                            SELECT
                                id, callId, syncTimestamp, phoneNumber, timestamp, duration, type,
                                contactName, contactPhotoUri, isRead, isNew, deletedLocally,
                                uploadedToCloud, uploadTimestamp, presentationType,
                                callScreeningAppName, callScreeningComponentName, numberAttributes,
                                geoLocation, phoneAccountId, features, postDialDigits, viaNumber, deviceId
                            FROM call_logs
                            """
                        )
                    } catch (e: Exception) {
                        OptimizedLogger.e(TAG, "Error copying call_logs data during migration 4->5", e)
                    }

                    database.execSQL("DROP TABLE IF EXISTS call_logs")
                    database.execSQL("ALTER TABLE call_logs_new RENAME TO call_logs")

                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_call_logs_callId ON call_logs(callId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_phoneNumber ON call_logs(phoneNumber)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_timestamp ON call_logs(timestamp)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_call_logs_timestamp_uploaded ON call_logs(timestamp, uploadedToCloud)")

                    OptimizedLogger.d(TAG, "Database migration 4->5 completed with call_logs rebuild")
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Error during database migration 4->5", e)
                }
            }
        }
    }
}