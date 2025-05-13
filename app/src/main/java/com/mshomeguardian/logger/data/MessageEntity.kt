package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an SMS/MMS message with detailed information
 */
@Entity(
    tableName = "message_logs",
    indices = [
        Index("messageId", unique = true),
        Index("phoneNumber"),
        Index("timestamp")
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Message identification
    val messageId: String,                // Original message ID from Android
    val syncTimestamp: Long,              // When this message was last synced

    // Core message details
    val phoneNumber: String,              // The phone number involved
    val timestamp: Long,                  // When the message was sent/received
    val body: String?,                    // The message content
    val type: Int,                        // Incoming, outgoing, draft, etc.
    val subject: String?,                 // Message subject (mainly for MMS)
    val messageType: String,              // SMS or MMS

    // Contact info if available
    val contactName: String?,             // Name from contacts if available

    // Status flags
    val isRead: Boolean,                  // Whether the message was read
    val seen: Boolean,                    // Whether the message was seen
    val deliveryStatus: Int?,             // Delivery status if available
    val errorCode: Int?,                  // Error code if message failed

    // Tracking info
    val deletedLocally: Boolean = false,  // If the message was deleted on device
    val uploadedToCloud: Boolean = false, // If this record was uploaded to Firestore
    val uploadTimestamp: Long? = null,    // When this record was uploaded

    // Additional data
    val thread_id: Long?,                 // Thread ID for the conversation
    val person: String?,                  // Person identifier
    val protocol: Int?,                   // Protocol used
    val replyPathPresent: Boolean?,       // If reply path is present
    val serviceCenter: String?,           // Service center address
    val status: Int?,                     // Status code

    // Device Info at time of recording
    val deviceId: String                  // The persistent device ID
)