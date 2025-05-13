package com.mshomeguardian.logger.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.ui.MainActivity
import com.mshomeguardian.logger.workers.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var monitoringJob: Job? = null

    companion object {
        private const val TAG = "MonitoringService"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Monitoring service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Monitoring service started")

        // Create and show the notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start monitoring coroutine
        startMonitoring()

        // Make sure service restarts if it's killed
        return START_STICKY
    }

    private fun createNotification(): Notification {
        // Create an intent to open the app when notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Build the notification
        return NotificationCompat.Builder(this, "monitoring_service")
            .setContentTitle("Home Guardian is active")
            .setContentText("Monitoring your device for security")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()

        monitoringJob = serviceScope.launch {
            while (true) {
                Log.d(TAG, "Monitoring service checking workers...")

                // Ensure workers are scheduled
                try {
                    WorkerScheduler.schedule(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error ensuring workers are scheduled", e)
                }

                // Wait for 15 minutes before checking again
                delay(TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Monitoring service destroyed")
        monitoringJob?.cancel()

        // Restart service if it's destroyed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, MonitoringService::class.java))
        } else {
            startService(Intent(this, MonitoringService::class.java))
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}