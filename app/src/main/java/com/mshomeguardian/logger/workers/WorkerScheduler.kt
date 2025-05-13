package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private const val CONTACTS_WORK_NAME = "ContactsWork"
    private const val PHONE_STATE_WORK_NAME = "PhoneStateWork"
    private const val WEATHER_WORK_NAME = "WeatherWork"

    /**
     * Schedule all workers
     */
    fun schedule(context: Context) {
        try {
            Log.d(TAG, "Scheduling all workers")
            scheduleLocationWork(context)
            scheduleCallLogWork(context)
            scheduleMessageWork(context)
            scheduleDeviceInfoWork(context)
            scheduleContactsWork(context)
            schedulePhoneStateWork(context)
            scheduleWeatherWork(context)
            Log.d(TAG, "All workers scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling workers", e)
        }
    }

    /**
     * Check if workers are scheduled and reschedule if needed
     */
    suspend fun ensureWorkersAreScheduled(context: Context) = withContext(Dispatchers.IO) {
        try {
            val workManager = WorkManager.getInstance(context)

            // Check each worker
            val locationWorkInfo = workManager.getWorkInfosForUniqueWork(LOCATION_WORK_NAME).get()
            val callLogWorkInfo = workManager.getWorkInfosForUniqueWork(CALL_LOG_WORK_NAME).get()
            val messageWorkInfo = workManager.getWorkInfosForUniqueWork(MESSAGE_WORK_NAME).get()
            val deviceInfoWorkInfo = workManager.getWorkInfosForUniqueWork(DEVICE_INFO_WORK_NAME).get()
            val contactsWorkInfo = workManager.getWorkInfosForUniqueWork(CONTACTS_WORK_NAME).get()
            val phoneStateWorkInfo = workManager.getWorkInfosForUniqueWork(PHONE_STATE_WORK_NAME).get()
            val weatherWorkInfo = workManager.getWorkInfosForUniqueWork(WEATHER_WORK_NAME).get()

            // If any worker is not scheduled or not running, reschedule it
            if (locationWorkInfo.isEmpty() || locationWorkInfo.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }) {
                scheduleLocationWork(context)
            }

            if (callLogWorkInfo.isEmpty() || callLogWorkInfo.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }) {
                scheduleCallLogWork(context)
            }

            if (messageWorkInfo.isEmpty() || messageWorkInfo.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }) {
                scheduleMessageWork(context)
            }

            if (deviceInfoWorkInfo.isEmpty() || deviceInfoWorkInfo.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }) {
                scheduleDeviceInfoWork(context)
            }

            if (contactsWorkInfo.isEmpty() || contactsWorkInfo.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }) {
                scheduleContactsWork(context)
            }

            if (phoneStateWorkInfo.isEmpty() || phoneStateWorkInfo.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }) {
                schedulePhoneStateWork(context)
            }

            if (weatherWorkInfo.isEmpty() || weatherWorkInfo.all { it.state == WorkInfo.State.CANCELLED || it.state == WorkInfo.State.FAILED }) {
                scheduleWeatherWork(context)
            }

            Log.d(TAG, "Verified all workers are scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking worker status", e)
            // If there's an error, reschedule all workers to be safe
            schedule(context)
        }
    }

    /**
     * Schedule location tracking worker
     */
    fun scheduleLocationWork(context: Context) {
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
            ExistingPeriodicWorkPolicy.KEEP,
            locationWorkRequest
        )

        Log.d(TAG, "Location worker scheduled")
    }

    /**
     * Schedule call log sync worker
     */
    fun scheduleCallLogWork(context: Context) {
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
            ExistingPeriodicWorkPolicy.KEEP,
            callLogWorkRequest
        )

        Log.d(TAG, "Call log worker scheduled")
    }

    /**
     * Schedule message sync worker
     */
    fun scheduleMessageWork(context: Context) {
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
            ExistingPeriodicWorkPolicy.KEEP,
            messageWorkRequest
        )

        Log.d(TAG, "Message worker scheduled")
    }

    /**
     * Schedule device info update worker
     * This runs less frequently as device info changes less often
     */
    fun scheduleDeviceInfoWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val deviceInfoWorkRequest = PeriodicWorkRequestBuilder<DeviceInfoWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DEVICE_INFO_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            deviceInfoWorkRequest
        )

        Log.d(TAG, "Device info worker scheduled")
    }

    /**
     * Schedule contacts sync worker
     */
    fun scheduleContactsWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val contactsWorkRequest = PeriodicWorkRequestBuilder<ContactsWorker>(
            24, TimeUnit.HOURS // Contacts change less frequently
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CONTACTS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            contactsWorkRequest
        )

        Log.d(TAG, "Contacts worker scheduled")
    }

    /**
     * Schedule phone state monitoring worker
     */
    fun schedulePhoneStateWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val phoneStateWorkRequest = PeriodicWorkRequestBuilder<PhoneStateWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PHONE_STATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            phoneStateWorkRequest
        )

        Log.d(TAG, "Phone state worker scheduled")
    }

    /**
     * Schedule weather update worker
     */
    fun scheduleWeatherWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEATHER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            weatherWorkRequest
        )

        Log.d(TAG, "Weather worker scheduled")
    }

    /**
     * Run all workers immediately (for testing or initial sync)
     */
    fun runAllWorkersOnce(context: Context) {
        try {
            WorkManager.getInstance(context).run {
                // Run location worker
                enqueue(OneTimeWorkRequestBuilder<LocationWorker>().build())

                // Run call log worker
                enqueue(OneTimeWorkRequestBuilder<CallLogWorker>().build())

                // Run message worker
                enqueue(OneTimeWorkRequestBuilder<MessageWorker>().build())

                // Run device info worker
                enqueue(OneTimeWorkRequestBuilder<DeviceInfoWorker>().build())

                // Run contacts worker
                enqueue(OneTimeWorkRequestBuilder<ContactsWorker>().build())

                // Run phone state worker
                enqueue(OneTimeWorkRequestBuilder<PhoneStateWorker>().build())

                // Run weather worker
                enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
            }
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