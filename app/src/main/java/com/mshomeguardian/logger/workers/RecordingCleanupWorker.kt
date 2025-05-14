package com.mshomeguardian.logger.workers

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.AudioRecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Worker to clean up old audio recordings to manage storage space
 */
class RecordingCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context.applicationContext)

    companion object {
        private const val TAG = "RecordingCleanupWorker"

        // Settings for cleanup
        private const val MAX_RECORDING_AGE_DAYS = 30L  // Keep recordings for 30 days
        private const val MAX_STORAGE_USAGE_BYTES = 1024L * 1024L * 1024L  // 1GB maximum storage
        private const val DELETE_BATCH_SIZE = 20  // Number of recordings to delete in one batch
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting recording cleanup process")

            // Calculate cleanup threshold timestamp (recordings older than this will be deleted)
            val cleanupThreshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_RECORDING_AGE_DAYS)

            // 1. Delete old uploaded recordings from the database
            val oldRecordingsDeleted = deleteOldUploadedRecordings(cleanupThreshold)

            // 2. Check total storage used by recordings
            val totalStorageUsed = db.audioRecordingDao().getTotalRecordingsSize() ?: 0L
            Log.d(TAG, "Total storage used by recordings: ${formatStorageSize(totalStorageUsed)}")

            // 3. If storage exceeds limit, delete oldest uploaded recordings first
            if (totalStorageUsed > MAX_STORAGE_USAGE_BYTES) {
                val excessBytes = totalStorageUsed - MAX_STORAGE_USAGE_BYTES
                Log.d(TAG, "Storage limit exceeded by ${formatStorageSize(excessBytes)}, cleaning up oldest recordings")

                deleteRecordingsToFreeSpace(excessBytes)
            }

            // 4. Clean up orphaned audio files
            val orphanedFilesDeleted = cleanupOrphanedAudioFiles()

            Log.d(TAG, "Cleanup complete. Deleted $oldRecordingsDeleted old recordings from database and $orphanedFilesDeleted orphaned files")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in recording cleanup worker", e)
            Result.retry()
        }
    }

    /**
     * Delete recordings older than the threshold that have already been uploaded
     */
    private suspend fun deleteOldUploadedRecordings(thresholdTimestamp: Long): Int {
        try {
            // Get old uploaded recordings
            val oldRecordings = db.audioRecordingDao().getAllRecordings().filter {
                it.endTime < thresholdTimestamp && it.uploadedToCloud
            }

            if (oldRecordings.isEmpty()) {
                return 0
            }

            Log.d(TAG, "Deleting ${oldRecordings.size} old uploaded recordings")

            var deletedCount = 0

            // Delete recordings in batches
            oldRecordings.chunked(DELETE_BATCH_SIZE).forEach { batch ->
                batch.forEach { recording ->
                    try {
                        // Delete the audio file
                        val file = File(recording.filePath)
                        if (file.exists() && file.delete()) {
                            Log.d(TAG, "Deleted audio file: ${file.absolutePath}")
                        }

                        // Mark as deleted in the database
                        db.audioRecordingDao().markRecordingAsDeletedLocally(
                            recordingId = recording.recordingId,
                            lastUpdatedTime = System.currentTimeMillis()
                        )

                        deletedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting recording ${recording.recordingId}", e)
                    }
                }
            }

            return deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old uploaded recordings", e)
            return 0
        }
    }

    /**
     * Delete oldest recordings to free up the required space
     */
    private suspend fun deleteRecordingsToFreeSpace(bytesToFree: Long): Int {
        try {
            // Get all recordings, sorted by age (oldest first)
            val allRecordings = db.audioRecordingDao().getAllRecordings()
                .sortedBy { it.startTime }

            if (allRecordings.isEmpty()) {
                return 0
            }

            var bytesFreed = 0L
            var deletedCount = 0

            // Delete recordings until we've freed enough space
            for (recording in allRecordings) {
                if (bytesFreed >= bytesToFree) {
                    break
                }

                try {
                    // Prioritize deleting recordings that have been uploaded
                    if (!recording.uploadedToCloud) {
                        continue
                    }

                    // Delete the audio file
                    val file = File(recording.filePath)
                    if (file.exists() && file.delete()) {
                        bytesFreed += recording.fileSize
                        Log.d(TAG, "Deleted audio file to free space: ${file.absolutePath}")
                    }

                    // Mark as deleted in the database
                    db.audioRecordingDao().markRecordingAsDeletedLocally(
                        recordingId = recording.recordingId,
                        lastUpdatedTime = System.currentTimeMillis()
                    )

                    deletedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting recording ${recording.recordingId}", e)
                }
            }

            Log.d(TAG, "Freed ${formatStorageSize(bytesFreed)} by deleting $deletedCount recordings")
            return deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error freeing space by deleting recordings", e)
            return 0
        }
    }

    /**
     * Clean up audio files that exist on disk but are not in the database
     */
    private suspend fun cleanupOrphanedAudioFiles(): Int {
        try {
            // Get audio directory
            val recordingsDir = File(
                Environment.getExternalStorageDirectory(),
                "Android/media/com.mshomeguardian.logger/audio_recordings"
            )

            if (!recordingsDir.exists() || !recordingsDir.isDirectory) {
                return 0
            }

            // Get all audio files on disk
            val audioFiles = recordingsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".wav", ignoreCase = true)
            } ?: return 0

            if (audioFiles.isEmpty()) {
                return 0
            }

            // Get all recording file paths from database
            val dbFilePaths = db.audioRecordingDao().getAllRecordings()
                .map { it.filePath }
                .toSet()

            var deletedCount = 0

            // Delete files that are not in the database
            for (file in audioFiles) {
                if (!dbFilePaths.contains(file.absolutePath)) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted orphaned audio file: ${file.absolutePath}")
                        deletedCount++
                    }
                }
            }

            return deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up orphaned audio files", e)
            return 0
        }
    }

    /**
     * Format storage size in human-readable format
     */
    private fun formatStorageSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}