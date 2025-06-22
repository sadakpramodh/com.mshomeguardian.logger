package com.mshomeguardian.logger.utils

import android.util.Log

/**
 * Optimized logger that safely detects debug mode
 */
object OptimizedLogger {

    // Safely detect if we're in debug mode
    private val isDebugMode: Boolean by lazy {
        try {
            val buildConfigClass = Class.forName("com.mshomeguardian.logger.BuildConfig")
            val debugField = buildConfigClass.getDeclaredField("DEBUG")
            debugField.getBoolean(null)
        } catch (e: Exception) {
            // If BuildConfig is not available, default to true for safety
            Log.w("OptimizedLogger", "Could not access BuildConfig.DEBUG, defaulting to debug mode")
            true
        }
    }

    fun d(tag: String, message: String) {
        if (isDebugMode) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (isDebugMode) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (isDebugMode) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Always log errors, even in release builds
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    fun v(tag: String, message: String) {
        if (isDebugMode) {
            Log.v(tag, message)
        }
    }
}