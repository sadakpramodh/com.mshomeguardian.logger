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
import com.mshomeguardian.logger.workers.InstalledAppsWorker
import com.mshomeguardian.logger.workers.AppUsageWorker
import com.mshomeguardian.logger.workers.NetworkUsageWorker
import com.mshomeguardian.logger.workers.BatteryStatusWorker
import com.mshomeguardian.logger.workers.SystemMetricsWorker
import com.mshomeguardian.logger.workers.SensorDataWorker
import com.mshomeguardian.logger.workers.DeviceAdminWorker
import com.mshomeguardian.logger.workers.MediaInventoryWorker
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
    private const val INSTALLED_APPS_WORK_NAME = "OptimizedInstalledAppsWork"
    private const val APP_USAGE_WORK_NAME = "OptimizedAppUsageWork"
    private const val NETWORK_USAGE_WORK_NAME = "OptimizedNetworkUsageWork"
    private const val BATTERY_STATUS_WORK_NAME = "OptimizedBatteryStatusWork"
    private const val SYSTEM_METRICS_WORK_NAME = "OptimizedSystemMetricsWork"
    private const val SENSOR_DATA_WORK_NAME = "OptimizedSensorDataWork"
    private const val DEVICE_ADMIN_WORK_NAME = "OptimizedDeviceAdminWork"
    private const val MEDIA_INVENTORY_WORK_NAME = "OptimizedMediaInventoryWork"
    private const val HEALTH_VITALS_WORK_NAME = "OptimizedHealthVitalsWork"
    private const val DIGITAL_WELLBEING_WORK_NAME = "OptimizedDigitalWellbeingWork"

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
            scheduleInstalledAppsWork(context, 720L)
            scheduleAppUsageWork(context, 60L)
            scheduleNetworkUsageWork(context, 60L)
            scheduleBatteryStatusWork(context, 30L)
            scheduleSystemMetricsWork(context, 60L)
            scheduleSensorDataWork(context, 30L)
            scheduleMediaInventoryWork(context, 720L)
            scheduleDeviceAdminWork(context, 15L)
            scheduleHealthVitalsWork(context, 60L)
            scheduleDigitalWellbeingWork(context, 60L)
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

    /**
     * Create constraints for data-collection workers that must run regardless of battery level.
     * Used for BatteryStatusWorker and SensorDataWorker where low-battery readings are valuable.
     */
    private fun createDataCollectionConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .setRequiresDeviceIdle(false)
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

    private fun scheduleInstalledAppsWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val request = PeriodicWorkRequestBuilder<InstalledAppsWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                INSTALLED_APPS_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Installed apps worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling installed apps worker", e)
        }
    }

    private fun scheduleAppUsageWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val request = PeriodicWorkRequestBuilder<AppUsageWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                APP_USAGE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "App usage worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling app usage worker", e)
        }
    }

    private fun scheduleNetworkUsageWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val request = PeriodicWorkRequestBuilder<NetworkUsageWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NETWORK_USAGE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Network usage worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling network usage worker", e)
        }
    }

    private fun scheduleBatteryStatusWork(context: Context, intervalMinutes: Long) {
        try {
            // Use data-collection constraints — must run even when battery is low
            val constraints = createDataCollectionConstraints()

            val request = PeriodicWorkRequestBuilder<BatteryStatusWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BATTERY_STATUS_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Battery status worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling battery status worker", e)
        }
    }

    private fun scheduleSystemMetricsWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val request = PeriodicWorkRequestBuilder<SystemMetricsWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYSTEM_METRICS_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "System metrics worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling system metrics worker", e)
        }
    }

    private fun scheduleSensorDataWork(context: Context, intervalMinutes: Long) {
        try {
            // Use data-collection constraints — sensors should be readable at any battery level
            val constraints = createDataCollectionConstraints()

            val request = PeriodicWorkRequestBuilder<SensorDataWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SENSOR_DATA_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Sensor data worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling sensor data worker", e)
        }
    }

    private fun scheduleMediaInventoryWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val request = PeriodicWorkRequestBuilder<MediaInventoryWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MEDIA_INVENTORY_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Media inventory worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling media inventory worker", e)
        }
    }

    private fun scheduleHealthVitalsWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()
            val request = PeriodicWorkRequestBuilder<HealthVitalsWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                HEALTH_VITALS_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Health vitals worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling health vitals worker", e)
        }
    }

    private fun scheduleDigitalWellbeingWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()
            val request = PeriodicWorkRequestBuilder<DigitalWellbeingWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DIGITAL_WELLBEING_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Digital wellbeing worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling digital wellbeing worker", e)
        }
    }

    private fun scheduleDeviceAdminWork(context: Context, intervalMinutes: Long) {
        try {
            val constraints = createOptimizedConstraints()

            val request = PeriodicWorkRequestBuilder<DeviceAdminWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DEVICE_ADMIN_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            OptimizedLogger.d(TAG, "Device admin worker scheduled (${intervalMinutes}m interval)")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error scheduling device admin worker", e)
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
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<InstalledAppsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<AppUsageWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<BatteryStatusWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<SystemMetricsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<SensorDataWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<MediaInventoryWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<HealthVitalsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<DigitalWellbeingWorker>().setConstraints(constraints).build()
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
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<InstalledAppsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<AppUsageWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<BatteryStatusWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<SystemMetricsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<SensorDataWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<MediaInventoryWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<HealthVitalsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<DigitalWellbeingWorker>().setConstraints(constraints).build()
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