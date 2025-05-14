package com.mshomeguardian.logger.workers

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaExtractor
import android.media.MediaFormat
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
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TranscriptionWorker"
        private const val MODEL_FILE = "deepspeech-0.9.3-models.pbmm"
        private const val SCORER_FILE = "deepspeech-0.9.3-models.scorer"
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

            // Prepare the DeepSpeech model files
            if (!prepareModelFiles()) {
                Log.e(TAG, "Failed to prepare DeepSpeech model files")
                // Fall back to uploading without transcription
                val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
                val basicInfo = createBasicInfo(file)
                val uploaded = uploadTranscription(file.name, basicInfo, deviceId)
                val audioUploaded = uploadAudioFile(file, deviceId)

                return@withContext if (uploaded && audioUploaded) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }

            // Perform transcription
            val transcription = transcribeAudio(file)

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

    private suspend fun prepareModelFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Copy model file
            val modelFile = File(applicationContext.filesDir, MODEL_FILE)
            if (!modelFile.exists()) {
                copyAsset(MODEL_FILE, modelFile)
            }

            // Copy scorer file
            val scorerFile = File(applicationContext.filesDir, SCORER_FILE)
            if (!scorerFile.exists()) {
                copyAsset(SCORER_FILE, scorerFile)
            }

            return@withContext modelFile.exists() && scorerFile.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing model files", e)
            return@withContext false
        }
    }

    private fun copyAsset(assetName: String, destination: File) {
        try {
            val inputStream = applicationContext.assets.open(assetName)
            val outputStream = FileOutputStream(destination)

            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }

            inputStream.close()
            outputStream.close()

            Log.d(TAG, "Copied asset $assetName to ${destination.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset $assetName", e)
            throw e
        }
    }

    private fun transcribeAudio(audioFile: File): String {
        try {
            // Convert WAV file to raw PCM data suitable for DeepSpeech
            val (audioData, sampleRate) = extractPCMAudio(audioFile)
            if (audioData == null) {
                Log.e(TAG, "Failed to extract PCM audio from file")
                return createBasicInfo(audioFile)
            }

            // Initialize DeepSpeech
            val modelFile = File(applicationContext.filesDir, MODEL_FILE)
            val scorerFile = File(applicationContext.filesDir, SCORER_FILE)

            val model = DeepSpeechModel(modelFile.absolutePath)
            model.enableExternalScorer(scorerFile.absolutePath)

            // Perform transcription
            val result = model.stt(audioData)

            // Clean up
            model.freeModel()

            if (result.isBlank()) {
                Log.w(TAG, "Transcription result is empty")
                return createBasicInfo(audioFile) + "\n\nNo speech detected in this recording."
            }

            return "Transcription of ${audioFile.name}:\n\n$result"
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            return createBasicInfo(audioFile) + "\n\nTranscription failed: ${e.message}"
        }
    }

    private fun extractPCMAudio(audioFile: File): Pair<ShortArray?, Int> {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioFile.absolutePath)

            // Find the audio track
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                if (mime?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)

                    // Get sample rate, which is needed for DeepSpeech
                    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

                    // Read all audio data
                    val maxBufferSize = 1024 * 1024 // 1MB buffer
                    val buffer = ByteBuffer.allocateDirect(maxBufferSize)

                    // Collect all PCM data
                    val pcmData = ArrayList<Short>()

                    while (true) {
                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        buffer.position(0)
                        buffer.limit(sampleSize)

                        // Convert bytes to shorts (16-bit PCM)
                        val shortBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val shorts = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(shorts)

                        // Add to collection
                        pcmData.addAll(shorts.toList())

                        // Advance to next sample
                        extractor.advance()
                    }

                    extractor.release()

                    // Convert to flat array
                    val result = ShortArray(pcmData.size)
                    for (j in pcmData.indices) {
                        result[j] = pcmData[j]
                    }

                    return Pair(result, sampleRate)
                }
            }

            extractor.release()
            Log.e(TAG, "No audio track found in file")
            return Pair(null, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PCM audio", e)
            return Pair(null, 0)
        }
    }

    private fun createBasicInfo(audioFile: File): String {
        try {
            // Extract basic metadata about the audio
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val durationSecs = duration / 1000
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

    private suspend fun uploadTranscription(fileName: String, transcription: String, deviceId: String): Boolean {
        try {
            // Create a map of the transcription data
            val transcriptionData = hashMapOf(
                "fileName" to fileName,
                "text" to transcription,
                "deviceId" to deviceId,
                "timestamp" to System.currentTimeMillis(),
                "language" to "en-US"
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