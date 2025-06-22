package com.mshomeguardian.logger.utils

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.LocationEntity
import com.mshomeguardian.logger.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Manages Firebase Authentication for the Home Guardian app
 */
object AuthManager {
    private const val TAG = "AuthManager"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /**
     * Get the current authenticated user
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Check if user is currently signed in
     */
    fun isSignedIn(): Boolean {
        return getCurrentUser() != null
    }

    /**
     * Get the current user's UID (for Firestore security rules)
     */
    fun getCurrentUserId(): String? {
        return getCurrentUser()?.uid
    }

    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            if (result.user != null) {
                Log.d(TAG, "Sign in successful for user: ${result.user?.email}")
                AuthResult.Success(result.user!!)
            } else {
                Log.e(TAG, "Sign in failed: No user returned")
                AuthResult.Error("Authentication failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult.Error(e.message ?: "Authentication failed")
        }
    }

    /**
     * Create a new user account
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            if (result.user != null) {
                Log.d(TAG, "Account creation successful for user: ${result.user?.email}")
                AuthResult.Success(result.user!!)
            } else {
                Log.e(TAG, "Account creation failed: No user returned")
                AuthResult.Error("Account creation failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account creation failed", e)
            AuthResult.Error(e.message ?: "Account creation failed")
        }
    }

    /**
     * Sign in anonymously (for testing or guest access)
     */
    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            if (result.user != null) {
                Log.d(TAG, "Anonymous sign in successful")
                AuthResult.Success(result.user!!)
            } else {
                Log.e(TAG, "Anonymous sign in failed: No user returned")
                AuthResult.Error("Anonymous authentication failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign in failed", e)
            AuthResult.Error(e.message ?: "Anonymous authentication failed")
        }
    }

    /**
     * Sign out the current user
     */
    fun signOut() {
        try {
            auth.signOut()
            Log.d(TAG, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }

    /**
     * Save authentication credentials for automatic sign-in
     */
    fun saveCredentials(context: Context, email: String, password: String) {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("email", email)
            .putString("password", password)
            .apply()
        Log.d(TAG, "Credentials saved for automatic sign-in")
    }

    /**
     * Get saved credentials for automatic sign-in
     */
    fun getSavedCredentials(context: Context): Pair<String?, String?> {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null)
        val password = prefs.getString("password", null)
        return Pair(email, password)
    }

    /**
     * Clear saved credentials
     */
    fun clearSavedCredentials(context: Context) {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Saved credentials cleared")
    }

    /**
     * Attempt automatic sign-in using saved credentials
     */
    suspend fun attemptAutoSignIn(context: Context): AuthResult {
        val (email, password) = getSavedCredentials(context)

        return if (email != null && password != null) {
            Log.d(TAG, "Attempting automatic sign-in with saved credentials")
            signInWithEmailAndPassword(email, password)
        } else {
            Log.d(TAG, "No saved credentials found")
            AuthResult.Error("No saved credentials")
        }
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent to: $email")
            AuthResult.Success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send password reset email", e)
            AuthResult.Error(e.message ?: "Failed to send password reset email")
        }
    }
}

/**
 * Sealed class to represent authentication results
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser?) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Service that monitors location changes and updates when a significant change occurs
 * Updated for Android 14+ compatibility with proper permission handling
 */
class LocationMonitoringService : Service() {

    companion object {
        private const val TAG = "LocationMonitoringService"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "location_monitoring_channel"

        // Distance threshold for updating location (1 meter)
        private const val DISTANCE_THRESHOLD_METERS = 1.0f

        // Interval for active location checks (5 minutes)
        private val LOCATION_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(5)

        // Fastest interval for location updates (30 seconds)
        private val FASTEST_LOCATION_INTERVAL = TimeUnit.SECONDS.toMillis(30)
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

        // Create notification channel for Android 8.0+
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check authentication first
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated - stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        // Check for required permissions
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions - stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // Start as a foreground service for Android 8.0+
            startForeground(NOTIFICATION_ID, createNotification())

            // Start location updates
            startLocationUpdates()

            Log.d(TAG, "LocationMonitoringService started successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error starting LocationMonitoringService", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Return sticky to automatically restart the service if it gets killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        Log.d(TAG, "LocationMonitoringService destroyed")
    }

    /**
     * Check if all required permissions are granted
     */
    private fun hasRequiredPermissions(): Boolean {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasForegroundServicePermission = if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }

        return (hasLocationPermission || hasCoarseLocationPermission) && hasForegroundServicePermission
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

            Log.d(
                TAG,
                "Location changed significantly: lat=${currentLocation.latitude}, lng=${currentLocation.longitude}, distanceMoved=${
                    previousLocation?.distanceTo(currentLocation) ?: 0f
                }m"
            )
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

                // Update shared preferences for last sync time
                updateLastSyncTime(timestamp)

                Log.d(TAG, "Location saved: lat=${location.latitude}, lng=${location.longitude}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving location", e)
            }
        }
    }

    private fun updateLastSyncTime(timestamp: Long) {
        applicationContext.getSharedPreferences("location_sync", MODE_PRIVATE)
            .edit()
            .putLong("last_sync_time", timestamp)
            .apply()
    }

    private fun uploadLocationToFirebase(locationEntity: LocationEntity) {
        // Skip if not authenticated or Firebase not available
        val userEmail = AuthManager.getCurrentUser()?.email
        if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
            Log.w(TAG, "Cannot upload to Firebase - user not authenticated or Firebase unavailable")
            return
        }

        try {
            // Use the new Firebase structure with email-based paths
            serviceScope.launch {
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
                        Log.d(TAG, "Location uploaded to Firebase with new structure")
                        // Update device last active timestamp
                        FirebaseServiceHelper.updateDeviceLastActive(userEmail, deviceId)
                    } else {
                        Log.w(TAG, "Failed to upload location to Firebase")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading location to Firebase", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadLocationToFirebase", e)
        }
    }

    private fun startLocationUpdates() {
        // Double-check permissions before starting
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Cannot start location updates - missing permissions")
            stopSelf()
            return
        }

        // Create the location request with higher accuracy for Android 8+
        val locationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 and above, use the new Builder constructor
            LocationRequest.Builder(LOCATION_UPDATE_INTERVAL)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL)
                .setMinUpdateDistanceMeters(DISTANCE_THRESHOLD_METERS)
                .setWaitForAccurateLocation(true)
                .build()
        } else {
            // For Android 8-11, use the older style builder
            LocationRequest.create().apply {
                interval = LOCATION_UPDATE_INTERVAL
                fastestInterval = FASTEST_LOCATION_INTERVAL
                priority = Priority.PRIORITY_HIGH_ACCURACY
                smallestDisplacement = DISTANCE_THRESHOLD_METERS
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started with ${DISTANCE_THRESHOLD_METERS}m threshold")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting location updates", e)
            stopSelf()
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

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Create a pending intent to launch the app when notification is tapped
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Home Guardian")
            .setContentText("Monitoring your location for security")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}