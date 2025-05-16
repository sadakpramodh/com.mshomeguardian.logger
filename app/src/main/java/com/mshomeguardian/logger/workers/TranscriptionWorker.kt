package com.mshomeguardian.logger.workers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Simplified implementation of TranscriptionWorker that focuses on audio upload
 * rather than transcription functionality. This version removes dependencies
 * on external libraries like DeepSpeech that are causing build issues.
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
            val filePath = inputData.getString("file_path")
            val deviceId = inputData.getString("device_id") ?: DeviceIdentifier.getPersistentDeviceId(applicationContext)

            if (filePath == null) {
                Log.e(TAG, "No file path provided")
                return@withContext Result.failure()
            }

            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return@withContext Result.failure()
            }

            Log.d(TAG, "Starting processing for file: ${file.name}")

            // Extract basic info about the audio
            val basicInfo = createBasicInfo(file)

            // Upload the audio file to Firebase Storage
            val audioUploaded = uploadAudioFile(file, deviceId)

            // Upload basic info to Firestore
            val metadataUploaded = uploadMetadata(file.name, basicInfo, deviceId)

            return@withContext if (audioUploaded && metadataUploaded) {
                Log.d(TAG, "Audio file uploaded successfully")
                Result.success()
            } else {
                Log.e(TAG, "Failed to upload audio or metadata")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in transcription worker", e)
            return@withContext Result.retry()
        }
    }

    private fun createBasicInfo(audioFile: File): String {
        try {
            // Extract basic metadata about the audio
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)

            val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationString?.toLongOrNull() ?: 0L
            val durationSecs = duration / 1000L
            val minutes = durationSecs / 60
            val seconds = durationSecs % 60

            val timestamp = audioFile.name
                .substringAfter("recording_")
                .substringBefore(".wav")
                .replace("_", ":")

            retriever.release()

            return "Audio recording from $timestamp\n" +
                    "Duration: $minutes minutes and $seconds seconds\n" +
                    "This recording has been securely uploaded to Firebase Storage."

        } catch (e: Exception) {
            Log.e(TAG, "Error creating basic info", e)
            return "Audio file ${audioFile.name} has been uploaded to Firebase Storage. " +
                    "Details unavailable: ${e.message}"
        }
    }

    private suspend fun uploadMetadata(fileName: String, info: String, deviceId: String): Boolean {
        try {
            // Create a map of the audio metadata
            val metadata = hashMapOf(
                "fileName" to fileName,
                "info" to info,
                "deviceId" to deviceId,
                "timestamp" to System.currentTimeMillis(),
                "status" to "uploaded_without_transcription"
            )

            // Upload to Firestore
            val firestoreInstance = FirebaseFirestore.getInstance()
            val documentId = fileName.substringBeforeLast(".")

            return withContext(Dispatchers.IO) {
                try {
                    // Add to Firestore
                    firestoreInstance.collection("devices")
                        .document(deviceId)
                        .collection("audio_recordings")
                        .document(documentId)
                        .set(metadata)
                        .await()

                    Log.d(TAG, "Audio metadata uploaded successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading audio metadata", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing metadata upload", e)
            return false
        }
    }

    private suspend fun uploadAudioFile(audioFile: File, deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val storageRef = FirebaseStorage.getInstance().reference
                val audioRef = storageRef.child("devices/$deviceId/audio/${audioFile.name}")

                val uploadTask = audioRef.putFile(android.net.Uri.fromFile(audioFile))

                try {
                    uploadTask.await()
                    Log.d(TAG, "Audio file uploaded successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading audio file", e)

                    // Try with a retry
                    try {
                        val retryUploadTask = audioRef.putFile(android.net.Uri.fromFile(audioFile))
                        retryUploadTask.await()
                        Log.d(TAG, "Audio file uploaded successfully after retry")
                        true
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error uploading audio file even after retry", e2)
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up audio file upload", e)
                false
            }
        }
    }
}