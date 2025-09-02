package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.DeviceInfoEntity
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceInfoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "DeviceInfoWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check authentication first
            val userEmail = AuthManager.getCurrentUser()?.email
            if (userEmail == null) {
                Log.w(TAG, "User not authenticated, skipping device info sync")
                return@withContext Result.success()
            }

            // Check if Firebase is available
            if (!FirebaseServiceHelper.isFirebaseAvailable()) {
                Log.w(TAG, "Firebase not available, skipping device info sync")
                return@withContext Result.success()
            }

            val currentTime = System.currentTimeMillis()

            // Get or create device info
            val deviceInfoEntity = getOrCreateDeviceInfo(currentTime)

            // Upload to Firebase using new structure
            val success = uploadDeviceInfo(userEmail, deviceInfoEntity, currentTime)

            if (success) {
                Log.d(TAG, "Device info sync completed successfully.")
            } else {
                Log.w(TAG, "Device info sync completed but upload failed.")
            }

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
            timezone = deviceInfo["timezone"],
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

    private suspend fun uploadDeviceInfo(
        userEmail: String,
        deviceInfo: DeviceInfoEntity,
        currentTime: Long
    ): Boolean {
        // Only upload if not already uploaded or if it's been updated
        if (!deviceInfo.uploadedToCloud || deviceInfo.uploadTimestamp != deviceInfo.lastUpdated) {
            try {
                // Create device data map
                val deviceData = mapOf(
                    "deviceId" to deviceInfo.deviceId,
                    "firstRegistered" to deviceInfo.firstRegistered,
                    "lastUpdated" to deviceInfo.lastUpdated,
                    "manufacturer" to deviceInfo.manufacturer,
                    "brand" to deviceInfo.brand,
                    "model" to deviceInfo.model,
                    "product" to deviceInfo.product,
                    "device" to deviceInfo.device,
                    "hardware" to deviceInfo.hardware,
                    "androidVersion" to deviceInfo.androidVersion,
                    "sdkVersion" to deviceInfo.sdkVersion,
                    "buildId" to deviceInfo.buildId,
                    "androidId" to deviceInfo.androidId,
                    "networkOperatorName" to (deviceInfo.networkOperatorName ?: ""),
                    "networkOperator" to (deviceInfo.networkOperator ?: ""),
                    "networkCountryIso" to (deviceInfo.networkCountryIso ?: ""),
                    "simOperator" to (deviceInfo.simOperator ?: ""),
                    "simOperatorName" to (deviceInfo.simOperatorName ?: ""),
                    "simCountryIso" to (deviceInfo.simCountryIso ?: ""),
                    "imei" to (deviceInfo.imei ?: ""),
                    "phoneType" to (deviceInfo.phoneType ?: ""),
                    "timezone" to (deviceInfo.timezone ?: ""),
                    "isActive" to deviceInfo.isActive,
                    "uploadedAt" to currentTime
                )

                // Upload using new Firebase structure
                val success = FirebaseServiceHelper.uploadDeviceInfo(userEmail, deviceId, deviceData)

                if (success) {
                    // Mark as uploaded in database
                    db.deviceInfoDao().markDeviceInfoAsUploaded(deviceId, currentTime)
                    Log.d(TAG, "Device info uploaded successfully")
                    return true
                } else {
                    Log.e(TAG, "Failed to upload device info")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading device info", e)
                return false
            }
        } else {
            Log.d(TAG, "Device info already uploaded and up to date")
            return true
        }
    }
}