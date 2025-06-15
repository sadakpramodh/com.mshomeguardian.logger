package com.mshomeguardian.logger.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mshomeguardian.logger.services.LocationMonitoringService
import com.mshomeguardian.logger.services.AudioRecordingService
import com.mshomeguardian.logger.workers.CallLogWorker
import com.mshomeguardian.logger.workers.ContactsWorker
import com.mshomeguardian.logger.workers.DeviceInfoWorker
import com.mshomeguardian.logger.workers.MessageWorker
import com.mshomeguardian.logger.workers.WeatherWorker
import com.mshomeguardian.logger.workers.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages data synchronization across different triggers
 * Updated to require authentication for all operations
 */
object DataSyncManager {
    private const val TAG = "DataSyncManager"

    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Start all necessary services and initial sync
     * Requires user to be authenticated
     */
    fun initialize(context: Context, checkPermissions: Boolean = true) {
        Log.d(TAG, "Initializing DataSyncManager")

        // Check authentication first
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, cannot initialize services")
            return
        }

        Log.d(TAG, "User authenticated, proceeding with initialization")

        try {
            // Schedule periodic workers
            WorkerScheduler.schedule(context)

            // Start location monitoring service
            startLocationService(context)

            // Run an initial sync
            syncAll(context)

            Log.d(TAG, "DataSyncManager initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during DataSyncManager initialization", e)
        }
    }

    /**
     * Start the location monitoring service
     * Only starts if user is authenticated
     */
    private fun startLocationService(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "Cannot start location service - user not authenticated")
            return
        }

        try {
            Log.d(TAG, "Starting location monitoring service")

            // Start location monitoring service
            val locationIntent = Intent(context, LocationMonitoringService::class.java)

            // Use startForegroundService for Android 8.0+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(locationIntent)
            } else {
                context.startService(locationIntent)
            }

            Log.d(TAG, "Location service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location service", e)
        }
    }

    /**
     * Force sync of all data types immediately
     * Requires authentication
     */
    fun syncAll(context: Context) {
        // Check authentication first
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, cannot sync data")
            return
        }

        Log.d(TAG, "Starting manual sync of all data")

        try {
            val workManager = WorkManager.getInstance(context)

            // Run call log worker
            workManager.enqueue(OneTimeWorkRequestBuilder<CallLogWorker>().build())

            // Run message worker
            workManager.enqueue(OneTimeWorkRequestBuilder<MessageWorker>().build())

            // Run contacts worker
            workManager.enqueue(OneTimeWorkRequestBuilder<ContactsWorker>().build())

            // Run device info worker
            workManager.enqueue(OneTimeWorkRequestBuilder<DeviceInfoWorker>().build())

            // Run weather worker for widget
            workManager.enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())

            Log.d(TAG, "Sync workers enqueued successfully")

            // Update widgets
            updateWidgets(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error during manual sync", e)
        }
    }

    /**
     * Update the Home Guardian widget
     */
    private fun updateWidgets(context: Context) {
        try {
            // Send broadcast to update widgets
            val intent = Intent("com.mshomeguardian.logger.widget.ACTION_UPDATE")
            context.sendBroadcast(intent)
            Log.d(TAG, "Widget update broadcast sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widgets", e)
        }
    }

    /**
     * Check if any data type needs syncing based on thresholds
     * Only performs checks if user is authenticated
     */
    fun checkTriggers(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, skipping trigger checks")
            return
        }

        scope.launch {
            try {
                var shouldSync = false

                // Check if calls reached threshold
                if (withContext(Dispatchers.IO) { CallLogWorker.shouldSync(context) }) {
                    Log.d(TAG, "Call log threshold reached, triggering sync")
                    withContext(Dispatchers.Main) {
                        WorkManager.getInstance(context)
                            .enqueue(OneTimeWorkRequestBuilder<CallLogWorker>().build())
                    }
                    shouldSync = true
                }

                // Check if messages reached threshold
                if (withContext(Dispatchers.IO) { MessageWorker.shouldSync(context) }) {
                    Log.d(TAG, "Message threshold reached, triggering sync")
                    withContext(Dispatchers.Main) {
                        WorkManager.getInstance(context)
                            .enqueue(OneTimeWorkRequestBuilder<MessageWorker>().build())
                    }
                    shouldSync = true
                }

                // Run device info worker if any other sync occurred
                if (shouldSync) {
                    withContext(Dispatchers.Main) {
                        WorkManager.getInstance(context)
                            .enqueue(OneTimeWorkRequestBuilder<DeviceInfoWorker>().build())

                        // Update widget
                        updateWidgets(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkTriggers", e)
            }
        }
    }

    /**
     * Should be called when a new call is detected
     * Only syncs if user is authenticated
     */
    fun onCallDetected(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, skipping call detection sync")
            return
        }

        Log.d(TAG, "New call detected, triggering immediate sync")

        // Always sync on new call detection, don't check threshold
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    WorkManager.getInstance(context)
                        .enqueue(OneTimeWorkRequestBuilder<CallLogWorker>().build())

                    // Update widget
                    updateWidgets(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCallDetected", e)
            }
        }
    }

    /**
     * Should be called when a new message is detected
     * Only syncs if user is authenticated
     */
    fun onMessageDetected(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, skipping message detection sync")
            return
        }

        Log.d(TAG, "New message detected, triggering immediate sync")

        // Always sync on new message detection, don't check threshold
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    WorkManager.getInstance(context)
                        .enqueue(OneTimeWorkRequestBuilder<MessageWorker>().build())

                    // Update widget
                    updateWidgets(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onMessageDetected", e)
            }
        }
    }

    /**
     * Start or stop the recording service
     * Authentication is checked but recording can work offline if already authenticated
     */
    fun toggleRecordingService(context: Context, start: Boolean) {
        try {
            if (start) {
                // Check authentication for starting recording
                if (!AuthManager.isSignedIn()) {
                    Log.w(TAG, "User not authenticated, cannot start recording service")
                    return
                }

                Log.d(TAG, "Starting recording service")
                val intent = Intent(context, AudioRecordingService::class.java)
                intent.action = AudioRecordingService.ACTION_START_RECORDING

                // Use startForegroundService for Android 8.0+ (API 26+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                // Stopping recording doesn't require authentication check
                Log.d(TAG, "Stopping recording service")
                val intent = Intent(context, AudioRecordingService::class.java)
                intent.action = AudioRecordingService.ACTION_STOP_RECORDING
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling recording service", e)
        }
    }

    /**
     * Check if recording service is running
     */
    fun isRecordingServiceRunning(): Boolean {
        return AudioRecordingService.isRunning()
    }

    /**
     * Stop all services (called when user signs out)
     */
    fun stopAllServices(context: Context) {
        Log.d(TAG, "Stopping all services due to authentication state change")

        try {
            // Stop recording service
            toggleRecordingService(context, false)

            // Cancel all scheduled workers
            WorkerScheduler.cancelAllWork(context)

            // Stop location service
            val locationIntent = Intent(context, LocationMonitoringService::class.java)
            context.stopService(locationIntent)

            Log.d(TAG, "All services stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping services", e)
        }
    }

    /**
     * Restart all services (called when user signs in)
     */
    fun restartAllServices(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "Cannot restart services - user not authenticated")
            return
        }

        Log.d(TAG, "Restarting all services after authentication")

        try {
            // Reinitialize everything
            initialize(context)

            Log.d(TAG, "All services restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting services", e)
        }
    }

    /**
     * Check authentication status for external callers
     */
    fun isAuthenticated(): Boolean {
        return AuthManager.isSignedIn()
    }

    /**
     * Get current user ID for logging/debugging
     */
    fun getCurrentUserId(): String? {
        return AuthManager.getCurrentUserId()
    }

    /**
     * Perform an operation only if authenticated
     */
    fun withAuthentication(operation: String, action: () -> Unit) {
        if (AuthManager.isSignedIn()) {
            Log.d(TAG, "Performing authenticated operation: $operation")
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Error in authenticated operation '$operation'", e)
            }
        } else {
            Log.w(TAG, "Skipping operation '$operation' - user not authenticated")
        }
    }

    /**
     * Force authentication check and restart services if needed
     */
    fun verifyAndRestartServices(context: Context) {
        Log.d(TAG, "Verifying authentication and service state")

        if (AuthManager.isSignedIn()) {
            val currentUserId = AuthManager.getCurrentUserId()
            Log.d(TAG, "User authenticated: $currentUserId")

            // Check if services need to be restarted
            restartAllServices(context)
        } else {
            Log.w(TAG, "User not authenticated, stopping all services")
            stopAllServices(context)
        }
    }

    /**
     * Get sync status for UI display
     */
    fun getSyncStatus(): Map<String, Any> {
        return mapOf(
            "authenticated" to AuthManager.isSignedIn(),
            "user_id" to (AuthManager.getCurrentUserId() ?: "none"),
            "recording_service_running" to isRecordingServiceRunning(),
            "last_check" to System.currentTimeMillis()
        )
    }
}