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
            metrics["internalUsed"] = metrics["internalTotal"] as Long - metrics["internalFree"] as Long
            metrics["internalUsagePercent"] = if ((metrics["internalTotal"] as Long) > 0) {
                ((metrics["internalUsed"] as Long) * 100.0 / (metrics["internalTotal"] as Long))
            } else 0.0
            applicationContext.getExternalFilesDir(null)?.let {
                val ext = StatFs(it.path)
                metrics["externalTotal"] = ext.blockCountLong * ext.blockSizeLong
                metrics["externalFree"] = ext.availableBlocksLong * ext.blockSizeLong
                metrics["externalUsed"] = metrics["externalTotal"] as Long - metrics["externalFree"] as Long
            }

            // Memory metrics
            val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            metrics["memoryTotal"] = memInfo.totalMem
            metrics["memoryAvailable"] = memInfo.availMem
            metrics["memoryUsed"] = memInfo.totalMem - memInfo.availMem
            metrics["memoryUsagePercent"] = if (memInfo.totalMem > 0) {
                ((memInfo.totalMem - memInfo.availMem) * 100.0 / memInfo.totalMem)
            } else 0.0

            // CPU information
            metrics["cpuCores"] = Runtime.getRuntime().availableProcessors()
            metrics["cpuAbi"] = Build.SUPPORTED_ABIS.joinToString()
            metrics["runtimeMaxMemory"] = Runtime.getRuntime().maxMemory()
            metrics["runtimeFreeMemory"] = Runtime.getRuntime().freeMemory()

            // Display metrics
            val dm = applicationContext.resources.displayMetrics
            metrics["screenWidthPx"] = dm.widthPixels
            metrics["screenHeightPx"] = dm.heightPixels
            metrics["density"] = dm.density
            metrics["xdpi"] = dm.xdpi
            metrics["ydpi"] = dm.ydpi
            metrics["densityDpi"] = dm.densityDpi
            metrics["refreshRate"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                applicationContext.display?.refreshRate ?: 0f
            } else {
                0f
            }

            // Security status
            val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            metrics["deviceSecure"] = km.isDeviceSecure
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            metrics["isInteractive"] = powerManager.isInteractive

            FirebaseServiceHelper.uploadSystemMetrics(userEmail, deviceId, metrics)
            OptimizedLogger.d(TAG, "System metrics uploaded")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting system metrics", e)
            Result.retry()
        }
    }
}
