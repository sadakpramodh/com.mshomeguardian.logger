package com.mshomeguardian.logger.workers

import android.Manifest
import android.app.usage.UsageStatsManager
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

            stats.forEach { stat ->
                if (stat.totalTimeInForeground > 0) {
                    val map = HashMap<String, Any>()
                    map["packageName"] = stat.packageName
                    map["lastTimeUsed"] = stat.lastTimeUsed
                    map["totalForeground"] = stat.totalTimeInForeground
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
}
