package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
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
    private const val CONTACTS_WORK_NAME = "ContactsWork"
    private const val DEVICE_INFO_WORK_NAME = "DeviceInfoWork"
    private const val WEATHER_WORK_NAME = "WeatherWork"
    private const val RECORDING_CLEANUP_WORK_NAME = "RecordingCleanupWork"

    /**
     * Schedule all workers
     */
    fun schedule(context: Context) {
        try {
            Log.d(TAG, "Scheduling all workers")

            // Schedule individual workers
            scheduleLocationWork(context)
            scheduleCallLogWork(context)
            scheduleMessageWork(context)
            scheduleContactsWork(context)
            scheduleDeviceInfoWork(context)
            scheduleWeatherWork(context)
            scheduleRecordingCleanupWork(context)

            Log.d(TAG, "All workers scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling workers", e)
        }
    }

    /**
     * Schedule location tracking worker
     */
    private fun scheduleLocationWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val locationWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                LOCATION_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
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
    private fun scheduleCallLogWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val callLogWorkRequest = PeriodicWorkRequestBuilder<CallLogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CALL_LOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
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
    private fun scheduleMessageWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val messageWorkRequest = PeriodicWorkRequestBuilder<MessageWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MESSAGE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                messageWorkRequest
            )

            Log.d(TAG, "Message worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling message worker", e)
        }
    }

    /**
     * Schedule contacts sync worker
     */
    private fun scheduleContactsWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val contactsWorkRequest = PeriodicWorkRequestBuilder<ContactsWorker>(
                30, TimeUnit.MINUTES // Less frequent than other sync jobs
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CONTACTS_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                contactsWorkRequest
            )

            Log.d(TAG, "Contacts worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling contacts worker", e)
        }
    }

    /**
     * Schedule device info update worker
     */
    private fun scheduleDeviceInfoWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val deviceInfoWorkRequest = PeriodicWorkRequestBuilder<DeviceInfoWorker>(
                6, TimeUnit.HOURS  // Every 6 hours
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DEVICE_INFO_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                deviceInfoWorkRequest
            )

            Log.d(TAG, "Device info worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling device info worker", e)
        }
    }

    /**
     * Schedule weather updates for widget
     */
    private fun scheduleWeatherWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherWorker>(
                30, TimeUnit.MINUTES  // Update weather every 30 minutes
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WEATHER_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                weatherWorkRequest
            )

            Log.d(TAG, "Weather worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling weather worker", e)
        }
    }

    /**
     * Schedule cleanup of old recordings
     */
    private fun scheduleRecordingCleanupWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val cleanupWorkRequest = PeriodicWorkRequestBuilder<RecordingCleanupWorker>(
                1, TimeUnit.DAYS  // Daily cleanup
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                RECORDING_CLEANUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                cleanupWorkRequest
            )

            Log.d(TAG, "Recording cleanup worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling recording cleanup worker", e)
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

            // Run contacts worker
            val contactsWorkerRequest = OneTimeWorkRequestBuilder<ContactsWorker>().build()
            workManager.enqueue(contactsWorkerRequest)
            Log.d(TAG, "Enqueued one-time contacts worker: ${contactsWorkerRequest.id}")

            // Run device info worker
            val deviceInfoWorkerRequest = OneTimeWorkRequestBuilder<DeviceInfoWorker>().build()
            workManager.enqueue(deviceInfoWorkerRequest)
            Log.d(TAG, "Enqueued one-time device info worker: ${deviceInfoWorkerRequest.id}")

            // Run weather worker
            val weatherWorkerRequest = OneTimeWorkRequestBuilder<WeatherWorker>().build()
            workManager.enqueue(weatherWorkerRequest)
            Log.d(TAG, "Enqueued one-time weather worker: ${weatherWorkerRequest.id}")

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