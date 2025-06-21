package com.mshomeguardian.logger

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.google.firebase.FirebaseApp
// ❌ COMPLETELY REMOVED - App Check imports
// import com.google.firebase.appcheck.FirebaseAppCheck
// import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.mshomeguardian.logger.utils.AuthStateHandler
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.WorkManagerInitializer

/**
 * EMERGENCY VERSION - All App Check code removed
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

        Log.d(TAG, "LoggerApp starting (EMERGENCY - NO APP CHECK)...")

        try {
            initializeFirebase()
            initializeDeviceIdentifier()
            initializeWorkManager()
            initializeAuthenticationHandler()

            Log.d(TAG, "LoggerApp initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LoggerApp initialization", e)
        }
    }

    /**
     * BASIC Firebase initialization - NO APP CHECK
     */
    private fun initializeFirebase() {
        try {
            Log.d(TAG, "Initializing Firebase...")

            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase App initialized")
            } else {
                Log.d(TAG, "Firebase App already initialized")
            }

            // ❌ ALL APP CHECK CODE REMOVED

            Log.d(TAG, "Firebase initialized successfully (NO APP CHECK)")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
    }

    private fun initializeDeviceIdentifier() {
        try {
            Log.d(TAG, "Initializing device identifier...")
            val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            Log.d(TAG, "Device ID initialized: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing device ID", e)
        }
    }

    private fun initializeWorkManager() {
        try {
            Log.d(TAG, "Initializing WorkManager...")
            WorkManagerInitializer.initialize(applicationContext)
            Log.d(TAG, "WorkManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WorkManager", e)
        }
    }

    private fun initializeAuthenticationHandler() {
        try {
            Log.d(TAG, "Initializing authentication state handler...")
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
            AuthStateHandler.cleanup()
            Log.d(TAG, "LoggerApp cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LoggerApp cleanup", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning received")
        try {
            System.gc()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling low memory", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "Memory trim level: $level")
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW) {
                System.gc()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during memory trimming", e)
        }
    }

    fun getAppContext(): Context = applicationContext

    fun isInitialized(): Boolean {
        return try {
            FirebaseApp.getApps(this).isNotEmpty() &&
                    DeviceIdentifier.getPersistentDeviceId(applicationContext).isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking initialization status", e)
            false
        }
    }

    fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app version", e)
            "Unknown"
        }
    }
}