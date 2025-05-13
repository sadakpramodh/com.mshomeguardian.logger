package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.DeviceInfoEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceInfoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firestore", e)
        null
    }

    companion object {
        private const val TAG = "DeviceInfoWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentTime = System.currentTimeMillis()

            // Get or create device info
            val deviceInfoEntity = getOrCreateDeviceInfo(currentTime)

            // Upload to Firestore if needed
            uploadDeviceInfo(deviceInfoEntity, currentTime)

            Log.d(TAG, "Device info sync completed.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing device info", e)
            Result.retry()
        }
    }

    private suspend fun getOrCreateDeviceInfo(currentTime: Long): DeviceInfoEntity {
        // Check if device info already exists
        val existingDeviceInfo = db.deviceInfoDao().getDeviceInfo(deviceId)

        if (existingDeviceInfo != null) {
            // Update last updated time
            db.deviceInfoDao().updateDeviceLastUpdated(deviceId, currentTime)
            return existingDeviceInfo.copy(lastUpdated = currentTime)
        }

        // Collect device information
        val deviceInfo = DeviceIdentifier.collectDeviceInfo(applicationContext)

        // Create new device info entity
        val deviceInfoEntity = DeviceInfoEntity(
            deviceId = deviceId,
            firstRegistered = currentTime,
            lastUpdated = currentTime,
            manufacturer = deviceInfo["manufacturer"] ?: "unknown",
            brand = deviceInfo["brand"] ?: "unknown",
            model = deviceInfo["model"] ?: "unknown",
            product = deviceInfo["product"] ?: "unknown",
            device = deviceInfo["device"] ?: "unknown",
            hardware = deviceInfo["hardware"] ?: "unknown",
            androidVersion = deviceInfo["android_version"] ?: "unknown",
            sdkVersion = deviceInfo["sdk_version"] ?: "unknown",
            buildId = deviceInfo["build_id"] ?: "unknown",
            androidId = deviceInfo["android_id"] ?: "unknown",
            networkOperatorName = deviceInfo["network_operator_name"],
            networkOperator = deviceInfo["network_operator"],
            networkCountryIso = deviceInfo["network_country_iso"],
            simOperator = deviceInfo["sim_operator"],
            simOperatorName = deviceInfo["sim_operator_name"],
            simCountryIso = deviceInfo["sim_country_iso"],
            imei = deviceInfo["imei"],
            phoneType = deviceInfo["phone_type"],
            isActive = true,
            uploadedToCloud = false
        )

        // Insert into database
        db.deviceInfoDao().insertDeviceInfo(deviceInfoEntity)
        Log.d(TAG, "New device info created and saved")

        return deviceInfoEntity
    }

    private suspend fun uploadDeviceInfo(deviceInfo: DeviceInfoEntity, currentTime: Long) {
        val firestoreInstance = firestore ?: return

        // Only upload if not already uploaded or if it's been updated
        if (!deviceInfo.uploadedToCloud || deviceInfo.uploadTimestamp != deviceInfo.lastUpdated) {
            try {
                firestoreInstance.collection("devices")
                    .document(deviceId)
                    .set(deviceInfo, SetOptions.merge())
                    .addOnSuccessListener {
                        // Mark as uploaded in a separate coroutine
                        GlobalScope.launch(Dispatchers.IO) {
                            db.deviceInfoDao().markDeviceInfoAsUploaded(deviceId, currentTime)
                            Log.d(TAG, "Device info marked as uploaded")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to upload device info", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading device info", e)
            }
        }
    }
}