package com.mshomeguardian.logger.workers

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.LocationEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firestore", e)
        null
    }

    companion object {
        private const val TAG = "LocationWorker"
        private const val MAX_RETRIES = 3
        private var consecutiveLocationFailures = 0
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val loc: Location? = LocationUtils.getLastKnownLocation(applicationContext)

            if (loc != null) {
                consecutiveLocationFailures = 0
                val ts = System.currentTimeMillis()
                val entity = LocationEntity(ts, loc.latitude, loc.longitude)

                Log.d(TAG, "New location captured: lat=${loc.latitude}, lng=${loc.longitude}")

                try {
                    db.locationDao().insertLocation(entity)
                    Log.d(TAG, "Location saved to local database")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save location to local database", e)
                }

                val firestoreInstance = firestore
                if (firestoreInstance != null) {
                    try {
                        firestoreInstance.collection("devices")
                            .document(deviceId)
                            .collection("locations")
                            .document(ts.toString())
                            .set(entity, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d(TAG, "Location uploaded to Firestore")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Firestore upload failed", e)
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading to Firestore", e)
                    }
                } else {
                    Log.w(TAG, "Firestore not configured, skipping upload")
                }

                Result.success()
            } else {
                consecutiveLocationFailures++
                Log.e(TAG, "Location == null, attempt $consecutiveLocationFailures of $MAX_RETRIES")

                if (consecutiveLocationFailures >= MAX_RETRIES) {
                    Log.w(TAG, "Giving up after $MAX_RETRIES location failures")
                    consecutiveLocationFailures = 0
                    Result.success()
                } else {
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
}