package com.mshomeguardian.logger.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.ScreenContentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that captures the current wallpaper and screen contents
 * and exposes them via a minimal HTTP server for remote dashboard access.
 */
class ScreenContentService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var server: DashboardServer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        serviceScope.launch {
            // Capture wallpaper immediately
            ScreenContentManager.captureWallpaper(this@ScreenContentService)

            // Capture screenshot if permission data is provided
            val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
            val data: Intent? = intent?.getParcelableExtra("data")
            if (resultCode == RESULT_OK && data != null) {
                ScreenContentManager.captureScreenshot(this@ScreenContentService, resultCode, data)
            }

            // Start the local server to expose files
            if (server == null) {
                server = DashboardServer(this@ScreenContentService)
                server?.start()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "screen_content"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Content", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Home Guardian")
            .setContentText("Screen content capture active")
            .build()
    }
}
