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
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.LocationEntity
import com.mshomeguardian.logger.ui.MainActivity
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import com.mshomeguardian.logger.workers.CallLogWorker
import com.mshomeguardian.logger.workers.ContactsWorker
import com.mshomeguardian.logger.workers.DeviceInfoWorker
import com.mshomeguardian.logger.workers.MessageWorker
import com.mshomeguardian.logger.workers.WeatherWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Unified service that handles both location monitoring and coordination with other services
 * Replaces separate LocationMonitoringService and provides centralized management
 */
class UnifiedMonitoringService : Service() {

    companion object {
        private const val TAG = "UnifiedMonitoringService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "unified_monitoring_channel"

        // Location settings
        private const val DISTANCE_THRESHOLD_METERS = 1.0f
        private val LOCATION_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15)
        private val FASTEST_LOCATION_INTERVAL = TimeUnit.SECONDS.toMillis(30)

        // Service actions
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_START_AUDIO = "START_AUDIO"
        const val ACTION_STOP_AUDIO = "STOP_AUDIO"

        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    // Core components
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var db: AppDatabase
    private lateinit var deviceId: String

    // Location tracking
    private var lastLocation: Location? = null
    private var isLocationTracking = false

    // Audio recording
    private var isAudioRecording = false

    // Power management
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        OptimizedLogger.d(TAG, "UnifiedMonitoringService created")

        try {
            // Initialize core components
            createNotificationChannel()
            db = AppDatabase.getInstance(applicationContext)
            deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            // Setup location callback
            createLocationCallback()

            OptimizedLogger.d(TAG, "Service initialization completed")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        OptimizedLogger.d(TAG, "onStartCommand: ${intent?.action}")

        try {
            // Check authentication
            if (!AuthManager.isSignedIn()) {
                OptimizedLogger.w(TAG, "User not authenticated - stopping service")
                stopSelf()
                return START_NOT_STICKY
            }

            when (intent?.action) {
                ACTION_START_MONITORING -> {
                    if (!isServiceRunning) {
                        startForeground(NOTIFICATION_ID, createNotification())
                        acquireWakeLock()
                        startLocationTracking()
                        isServiceRunning = true
                        OptimizedLogger.d(TAG, "Unified monitoring started")
                    }
                }
                ACTION_STOP_MONITORING -> {
                    stopMonitoring()
                }
                ACTION_START_AUDIO -> {
                    if (isServiceRunning && !isAudioRecording) {
                        startAudioRecording()
                    }
                }
                ACTION_STOP_AUDIO -> {
                    stopAudioRecording()
                }
                else -> {
                    // Default: start monitoring
                    if (!isServiceRunning) {
                        startForeground(NOTIFICATION_ID, createNotification())
                        acquireWakeLock()
                        startLocationTracking()
                        isServiceRunning = true
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
            stopMonitoring()
            isServiceRunning = false
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun stopMonitoring() {
        try {
            stopLocationTracking()
            stopAudioRecording()
            releaseWakeLock()
            serviceScope.cancel()
            stopSelf()
            OptimizedLogger.d(TAG, "Monitoring stopped")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error stopping monitoring", e)
        }
    }

    // LOCATION TRACKING METHODS
    private fun startLocationTracking() {
        if (!hasLocationPermissions()) {
            OptimizedLogger.e(TAG, "Missing location permissions")
            return
        }

        if (isLocationTracking) return

        try {
            val locationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LocationRequest.Builder(LOCATION_UPDATE_INTERVAL)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL)
                    .setMinUpdateDistanceMeters(DISTANCE_THRESHOLD_METERS)
                    .build()
            } else {
                LocationRequest.create().apply {
                    interval = LOCATION_UPDATE_INTERVAL
                    fastestInterval = FASTEST_LOCATION_INTERVAL
                    priority = Priority.PRIORITY_HIGH_ACCURACY
                    smallestDisplacement = DISTANCE_THRESHOLD_METERS
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            isLocationTracking = true
            updateNotification("Location tracking active")
            OptimizedLogger.d(TAG, "Location tracking started")
        } catch (e: SecurityException) {
            OptimizedLogger.e(TAG, "SecurityException starting location updates", e)
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error starting location tracking", e)
        }
    }

    private fun stopLocationTracking() {
        try {
            if (isLocationTracking) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                isLocationTracking = false
                OptimizedLogger.d(TAG, "Location tracking stopped")
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error stopping location tracking", e)
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processNewLocation(location)
                }
            }
        }
    }

    private fun processNewLocation(location: Location) {
        val previousLocation = lastLocation

        if (previousLocation == null ||
            previousLocation.distanceTo(location) >= DISTANCE_THRESHOLD_METERS) {

            saveLocationToDatabase(location)
            lastLocation = location

            OptimizedLogger.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
        }
    }

    private fun saveLocationToDatabase(location: Location) {
        serviceScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val locationEntity = LocationEntity(
                    timestamp = timestamp,
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // Save locally
                db.locationDao().insertLocation(locationEntity)

                // Upload to Firebase
                uploadLocationToFirebase(locationEntity)

                // Update sync time
                applicationContext.getSharedPreferences("location_sync", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_sync_time", timestamp)
                    .apply()

            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error saving location", e)
            }
        }
    }

    private suspend fun uploadLocationToFirebase(locationEntity: LocationEntity) {
        val userEmail = AuthManager.getCurrentUser()?.email
        if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
            return
        }

        try {
            val locationData = mapOf(
                "timestamp" to locationEntity.timestamp,
                "latitude" to locationEntity.latitude,
                "longitude" to locationEntity.longitude,
                "deviceId" to deviceId,
                "syncedAt" to System.currentTimeMillis()
            )

            val success = FirebaseServiceHelper.uploadLocation(userEmail, deviceId, locationData)
            if (success) {
                FirebaseServiceHelper.updateDeviceLastActive(userEmail, deviceId)
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error uploading location to Firebase", e)
        }
    }

    // SYNC COORDINATION METHODS
    private fun syncAll() {
        if (!AuthManager.isSignedIn()) {
            OptimizedLogger.w(TAG, "User not authenticated, cannot sync data")
            return
        }

        OptimizedLogger.d(TAG, "Starting coordinated sync of all data")

        try {
            val workManager = WorkManager.getInstance(applicationContext)

            // Create constraints for optimal sync conditions
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // Batch all workers with constraints
            val workers = listOf(
                OneTimeWorkRequestBuilder<CallLogWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<MessageWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<ContactsWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<DeviceInfoWorker>().setConstraints(constraints).build(),
                OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(constraints).build()
            )

            // Enqueue all workers
            workers.forEach { worker -> workManager.enqueue(worker) }

            OptimizedLogger.d(TAG, "Coordinated sync workers enqueued successfully")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error during coordinated sync", e)
        }
    }

    private fun checkTriggers() {
        if (!AuthManager.isSignedIn()) {
            return
        }

        serviceScope.launch {
            try {
                var shouldSync = false
                val workManager = WorkManager.getInstance(applicationContext)

                // Check thresholds efficiently
                if (CallLogWorker.shouldSync(applicationContext)) {
                    workManager.enqueue(OneTimeWorkRequestBuilder<CallLogWorker>().build())
                    shouldSync = true
                }

                if (MessageWorker.shouldSync(applicationContext)) {
                    workManager.enqueue(OneTimeWorkRequestBuilder<MessageWorker>().build())
                    shouldSync = true
                }

                if (shouldSync) {
                    workManager.enqueue(OneTimeWorkRequestBuilder<DeviceInfoWorker>().build())
                }
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error in coordinated trigger check", e)
            }
        }
    }

    // AUDIO RECORDING METHODS (Coordination only)
    private fun startAudioRecording() {
        if (!hasAudioPermissions()) {
            OptimizedLogger.e(TAG, "Missing audio permissions")
            return
        }

        if (isAudioRecording) return

        try {
            isAudioRecording = true
            updateNotification("Location + Audio monitoring active")

            // Start AudioRecordingService
            val audioIntent = Intent(this, AudioRecordingService::class.java)
            audioIntent.action = AudioRecordingService.ACTION_START_RECORDING
            startService(audioIntent)

            OptimizedLogger.d(TAG, "Audio recording coordinated through AudioRecordingService")
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error coordinating audio recording", e)
            isAudioRecording = false
        }
    }

    private fun stopAudioRecording() {
        try {
            if (isAudioRecording) {
                // Stop AudioRecordingService
                val audioIntent = Intent(this, AudioRecordingService::class.java)
                audioIntent.action = AudioRecordingService.ACTION_STOP_RECORDING
                startService(audioIntent)

                isAudioRecording = false
                updateNotification("Location tracking active")
                OptimizedLogger.d(TAG, "Audio recording stopped")
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error stopping audio recording", e)
        }
    }

    // PERMISSION CHECKS
    private fun hasLocationPermissions(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasForegroundLocation = if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasLocation && hasForegroundLocation
    }

    private fun hasAudioPermissions(): Boolean {
        val hasAudio = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasForegroundAudio = if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasAudio && hasForegroundAudio
    }

    // NOTIFICATION METHODS
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unified Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Location tracking and monitoring coordination"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String = "Home Guardian monitoring active"): Notification {
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
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        try {
            val notification = createNotification(message)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error updating notification", e)
        }
    }

    // POWER MANAGEMENT
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "UnifiedMonitoringService::WakeLock"
                )
                wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours max
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