package com.mshomeguardian.logger.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatteryStatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "BatteryStatusWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            val intent = applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
            val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true

            val pct = if (scale > 0) level * 100 / scale else 0
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val chargingSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                else -> "UNKNOWN"
            }
            val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryPropertyCapacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                -1
            }
            val chargeCounter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            } else {
                -1
            }
            val currentNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } else {
                -1
            }

            val data = hashMapOf<String, Any>(
                "level" to pct,
                "isCharging" to charging,
                "chargingSource" to chargingSource,
                "health" to health,
                "temperature" to temperature,
                "voltage" to voltage,
                "present" to present,
                "capacityPercent" to batteryPropertyCapacity,
                "chargeCounter" to chargeCounter,
                "currentNow" to currentNow,
                "powerSaveMode" to powerManager.isPowerSaveMode,
                "timestamp" to System.currentTimeMillis(),
                "deviceId" to deviceId
            )

            FirebaseServiceHelper.uploadBatteryStatus(userEmail, deviceId, data)
            OptimizedLogger.d(TAG, "Battery status uploaded")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting battery status", e)
            Result.retry()
        }
    }
}
