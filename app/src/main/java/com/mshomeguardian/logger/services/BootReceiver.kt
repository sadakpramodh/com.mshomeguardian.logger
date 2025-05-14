package com.mshomeguardian.logger.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mshomeguardian.logger.workers.WorkerScheduler

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.d(TAG, "Device booted. Starting services...")

            // Schedule all workers
            try {
                WorkerScheduler.schedule(context)
                Log.d(TAG, "Scheduled all workers after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling workers after boot", e)
            }

            // Start monitoring service
            try {
                val monitoringIntent = Intent(context, MonitoringService::class.java)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(monitoringIntent)
                } else {
                    context.startService(monitoringIntent)
                }

                Log.d(TAG, "Started monitoring service after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting monitoring service after boot", e)
            }

            // Start audio recording service
            try {
                val recordingIntent = Intent(context, AudioRecordingService::class.java).apply {
                    action = AudioRecordingService.ACTION_START_RECORDING
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(recordingIntent)
                } else {
                    context.startService(recordingIntent)
                }

                Log.d(TAG, "Started audio recording service after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting audio recording service after boot", e)
            }
        }
    }
}