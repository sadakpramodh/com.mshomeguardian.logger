package com.mshomeguardian.logger.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.util.Log
import com.mshomeguardian.logger.ui.DebugConsoleView
import com.mshomeguardian.logger.ui.SyncStatsView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

/**
 * Unified debug features manager for easy integration into activities
 */
class DebugFeaturesManager(private val activity: Activity) {
    private val TAG = "DebugManager"
    private lateinit var debugConsoleView: DebugConsoleView
    private lateinit var syncStatsView: SyncStatsView
    private lateinit var syncStatsManager: SyncStatsManager
    private lateinit var performanceMonitor: PerformanceMetricsMonitor
    private val consoleLogger = ConsoleLogger.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private var performanceMonitoringHandler: Handler? = null
    private val performanceUpdateRunnable = object : Runnable {
        override fun run() {
            updatePerformanceMetrics()
            performanceMonitoringHandler?.postDelayed(this, 5000)
        }
    }
    
    private var isInitialized = false

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Initialize all debug features
     * @param rootContainer The root ViewGroup where debug views will be added
     * @param deviceIdTextView The Device ID TextView for triple-tap detection
     */
    fun initialize(rootContainer: ViewGroup, deviceIdTextView: TextView) {
        if (isInitialized) return
        
        try {
            // Initialize managers
            syncStatsManager = SyncStatsManager(activity.applicationContext)
            performanceMonitor = PerformanceMetricsMonitor(activity.applicationContext)
            
            val debugOverlayContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Create and add sync stats view
            syncStatsView = SyncStatsView(activity)
            syncStatsView.setSyncStatsManager(syncStatsManager)
            debugOverlayContainer.addView(
                syncStatsView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            // Create and add debug console
            debugConsoleView = DebugConsoleView(activity)
            debugConsoleView.visibility = View.GONE
            debugOverlayContainer.addView(
                debugConsoleView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            rootContainer.addView(
                debugOverlayContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                    bottomMargin = dp(12)
                }
            )
            
            // Setup 4-tap detector for debug display
            TripleTapDetector(deviceIdTextView, requiredTapCount = 4) {
                toggleDebugDisplay()
                logDebugToggle()
            }
            
            // Start performance monitoring
            startPerformanceMonitoring()
            
            // Log initialization
            consoleLogger.success(TAG, "✓ Debug features initialized")
            
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize debug features", e)
            consoleLogger.error(TAG, "Failed to initialize debug features", e)
        }
    }
    
    /**
     * Record a sync event
     */
    fun recordSync(itemCount: Int, status: String = "Success") {
        try {
            syncStatsManager.recordSync(itemCount, status)
            consoleLogger.success(TAG, "✓ Sync recorded: $itemCount items, Status: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record sync", e)
        }
    }
    
    /**
     * Set sync in progress state
     */
    fun setSyncInProgress(inProgress: Boolean) {
        try {
            syncStatsManager.setSyncInProgress(inProgress)
            if (inProgress) {
                consoleLogger.info(TAG, "📤 Sync in progress...")
            } else {
                consoleLogger.success(TAG, "✓ Sync completed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set sync progress", e)
        }
    }
    
    /**
     * Log a debug message
     */
    fun logDebug(message: String) {
        consoleLogger.debug(TAG, message)
    }
    
    /**
     * Log an info message
     */
    fun logInfo(message: String) {
        consoleLogger.info(TAG, message)
    }
    
    /**
     * Log a warning message
     */
    fun logWarning(message: String) {
        consoleLogger.warning(TAG, message)
    }
    
    /**
     * Log an error message
     */
    fun logError(message: String, throwable: Throwable? = null) {
        consoleLogger.error(TAG, message, throwable)
    }
    
    /**
     * Log a success message
     */
    fun logSuccess(message: String) {
        consoleLogger.success(TAG, message)
    }
    
    /**
     * Log a performance metric
     */
    fun logPerformance(message: String) {
        consoleLogger.performance(TAG, message)
    }
    
    /**
     * Log a network event
     */
    fun logNetwork(message: String) {
        consoleLogger.network(TAG, message)
    }
    
    /**
     * Log a thermal event
     */
    fun logThermal(message: String) {
        consoleLogger.thermal(TAG, message)
    }
    
    /**
     * Toggle debug display visibility
     */
    fun toggleDebugDisplay() {
        try {
            val newState = !syncStatsManager.isDebugConsoleEnabled()
            syncStatsManager.setDebugConsoleEnabled(newState)
            syncStatsView.visibility = if (newState) View.VISIBLE else View.GONE
            debugConsoleView.visibility = if (newState) View.VISIBLE else View.GONE
            
            if (newState) {
                consoleLogger.info(TAG, "🔍 Debug console enabled")
            } else {
                consoleLogger.info(TAG, "🔍 Debug console disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle debug display", e)
        }
    }
    
    /**
     * Get sync stats
     */
    fun getSyncStats(): SyncStatsManager.SyncStats {
        return syncStatsManager.getSyncStats()
    }
    
    /**
     * Clear all logs
     */
    fun clearAllLogs() {
        consoleLogger.clearLogs()
        consoleLogger.info(TAG, "✓ Logs cleared")
    }
    
    /**
     * Get console logger instance
     */
    fun getConsoleLogger(): ConsoleLogger {
        return consoleLogger
    }
    
    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetricsMonitor.DeviceMetrics {
        return performanceMonitor.metricsFlow.value
    }
    
    /**
     * Destroy debug features manager
     */
    fun destroy() {
        stopPerformanceMonitoring()
        scope.cancel()
        isInitialized = false
        instance = null
    }
    
    private fun logDebugToggle() {
        consoleLogger.info(TAG, "🎯 Debug display toggled via 4-tap")
    }
    
    private fun startPerformanceMonitoring() {
        performanceMonitoringHandler = Handler(Looper.getMainLooper())
        performanceMonitoringHandler?.post(performanceUpdateRunnable)
    }
    
    private fun stopPerformanceMonitoring() {
        performanceMonitoringHandler?.removeCallbacks(performanceUpdateRunnable)
        performanceMonitoringHandler = null
    }
    
    private fun updatePerformanceMetrics() {
        try {
            performanceMonitor.updateMetrics()
            val metrics = performanceMonitor.metricsFlow.value
            
            // Log thermal warnings
            if (metrics.thermalStatus != PerformanceMetricsMonitor.ThermalStatus.NORMAL) {
                consoleLogger.thermal(TAG, "🌡️ Thermal status: ${metrics.thermalStatus}")
            }
            
            // Log memory warnings
            if (metrics.memoryUsage.usedPercentage > 80) {
                consoleLogger.warning(TAG, "⚠️ Memory usage high: ${String.format("%.1f", metrics.memoryUsage.usedPercentage)}%")
            }
            
            // Log battery warnings
            if (metrics.batteryStatus.level < 15) {
                consoleLogger.warning(TAG, "🔋 Battery low: ${metrics.batteryStatus.level}%")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating performance metrics", e)
        }
    }
    
    companion object {
        private var instance: DebugFeaturesManager? = null
        
        fun getInstance(activity: Activity): DebugFeaturesManager {
            return instance ?: synchronized(this) {
                DebugFeaturesManager(activity).also { instance = it }
            }
        }
    }
}
