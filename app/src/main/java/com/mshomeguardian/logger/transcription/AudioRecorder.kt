package com.mshomeguardian.logger.transcription

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Helper class for audio recording operations with Vosk-compatible formatting
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    companion object {
        private const val TAG = "AudioRecorder"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    // Buffer for audio data (2 seconds)
    private val bufferSize = 2 * AudioRecord.getMinBufferSize(
        sampleRate, channelConfig, audioFormat
    )

    /**
     * Interface for audio recording callbacks
     */
    interface AudioDataCallback {
        fun onAudioData(data: ShortArray)
        fun onError(exception: Exception)
    }

    /**
     * Start recording audio and sending data to callback
     */
    fun startRecording(callback: AudioDataCallback) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                callback.onError(IOException("AudioRecord initialization failed"))
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            // Create a new thread for reading audio data
            recordingThread = Thread {
                readAudioData(callback)
            }
            recordingThread?.start()

            Log.d(TAG, "Audio recording started with buffer size: $bufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            callback.onError(e)
        }
    }

    /**
     * Stop recording audio
     */
    fun stopRecording() {
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join(500) // Wait for thread to finish
            recordingThread = null

            Log.d(TAG, "Audio recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    /**
     * Read audio data from the AudioRecord and send to callback
     */
    private fun readAudioData(callback: AudioDataCallback) {
        // Each sample is 2 bytes (short)
        val buffer = ShortArray(bufferSize / 2)

        try {
            while (isRecording) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (readResult > 0) {
                    // Copy the buffer to avoid reusing the same buffer
                    val audioData = buffer.copyOf(readResult)
                    callback.onAudioData(audioData)
                } else if (readResult == AudioRecord.ERROR_BAD_VALUE ||
                    readResult == AudioRecord.ERROR_INVALID_OPERATION ||
                    readResult == AudioRecord.ERROR) {
                    Log.e(TAG, "Error reading audio data: $readResult")
                    callback.onError(IOException("Error reading audio data: $readResult"))
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in audio recording thread", e)
            callback.onError(e)
        }
    }

    /**
     * Convert short array to byte array (for Vosk input)
     */
    fun shortToByteArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = buffer.asShortBuffer()
        shortBuffer.put(shorts)
        return bytes
    }

    /**
     * Save audio data to WAV file
     */
    fun saveToWavFile(audioData: ShortArray, outputFile: File): Boolean {
        try {
            FileOutputStream(outputFile).use { outputStream ->
                // Write WAV header
                val dataSize = audioData.size * 2 // 2 bytes per sample
                val fileSize = 36 + dataSize

                // RIFF header
                outputStream.write("RIFF".toByteArray())
                writeInt(outputStream, fileSize)
                outputStream.write("WAVE".toByteArray())

                // fmt subchunk
                outputStream.write("fmt ".toByteArray())
                writeInt(outputStream, 16) // Subchunk1Size (16 for PCM)
                writeShort(outputStream, 1) // AudioFormat (1 for PCM)
                writeShort(outputStream, 1) // NumChannels (1 for mono)
                writeInt(outputStream, sampleRate) // SampleRate
                writeInt(outputStream, sampleRate * 2) // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
                writeShort(outputStream, 2) // BlockAlign (NumChannels * BitsPerSample/8)
                writeShort(outputStream, 16) // BitsPerSample

                // data subchunk
                outputStream.write("data".toByteArray())
                writeInt(outputStream, dataSize) // Subchunk2Size

                // Write audio data
                val bytes = shortToByteArray(audioData)
                outputStream.write(bytes)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving WAV file", e)
            return false
        }
    }

    /**
     * Convert multiple audio chunks to a single WAV file
     */
    fun saveChunksToWavFile(audioChunks: List<ShortArray>, outputFile: File): Boolean {
        try {
            // First, calculate total size
            var totalSamples = 0
            for (chunk in audioChunks) {
                totalSamples += chunk.size
            }

            // Create a combined buffer
            val combinedData = ShortArray(totalSamples)
            var position = 0

            // Copy all chunks into combined buffer
            for (chunk in audioChunks) {
                System.arraycopy(chunk, 0, combinedData, position, chunk.size)
                position += chunk.size
            }

            // Save to WAV file
            return saveToWavFile(combinedData, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio chunks to WAV file", e)
            return false
        }
    }

    /**
     * Write int value to output stream (little endian)
     */
    private fun writeInt(output: FileOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
        output.write(value shr 16 and 0xFF)
        output.write(value shr 24 and 0xFF)
    }

    /**
     * Write short value to output stream (little endian)
     */
    private fun writeShort(output: FileOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
    }
}