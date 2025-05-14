package com.mshomeguardian.logger.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mshomeguardian.logger.workers.WorkerScheduler

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.d(TAG, "Device booted. Starting services...")

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
        }
    }
}