package com.mshomeguardian.logger.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.mshomeguardian.logger.utils.DataSyncManager

/**
 * Receiver that listens for new calls and messages
 */
class CommunicationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CommunicationReceiver"

        // Actions for call state changes
        private const val ACTION_PHONE_STATE = "android.intent.action.PHONE_STATE"

        // Extra for call state
        private const val EXTRA_STATE = "state"

        // State for ringing
        private const val STATE_RINGING = "RINGING"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            // Handle new SMS received
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                Log.d(TAG, "New SMS received")
                DataSyncManager.onMessageDetected(context)
            }

            // Handle phone state changes
            ACTION_PHONE_STATE -> {
                val state = intent.getStringExtra(EXTRA_STATE)
                if (state == STATE_RINGING) {
                    Log.d(TAG, "Phone is ringing")
                    DataSyncManager.onCallDetected(context)
                }
            }
        }
    }
}