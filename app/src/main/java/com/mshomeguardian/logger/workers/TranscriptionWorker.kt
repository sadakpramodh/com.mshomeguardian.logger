package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.AudioRecordingEntity
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.SpeechToTextManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)
    private val speechToTextManager = SpeechToTextManager(context.applicationContext)

    private val firestore = try { Firebase.firestore } catch (e: Exception) { null }

    companion object {
        private const val TAG = "TranscriptionWorker"
        private const val MAX_TRANSCRIPTION_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get recordings pending transcription
            val pendingRecordings = db.audioRecordingDao().getPendingTranscriptionRecordings()

            if (pendingRecordings.isEmpty()) {
                Log.d(TAG, "No recordings pending transcription")
                return@withContext Result.success()
            }

            Log.d(TAG, "Found ${pendingRecordings.size} recordings pending transcription")

            var successCount = 0
            var failureCount = 0

            for (recording in pendingRecordings) {
                try {
                    val audioFile = File(recording.filePath)

                    if (!audioFile.exists()) {
                        Log.e(TAG, "Audio file does not exist: ${recording.filePath}")
                        markTranscriptionFailed(recording.recordingId, "File not found")
                        failureCount++
                        continue
                    }

                    // Mark as in progress
                    db.audioRecordingDao().updateTranscription(
                        recordingId = recording.recordingId,
                        transcription = "",
                        status = AudioRecordingEntity.TranscriptionStatus.IN_PROGRESS.name,
                        timestamp = System.currentTimeMillis()
                    )

                    // Perform transcription
                    val transcriptionResult = speechToTextManager.transcribeAudio(
                        audioFile,
                        recording.transcriptionLanguage
                    )

                    if (transcriptionResult.isSuccess) {
                        // Update database with transcription
                        val text = transcriptionResult.text ?: ""
                        val status = if (text.isBlank()) {
                            AudioRecordingEntity.TranscriptionStatus.FAILED
                        } else {
                            AudioRecordingEntity.TranscriptionStatus.COMPLETED
                        }

                        db.audioRecordingDao().updateTranscription(
                            recordingId = recording.recordingId,
                            transcription = text,
                            status = status.name,
                            timestamp = System.currentTimeMillis()
                        )

                        // Update Firestore with transcription
                        updateFirestoreWithTranscription(
                            recordingId = recording.recordingId,
                            transcription = text,
                            status = status.name
                        )

                        successCount++
                        Log.d(TAG, "Successfully transcribed recording: ${recording.recordingId}")
                    } else {
                        // Mark as failed
                        markTranscriptionFailed(
                            recordingId = recording.recordingId,
                            errorMessage = transcriptionResult.errorMessage ?: "Unknown error"
                        )
                        failureCount++
                        Log.e(TAG, "Failed to transcribe recording: ${recording.recordingId}, error: ${transcriptionResult.errorMessage}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing recording for transcription: ${recording.recordingId}", e)
                    markTranscriptionFailed(recording.recordingId, e.message ?: "Exception occurred")
                    failureCount++
                }
            }

            Log.d(TAG, "Transcription worker completed: $successCount succeeded, $failureCount failed")

            // Return success if at least some transcriptions succeeded, or if all failed after MAX_ATTEMPTS
            if (successCount > 0 || shouldGiveUp(pendingRecordings)) {
                Result.success()
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in transcription worker", e)
            Result.retry()
        }
    }

    private suspend fun markTranscriptionFailed(recordingId: String, errorMessage: String) {
        try {
            // Get the current recording to check attempts
            val recording = db.audioRecordingDao().getRecordingById(recordingId) ?: return

            // Determine if we should give up (based on custom metadata or other logic)
            val attempts = extractTranscriptionAttempts(recording)
            val newAttempts = attempts + 1

            // If max attempts reached, mark as permanently failed
            val status = if (newAttempts >= MAX_TRANSCRIPTION_ATTEMPTS) {
                AudioRecordingEntity.TranscriptionStatus.FAILED
            } else {
                AudioRecordingEntity.TranscriptionStatus.PENDING  // Will retry later
            }

            // Store the attempt count in the transcription field temporarily
            val transcription = if (status == AudioRecordingEntity.TranscriptionStatus.FAILED) {
                "Transcription failed after $newAttempts attempts. Last error: $errorMessage"
            } else {
                "ATTEMPT:$newAttempts|ERROR:$errorMessage"
            }

            db.audioRecordingDao().updateTranscription(
                recordingId = recordingId,
                transcription = transcription,
                status = status.name,
                timestamp = System.currentTimeMillis()
            )

            // Update Firestore with failure status
            updateFirestoreWithTranscription(
                recordingId = recordingId,
                transcription = transcription,
                status = status.name
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error marking transcription as failed", e)
        }
    }

    private fun extractTranscriptionAttempts(recording: AudioRecordingEntity): Int {
        val transcription = recording.transcription ?: return 0

        // Extract attempt count from the transcription field if present
        return if (transcription.startsWith("ATTEMPT:")) {
            try {
                val attemptStr = transcription.substringAfter("ATTEMPT:").substringBefore("|")
                attemptStr.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }

    private fun shouldGiveUp(recordings: List<AudioRecordingEntity>): Boolean {
        // Check if all recordings have reached max attempts
        return recordings.all { recording ->
            extractTranscriptionAttempts(recording) >= MAX_TRANSCRIPTION_ATTEMPTS
        }
    }

    private suspend fun updateFirestoreWithTranscription(
        recordingId: String,
        transcription: String,
        status: String
    ) {
        val firestoreInstance = firestore ?: return

        try {
            // Update the Firestore document with transcription info
            val transcriptionData = hashMapOf(
                "transcription" to transcription,
                "transcriptionStatus" to status,
                "transcriptionTimestamp" to System.currentTimeMillis()
            )

            firestoreInstance.collection("devices")
                .document(deviceId)
                .collection("audio_recordings")
                .document(recordingId)
                .update(transcriptionData as Map<String, Any>)
                .addOnSuccessListener {
                    Log.d(TAG, "Transcription updated in Firestore: $recordingId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating transcription in Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateFirestoreWithTranscription", e)
        }
    }
}