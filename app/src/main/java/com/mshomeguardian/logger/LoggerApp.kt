package com.mshomeguardian.logger

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase
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

        // Initialize Firebase App Check
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // Initialize device ID
        try {
            val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            Log.d(TAG, "Device ID initialized: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing device ID", e)
        }

        // Initialize WorkManager using our utility class
        WorkManagerInitializer.initialize(applicationContext)

        // Workers will be scheduled after permissions are granted in MainActivity
    }
}