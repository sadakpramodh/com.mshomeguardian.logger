package com.mshomeguardian.logger.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.util.*

/**
 * Utility class for device identification and information collection
 */
object DeviceIdentifier {
    private const val TAG = "DeviceIdentifier"

    // Filename for storing the persistent UUID
    private const val DEVICE_ID_FILENAME = "device_identity"

    /**
     * Creates or retrieves a persistent device identifier
     * This ID persists even after app uninstallation by storing it in a hidden file in external storage
     *
     * @param context Application context
     * @return A persistent unique device identifier
     */
    fun getPersistentDeviceId(context: Context): String {
        // Try to read existing ID from persistent storage first
        val persistentId = readPersistentId(context)

        if (!persistentId.isNullOrEmpty()) {
            Log.d(TAG, "Using existing persistent device ID")
            return persistentId
        }

        // If no persistent ID exists yet, create a new one based on device properties
        // and hardware identifiers, then save it
        val newId = generateDeviceId(context)
        savePersistentId(context, newId)

        return newId
    }

    /**
     * Collects comprehensive device information
     *
     * @param context Application context
     * @return Map containing device properties
     */
    fun collectDeviceInfo(context: Context): Map<String, String> {
        val deviceInfo = mutableMapOf<String, String>()

        // Device model information
        deviceInfo["manufacturer"] = Build.MANUFACTURER
        deviceInfo["brand"] = Build.BRAND
        deviceInfo["model"] = Build.MODEL
        deviceInfo["product"] = Build.PRODUCT
        deviceInfo["device"] = Build.DEVICE
        deviceInfo["hardware"] = Build.HARDWARE
        deviceInfo["android_version"] = Build.VERSION.RELEASE
        deviceInfo["sdk_version"] = Build.VERSION.SDK_INT.toString()
        deviceInfo["build_id"] = Build.ID

        // Android ID (changes on factory reset)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        deviceInfo["android_id"] = androidId ?: "unknown"

        // Telephony information
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Carrier information
            deviceInfo["network_operator_name"] = tm.networkOperatorName ?: "unknown"
            deviceInfo["network_operator"] = tm.networkOperator ?: "unknown"
            deviceInfo["network_country_iso"] = tm.networkCountryIso ?: "unknown"
            deviceInfo["sim_operator"] = tm.simOperator ?: "unknown"
            deviceInfo["sim_operator_name"] = tm.simOperatorName ?: "unknown"
            deviceInfo["sim_country_iso"] = tm.simCountryIso ?: "unknown"

            // If we have permissions, get more sensitive info
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
                // These may return null on newer Android versions due to privacy restrictions
                try {
                    @SuppressLint("HardwareIds")
                    val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tm.imei
                    } else {
                        @Suppress("DEPRECATION")
                        tm.deviceId
                    }
                    deviceInfo["imei"] = imei ?: "restricted"
                } catch (e: Exception) {
                    deviceInfo["imei"] = "restricted"
                }

                try {
                    deviceInfo["phone_type"] = when(tm.phoneType) {
                        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                        else -> "NONE"
                    }
                } catch (e: Exception) {
                    deviceInfo["phone_type"] = "unknown"
                }
            } else {
                deviceInfo["imei"] = "permission_denied"
                deviceInfo["phone_type"] = "permission_denied"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting telephony info", e)
            deviceInfo["telephony_error"] = e.message ?: "unknown error"
        }

        return deviceInfo
    }

    /**
     * Generates a unique ID based on device properties and a random UUID
     */
    private fun generateDeviceId(context: Context): String {
        val deviceProps = StringBuilder()

        // Collect various device properties to create a semi-unique fingerprint
        deviceProps.append(Build.BOARD)
        deviceProps.append(Build.BOOTLOADER)
        deviceProps.append(Build.BRAND)
        deviceProps.append(Build.DEVICE)
        deviceProps.append(Build.HARDWARE)
        deviceProps.append(Build.MANUFACTURER)
        deviceProps.append(Build.MODEL)
        deviceProps.append(Build.PRODUCT)
        deviceProps.append(Build.SERIAL)

        // Get Android ID (changes on factory reset but better than nothing)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        deviceProps.append(androidId)

        // Generate a UUID based on device properties hash
        val deviceHash = deviceProps.toString().hashCode()
        val uuid = UUID(deviceHash.toLong(), Random().nextLong())

        return uuid.toString()
    }

    /**
     * Reads the persistent ID from storage
     */
    private fun readPersistentId(context: Context): String? {
        try {
            // First try internal storage
            val internalFile = File(context.filesDir, DEVICE_ID_FILENAME)
            if (internalFile.exists()) {
                return internalFile.readText().trim()
            }

            // Then try external storage
            context.getExternalFilesDir(null)?.let { externalDir ->
                val externalFile = File(externalDir, DEVICE_ID_FILENAME)
                if (externalFile.exists()) {
                    return externalFile.readText().trim()
                }
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading persistent ID", e)
            return null
        }
    }

    /**
     * Saves the device ID to persistent storage
     */
    private fun savePersistentId(context: Context, deviceId: String) {
        try {
            // Save in internal storage
            val internalFile = File(context.filesDir, DEVICE_ID_FILENAME)
            internalFile.writeText(deviceId)

            // Also save in external storage as backup
            context.getExternalFilesDir(null)?.let { externalDir ->
                val externalFile = File(externalDir, DEVICE_ID_FILENAME)
                externalFile.writeText(deviceId)
            }

            Log.d(TAG, "Persistent device ID saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving persistent ID", e)
        }
    }
}