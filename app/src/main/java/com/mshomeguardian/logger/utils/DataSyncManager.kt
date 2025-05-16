package com.mshomeguardian.logger.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mshomeguardian.logger.services.LocationMonitoringService
import com.mshomeguardian.logger.services.RecordingService
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
 */
object DataSyncManager {
    private const val TAG = "DataSyncManager"

    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Start all necessary services and initial sync
     */
    fun initialize(context: Context, checkPermissions: Boolean = true) {
        Log.d(TAG, "Initializing DataSyncManager")

        // Schedule periodic workers
        WorkerScheduler.schedule(context)

        // Start location monitoring service
        startLocationService(context)

        // Run an initial sync
        syncAll(context)
    }

    /**
     * Start the location monitoring service
     */
    private fun startLocationService(context: Context) {
        try {
            Log.d(TAG, "Starting location monitoring service")

            // Start location monitoring service
            val locationIntent = Intent(context, LocationMonitoringService::class.java)
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
     */
    fun syncAll(context: Context) {
        Log.d(TAG, "Starting manual sync of all data")

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

        Log.d(TAG, "Sync workers enqueued")

        // Update widgets
        updateWidgets(context)
    }

    /**
     * Update the Home Guardian widget
     */
    private fun updateWidgets(context: Context) {
        // Send broadcast to update widgets
        val intent = Intent("com.mshomeguardian.logger.widget.ACTION_UPDATE")
        context.sendBroadcast(intent)
    }

    /**
     * Check if any data type needs syncing based on thresholds
     */
    fun checkTriggers(context: Context) {
        scope.launch {
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
        }
    }

    /**
     * Should be called when a new call is detected
     */
    fun onCallDetected(context: Context) {
        Log.d(TAG, "New call detected, triggering immediate sync")

        // Always sync on new call detection, don't check threshold
        scope.launch {
            withContext(Dispatchers.Main) {
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<CallLogWorker>().build())

                // Update widget
                updateWidgets(context)
            }
        }
    }

    /**
     * Should be called when a new message is detected
     */
    fun onMessageDetected(context: Context) {
        Log.d(TAG, "New message detected, triggering immediate sync")

        // Always sync on new message detection, don't check threshold
        scope.launch {
            withContext(Dispatchers.Main) {
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<MessageWorker>().build())

                // Update widget
                updateWidgets(context)
            }
        }
    }

    /**
     * Start or stop the recording service
     */
    fun toggleRecordingService(context: Context, start: Boolean) {
        try {
            if (start) {
                Log.d(TAG, "Starting recording service")
                val intent = Intent(context, RecordingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                Log.d(TAG, "Stopping recording service")
                context.stopService(Intent(context, RecordingService::class.java))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling recording service", e)
        }
    }

    /**
     * Check if recording service is running
     */
    fun isRecordingServiceRunning(): Boolean {
        return RecordingService.isRunning()
    }
}