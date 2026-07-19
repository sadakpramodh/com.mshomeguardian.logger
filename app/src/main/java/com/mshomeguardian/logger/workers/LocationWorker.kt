package com.mshomeguardian.logger.workers

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.LocationEntity
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "LocationWorker"
        private const val MAX_RETRIES = 3
        private var consecutiveLocationFailures = 0
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check if user is authenticated
            val userEmail = AuthManager.getCurrentUser()?.email
            if (userEmail == null) {
                Log.w(TAG, "User not authenticated, skipping location sync")
                return@withContext Result.success()
            }

            // Check if Firebase is available
            if (!FirebaseServiceHelper.isFirebaseAvailable()) {
                Log.w(TAG, "Firebase not available, saving locally only")
                return@withContext handleLocationWithoutFirebase()
            }

            val loc: Location? = LocationUtils.getLastKnownLocation(applicationContext)

            if (loc != null) {
                // Reset failure counter on success
                consecutiveLocationFailures = 0

                val ts = System.currentTimeMillis()
                val timezoneId = TimeZone.getDefault().id
                val entity = LocationEntity(
                    timestamp = ts,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracy = loc.accuracy,
                    altitude = loc.altitude,
                    bearing = loc.bearing,
                    speed = loc.speed,
                    provider = loc.provider ?: "unknown",
                    timezone = timezoneId
                )

                Log.d(TAG, "New location captured: lat=${loc.latitude}, lng=${loc.longitude}")

                // Save to local Room database first
                try {
                    db.locationDao().insertLocation(entity)
                    Log.d(TAG, "Location saved to local database")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save location to local database", e)
                    // Continue even if local save fails
                }

                // Upload to Firebase with new structure
                try {
                    val locationData = mapOf(
                        "timestamp" to ts,
                        "latitude" to loc.latitude,
                        "longitude" to loc.longitude,
                        "accuracy" to loc.accuracy.toDouble(),
                        "altitude" to loc.altitude,
                        "bearing" to loc.bearing.toDouble(),
                        "speed" to loc.speed.toDouble(),
                        "verticalAccuracy" to if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters.toDouble() else -1.0,
                        "speedAccuracy" to if (loc.hasSpeedAccuracy()) loc.speedAccuracyMetersPerSecond.toDouble() else -1.0,
                        "bearingAccuracy" to if (loc.hasBearingAccuracy()) loc.bearingAccuracyDegrees.toDouble() else -1.0,
                        "elapsedRealtimeNanos" to loc.elapsedRealtimeNanos,
                        "timezone" to timezoneId,
                        "deviceId" to deviceId,
                        "provider" to (loc.provider ?: "unknown"),
                        "syncedAt" to System.currentTimeMillis()
                    )

                    val success = FirebaseServiceHelper.uploadLocation(userEmail, deviceId, locationData)

                    if (success) {
                        Log.d(TAG, "Location uploaded to Firebase successfully")

                        // Update device last active
                        FirebaseServiceHelper.updateDeviceLastActive(userEmail, deviceId)

                        // Save last sync time to SharedPreferences
                        val prefs = applicationContext.getSharedPreferences(
                            "location_sync", Context.MODE_PRIVATE)
                        prefs.edit().putLong("last_sync_time", ts).apply()
                    } else {
                        Log.w(TAG, "Failed to upload location to Firebase")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading to Firebase", e)
                    // Don't fail the worker if Firebase upload fails
                }

                Result.success()
            } else {
                // Increment failure counter
                consecutiveLocationFailures++

                Log.e(TAG, "Location == null, attempt $consecutiveLocationFailures of $MAX_RETRIES")

                if (consecutiveLocationFailures >= MAX_RETRIES) {
                    // After multiple failures, succeed anyway to prevent endless retries
                    Log.w(TAG, "Giving up after $MAX_RETRIES location failures")
                    consecutiveLocationFailures = 0
                    Result.success()
                } else {
                    // Exponential backoff for retries (15s, 30s, 60s)
                    val backoffDelaySeconds = (15 * Math.pow(2.0, (consecutiveLocationFailures - 1).toDouble())).toLong()
                    Log.d(TAG, "Will retry in $backoffDelaySeconds seconds")

                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled error in worker", e)
            Result.retry()
        }
    }

    /**
     * Handle location capture when Firebase is not available
     */
    private suspend fun handleLocationWithoutFirebase(): Result {
        return try {
            val loc: Location? = LocationUtils.getLastKnownLocation(applicationContext)

            if (loc != null) {
                val ts = System.currentTimeMillis()
                val entity = LocationEntity(
                    timestamp = ts,
                    latitude = loc.latitude,
                    longitude = loc.longitude
                )

                // Save to local database only
                db.locationDao().insertLocation(entity)
                Log.d(TAG, "Location saved locally (Firebase unavailable)")

                // Update last sync time
                val prefs = applicationContext.getSharedPreferences(
                    "location_sync", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_sync_time", ts).apply()

                Result.success()
            } else {
                Log.w(TAG, "No location available")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in offline location handling", e)
            Result.retry()
        }
    }
}