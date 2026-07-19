package com.mshomeguardian.logger.workers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.DigitalWellbeingEntity
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DigitalWellbeingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DigitalWellbeingWorker"
    }

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userEmail = AuthManager.getCurrentUser()?.email
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            if (!hasUsageStatsAccess(applicationContext)) {
                OptimizedLogger.w(TAG, "Usage access not granted for digital wellbeing")
                return@withContext Result.success()
            }

            val usageManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val intervalEnd = System.currentTimeMillis()
            val intervalStart = intervalEnd - (24L * 60L * 60L * 1000L)

            val usageStats = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                intervalStart,
                intervalEnd
            )

            val appUsage = usageStats
                .filter { it.totalTimeInForeground > 0L }
                .associate { it.packageName to it.totalTimeInForeground }

            val totalScreenTimeMs = appUsage.values.sum()
            val topApp = appUsage.maxByOrNull { it.value }

            var appLaunchCount = 0
            var unlockCount = 0
            var notificationInterruptions = 0
            val uniqueAppsOpened = linkedSetOf<String>()

            val events = usageManager.queryEvents(intervalStart, intervalEnd)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        appLaunchCount++
                        event.packageName?.let { pkg -> uniqueAppsOpened.add(pkg) }
                    }

                    UsageEvents.Event.KEYGUARD_HIDDEN -> unlockCount++

                    // NOTIFICATION_SEEN = 10, added in API 26
                    10 -> notificationInterruptions++
                }
            }

            val snapshot = DigitalWellbeingEntity(
                snapshotId = "${intervalEnd}_$deviceId",
                intervalStart = intervalStart,
                intervalEnd = intervalEnd,
                totalScreenTimeMs = totalScreenTimeMs,
                appLaunchCount = appLaunchCount,
                unlockCount = unlockCount,
                notificationInterruptions = notificationInterruptions,
                uniqueAppsUsed = uniqueAppsOpened.size,
                topAppPackage = topApp?.key,
                topAppScreenTimeMs = topApp?.value ?: 0L,
                deviceId = deviceId
            )

            db.digitalWellbeingDao().insert(snapshot)

            val pending = db.digitalWellbeingDao().getNotUploaded()
            val uploadedIds = mutableListOf<Long>()
            pending.forEach { row ->
                val payload = mapOf(
                    "snapshotId" to row.snapshotId,
                    "intervalStart" to row.intervalStart,
                    "intervalEnd" to row.intervalEnd,
                    "totalScreenTimeMs" to row.totalScreenTimeMs,
                    "appLaunchCount" to row.appLaunchCount,
                    "unlockCount" to row.unlockCount,
                    "notificationInterruptions" to row.notificationInterruptions,
                    "uniqueAppsUsed" to row.uniqueAppsUsed,
                    "topAppPackage" to (row.topAppPackage ?: ""),
                    "topAppScreenTimeMs" to row.topAppScreenTimeMs,
                    "timezone" to row.timezone,
                    "deviceId" to row.deviceId
                )
                if (FirebaseServiceHelper.uploadDigitalWellbeing(userEmail, deviceId, payload)) {
                    uploadedIds.add(row.id)
                }
            }

            if (uploadedIds.isNotEmpty()) {
                db.digitalWellbeingDao().markAsUploaded(uploadedIds, System.currentTimeMillis())
            }

            OptimizedLogger.d(TAG, "Digital wellbeing synced. Uploaded=${uploadedIds.size}")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error syncing digital wellbeing", e)
            Result.retry()
        }
    }

    private fun hasUsageStatsAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
