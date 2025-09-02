package com.mshomeguardian.logger.workers

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.services.DeviceAdminService
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that checks Firebase for pending device admin actions
 * like locking or wiping the device.
 */
class DeviceAdminWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DeviceAdminWorker"
    }

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                Log.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            val actions = FirebaseServiceHelper.fetchPendingAdminActions(userEmail, deviceId)
            for (action in actions) {
                try {
                    val serviceIntent = Intent(applicationContext, DeviceAdminService::class.java).apply {
                        this.action = when (action.action.lowercase()) {
                            "lock", "lock_device" -> DeviceAdminService.ACTION_LOCK
                            "wipe", "wipe_device" -> DeviceAdminService.ACTION_WIPE
                            else -> null
                        }
                    }

                    if (serviceIntent.action != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            applicationContext.startForegroundService(serviceIntent)
                        } else {
                            applicationContext.startService(serviceIntent)
                        }
                        FirebaseServiceHelper.markAdminActionExecuted(userEmail, deviceId, action.id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute admin action ${action.action}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing admin actions", e)
            Result.retry()
        }
    }
}
