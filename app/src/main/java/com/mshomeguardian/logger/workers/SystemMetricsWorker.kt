package com.mshomeguardian.logger.workers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.app.KeyguardManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemMetricsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "SystemMetricsWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            val metrics = HashMap<String, Any>()
            metrics["timestamp"] = System.currentTimeMillis()
            metrics["deviceId"] = deviceId

            // Battery extras
            val intent = applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            intent?.let {
                metrics["batteryHealth"] = it.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                metrics["batteryTemperature"] = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                metrics["batteryVoltage"] = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            }

            // Storage metrics
            val internal = StatFs(applicationContext.filesDir.absolutePath)
            metrics["internalTotal"] = internal.blockCountLong * internal.blockSizeLong
            metrics["internalFree"] = internal.availableBlocksLong * internal.blockSizeLong
            applicationContext.getExternalFilesDir(null)?.let {
                val ext = StatFs(it.path)
                metrics["externalTotal"] = ext.blockCountLong * ext.blockSizeLong
                metrics["externalFree"] = ext.availableBlocksLong * ext.blockSizeLong
            }

            // Memory metrics
            val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            metrics["memoryTotal"] = memInfo.totalMem
            metrics["memoryAvailable"] = memInfo.availMem

            // CPU information
            metrics["cpuCores"] = Runtime.getRuntime().availableProcessors()
            metrics["cpuAbi"] = Build.SUPPORTED_ABIS.joinToString()

            // Display metrics
            val dm = applicationContext.resources.displayMetrics
            metrics["screenWidthPx"] = dm.widthPixels
            metrics["screenHeightPx"] = dm.heightPixels
            metrics["density"] = dm.density

            // Security status
            val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            metrics["deviceSecure"] = km.isDeviceSecure

            FirebaseServiceHelper.uploadSystemMetrics(userEmail, deviceId, metrics)
            OptimizedLogger.d(TAG, "System metrics uploaded")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting system metrics", e)
            Result.retry()
        }
    }
}
