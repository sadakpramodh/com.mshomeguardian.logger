package com.mshomeguardian.logger.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.storage.FirebaseStorage
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.workers.TranscriptionWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.WorkManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.google.firebase.FirebaseApp
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RecordingService : Service() {
    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 12346
        private const val CHANNEL_ID = "recording_service_channel"

        // Recording length (30 minutes in milliseconds)
        private const val RECORDING_DURATION = 30 * 60 * 1000L

        // For checking if service is running
        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    private var mediaRecorder: MediaRecorder? = null
    private lateinit var storageDir: File
    private lateinit var deviceId: String
    private var currentRecordingFile: File? = null
    private val executorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Wake lock to keep CPU running during recording
    private var wakeLock: PowerManager.WakeLock? = null

    // Timer for recording segments
    private var timer: Timer? = null

    // Firebase Storage reference
    private lateinit var firebaseStorage: FirebaseStorage

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RecordingService onCreate")
        deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)

        // Create recordings directory
        storageDir = File(getExternalFilesDir(null), "recordings")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        // Initialize Firebase Storage
        try {
            if (FirebaseApp.getApps(applicationContext).isNotEmpty()) {
                firebaseStorage = FirebaseStorage.getInstance()
                Log.d(TAG, "Firebase Storage initialized successfully")
            } else {
                Log.e(TAG, "Firebase not initialized properly!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase Storage", e)
        }

        // Acquire partial wake lock to keep recording even when device is idle
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HomeguardianLogger::RecordingWakeLock"
        )
        wakeLock?.acquire(RECORDING_DURATION + 60000) // Add 1 minute buffer
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RecordingService onStartCommand")

        // Create notification channel for Android 8.0+
        createNotificationChannel()

        // Start foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start recording
        if (!isServiceRunning) {
            startRecording()
            isServiceRunning = true
        }

        // If service is killed, restart it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "RecordingService onDestroy")

        // Stop recording
        stopRecording()
        isServiceRunning = false

        // Clean up
        timer?.cancel()
        executorService.shutdown()

        // Release wake lock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recording Service"
            val descriptionText = "Audio recording in background"
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
            .setContentText("Recording audio for your security")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startRecording() {
        executorService.execute {
            try {
                // Schedule periodic recording
                schedulePeriodicRecording()

                // Start first recording
                startNewRecordingFile()

            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
            }
        }
    }

    private fun schedulePeriodicRecording() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    // Stop current recording
                    stopCurrentRecording()

                    // Process the completed recording file
                    currentRecordingFile?.let { file ->
                        // Upload to Firebase and start transcription in a separate thread
                        processRecordingFile(file)
                    }

                    // Start a new recording
                    startNewRecordingFile()

                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic recording", e)
                }
            }
        }, RECORDING_DURATION, RECORDING_DURATION)
    }

    private fun startNewRecordingFile() {
        try {
            // Create a new file for recording
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "recording_$timestamp.wav"  // Changed to WAV for better quality
            currentRecordingFile = File(storageDir, fileName)

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)

                // Use higher quality settings for better transcription
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)  // 44.1 kHz
                setAudioEncodingBitRate(128000)  // 128 kbps

                setOutputFile(currentRecordingFile?.absolutePath)
                prepare()
                start()
            }

            Log.d(TAG, "Recording started: ${currentRecordingFile?.absolutePath}")

            // Update notification to show recording is in progress
            updateNotification("Recording in progress: $fileName")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting new recording", e)
        }
    }

    private fun stopCurrentRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.d(TAG, "Recording stopped: ${currentRecordingFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            // If error occurs, the file might be corrupted
            currentRecordingFile?.delete()
            currentRecordingFile = null
        }
    }

    private fun stopRecording() {
        try {
            // Stop timer
            timer?.cancel()
            timer = null

            // Stop current recording
            stopCurrentRecording()

            // Process the last recording file if exists
            currentRecordingFile?.let { file ->
                processRecordingFile(file)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording service", e)
        }
    }

    private fun processRecordingFile(file: File) {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "File doesn't exist or is empty: ${file.absolutePath}")
            return
        }

        try {
            // Upload to Firebase Storage
            uploadToFirebase(file)

            // Start transcription using WorkManager
            startTranscriptionWorker(file)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing recording file", e)
        }
    }

    private fun uploadToFirebase(file: File) {
        coroutineScope.launch {
            try {
                // Check if Firebase is initialized
                if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                    Log.e(TAG, "Firebase not initialized, cannot upload file")
                    return@launch
                }

                // First verify storage bucket exists by trying a minimal operation
                try {
                    val storageRef = FirebaseStorage.getInstance().reference
                    // Just list a single item to verify connection
                    storageRef.child("test").listAll().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase Storage is not properly configured", e)
                    Log.e(TAG, "Please check your Firebase Console and make sure Storage is enabled")
                    return@launch
                }

                val storageRef = FirebaseStorage.getInstance().reference
                val audioRef = storageRef.child("devices/$deviceId/audio/${file.name}")

                // Upload file with retry logic
                var retryCount = 0
                var uploadSuccessful = false
                var lastException: Exception? = null

                while (retryCount < 3 && !uploadSuccessful) {
                    try {
                        // Upload file
                        val uploadTask = audioRef.putFile(android.net.Uri.fromFile(file))
                        uploadTask.await()

                        // If we get here, upload was successful
                        Log.d(TAG, "Successfully uploaded audio file: ${file.name}")
                        uploadSuccessful = true
                    } catch (e: Exception) {
                        lastException = e
                        Log.e(TAG, "Upload attempt ${retryCount + 1} failed", e)
                        retryCount++
                        delay(1000L * retryCount) // Exponential backoff
                    }
                }

                if (!uploadSuccessful && lastException != null) {
                    // All retries failed
                    handleUploadFailure(lastException, file.name)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file to Firebase", e)
            }
        }
    }

    private fun handleUploadFailure(exception: Exception, fileName: String) {
        Log.e(TAG, "Failed to upload audio file: $fileName", exception)

        // Try to diagnose the issue
        when {
            exception is StorageException && exception.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ->
                Log.e(TAG, "Storage bucket might not exist - check Firebase console")
            exception.message?.contains("permission_denied") == true ||
                    exception.message?.contains("Permission denied") == true ->
                Log.e(TAG, "Permission denied - check your Firebase Storage Rules")
            exception.message?.contains("authentication") == true ->
                Log.e(TAG, "Authentication issue with Firebase - check your configuration")
            else ->
                Log.e(TAG, "General upload error: ${exception.message}")
        }
    }

    private fun startTranscriptionWorker(file: File) {
        try {
            // Create input data for the worker
            val inputData = Data.Builder()
                .putString("file_path", file.absolutePath)
                .putString("device_id", deviceId)
                .build()

            // Create a one-time work request
            val transcriptionRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(inputData)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiresCharging(false)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            // Enqueue the work
            WorkManager.getInstance(applicationContext)
                .enqueue(transcriptionRequest)

            Log.d(TAG, "Transcription worker started for file: ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting transcription worker", e)
        }
    }

    private fun updateNotification(contentText: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Home Guardian")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun delay(millis: Long) {
        withContext(Dispatchers.IO) {
            Thread.sleep(millis)
        }
    }
}