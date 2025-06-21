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
 * Application class with proper Firebase App Check initialization
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
        Log.d(TAG, "LoggerApp starting...")

        try {
            initializeFirebase()
            initializeFirestore()
            initializeDeviceIdentifier()
            initializeWorkManager()
            initializeAuthenticationHandler()

            Log.d(TAG, "LoggerApp initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LoggerApp initialization", e)
        }
    }

    /**
     * Initialize Firebase with App Check
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

            // Initialize App Check with Play Integrity
            try {
                val firebaseAppCheck = FirebaseAppCheck.getInstance()
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check initialized with Play Integrity")
            } catch (e: Exception) {
                Log.w(TAG, "App Check initialization failed, continuing without it", e)
                // Continue without App Check - your app will still work
            }

            Log.d(TAG, "Firebase initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
    }

    /**
     * Configure Firestore with offline persistence
     */
    private fun initializeFirestore() {
        try {
            Log.d(TAG, "Configuring Firestore...")

            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // Configure Firestore settings for better connectivity
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()

            firestore.firestoreSettings = settings

            Log.d(TAG, "Firestore configured with offline persistence")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring Firestore", e)
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