package com.mshomeguardian.logger.transcription

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for managing transcription results
 */
class TranscriptionResultManager(private val context: Context) {
    companion object {
        private const val TAG = "TranscriptionResultMgr"
    }

    /**
     * Copy transcription text to clipboard
     */
    fun copyToClipboard(text: String): Boolean {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Transcription", text)
            clipboard.setPrimaryClip(clip)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard", e)
            return false
        }
    }

    /**
     * Save transcription to file
     */
    fun saveToFile(text: String, fileName: String? = null): String? {
        try {
            // Create transcriptions directory if it doesn't exist
            val transcriptionsDir = File(
                context.getExternalFilesDir(null),
                "transcriptions"
            )
            if (!transcriptionsDir.exists()) {
                transcriptionsDir.mkdirs()
            }

            // Generate file name if not provided
            val actualFileName = fileName ?: generateFileName()

            // Create file
            val file = File(transcriptionsDir, actualFileName)

            // Write text to file
            FileWriter(file).use { writer ->
                writer.write(text)
            }

            // Scan file to make it visible in gallery
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("text/plain"),
                null
            )

            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transcription to file", e)
            return null
        }
    }

    /**
     * Generate a file name for the transcription
     */
    private fun generateFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val timestamp = dateFormat.format(Date())
        return "transcription_$timestamp.txt"
    }

    /**
     * Get the list of saved transcription files
     */
    fun getSavedTranscriptions(): List<File> {
        val transcriptionsDir = File(
            context.getExternalFilesDir(null),
            "transcriptions"
        )

        if (!transcriptionsDir.exists()) {
            return emptyList()
        }

        return transcriptionsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".txt")
        }?.toList() ?: emptyList()
    }

    /**
     * Delete a transcription file
     */
    fun deleteTranscription(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transcription file", e)
            false
        }
    }

    /**
     * Read transcription from file
     */
    fun readTranscription(file: File): String? {
        return try {
            file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading transcription file", e)
            null
        }
    }
}