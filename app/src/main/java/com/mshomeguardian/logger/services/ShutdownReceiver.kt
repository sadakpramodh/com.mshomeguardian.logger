package com.mshomeguardian.logger.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShutdownReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ShutdownReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SHUTDOWN && context != null) {
            Log.d(TAG, "Device shutting down")
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail() ?: return
            val deviceId = DeviceIdentifier.getPersistentDeviceId(context)
            val data = hashMapOf<String, Any>(
                "event" to "shutdown",
                "timestamp" to System.currentTimeMillis(),
                "deviceId" to deviceId
            )
            CoroutineScope(Dispatchers.IO).launch {
                FirebaseServiceHelper.uploadSystemEvent(userEmail, deviceId, data)
            }
        }
    }
}
