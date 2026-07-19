package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fallback audio sync worker. Scans Room for audio recordings with
 * uploadedToCloud = false and re-attempts Firebase Storage + Firestore upload
 * using the correct UUID-based recordingId stored in Room.
 *
 * This runs periodically as a safety net for recordings that failed to upload
 * during AudioRecordingService runs (e.g. due to transient network issues).
 */
class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TranscriptionWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
                ?: return@withContext Result.success() // not signed in, skip silently

            val db = AppDatabase.getInstance(applicationContext)
            val pending = db.audioRecordingDao().getNotUploadedRecordings()

            if (pending.isEmpty()) {
                Log.d(TAG, "No pending audio recordings to sync")
                return@withContext Result.success()
            }

            Log.d(TAG, "Found ${pending.size} unuploaded audio recordings, syncing...")
            var failed = 0

            for (recording in pending) {
                val file = File(recording.filePath)

                // File deleted locally — mark uploaded to stop retrying
                if (!file.exists()) {
                    db.audioRecordingDao().markRecordingAsUploaded(
                        recording.recordingId,
                        System.currentTimeMillis()
                    )
                    continue
                }

                val deviceId = recording.deviceId

                // Upload audio file to Firebase Storage
                val storageRef = FirebaseServiceHelper.getAudioStorageReference(
                    userEmail, deviceId, recording.fileName
                )
                if (storageRef == null) { failed++; continue }

                val fileUploaded = try {
                    storageRef.putFile(android.net.Uri.fromFile(file)).await()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Storage upload failed for ${recording.fileName}", e)
                    false
                }

                if (!fileUploaded) { failed++; continue }

                // Upload metadata to Firestore using the UUID recordingId from Room
                val metadata = hashMapOf<String, Any>(
                    "recordingId" to recording.recordingId,
                    "fileName" to recording.fileName,
                    "startTime" to recording.startTime,
                    "endTime" to recording.endTime,
                    "duration" to recording.duration,
                    "fileSize" to recording.fileSize,
                    "transcriptionStatus" to recording.transcriptionStatus.name,
                    "timezone" to recording.timezone,
                    "deviceId" to deviceId,
                    "uploadTime" to System.currentTimeMillis(),
                    "transcription" to (recording.transcription ?: "")
                )

                val metaUploaded = FirebaseServiceHelper.uploadAudioRecording(
                    userEmail, deviceId, metadata
                )

                if (metaUploaded) {
                    db.audioRecordingDao().markRecordingAsUploaded(
                        recording.recordingId,
                        System.currentTimeMillis()
                    )
                    Log.d(TAG, "Audio recording synced: ${recording.recordingId}")
                } else {
                    failed++
                }
            }

            if (failed > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in audio sync worker", e)
            Result.retry()
        }
    }
}