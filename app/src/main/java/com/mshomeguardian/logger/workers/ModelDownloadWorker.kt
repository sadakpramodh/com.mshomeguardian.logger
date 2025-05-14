package com.mshomeguardian.logger.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.transcription.TranscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ModelDownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val languageCode = inputData.getString("language_code")
            if (languageCode.isNullOrEmpty()) {
                Log.e(TAG, "No language code provided")
                return@withContext Result.failure()
            }

            Log.d(TAG, "Starting download for language model: $languageCode")

            val transcriptionManager = TranscriptionManager.getInstance(applicationContext)
            val success = transcriptionManager.downloadModelSync(languageCode)

            return@withContext if (success) {
                Log.d(TAG, "Successfully downloaded language model: $languageCode")
                Result.success()
            } else {
                Log.e(TAG, "Failed to download language model: $languageCode")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading language model", e)
            Result.retry()
        }
    }
}