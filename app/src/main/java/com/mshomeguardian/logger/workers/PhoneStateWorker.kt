package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.HashMap

class PhoneStateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firestore", e)
        null
    }

    companion object {
        private const val TAG = "PhoneStateWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing READ_PHONE_STATE permission")
                return@withContext Result.failure()
            }

            // Get current timestamp
            val currentTime = System.currentTimeMillis()

            // Collect phone state info
            val phoneStateInfo = collectPhoneStateInfo()

            // Add metadata
            phoneStateInfo["timestamp"] = currentTime
            phoneStateInfo["deviceId"] = deviceId

            // Upload to Firestore
            uploadPhoneState(currentTime.toString(), phoneStateInfo)

            Log.d(TAG, "Phone state sync completed.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing phone state", e)
            Result.retry()
        }
    }

    private fun collectPhoneStateInfo(): HashMap<String, Any> {
        val phoneStateMap = HashMap<String, Any>()

        try {
            val tm = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Network information
            phoneStateMap["networkOperator"] = tm.networkOperator ?: "unknown"
            phoneStateMap["networkOperatorName"] = tm.networkOperatorName ?: "unknown"
            phoneStateMap["networkCountryIso"] = tm.networkCountryIso ?: "unknown"
            phoneStateMap["networkType"] = getNetworkTypeName(tm.networkType)

            // SIM information
            phoneStateMap["simOperator"] = tm.simOperator ?: "unknown"
            phoneStateMap["simOperatorName"] = tm.simOperatorName ?: "unknown"
            phoneStateMap["simCountryIso"] = tm.simCountryIso ?: "unknown"
            phoneStateMap["simState"] = getSimStateName(tm.simState)

            // Phone type
            phoneStateMap["phoneType"] = getPhoneTypeName(tm.phoneType)

            // Data state
            phoneStateMap["dataState"] = getDataStateName(tm.dataState)
            phoneStateMap["dataActivity"] = getDataActivityName(tm.dataActivity)

            // Call state if supported
            phoneStateMap["callState"] = getCallStateName(tm.callState)

            // Signal strength will be collected in a separate worker or with a listener
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting phone state info", e)
            phoneStateMap["error"] = e.message ?: "unknown error"
        }

        return phoneStateMap
    }

    private fun getNetworkTypeName(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            // Add newer constants as they become available
            else -> "UNKNOWN"
        }
    }

    private fun getSimStateName(simState: Int): String {
        return when (simState) {
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "PERM_DISABLED"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "CARD_IO_ERROR"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "CARD_RESTRICTED"
            else -> "UNKNOWN"
        }
    }

    private fun getPhoneTypeName(phoneType: Int): String {
        return when (phoneType) {
            TelephonyManager.PHONE_TYPE_NONE -> "NONE"
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            else -> "UNKNOWN"
        }
    }

    private fun getDataStateName(dataState: Int): String {
        return when (dataState) {
            TelephonyManager.DATA_DISCONNECTED -> "DISCONNECTED"
            TelephonyManager.DATA_CONNECTING -> "CONNECTING"
            TelephonyManager.DATA_CONNECTED -> "CONNECTED"
            TelephonyManager.DATA_SUSPENDED -> "SUSPENDED"
            else -> "UNKNOWN"
        }
    }

    private fun getDataActivityName(dataActivity: Int): String {
        return when (dataActivity) {
            TelephonyManager.DATA_ACTIVITY_NONE -> "NONE"
            TelephonyManager.DATA_ACTIVITY_IN -> "IN"
            TelephonyManager.DATA_ACTIVITY_OUT -> "OUT"
            TelephonyManager.DATA_ACTIVITY_INOUT -> "INOUT"
            TelephonyManager.DATA_ACTIVITY_DORMANT -> "DORMANT"
            else -> "UNKNOWN"
        }
    }

    private fun getCallStateName(callState: Int): String {
        return when (callState) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN"
        }
    }

    private suspend fun uploadPhoneState(id: String, phoneStateMap: Map<String, Any>) {
        val firestoreInstance = firestore ?: return

        try {
            firestoreInstance.collection("devices")
                .document(deviceId)
                .collection("phone_state")
                .document(id)
                .set(phoneStateMap, SetOptions.merge())
                .await()

            Log.d(TAG, "Phone state uploaded to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload phone state", e)
        }
    }
}