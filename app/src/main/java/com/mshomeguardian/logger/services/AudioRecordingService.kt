package com.mshomeguardian.logger.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.AudioRecordingEntity
import com.mshomeguardian.logger.ui.MainActivity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optimized AudioRecordingService with improved memory management
 */
class AudioRecordingService : Service() {
    companion object {
        private const val TAG = "AudioRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_service_channel"

        // Optimized audio parameters
        private const val SAMPLING_RATE_IN_HZ = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Optimized recording management (reduced from 1 hour to 30 minutes)
        private const val RECORDING_DURATION = 30 * 60 * 1000L

        // Reduced buffer size for better memory efficiency
        private const val MAX_BUFFER_SIZE = 5 * 1024 * 1024  // 5MB instead of 10MB
        private const val RETRY_DELAY_MS = 5000L
        private const val MAX_RETRY_COUNT = 5  // Reduced from 10

        // Actions
        const val ACTION_START_RECORDING = "com.mshomeguardian.logger.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.mshomeguardian.logger.ACTION_STOP_RECORDING"
        const val ACTION_SAVE_CURRENT_RECORDING = "com.mshomeguardian.logger.ACTION_SAVE_CURRENT_RECORDING"

        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    // Optimized service state
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: kotlinx.coroutines.Job? = null
    private var hourlyProcessingJob: kotlinx.coroutines.Job? = null
    private var audioRecord: AudioRecord? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Optimized audio buffer management
    private val audioDataQueue: Queue<ShortArray> = ConcurrentLinkedQueue()
    private var totalBufferSizeBytes = 0
    private val queueLock = Any()

    // Optimized power management
    private var wakeLock: PowerManager.WakeLock? = null
    private var retryCount = 0

    // Database and device info
    private lateinit var db: AppDatabase
    private lateinit var deviceId: String

    // Optimized Firebase instances (lazy initialization)
    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Failed to initialize Firestore", e)
            null
        }
    }

    private val storage by lazy {
        try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Failed to initialize Firebase Storage", e)
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        OptimizedLogger.d(TAG, "Service onCreate")

        try {
            createNotificationChannel()
            db = AppDatabase.getInstance(applicationContext)
            deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            createRecordingsDirectory()

            OptimizedLogger.d(TAG, "Service initialized successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        OptimizedLogger.d(TAG, "onStartCommand: ${intent?.action}")

        try {
            if (!hasRequiredPermissions()) {
                OptimizedLogger.e(TAG, "Missing required permissions - stopping service")
                stopSelf()
                return START_NOT_STICKY
            }

            when (intent?.action) {
                ACTION_START_RECORDING -> {
                    if (!isServiceRunning) {
                        startForeground(NOTIFICATION_ID, createNotification("Recording in progress"))
                        acquireWakeLock()
                        isServiceRunning = true
                        startRecording()
                        startHourlyProcessing()
                        OptimizedLogger.d(TAG, "Recording service started successfully")
                    }
                }
                ACTION_STOP_RECORDING -> {
                    stopRecording()
                    stopSelf()
                }
                ACTION_SAVE_CURRENT_RECORDING -> {
                    serviceScope.launch(Dispatchers.IO) {
                        saveCurrentRecording()
                    }
                }
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        OptimizedLogger.d(TAG, "Service onDestroy")
        try {
            stopRecording()
            stopHourlyProcessing()
            releaseWakeLock()
            serviceScope.cancel()
            isServiceRunning = false
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun hasRequiredPermissions(): Boolean {
        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasForegroundServiceMicrophone = if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasRecordAudio && hasForegroundServiceMicrophone
    }

    // [Continue with optimized recording methods...]
    private fun startRecording() {
        if (isRecording.get()) return

        OptimizedLogger.d(TAG, "Starting optimized recording process")

        recordingJob = serviceScope.launch {
            try {
                if (!checkMicrophonePermission()) {
                    OptimizedLogger.e(TAG, "Missing microphone permission")
                    return@launch
                }

                while (isServiceRunning) {
                    try {
                        initializeAudioRecord()
                        recordAudio()
                    } catch (e: Exception) {
                        when (e) {
                            is SecurityException -> {
                                OptimizedLogger.e(TAG, "Security exception: microphone permission denied", e)
                                break
                            }
                            else -> {
                                OptimizedLogger.e(TAG, "Error in recording process, will retry", e)
                                handleMicrophoneContention()
                            }
                        }
                    }
                }
            } finally {
                releaseAudioRecord()
                OptimizedLogger.d(TAG, "Recording job ended")
            }
        }
    }

    private fun checkMicrophonePermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private suspend fun initializeAudioRecord() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                OptimizedLogger.e(TAG, "Invalid buffer size: $bufferSize")
                throw IllegalStateException("Invalid audio buffer size")
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                OptimizedLogger.e(TAG, "Failed to initialize AudioRecord")
                releaseAudioRecord()
                throw IllegalStateException("AudioRecord initialization failed")
            }

            resetRetryCounter()
            isRecording.set(true)
            updateNotification("Recording in progress")
            OptimizedLogger.d(TAG, "AudioRecord initialized successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing AudioRecord", e)
            throw e
        }
    }

    private suspend fun recordAudio() {
        val audioRecord = this.audioRecord ?: return

        try {
            audioRecord.startRecording()
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val audioBuffer = ShortArray(bufferSize / 2)

            OptimizedLogger.d(TAG, "Started recording with buffer size: $bufferSize")

            while (isRecording.get() && isServiceRunning) {
                val readResult = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                if (readResult > 0) {
                    val bufferCopy = audioBuffer.copyOf()

                    synchronized(queueLock) {
                        audioDataQueue.add(bufferCopy)
                        totalBufferSizeBytes += bufferCopy.size * 2

                        // Optimized queue trimming
                        while (totalBufferSizeBytes > MAX_BUFFER_SIZE && audioDataQueue.isNotEmpty()) {
                            val removed = audioDataQueue.remove()
                            totalBufferSizeBytes -= removed.size * 2
                        }
                    }
                } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION ||
                    readResult == AudioRecord.ERROR_BAD_VALUE) {
                    OptimizedLogger.e(TAG, "Error reading audio data: $readResult")
                    throw IllegalStateException("Error reading audio data")
                }

                delay(10) // Small delay to prevent tight-looping
            }
        } finally {
            try {
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop()
                }
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error stopping AudioRecord", e)
            }
        }
    }

    private fun handleMicrophoneContention() {
        retryCount++

        if (retryCount > MAX_RETRY_COUNT) {
            OptimizedLogger.e(TAG, "Max retry count exceeded, giving up")
            isRecording.set(false)
            return
        }

        OptimizedLogger.d(TAG, "Microphone busy, will retry (attempt $retryCount)")
        updateNotification("Microphone busy, will retry automatically")

        serviceScope.launch {
            delay(RETRY_DELAY_MS * retryCount) // Exponential backoff
            if (isServiceRunning && !isRecording.get()) {
                OptimizedLogger.d(TAG, "Attempting to restart recording after microphone contention")
                releaseAudioRecord()
                startRecording()
            }
        }
    }

    private fun resetRetryCounter() {
        retryCount = 0
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
            isRecording.set(false)
        }
    }

    private fun stopRecording() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()

        serviceScope.launch(Dispatchers.IO) {
            saveCurrentRecording()
        }
    }

    private fun startHourlyProcessing() {
        hourlyProcessingJob = serviceScope.launch {
            while (isServiceRunning) {
                try {
                    delay(RECORDING_DURATION)
                    saveCurrentRecording()
                } catch (e: Exception) {
                    OptimizedLogger.e(TAG, "Error in hourly processing", e)
                }
            }
        }
    }

    private fun stopHourlyProcessing() {
        hourlyProcessingJob?.cancel()
        hourlyProcessingJob = null
    }

    // [Additional optimized methods continue...]

    private suspend fun saveCurrentRecording() {
        OptimizedLogger.d(TAG, "Attempting to save current recording")

        if (audioDataQueue.isEmpty()) {
            OptimizedLogger.d(TAG, "No audio data to save")
            return
        }

        try {
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
            val fileName = "recording_${dateFormat.format(Date(timestamp))}.wav"

            val recordingsDir = getRecordingsDirectory()
            if (recordingsDir == null) {
                OptimizedLogger.e(TAG, "Failed to create recordings directory")
                return
            }

            val outputFile = File(recordingsDir, fileName)
            val recordingId = UUID.randomUUID().toString()
            var duration = 0L

            withContext(Dispatchers.IO) {
                var currentAudioData: Queue<ShortArray>

                synchronized(queueLock) {
                    currentAudioData = ConcurrentLinkedQueue(audioDataQueue)
                    audioDataQueue.clear()
                    totalBufferSizeBytes = 0
                }

                if (currentAudioData.isEmpty()) {
                    return@withContext
                }

                var totalSamples = 0
                for (buffer in currentAudioData) {
                    totalSamples += buffer.size
                }

                duration = (totalSamples * 1000L) / SAMPLING_RATE_IN_HZ

                FileOutputStream(outputFile).use { fileOutputStream ->
                    writeWavHeader(fileOutputStream, totalSamples)

                    while (currentAudioData.isNotEmpty()) {
                        val buffer = currentAudioData.poll() ?: break
                        val bytes = ByteArray(buffer.size * 2)
                        for (i in buffer.indices) {
                            val sample = buffer[i]
                            bytes[i * 2] = sample.toByte()
                            bytes[i * 2 + 1] = (sample.toInt() shr 8).toByte()
                        }
                        fileOutputStream.write(bytes)
                    }
                }

                OptimizedLogger.d(TAG, "Saved audio recording to ${outputFile.absolutePath}")
            }

            val recordingEntity = AudioRecordingEntity(
                recordingId = recordingId,
                filePath = outputFile.absolutePath,
                fileName = fileName,
                startTime = timestamp - duration,
                endTime = timestamp,
                duration = duration,
                fileSize = outputFile.length(),
                transcriptionStatus = AudioRecordingEntity.TranscriptionStatus.PENDING,
                uploadedToCloud = false,
                deviceId = deviceId
            )

            try {
                db.audioRecordingDao().insertRecording(recordingEntity)
                OptimizedLogger.d(TAG, "Recording metadata saved to database")

                val audioPrefs = applicationContext.getSharedPreferences("audio_recording_sync", Context.MODE_PRIVATE)
                audioPrefs.edit().putLong("last_save_time", timestamp).apply()

                serviceScope.launch {
                    try {
                        processRecording(recordingEntity)
                    } catch (e: Exception) {
                        OptimizedLogger.e(TAG, "Error processing recording", e)
                    }
                }
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error saving recording metadata to database", e)
            }

        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error saving audio recording", e)
        }
    }

    private fun writeWavHeader(outputStream: FileOutputStream, totalSamples: Int) {
        try {
            val bytesPerSample = 2
            val dataSize = totalSamples * bytesPerSample
            val fileSize = 36 + dataSize

            outputStream.write("RIFF".toByteArray())
            writeInt(outputStream, fileSize)
            outputStream.write("WAVE".toByteArray())

            outputStream.write("fmt ".toByteArray())
            writeInt(outputStream, 16)
            writeShort(outputStream, 1)
            writeShort(outputStream, 1)
            writeInt(outputStream, SAMPLING_RATE_IN_HZ)
            writeInt(outputStream, SAMPLING_RATE_IN_HZ * bytesPerSample)
            writeShort(outputStream, bytesPerSample)
            writeShort(outputStream, 16)

            outputStream.write("data".toByteArray())
            writeInt(outputStream, dataSize)
        } catch (e: IOException) {
            OptimizedLogger.e(TAG, "Error writing WAV header", e)
        }
    }

    private fun writeInt(outputStream: FileOutputStream, value: Int) {
        outputStream.write(value and 0xFF)
        outputStream.write(value shr 8 and 0xFF)
        outputStream.write(value shr 16 and 0xFF)
        outputStream.write(value shr 24 and 0xFF)
    }

    private fun writeShort(outputStream: FileOutputStream, value: Int) {
        outputStream.write(value and 0xFF)
        outputStream.write(value shr 8 and 0xFF)
    }

    private suspend fun processRecording(recording: AudioRecordingEntity) {
        uploadToFirebaseStorage(recording)
    }

    private suspend fun uploadToFirebaseStorage(recording: AudioRecordingEntity) {
        val storageInstance = storage ?: return

        try {
            val file = File(recording.filePath)
            if (!file.exists()) {
                OptimizedLogger.e(TAG, "Audio file does not exist: ${recording.filePath}")
                return
            }

            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null) {
                OptimizedLogger.e(TAG, "No authenticated user for upload")
                return
            }

            val storageRef = FirebaseServiceHelper.getAudioStorageReference(
                userEmail,
                deviceId,
                recording.fileName
            ) ?: return

            val uploadTask = storageRef.putFile(android.net.Uri.fromFile(file))

            uploadTask.addOnSuccessListener {
                OptimizedLogger.d(TAG, "Audio file uploaded successfully: ${recording.fileName}")

                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val uploadTime = System.currentTimeMillis()
                        db.audioRecordingDao().markRecordingAsUploaded(
                            recordingId = recording.recordingId,
                            uploadTime = uploadTime
                        )
                        updateFirestoreWithRecordingMetadata(recording, uploadTime)
                    } catch (e: Exception) {
                        OptimizedLogger.e(TAG, "Error updating recording upload status", e)
                    }
                }
            }.addOnFailureListener { e ->
                OptimizedLogger.e(TAG, "Failed to upload audio file: ${recording.fileName}", e)
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error in uploadToFirebaseStorage", e)
        }
    }

    private fun createRecordingsDirectory(): Boolean {
        return try {
            val directory = getRecordingsDirectory()
            directory?.exists() ?: false
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error creating recordings directory", e)
            false
        }
    }

    private fun getRecordingsDirectory(): File? {
        return try {
            val storageDir = applicationContext.getExternalFilesDir("audio_recordings")
            if (storageDir != null && (!storageDir.exists() || !storageDir.isDirectory)) {
                storageDir.mkdirs()
            }
            storageDir
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error getting recordings directory", e)
            null
        }
    }

    private suspend fun updateFirestoreWithRecordingMetadata(
        recording: AudioRecordingEntity,
        uploadTime: Long
    ) {
        val firestoreInstance = firestore ?: return

        try {
            val recordingData = hashMapOf<String, Any>(
                "recordingId" to recording.recordingId,
                "fileName" to recording.fileName,
                "startTime" to recording.startTime,
                "endTime" to recording.endTime,
                "duration" to recording.duration,
                "fileSize" to recording.fileSize,
                "transcriptionStatus" to recording.transcriptionStatus.name,
                "deviceId" to deviceId,
                "uploadTime" to uploadTime,
                "transcription" to (recording.transcription ?: "")
            )

            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null) {
                OptimizedLogger.e(TAG, "User not authenticated")
                return
            }

            val success = FirebaseServiceHelper.uploadAudioRecording(
                userEmail,
                deviceId,
                recordingData
            )

            if (success) {
                OptimizedLogger.d(
                    TAG,
                    "Recording metadata stored in Firestore: ${recording.recordingId}"
                )
            } else {
                OptimizedLogger.e(TAG, "Error storing recording metadata in Firestore")
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error in updateFirestoreWithRecordingMetadata", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for the recording service notifications"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Home Guardian")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(message: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification(message))
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error updating notification", e)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AudioRecordingService::WakeLock"
                )
                wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours max (reduced from 10)
                OptimizedLogger.d(TAG, "Wake lock acquired")
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error acquiring wake lock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    OptimizedLogger.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error releasing wake lock", e)
        }
    }
}