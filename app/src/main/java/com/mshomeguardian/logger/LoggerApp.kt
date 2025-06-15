package com.mshomeguardian.logger

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.mshomeguardian.logger.utils.AuthStateHandler
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.WorkManagerInitializer

/**
 * Main Application class with MultiDex support and Authentication
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

        Log.d(TAG, "LoggerApp starting...")

        try {
            // Initialize Firebase first
            initializeFirebase()

            // Initialize device identification
            initializeDeviceIdentifier()

            // Initialize WorkManager
            initializeWorkManager()

            // Initialize authentication state handler
            initializeAuthenticationHandler()

            Log.d(TAG, "LoggerApp initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LoggerApp initialization", e)
        }
    }

    /**
     * Initialize Firebase services
     */
    private fun initializeFirebase() {
        try {
            Log.d(TAG, "Initializing Firebase...")

            // Initialize Firebase App if not already initialized
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase App initialized")
            } else {
                Log.d(TAG, "Firebase App already initialized")
            }

            // Initialize Firebase App Check for security
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "Firebase App Check initialized")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
            // Continue initialization even if Firebase fails
        }
    }

    /**
     * Initialize device identifier
     */
    private fun initializeDeviceIdentifier() {
        try {
            Log.d(TAG, "Initializing device identifier...")

            val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            Log.d(TAG, "Device ID initialized: $deviceId")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing device ID", e)
        }
    }

    /**
     * Initialize WorkManager
     */
    private fun initializeWorkManager() {
        try {
            Log.d(TAG, "Initializing WorkManager...")

            // Initialize WorkManager using our utility class
            WorkManagerInitializer.initialize(applicationContext)

            Log.d(TAG, "WorkManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WorkManager", e)
        }
    }

    /**
     * Initialize authentication state handler
     */
    private fun initializeAuthenticationHandler() {
        try {
            Log.d(TAG, "Initializing authentication state handler...")

            // Initialize authentication state handler
            AuthStateHandler.initialize(applicationContext)

            Log.d(TAG, "Authentication state handler initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing authentication state handler", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()

        Log.d(TAG, "LoggerApp terminating...")

        try {
            // Clean up authentication state handler
            AuthStateHandler.cleanup()

            Log.d(TAG, "LoggerApp cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LoggerApp cleanup", e)
        }
    }

    /**
     * Handle low memory situations
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning received")

        try {
            // You can add memory cleanup logic here if needed
            System.gc() // Suggest garbage collection
        } catch (e: Exception) {
            Log.e(TAG, "Error handling low memory", e)
        }
    }

    /**
     * Handle memory trimming
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "UI hidden - trimming memory")
            }
            TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.d(TAG, "Running moderate - trimming memory")
            }
            TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "Running low on memory - trimming memory")
            }
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "Running critically low on memory - trimming memory")
            }
            TRIM_MEMORY_BACKGROUND -> {
                Log.d(TAG, "App in background - trimming memory")
            }
            TRIM_MEMORY_MODERATE -> {
                Log.w(TAG, "Moderate memory pressure - trimming memory")
            }
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Complete memory pressure - trimming memory")
            }
        }

        try {
            // Perform memory cleanup based on level
            if (level >= TRIM_MEMORY_RUNNING_LOW) {
                // Aggressive cleanup for low memory situations
                System.gc()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during memory trimming", e)
        }
    }

    /**
     * Get application context safely
     */
    fun getAppContext(): Context {
        return applicationContext
    }

    /**
     * Check if the app is properly initialized
     */
    fun isInitialized(): Boolean {
        return try {
            // Check if key components are initialized
            FirebaseApp.getApps(this).isNotEmpty() &&
                    DeviceIdentifier.getPersistentDeviceId(applicationContext).isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking initialization status", e)
            false
        }
    }

    /**
     * Get app version information
     */
    fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app version", e)
            "Unknown"
        }
    }

    /**
     * Log app startup information
     */
    private fun logAppInfo() {
        try {
            Log.i(TAG, "=== Home Guardian App Started ===")
            Log.i(TAG, "App Version: ${getAppVersion()}")
            Log.i(TAG, "Device ID: ${DeviceIdentifier.getPersistentDeviceId(applicationContext)}")
            Log.i(TAG, "Firebase Apps: ${FirebaseApp.getApps(this).size}")
            Log.i(TAG, "===================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging app info", e)
        }
    }
}