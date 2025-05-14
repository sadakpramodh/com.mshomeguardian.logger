package com.mshomeguardian.logger.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.LocationEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Service that monitors location changes and updates when a significant change occurs
 */
class LocationMonitoringService : Service() {

    companion object {
        private const val TAG = "LocationMonitoringService"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "location_monitoring_channel"

        // Distance threshold for updating location (50 meters)
        private const val DISTANCE_THRESHOLD_METERS = 50f

        // Interval for active location checks (default 5 minutes)
        private val LOCATION_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(5)

        // Fastest interval for location updates (default 2 minutes)
        private val FASTEST_LOCATION_INTERVAL = TimeUnit.MINUTES.toMillis(2)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null
    private lateinit var db: AppDatabase
    private lateinit var deviceId: String

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore", e)
            null
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize the database and device ID
        db = AppDatabase.getInstance(applicationContext)
        deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create the location callback
        createLocationCallback()

        // Create and show the notification
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start location updates
        startLocationUpdates()

        // Return sticky to automatically restart the service if it gets killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Process the new location
                    processNewLocation(location)
                }
            }
        }
    }

    private fun processNewLocation(location: Location) {
        val currentLocation = location
        val previousLocation = lastLocation

        // Check if this is the first location or if we've moved enough to trigger an update
        if (previousLocation == null ||
            previousLocation.distanceTo(currentLocation) >= DISTANCE_THRESHOLD_METERS) {

            // Save the new location
            saveLocationToDatabase(currentLocation)

            // Update the last location
            lastLocation = currentLocation

            Log.d(TAG, "Location changed significantly: lat=${currentLocation.latitude}, lng=${currentLocation.longitude}")
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

                // Save to local database
                db.locationDao().insertLocation(locationEntity)

                // Upload to Firebase
                uploadLocationToFirebase(locationEntity)

                Log.d(TAG, "Location saved: lat=${location.latitude}, lng=${location.longitude}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving location", e)
            }
        }
    }

    private fun uploadLocationToFirebase(locationEntity: LocationEntity) {
        // Skip if Firestore is not initialized
        val firestoreInstance = firestore ?: return

        try {
            firestoreInstance.collection("devices")
                .document(deviceId)
                .collection("locations")
                .document(locationEntity.timestamp.toString())
                .set(locationEntity, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Location uploaded to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore upload failed", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading to Firestore", e)
        }
    }

    private fun startLocationUpdates() {
        // Check if we have the necessary permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }

        // Create the location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        )
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Location Monitoring"
            val descriptionText = "Monitors location for Home Guardian"
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
            .setContentText("Monitoring your location for security")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}