package com.mshomeguardian.logger.utils

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Utility class to handle WorkManager initialization
 */
object WorkManagerInitializer {
    private const val TAG = "WorkManagerInitializer"

    /**
     * Initialize WorkManager safely
     */
    fun initialize(context: Context) {
        try {
            // Check if WorkManager is already initialized
            try {
                WorkManager.getInstance(context)
                Log.d(TAG, "WorkManager is already initialized")
                return
            } catch (e: IllegalStateException) {
                // WorkManager isn't initialized yet, continue with initialization
                Log.d(TAG, "Initializing WorkManager")
            }

            // Create configuration for WorkManager
            val configuration = Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build()

            // Initialize WorkManager with our configuration
            WorkManager.initialize(context, configuration)

            Log.d(TAG, "WorkManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WorkManager", e)
        }
    }
}