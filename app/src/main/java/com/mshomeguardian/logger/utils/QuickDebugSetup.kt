package com.mshomeguardian.logger.utils

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import android.os.Build
import android.util.Log

/**
 * Quick setup helper for debug features - simpler alternative to DebugFeaturesManager
 * Use this if you want minimal setup with all features enabled
 */
object QuickDebugSetup {
    private val TAG = "QuickDebugSetup"
    private var manager: DebugFeaturesManager? = null
    
    /**
     * One-line setup for debug features
     * Usage: QuickDebugSetup.init(activity, rootViewGroup, deviceIdTextView)
     */
    fun init(activity: Activity, rootContainer: ViewGroup, deviceIdTextView: TextView) {
        try {
            if (manager != null) {
                Log.w(TAG, "Debug features already initialized")
                return
            }
            
            manager = DebugFeaturesManager.getInstance(activity)
            manager?.initialize(rootContainer, deviceIdTextView)
            
            Log.i(TAG, "✓ Debug features initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize debug features", e)
        }
    }
    
    /**
     * Get the debug manager instance
     */
    fun getManager(): DebugFeaturesManager? = manager
    
    /**
     * Quick log methods for easy access
     */
    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        manager?.let { mgr ->
            when (level) {
                LogLevel.DEBUG -> mgr.logDebug(message)
                LogLevel.INFO -> mgr.logInfo(message)
                LogLevel.WARNING -> mgr.logWarning(message)
                LogLevel.ERROR -> mgr.logError(message)
                LogLevel.SUCCESS -> mgr.logSuccess(message)
                LogLevel.PERFORMANCE -> mgr.logPerformance(message)
                LogLevel.NETWORK -> mgr.logNetwork(message)
                LogLevel.THERMAL -> mgr.logThermal(message)
            }
        }
    }
    
    fun recordSync(itemCount: Int, status: String = "Success") {
        manager?.recordSync(itemCount, status)
    }
    
    fun setSyncInProgress(inProgress: Boolean) {
        manager?.setSyncInProgress(inProgress)
    }
    
    fun toggle() {
        manager?.toggleDebugDisplay()
    }
    
    fun clearLogs() {
        manager?.clearAllLogs()
    }
    
    fun destroy() {
        manager?.destroy()
        manager = null
    }
}

/**
 * Extension functions for easier logging from any Activity or Context-aware class
 */

fun Activity.initDebugFeatures(
    rootContainer: ViewGroup,
    deviceIdTextView: TextView
) {
    QuickDebugSetup.init(this, rootContainer, deviceIdTextView)
}

fun Activity.logDebug(message: String) {
    QuickDebugSetup.log(message, LogLevel.DEBUG)
}

fun Activity.logInfo(message: String) {
    QuickDebugSetup.log(message, LogLevel.INFO)
}

fun Activity.logWarning(message: String) {
    QuickDebugSetup.log(message, LogLevel.WARNING)
}

fun Activity.logError(message: String, throwable: Throwable? = null) {
    QuickDebugSetup.log(message, LogLevel.ERROR)
    if (throwable != null) {
        QuickDebugSetup.getManager()?.logError(throwable.stackTraceToString())
    }
}

fun Activity.logSuccess(message: String) {
    QuickDebugSetup.log(message, LogLevel.SUCCESS)
}

fun Activity.logPerformance(message: String) {
    QuickDebugSetup.log(message, LogLevel.PERFORMANCE)
}

fun Activity.logNetwork(message: String) {
    QuickDebugSetup.log(message, LogLevel.NETWORK)
}

fun Activity.logThermal(message: String) {
    QuickDebugSetup.log(message, LogLevel.THERMAL)
}

fun Activity.recordSync(itemCount: Int, status: String = "Success") {
    QuickDebugSetup.recordSync(itemCount, status)
}

fun Activity.setSyncInProgress(inProgress: Boolean) {
    QuickDebugSetup.setSyncInProgress(inProgress)
}

fun Activity.toggleDebugDisplay() {
    QuickDebugSetup.toggle()
}

fun Activity.clearDebugLogs() {
    QuickDebugSetup.clearLogs()
}
