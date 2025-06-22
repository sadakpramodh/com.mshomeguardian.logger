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
        AudioRecordingEntity::class
    ],
    version = 3, // Incremented for optimization
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao
    abstract fun callLogDao(): CallLogDao
    abstract fun messageDao(): MessageDao
    abstract fun deviceInfoDao(): DeviceInfoDao
    abstract fun audioRecordingDao(): AudioRecordingDao

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
                .addMigrations(MIGRATION_2_3)
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
    }
}