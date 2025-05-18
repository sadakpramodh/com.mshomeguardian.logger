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
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.storage.ktx.storage
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.AudioRecordingEntity
import com.mshomeguardian.logger.ui.MainActivity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service for continuous audio recording with automatic scheduling
 */
class AudioRecordingService : Service() {
    companion object {
        private const val TAG = "AudioRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_service_channel"

        // Audio recording parameters
        private const val SAMPLING_RATE_IN_HZ = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Recording management
        private const val HOUR_IN_MILLIS = 60 * 60 * 1000L
        private const val MAX_BUFFER_SIZE = 10 * 1024 * 1024  // 10MB, adjust as needed
        private const val RETRY_DELAY_MS = 5000L  // 5 seconds
        private const val MAX_RETRY_COUNT = 10

        // Actions - these need to be public for DataSyncManager to access
        const val ACTION_START_RECORDING = "com.mshomeguardian.logger.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.mshomeguardian.logger.ACTION_STOP_RECORDING"
        const val ACTION_SAVE_CURRENT_RECORDING = "com.mshomeguardian.logger.ACTION_SAVE_CURRENT_RECORDING"

        // Flag to track service running state - needed for DataSyncManager.isRecordingServiceRunning()
        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    // Service state
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var hourlyProcessingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Audio buffer management
    private val audioDataQueue: Queue<ShortArray> = ConcurrentLinkedQueue()
    private var totalBufferSizeBytes = 0
    private val queueLock = Any()

    // Wake lock to ensure service keeps running
    private var wakeLock: PowerManager.WakeLock? = null

    // Microphone contention management
    private var retryCount = 0
    private var audioManager: AudioManager? = null
    private var microphoneContentionTimer: Timer? = null

    // Database and device info
    private lateinit var db: AppDatabase
    private lateinit var deviceId: String

    // Firebase (using KTX)
    private val firestore = try { Firebase.firestore } catch (e: Exception) { null }
    private val storage = try { Firebase.storage } catch (e: Exception) { null }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        try {
            // Create notification channel for Android 8.0+
            createNotificationChannel()

            db = AppDatabase.getInstance(applicationContext)
            deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)

            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Ensure recordings directory exists
            createRecordingsDirectory()

            Log.d(TAG, "Service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        try {
            when (intent?.action) {
                ACTION_START_RECORDING -> {
                    if (!isServiceRunning) {
                        // Start as foreground service with notification
                        val notification = createNotification("Recording in progress")
                        startForeground(NOTIFICATION_ID, notification)

                        // Make sure we stay awake
                        acquireWakeLock()

                        // Start recording process
                        isServiceRunning = true
                        startRecording()
                        startHourlyProcessing()

                        Log.d(TAG, "Recording service started successfully")
                    }
                }
                ACTION_STOP_RECORDING -> {
                    stopRecording()
                    stopSelf()
                }
                ACTION_SAVE_CURRENT_RECORDING -> {
                    // Manually trigger saving of current recording
                    serviceScope.launch(Dispatchers.IO) {
                        saveCurrentRecording()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }

        // If service is killed, restart it
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        try {
            stopRecording()
            stopHourlyProcessing()
            releaseWakeLock()
            isServiceRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotification(message: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Home Guardian")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
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

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AudioRecordingService::WakeLock"
                )
                wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours max
                Log.d(TAG, "Wake lock acquired")
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring wake lock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    private fun startRecording() {
        if (isRecording.get()) return

        Log.d(TAG, "Starting recording process")

        recordingJob = serviceScope.launch {
            try {
                if (!checkMicrophonePermission()) {
                    Log.e(TAG, "Missing microphone permission")
                    return@launch
                }

                while (isServiceRunning) {
                    try {
                        initializeAudioRecord()
                        recordAudio()
                    } catch (e: Exception) {
                        when (e) {
                            is SecurityException -> {
                                Log.e(TAG, "Security exception: microphone permission denied", e)
                                break
                            }
                            else -> {
                                // Handle other exceptions that might be related to microphone contention
                                Log.e(TAG, "Error in recording process, will retry", e)
                                handleMicrophoneContention()
                            }
                        }
                    }
                }
            } finally {
                releaseAudioRecord()
                Log.d(TAG, "Recording job ended")
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
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                throw IllegalStateException("Invalid audio buffer size")
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,  // Using voice recognition for better quality
                SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2  // Double the buffer size for better performance
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                releaseAudioRecord()
                throw IllegalStateException("AudioRecord initialization failed")
            }

            // Successfully initialized
            resetRetryCounter()
            isRecording.set(true)

            // Update notification to indicate recording is in progress
            updateNotification("Recording in progress")

            Log.d(TAG, "AudioRecord initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioRecord", e)
            throw e
        }
    }

    private suspend fun recordAudio() {
        val audioRecord = this.audioRecord ?: return

        try {
            audioRecord.startRecording()

            // Calculate buffer size based on sampling rate (16-bit samples)
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            // Create buffer for reading audio data
            val audioBuffer = ShortArray(bufferSize / 2)

            Log.d(TAG, "Started recording with buffer size: $bufferSize")

            // Main recording loop
            while (isRecording.get() && isServiceRunning) {
                val readResult = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                if (readResult > 0) {
                    // Make a copy of the buffer to store in the queue
                    val bufferCopy = audioBuffer.copyOf()

                    // Add the audio data to the queue
                    synchronized(queueLock) {
                        audioDataQueue.add(bufferCopy)
                        totalBufferSizeBytes += bufferCopy.size * 2  // 2 bytes per short

                        // Trim the queue if it exceeds the maximum size
                        while (totalBufferSizeBytes > MAX_BUFFER_SIZE && audioDataQueue.isNotEmpty()) {
                            val removed = audioDataQueue.remove()
                            totalBufferSizeBytes -= removed.size * 2
                        }
                    }
                } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION ||
                    readResult == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Error reading audio data: $readResult")
                    throw IllegalStateException("Error reading audio data")
                }

                // Small delay to prevent tight-looping
                delay(10)
            }
        } finally {
            try {
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
        }
    }

    private fun handleMicrophoneContention() {
        retryCount++

        if (retryCount > MAX_RETRY_COUNT) {
            Log.e(TAG, "Max retry count exceeded, giving up")
            isRecording.set(false)
            return
        }

        Log.d(TAG, "Microphone seems busy, will retry (attempt $retryCount)")
        updateNotification("Microphone busy, will retry automatically")

        // Cancel any existing timer
        microphoneContentionTimer?.cancel()

        // Schedule a retry after delay
        microphoneContentionTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (isServiceRunning && !isRecording.get()) {
                        // If service is still running but recording stopped due to contention
                        Log.d(TAG, "Attempting to restart recording after microphone contention")
                        releaseAudioRecord()
                        startRecording()
                    }
                }
            }, RETRY_DELAY_MS * retryCount)  // Increasing backoff
        }
    }

    private fun resetRetryCounter() {
        retryCount = 0
        microphoneContentionTimer?.cancel()
        microphoneContentionTimer = null
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
            Log.e(TAG, "Error releasing AudioRecord", e)
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

        // Save any remaining audio before stopping
        serviceScope.launch(Dispatchers.IO) {
            saveCurrentRecording()
        }
    }

    private fun startHourlyProcessing() {
        hourlyProcessingJob = serviceScope.launch {
            while (isServiceRunning) {
                try {
                    // Wait for 1 hour (or a custom interval for testing)
                    delay(HOUR_IN_MILLIS)

                    // Process and save current recording
                    saveCurrentRecording()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in hourly processing", e)
                }
            }
        }
    }

    private fun stopHourlyProcessing() {
        hourlyProcessingJob?.cancel()
        hourlyProcessingJob = null
    }

    private suspend fun saveCurrentRecording() {
        Log.d(TAG, "Attempting to save current recording")

        if (audioDataQueue.isEmpty()) {
            Log.d(TAG, "No audio data to save")
            return
        }

        try {
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
            val fileName = "recording_${dateFormat.format(Date(timestamp))}.wav"

            // Create directory if it doesn't exist
            val recordingsDir = getRecordingsDirectory()
            if (recordingsDir == null) {
                Log.e(TAG, "Failed to create recordings directory")
                return
            }

            val outputFile = File(recordingsDir, fileName)
            val recordingId = UUID.randomUUID().toString()
            var duration = 0L

            // Convert queue data to WAV file
            withContext(Dispatchers.IO) {
                var currentAudioData: Queue<ShortArray>

                // Safely get and clear the audio queue
                synchronized(queueLock) {
                    currentAudioData = ConcurrentLinkedQueue(audioDataQueue)
                    audioDataQueue.clear()
                    totalBufferSizeBytes = 0
                }

                // Skip if we got no data after synchronization
                if (currentAudioData.isEmpty()) {
                    return@withContext
                }

                // Calculate total audio data size
                var totalSamples = 0
                for (buffer in currentAudioData) {
                    totalSamples += buffer.size
                }

                // Calculate audio duration in milliseconds
                duration = (totalSamples * 1000L) / SAMPLING_RATE_IN_HZ

                // Create WAV file
                FileOutputStream(outputFile).use { fileOutputStream ->
                    // Write WAV header
                    writeWavHeader(fileOutputStream, totalSamples)

                    // Write audio data
                    while (currentAudioData.isNotEmpty()) {
                        val buffer = currentAudioData.poll() ?: break

                        // Convert short array to byte array (little endian)
                        val bytes = ByteArray(buffer.size * 2)
                        for (i in buffer.indices) {
                            val sample = buffer[i]
                            bytes[i * 2] = sample.toByte()
                            bytes[i * 2 + 1] = (sample.toInt() shr 8).toByte()
                        }

                        fileOutputStream.write(bytes)
                    }
                }

                Log.d(TAG, "Saved audio recording to ${outputFile.absolutePath}")
            }

            // Store recording info in the database
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
                Log.d(TAG, "Recording metadata saved to database")

                // Update shared preferences with last save time
                val audioPrefs = applicationContext.getSharedPreferences("audio_recording_sync", Context.MODE_PRIVATE)
                audioPrefs.edit().putLong("last_save_time", timestamp).apply()

                // Start transcription & upload in a separate coroutine
                serviceScope.launch {
                    try {
                        processRecording(recordingEntity)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing recording", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving recording metadata to database", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio recording", e)
        }
    }

    private fun writeWavHeader(outputStream: FileOutputStream, totalSamples: Int) {
        try {
            val bytesPerSample = 2  // 16-bit audio
            val dataSize = totalSamples * bytesPerSample
            val fileSize = 36 + dataSize

            // RIFF header
            outputStream.write("RIFF".toByteArray())  // ChunkID
            writeInt(outputStream, fileSize)  // ChunkSize
            outputStream.write("WAVE".toByteArray())  // Format

            // fmt subchunk
            outputStream.write("fmt ".toByteArray())  // Subchunk1ID
            writeInt(outputStream, 16)  // Subchunk1Size (16 for PCM)
            writeShort(outputStream, 1)  // AudioFormat (1 for PCM)
            writeShort(outputStream, 1)  // NumChannels (1 for mono)
            writeInt(outputStream, SAMPLING_RATE_IN_HZ)  // SampleRate
            writeInt(outputStream, SAMPLING_RATE_IN_HZ * bytesPerSample)  // ByteRate
            writeShort(outputStream, bytesPerSample)  // BlockAlign
            writeShort(outputStream, 16)  // BitsPerSample

            // data subchunk
            outputStream.write("data".toByteArray())  // Subchunk2ID
            writeInt(outputStream, dataSize)  // Subchunk2Size
        } catch (e: IOException) {
            Log.e(TAG, "Error writing WAV header", e)
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
        // Upload file to Firebase Storage
        uploadToFirebaseStorage(recording)
    }

    private suspend fun uploadToFirebaseStorage(recording: AudioRecordingEntity) {
        val storageInstance = storage ?: return

        try {
            val file = File(recording.filePath)
            if (!file.exists()) {
                Log.e(TAG, "Audio file does not exist: ${recording.filePath}")
                return
            }

            // Create a reference to the file location in Firebase Storage
            val storageRef = storageInstance.reference
                .child("devices")
                .child(deviceId)
                .child("audio")
                .child(recording.fileName)

            // Upload file
            val uploadTask = storageRef.putFile(android.net.Uri.fromFile(file))

            // Monitor upload
            uploadTask.addOnSuccessListener {
                Log.d(TAG, "Audio file uploaded successfully: ${recording.fileName}")

                // Update database entry
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val uploadTime = System.currentTimeMillis()
                        db.audioRecordingDao().markRecordingAsUploaded(
                            recordingId = recording.recordingId,
                            uploadTime = uploadTime
                        )

                        // Also update Firestore with recording metadata
                        updateFirestoreWithRecordingMetadata(recording, uploadTime)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating recording upload status", e)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload audio file: ${recording.fileName}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadToFirebaseStorage", e)
        }
    }

    private fun createRecordingsDirectory(): Boolean {
        try {
            val directory = getRecordingsDirectory()
            return directory?.exists() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error creating recordings directory", e)
            return false
        }
    }

    private fun getRecordingsDirectory(): File? {
        try {
            // On Android 8+, use app-specific external storage
            val storageDir = applicationContext.getExternalFilesDir("audio_recordings")

            if (storageDir != null && (!storageDir.exists() || !storageDir.isDirectory)) {
                storageDir.mkdirs()
            }

            return storageDir
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recordings directory", e)
            return null
        }
    }

    private suspend fun updateFirestoreWithRecordingMetadata(
        recording: AudioRecordingEntity,
        uploadTime: Long
    ) {
        val firestoreInstance = firestore ?: return

        try {
            // Create document with recording metadata
            val recordingData = hashMapOf(
                "recordingId" to recording.recordingId,
                "fileName" to recording.fileName,
                "startTime" to recording.startTime,
                "endTime" to recording.endTime,
                "duration" to recording.duration,
                "fileSize" to recording.fileSize,
                "transcriptionStatus" to recording.transcriptionStatus.name,
                "deviceId" to deviceId,
                "uploadTime" to uploadTime,
                "transcription" to recording.transcription
            )

            // Store in Firestore
            firestoreInstance.collection("devices")
                .document(deviceId)
                .collection("audio_recordings")
                .document(recording.recordingId)
                .set(recordingData)
                .addOnSuccessListener {
                    Log.d(TAG, "Recording metadata stored in Firestore: ${recording.recordingId}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error storing recording metadata in Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateFirestoreWithRecordingMetadata", e)
        }
    }

    private fun updateNotification(message: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification(message))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
}