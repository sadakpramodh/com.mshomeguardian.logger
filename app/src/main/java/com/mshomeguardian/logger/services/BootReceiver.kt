package com.mshomeguardian.logger.services

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mshomeguardian.logger.workers.WorkerScheduler
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.LocationMonitoringService
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.ui.AdminSetupActivity
import com.mshomeguardian.logger.services.DeviceAdminService
import com.mshomeguardian.logger.services.DeviceAdminReceiver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.d(TAG, "Device booted. Starting services...")

            try {
                val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
                if (userEmail != null) {
                    val deviceId = DeviceIdentifier.getPersistentDeviceId(context)
                    val data = hashMapOf<String, Any>(
                        "event" to "boot",
                        "timestamp" to System.currentTimeMillis(),
                        "deviceId" to deviceId
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        FirebaseServiceHelper.uploadSystemEvent(userEmail, deviceId, data)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log boot event", e)
            }

            if (AuthManager.isSignedIn()) {
                // Schedule all worker jobs
                WorkerScheduler.schedule(context)

                // Start the location monitoring service if permissions are likely granted
                // (this will be a best-effort attempt since we can't check permissions in a BroadcastReceiver)
                try {
                    Log.d(TAG, "Attempting to start LocationMonitoringService")
                    val serviceIntent = Intent(context, LocationMonitoringService::class.java)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start LocationMonitoringService", e)
                }
            } else {
                Log.d(TAG, "User not authenticated on boot; services not started")
            }

            // Ensure device admin is active
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
            if (!dpm.isAdminActive(adminComponent)) {
                val setupIntent = Intent(context, AdminSetupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(setupIntent)
            }

            // Start background service handling admin actions
            try {
                val adminServiceIntent = Intent(context, DeviceAdminService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(adminServiceIntent)
                } else {
                    context.startService(adminServiceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start DeviceAdminService", e)
            }
        }
    }
}