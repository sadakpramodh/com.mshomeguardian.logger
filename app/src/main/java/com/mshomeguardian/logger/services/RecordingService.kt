package com.mshomeguardian.logger.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.DeviceIdentifier
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service for recording and monitoring audio
 */
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 12346
        private const val CHANNEL_ID = "recording_service_channel"

        private var isServiceRunning = false

        /**
         * Check if the recording service is currently running
         */
        fun isRunning(): Boolean {
            return isServiceRunning
        }
    }

    private lateinit var deviceId: String
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RecordingService onCreate")

        // Get device ID
        deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)

        // Create notification channel for foreground service
        createNotificationChannel()

        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Set service as running
        isServiceRunning = true

        // Start recording if permission is granted
        if (hasRecordPermission()) {
            startRecording()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RecordingService onStartCommand")

        // If service is killed, restart it
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "RecordingService onDestroy")
        // Stop recording if it's running
        stopRecording()
        isServiceRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecording() {
        if (!hasRecordPermission()) {
            Log.e(TAG, "Cannot start recording: RECORD_AUDIO permission not granted")
            return
        }

        try {
            // Create a file to save the recording
            val recordingsDir = File(getExternalFilesDir(null), "recordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }

            // Create a unique filename based on timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = "${recordingsDir.absolutePath}/recording_${timestamp}.3gp"

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile)

                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Recording started: $outputFile")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to prepare MediaRecorder", e)
                    isRecording = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                Log.d(TAG, "Recording stopped: $outputFile")

                // Here you could upload the recording to Firebase or process it

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recording Service"
            val descriptionText = "Records audio for security monitoring"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Home Guardian")
            .setContentText("Audio recording is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}