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
            if (filePath == null) {
                Log.e(TAG, "No file path provided")
                return@withContext Result.failure()
            }

            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: $filePath")
                return@withContext Result.failure()
            }

            Log.d(TAG, "Starting transcription for file: ${file.name}")

            // For language detection - currently just assuming English
            val language = "en"
            Log.d(TAG, "Detected language: $language")

            // Create a basic transcription
            val transcription = createBasicTranscription(file)

            // Upload transcription to Firebase
            val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            val uploaded = uploadTranscription(file.name, transcription, deviceId)

            // Also upload the audio file itself to Firebase Storage
            val audioUploaded = uploadAudioFile(file, deviceId)

            return@withContext if (uploaded && audioUploaded) {
                Log.d(TAG, "Transcription complete and uploaded successfully")
                Result.success()
            } else {
                Log.e(TAG, "Failed to upload transcription or audio")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in transcription worker", e)
            return@withContext Result.retry()
        }
    }

    private fun createBasicTranscription(audioFile: File): String {
        try {
            // Extract basic metadata about the audio
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val durationSecs = duration / 1000
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLong() ?: 0
            val bitrateKbps = bitrate / 1000

            retriever.release()

            // For now, we'll return a placeholder that includes some audio info
            return "Audio recording from ${audioFile.name}, duration: $durationSecs seconds, " +
                    "bitrate: $bitrateKbps kbps. This file has been uploaded to Firebase Storage. " +
                    "Full speech-to-text transcription will be integrated in a future update."

        } catch (e: Exception) {
            Log.e(TAG, "Error creating basic transcription", e)
            return "Audio file ${audioFile.name} has been uploaded to Firebase Storage. " +
                    "Transcription details unavailable: ${e.message}"
        }
    }

    private suspend fun uploadTranscription(fileName: String, transcription: String, deviceId: String): Boolean {
        try {
            // Create a map of the transcription data
            val transcriptionData = hashMapOf(
                "fileName" to fileName,
                "text" to transcription,
                "deviceId" to deviceId,
                "timestamp" to System.currentTimeMillis(),
                "language" to "en"
            )

            // Upload to Firestore
            val firestoreInstance = FirebaseFirestore.getInstance()
            val documentName = fileName.substringBeforeLast(".")

            return withContext(Dispatchers.IO) {
                try {
                    // Add to Firestore
                    val future = firestoreInstance.collection("devices")
                        .document(deviceId)
                        .collection("transcriptions")
                        .document(documentName)
                        .set(transcriptionData)

                    future.await() // Wait for completion

                    // Also create a text file with the transcription and upload to Storage
                    val tempFile = File(applicationContext.cacheDir, "$documentName.txt")
                    tempFile.writeText(transcription)

                    val storageRef = FirebaseStorage.getInstance().reference
                        .child("devices/$deviceId/transcriptions/$documentName.txt")

                    val uploadTask = storageRef.putFile(android.net.Uri.fromFile(tempFile))
                    uploadTask.await() // Wait for upload to complete

                    // Clean up temp file
                    tempFile.delete()

                    Log.d(TAG, "Transcription uploaded successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading transcription", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading transcription", e)
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