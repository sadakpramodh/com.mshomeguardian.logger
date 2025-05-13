package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Entity representing a call log entry with comprehensive details
 */
@Entity(
    tableName = "call_logs",
    indices = [
        Index("callId", unique = true),
        Index("phoneNumber"),
        Index("timestamp")
    ]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Call identification
    val callId: String,                   // Original call ID from Android
    val syncTimestamp: Long,              // When this record was last synced

    // Core call details
    val phoneNumber: String,              // The phone number involved
    val timestamp: Long,                  // When the call occurred
    val duration: Long,                   // Call duration in seconds
    val type: Int,                        // Incoming, outgoing, missed, etc.

    // Contact info if available
    val contactName: String?,             // Name from contacts if available
    val contactPhotoUri: String?,         // Contact photo URI if available

    // Status flags
    val isRead: Boolean,                  // Whether the call was viewed
    val isNew: Boolean,                   // Whether this call was newly synced

    // Tracking info
    val deletedLocally: Boolean = false,  // If the call was deleted on device
    val uploadedToCloud: Boolean = false, // If this record was uploaded to Firestore
    val uploadTimestamp: Long? = null,    // When this record was uploaded

    // Additional data that might be available
    val presentationType: Int? = null,    // How call was presented (e.g., HD voice)
    val callScreeningAppName: String? = null, // App that screened the call, if any
    val callScreeningComponentName: String? = null, // Component that screened the call
    val numberAttributes: String? = null, // Any attributes for the number
    val geoLocation: String? = null,      // Geocoded location if available
    val phoneAccountId: String? = null,   // Account ID for the call
    val features: Int? = null,            // Any additional call features
    val postDialDigits: String? = null,   // Post-dial digits
    val viaNumber: String? = null,        // Via number if any

    // Device Info at time of recording
    val deviceId: String                  // The persistent device ID
)