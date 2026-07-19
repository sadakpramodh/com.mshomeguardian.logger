package com.mshomeguardian.logger.workers

import android.Manifest
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.NetworkUsageEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkUsageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "NetworkUsageWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
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

            val statsManager = applicationContext.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val pm = applicationContext.packageManager
            val end = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000 // last 24 hours

            val usageMap = HashMap<String, Pair<Long, Long>>()

            fun collect(type: Int, subscriberId: String?) {
                try {
                    val stats = statsManager.querySummary(type, subscriberId, start, end)
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        val packages = pm.getPackagesForUid(bucket.uid) ?: continue
                        packages.forEach { pkg ->
                            val existing = usageMap[pkg] ?: (0L to 0L)
                            usageMap[pkg] = (existing.first + bucket.rxBytes) to (existing.second + bucket.txBytes)
                        }
                    }
                    stats.close()
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Error collecting stats for type $type", e)
                }
            }

            collect(ConnectivityManager.TYPE_WIFI, null)

            val telephony = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subscriberId = if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) telephony.subscriberId else null
            collect(ConnectivityManager.TYPE_MOBILE, subscriberId)

            val entities = usageMap.filter { it.value.first > 0 || it.value.second > 0 }.map { (pkg, pair) ->
                NetworkUsageEntity(
                    packageName = pkg,
                    rxBytes = pair.first,
                    txBytes = pair.second,
                    timestamp = end,
                    deviceId = deviceId
                )
            }
            db.networkUsageDao().insertAll(entities)

            val pending = db.networkUsageDao().getNotUploaded()
            val uploadedIds = mutableListOf<Long>()
            pending.forEach { usage ->
                val dataMap = mapOf(
                    "packageName" to usage.packageName,
                    "rxBytes" to usage.rxBytes,
                    "txBytes" to usage.txBytes,
                    "timestamp" to usage.timestamp,
                    "timezone" to usage.timezone,
                    "deviceId" to usage.deviceId
                )
                val success = FirebaseServiceHelper.uploadNetworkUsage(userEmail, deviceId, dataMap)
                if (success) {
                    uploadedIds.add(usage.id)
                }
            }
            if (uploadedIds.isNotEmpty()) {
                db.networkUsageDao().markAsUploaded(uploadedIds, System.currentTimeMillis())
            }

            OptimizedLogger.d(TAG, "Uploaded network usage for ${uploadedIds.size} apps")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting network usage", e)
            Result.retry()
        }
    }
}
