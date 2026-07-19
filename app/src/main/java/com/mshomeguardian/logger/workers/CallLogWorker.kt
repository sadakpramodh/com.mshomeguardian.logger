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
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.CallLogEntity
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class CallLogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "CallLogWorker"
        private const val SYNC_LIMIT = 500
        private const val CALL_COUNT_THRESHOLD = 3

        suspend fun shouldSync(context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CALL_LOG
                ) != PackageManager.PERMISSION_GRANTED) {
                return false
            }

            if (!AuthManager.isSignedIn()) {
                return false
            }

            try {
                val lastSyncTime = context.getSharedPreferences(
                    "call_log_sync", Context.MODE_PRIVATE).getLong("last_sync_time", 0)

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
            // Verify authentication first
            val userEmail = AuthManager.getCurrentUser()?.email
            if (userEmail == null) {
                Log.w(TAG, "User not authenticated, skipping call log sync")
                return@withContext Result.success()
            }

            Log.d(TAG, "Starting call log sync for user: $userEmail")

            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_CALL_LOG
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing READ_CALL_LOG permission")
                return@withContext Result.failure()
            }

            // Check Firebase availability
            if (!FirebaseServiceHelper.isFirebaseAvailable()) {
                Log.w(TAG, "Firebase not available, skipping call log sync")
                return@withContext Result.success()
            }

            val prefs = applicationContext.getSharedPreferences("call_log_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time", 0)
            val currentTime = System.currentTimeMillis()

            Log.d(TAG, "Last sync time: $lastSyncTime, Current time: $currentTime")

            // Sync call logs to local database
            val syncCount = syncCallLogs(lastSyncTime, currentTime)
            Log.d(TAG, "Synced $syncCount call logs to local database")

            // Upload to Firebase with correct structure
            val uploadCount = uploadNewRecords(userEmail)
            Log.d(TAG, "Uploaded $uploadCount call logs to Firebase")

            // Update last sync time
            prefs.edit().putLong("last_sync_time", currentTime).apply()

            Log.d(TAG, "Call log sync completed successfully")
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

            val selection = "${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(lastSyncTime.toString())
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

                    val existingCallLog = db.callLogDao().getCallLogByCallId(callId)
                    if (existingCallLog == null) {
                        callLogs.add(callLogEntity)
                    } else {
                        if (existingCallLog.isRead != isRead ||
                            existingCallLog.duration != duration ||
                            existingCallLog.contactName != name) {

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

    private suspend fun uploadNewRecords(userEmail: String): Int {
        try {
            val notUploadedCallLogs = db.callLogDao().getNotUploadedCallLogs()
            Log.d(TAG, "Found ${notUploadedCallLogs.size} call logs to upload")

            var successCount = 0

            for (callLog in notUploadedCallLogs) {
                try {
                    val callLogData = mapOf(
                        "callId" to callLog.callId,
                        "syncTimestamp" to callLog.syncTimestamp,
                        "phoneNumber" to callLog.phoneNumber,
                        "timestamp" to callLog.timestamp,
                        "duration" to callLog.duration,
                        "type" to callLog.type,
                        "contactName" to (callLog.contactName ?: ""),
                        "contactPhotoUri" to (callLog.contactPhotoUri ?: ""),
                        "isRead" to callLog.isRead,
                        "isNew" to callLog.isNew,
                        "timezone" to callLog.timezone,
                        "deviceId" to deviceId,
                        "uploadedAt" to System.currentTimeMillis()
                    )

                    val sanitizedEmail = FirebaseServiceHelper.sanitizeEmailForFirestore(userEmail)
                    val firestorePath = "users/$sanitizedEmail/devices/$deviceId/call_logs"
                    Log.d(TAG, "Uploading to $firestorePath")
                    val success = FirebaseServiceHelper.uploadCallLog(userEmail, deviceId, callLogData)

                    if (success) {
                        val uploadTime = System.currentTimeMillis()
                        db.callLogDao().markCallLogAsUploaded(callLog.id, uploadTime)
                        successCount++
                        Log.d(TAG, "Call log ${callLog.callId} uploaded successfully to call_logs collection")
                    } else {
                        Log.w(TAG, "Failed to upload call log ${callLog.callId}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading call log ${callLog.callId}", e)
                }
            }

            return successCount
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadNewRecords", e)
            return 0
        }
    }
}