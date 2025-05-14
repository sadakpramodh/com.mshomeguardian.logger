package com.mshomeguardian.logger

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.mshomeguardian.logger.utils.DataSyncManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.WorkManagerInitializer

/**
 * Main Application class with MultiDex support
 */
class LoggerApp : Application() {
    companion object {
        private const val TAG = "LoggerApp"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Enable MultiDex
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager first, before any other components
        try {
            WorkManagerInitializer.initialize(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WorkManager", e)
        }

        // Initialize device ID
        try {
            val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            Log.d(TAG, "Device ID initialized: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing device ID", e)
        }

        // Note: We don't start services here as they require permissions
        // Services will be started in MainActivity after permissions granted
        Log.d(TAG, "Initialized app components")
    }
}