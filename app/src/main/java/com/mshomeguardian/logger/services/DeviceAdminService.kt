package com.mshomeguardian.logger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background service handling privileged device admin actions like lock, wipe, and password reset.
 */
class DeviceAdminService : Service() {

    override fun onCreate() {
        super.onCreate()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            when (action) {
                ACTION_LOCK -> {
                    dpm.lockNow()
                    logAdminAction("lock")
                }
                ACTION_WIPE -> {
                    dpm.wipeData(0)
                    logAdminAction("wipe")
                }
                ACTION_CHANGE_PASSWORD -> {
                    val newPassword = intent.getStringExtra(EXTRA_NEW_PASSWORD)
                    if (newPassword.isNullOrBlank()) {
                        Log.w(TAG, "Password change action missing password payload")
                    } else {
                        val changed = resetDevicePassword(dpm, newPassword)
                        if (changed) {
                            logAdminAction("change_password")
                        } else {
                            Log.w(TAG, "Password change command failed")
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "Device admin not active; action $action ignored")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun logAdminAction(event: String) {
        val userEmail = FirebaseServiceHelper.getCurrentUserEmail() ?: return
        val deviceId = DeviceIdentifier.getPersistentDeviceId(this)
        val data = hashMapOf<String, Any>(
            "event" to event,
            "timestamp" to System.currentTimeMillis(),
            "deviceId" to deviceId
        )
        CoroutineScope(Dispatchers.IO).launch {
            FirebaseServiceHelper.uploadSystemEvent(userEmail, deviceId, data)
        }
    }

    private fun startInForeground() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "device_admin_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Device Admin Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Home Guardian")
            .setContentText("Device admin service active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            1002,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    @Suppress("DEPRECATION")
    private fun resetDevicePassword(dpm: DevicePolicyManager, password: String): Boolean {
        return try {
            dpm.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Password change rejected by device policy", securityException)
            false
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, "Password change payload is invalid", illegalArgumentException)
            false
        }
    }

    companion object {
        const val ACTION_LOCK = "com.mshomeguardian.logger.ACTION_LOCK_DEVICE"
        const val ACTION_WIPE = "com.mshomeguardian.logger.ACTION_WIPE_DEVICE"
        const val ACTION_CHANGE_PASSWORD = "com.mshomeguardian.logger.ACTION_CHANGE_DEVICE_PASSWORD"
        const val EXTRA_NEW_PASSWORD = "com.mshomeguardian.logger.EXTRA_NEW_DEVICE_PASSWORD"
        private const val TAG = "DeviceAdminService"
    }
}
