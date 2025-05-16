package com.mshomeguardian.logger.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.TelephonyManager
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
        private const val EXTRA_INCOMING_NUMBER = "incoming_number"

        // States for call
        private const val STATE_RINGING = "RINGING"
        private const val STATE_OFFHOOK = "OFFHOOK"
        private const val STATE_IDLE = "IDLE"

        // Last known call state - used to detect actual new calls
        private var lastCallState: String? = null
        private var lastPhoneNumber: String? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            // Handle new SMS received
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                Log.d(TAG, "New SMS received")
                handleSmsReceived(context, intent)
            }

            // Handle phone state changes
            ACTION_PHONE_STATE -> {
                val state = intent.getStringExtra(EXTRA_STATE)
                val phoneNumber = intent.getStringExtra(EXTRA_INCOMING_NUMBER)

                Log.d(TAG, "Phone state changed: $state, number: ${phoneNumber ?: "unknown"}")
                handleCallStateChange(context, state, phoneNumber)
            }
        }
    }

    private fun handleSmsReceived(context: Context, intent: Intent) {
        // Extract SMS message data if needed
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages != null && messages.isNotEmpty()) {
            Log.d(TAG, "Received ${messages.size} SMS messages")

            // Immediately sync the new message
            DataSyncManager.onMessageDetected(context)
        }
    }

    private fun handleCallStateChange(context: Context, state: String?, phoneNumber: String?) {
        // State transitions we care about:
        // 1. IDLE -> RINGING = New incoming call
        // 2. IDLE -> OFFHOOK = New outgoing call
        // 3. RINGING -> OFFHOOK = Incoming call answered
        // 4. RINGING/OFFHOOK -> IDLE = Call ended

        if (state == null) return

        when (state) {
            STATE_RINGING -> {
                // New incoming call
                if (lastCallState == STATE_IDLE || lastCallState == null) {
                    Log.d(TAG, "New incoming call from ${phoneNumber ?: "unknown"}")
                    lastPhoneNumber = phoneNumber
                    DataSyncManager.onCallDetected(context)
                }
            }
            STATE_OFFHOOK -> {
                // Either answered an incoming call or started an outgoing call
                if (lastCallState == STATE_IDLE || lastCallState == null) {
                    // New outgoing call
                    Log.d(TAG, "New outgoing call to ${phoneNumber ?: "unknown"}")
                    lastPhoneNumber = phoneNumber
                    DataSyncManager.onCallDetected(context)
                }
                // If lastCallState was RINGING, it's an answered incoming call, already detected
            }
            STATE_IDLE -> {
                // Call ended, no special handling needed for sync purposes
                // Already detected the call when it started
                lastPhoneNumber = null
            }
        }

        // Update last call state
        lastCallState = state
    }
}