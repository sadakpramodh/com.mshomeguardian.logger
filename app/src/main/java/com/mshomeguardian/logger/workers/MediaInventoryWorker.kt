package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaInventoryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "MediaInventoryWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userEmail = FirebaseServiceHelper.getCurrentUserEmail()
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            if (!hasMediaPermission()) {
                OptimizedLogger.w(TAG, "Missing media read permission")
                return@withContext Result.success()
            }

            val timestamp = System.currentTimeMillis()
            val inventory = hashMapOf<String, Any>(
                "timestamp" to timestamp,
                "deviceId" to deviceId
            )

            inventory.putAll(collectMediaSummary(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "images"
            ))
            inventory.putAll(collectMediaSummary(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "videos"
            ))
            inventory.putAll(collectMediaSummary(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "audio"
            ))

            inventory["eventType"] = "media_inventory"

            val success = FirebaseServiceHelper.uploadSystemEvent(userEmail, deviceId, inventory)
            if (success) {
                OptimizedLogger.d(TAG, "Media inventory uploaded")
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting media inventory", e)
            Result.retry()
        }
    }

    private fun hasMediaPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun collectMediaSummary(uri: android.net.Uri, prefix: String): Map<String, Any> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE
        )
        var count = 0
        var totalSize = 0L
        var latestDateAdded = 0L

        try {
            applicationContext.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    count++
                    if (dateIndex >= 0) {
                        latestDateAdded = maxOf(latestDateAdded, cursor.getLong(dateIndex))
                    }
                    if (sizeIndex >= 0) {
                        totalSize += cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error collecting $prefix media summary", e)
        }

        return mapOf(
            "${prefix}Count" to count,
            "${prefix}TotalSizeBytes" to totalSize,
            "${prefix}LatestDateAdded" to latestDateAdded
        )
    }
}
