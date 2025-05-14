package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Scheduler that sets up all periodic work tasks for the app
 */
object WorkerScheduler {
    private const val TAG = "WorkerScheduler"

    // Work tags
    private const val LOCATION_WORK_NAME = "LocationWork"
    private const val CALL_LOG_WORK_NAME = "CallLogWork"
    private const val MESSAGE_WORK_NAME = "MessageWork"
    private const val DEVICE_INFO_WORK_NAME = "DeviceInfoWork"

    /**
     * Schedule all workers
     */
    fun schedule(context: Context) {
        try {
            Log.d(TAG, "Scheduling all workers")

            // Cancel any existing work first
            WorkManager.getInstance(context).cancelAllWork()

            // Schedule all workers
            scheduleLocationWork(context)
            scheduleCallLogWork(context)
            scheduleMessageWork(context)
            scheduleDeviceInfoWork(context)

            Log.d(TAG, "All workers scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling workers", e)
        }
    }

    /**
     * Schedule location tracking worker
     */
    fun scheduleLocationWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val locationWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                LOCATION_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,  // Changed from UPDATE to REPLACE
                locationWorkRequest
            )

            Log.d(TAG, "Location worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling location worker", e)
        }
    }

    /**
     * Schedule call log sync worker
     */
    fun scheduleCallLogWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val callLogWorkRequest = PeriodicWorkRequestBuilder<CallLogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CALL_LOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,  // Changed from UPDATE to REPLACE
                callLogWorkRequest
            )

            Log.d(TAG, "Call log worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling call log worker", e)
        }
    }

    /**
     * Schedule message sync worker
     */
    fun scheduleMessageWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val messageWorkRequest = PeriodicWorkRequestBuilder<MessageWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MESSAGE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,  // Changed from UPDATE to REPLACE
                messageWorkRequest
            )

            Log.d(TAG, "Message worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling message worker", e)
        }
    }

    /**
     * Schedule device info update worker
     * This runs less frequently as device info changes less often
     */
    fun scheduleDeviceInfoWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val deviceInfoWorkRequest = PeriodicWorkRequestBuilder<DeviceInfoWorker>(
                6, TimeUnit.HOURS  // Run every 6 hours instead of 24
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DEVICE_INFO_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,  // Changed from UPDATE to REPLACE
                deviceInfoWorkRequest
            )

            Log.d(TAG, "Device info worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling device info worker", e)
        }
    }

    /**
     * Run all workers immediately (for testing or initial sync)
     */
    fun runAllWorkersOnce(context: Context) {
        try {
            Log.d(TAG, "Running all workers once")

            val workManager = WorkManager.getInstance(context)

            // Run location worker
            val locationWorkerRequest = OneTimeWorkRequestBuilder<LocationWorker>().build()
            workManager.enqueue(locationWorkerRequest)
            Log.d(TAG, "Enqueued one-time location worker: ${locationWorkerRequest.id}")

            // Run call log worker
            val callLogWorkerRequest = OneTimeWorkRequestBuilder<CallLogWorker>().build()
            workManager.enqueue(callLogWorkerRequest)
            Log.d(TAG, "Enqueued one-time call log worker: ${callLogWorkerRequest.id}")

            // Run message worker
            val messageWorkerRequest = OneTimeWorkRequestBuilder<MessageWorker>().build()
            workManager.enqueue(messageWorkerRequest)
            Log.d(TAG, "Enqueued one-time message worker: ${messageWorkerRequest.id}")

            // Run device info worker
            val deviceInfoWorkerRequest = OneTimeWorkRequestBuilder<DeviceInfoWorker>().build()
            workManager.enqueue(deviceInfoWorkerRequest)
            Log.d(TAG, "Enqueued one-time device info worker: ${deviceInfoWorkerRequest.id}")

            Log.d(TAG, "All one-time workers enqueued successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error running one-time workers", e)
        }
    }

    /**
     * Cancel all scheduled workers
     */
    fun cancelAllWork(context: Context) {
        try {
            WorkManager.getInstance(context).cancelAllWork()
            Log.d(TAG, "All work cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling work", e)
        }
    }
}