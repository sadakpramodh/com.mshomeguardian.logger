package com.mshomeguardian.logger.workers

import android.Manifest
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppUsageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "AppUsageWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.PACKAGE_USAGE_STATS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                OptimizedLogger.w(TAG, "Missing PACKAGE_USAGE_STATS permission")
                return@withContext Result.failure()
            }

            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            val usageManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000
            val stats = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val foregroundCounts = collectForegroundCounts(usageManager, start, end)

            stats.forEach { stat ->
                if (stat.totalTimeInForeground > 0) {
                    val map = HashMap<String, Any>()
                    map["packageName"] = stat.packageName
                    map["lastTimeUsed"] = stat.lastTimeUsed
                    map["totalForeground"] = stat.totalTimeInForeground
                    map["foregroundSessionCount"] = foregroundCounts[stat.packageName] ?: 0
                    map["isRecent"] = stat.lastTimeUsed >= end - 60 * 60 * 1000
                    map["timestamp"] = end
                    map["deviceId"] = deviceId
                    FirebaseServiceHelper.uploadAppUsage(userEmail, deviceId, map)
                }
            }
            OptimizedLogger.d(TAG, "Uploaded usage for ${stats.size} apps")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting app usage", e)
            Result.retry()
        }
    }

    private fun collectForegroundCounts(
        usageManager: UsageStatsManager,
        start: Long,
        end: Long
    ): Map<String, Int> {
        val counts = HashMap<String, Int>()
        try {
            val events = usageManager.queryEvents(start, end)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    val packageName = event.packageName ?: continue
                    counts[packageName] = (counts[packageName] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting usage events", e)
        }
        return counts
    }
}
