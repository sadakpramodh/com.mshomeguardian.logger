package com.mshomeguardian.logger

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.google.firebase.FirebaseApp
import com.mshomeguardian.logger.utils.AuthStateHandler
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.WorkManagerInitializer
import com.mshomeguardian.logger.utils.OptimizedLogger

/**
 * Optimized Application class without Firebase App Check
 * (App Check removed to reduce APK size - add back if needed for production)
 */
class LoggerApp : Application() {
    companion object {
        private const val TAG = "LoggerApp"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        OptimizedLogger.d(TAG, "LoggerApp starting...")

        try {
            initializeFirebase()
            initializeFirestore()
            initializeDeviceIdentifier()
            initializeWorkManager()
            initializeAuthenticationHandler()

            OptimizedLogger.d(TAG, "LoggerApp initialization completed successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error during LoggerApp initialization", e)
        }
    }

    /**
     * Initialize Firebase without App Check (for optimized build)
     */
    private fun initializeFirebase() {
        try {
            OptimizedLogger.d(TAG, "Initializing Firebase...")

            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                OptimizedLogger.d(TAG, "Firebase App initialized")
            } else {
                OptimizedLogger.d(TAG, "Firebase App already initialized")
            }

            // NOTE: Firebase App Check removed for optimization
            // If you need App Check for production, add back these dependencies to build.gradle:
            // implementation 'com.google.firebase:firebase-appcheck'
            // implementation 'com.google.firebase:firebase-appcheck-playintegrity'
            // implementation 'com.google.firebase:firebase-appcheck-interop'

            OptimizedLogger.d(TAG, "Firebase initialized successfully (without App Check)")

        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing Firebase", e)
        }
    }

    /**
     * Configure Firestore with offline persistence
     */
    private fun initializeFirestore() {
        try {
            OptimizedLogger.d(TAG, "Configuring Firestore...")

            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // Configure Firestore settings for better connectivity
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()

            firestore.firestoreSettings = settings

            OptimizedLogger.d(TAG, "Firestore configured with offline persistence")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error configuring Firestore", e)
        }
    }

    private fun initializeDeviceIdentifier() {
        try {
            OptimizedLogger.d(TAG, "Initializing device identifier...")
            val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            OptimizedLogger.d(TAG, "Device ID initialized: $deviceId")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing device ID", e)
        }
    }

    private fun initializeWorkManager() {
        try {
            OptimizedLogger.d(TAG, "Initializing WorkManager...")
            WorkManagerInitializer.initialize(applicationContext)
            OptimizedLogger.d(TAG, "WorkManager initialized successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing WorkManager", e)
        }
    }

    private fun initializeAuthenticationHandler() {
        try {
            OptimizedLogger.d(TAG, "Initializing authentication state handler...")
            AuthStateHandler.initialize(applicationContext)
            OptimizedLogger.d(TAG, "Authentication state handler initialized")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing authentication state handler", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        OptimizedLogger.d(TAG, "LoggerApp terminating...")
        try {
            AuthStateHandler.cleanup()
            OptimizedLogger.d(TAG, "LoggerApp cleanup completed")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error during LoggerApp cleanup", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        OptimizedLogger.w(TAG, "Low memory warning received")
        try {
            // Trigger garbage collection on low memory
            System.gc()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error handling low memory", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        OptimizedLogger.d(TAG, "Memory trim level: $level")
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW) {
                // More aggressive memory cleanup
                System.gc()
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error during memory trimming", e)
        }
    }

    // Utility methods
    fun getAppContext(): Context = applicationContext

    fun isInitialized(): Boolean {
        return try {
            FirebaseApp.getApps(this).isNotEmpty() &&
                    DeviceIdentifier.getPersistentDeviceId(applicationContext).isNotEmpty()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error checking initialization status", e)
            false
        }
    }

    fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error getting app version", e)
            "Unknown"
        }
    }
}