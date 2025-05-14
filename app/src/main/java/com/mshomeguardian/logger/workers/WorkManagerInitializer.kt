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

    // Add this flag to track if WorkManager has been initialized
    @Volatile private var isInitialized = false

    /**
     * Initialize WorkManager safely
     */
    @Synchronized
    fun initialize(context: Context) {
        // Check already initialized flag first
        if (isInitialized) {
            Log.d(TAG, "WorkManager is already initialized by this app")
            return
        }

        try {
            // Check if WorkManager is already initialized by system
            try {
                WorkManager.getInstance(context)
                Log.d(TAG, "WorkManager is already initialized by system")
                isInitialized = true
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
            isInitialized = true

            Log.d(TAG, "WorkManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WorkManager", e)
        }
    }
}