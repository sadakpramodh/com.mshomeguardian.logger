package com.mshomeguardian.logger.transcription

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mshomeguardian.logger.workers.ModelDownloadWorker
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class TranscriptionResult(
    val text: String,
    val languageCode: String,
    val confidence: Float,
    val durationMs: Long
)

class TranscriptionManager(private val context: Context) {
    companion object {
        private const val TAG = "TranscriptionManager"

        // Map of language codes to model URLs and zip file names
        private val LANGUAGE_MODELS = mapOf(
            "en" to ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                "vosk-model-small-en-us-0.15",
                "English"
            ),
            "hi" to ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
                "vosk-model-small-hi-0.22",
                "Hindi"
            ),
            "te" to ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-small-tel-0.4.zip",
                "vosk-model-small-tel-0.4",
                "Telugu"
            ),
            // Add more languages as needed
            "fr" to ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
                "vosk-model-small-fr-0.22",
                "French"
            ),
            "es" to ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
                "vosk-model-small-es-0.42",
                "Spanish"
            ),
            "de" to ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
                "vosk-model-small-de-0.15",
                "German"
            )
        )

        private val executor = Executors.newSingleThreadExecutor()

        @Volatile
        private var INSTANCE: TranscriptionManager? = null

        fun getInstance(context: Context): TranscriptionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TranscriptionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Directory where models are stored
    private val modelsDir = File(context.getExternalFilesDir(null), "vosk_models")

    init {
        // Create models directory if it doesn't exist
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    /**
     * Check if the model for the specified language is downloaded
     */
    fun isModelDownloaded(languageCode: String): Boolean {
        val modelInfo = LANGUAGE_MODELS[languageCode] ?: return false
        val modelDir = File(modelsDir, modelInfo.dirName)
        return modelDir.exists() && modelDir.isDirectory &&
                File(modelDir, "am").exists() && // Check for key files
                File(modelDir, "conf").exists() &&
                File(modelDir, "graph").exists() &&
                File(modelDir, "mfcc.conf").exists()
    }

    /**
     * Get the directory where the model for the specified language is stored
     */
    fun getModelDirectory(languageCode: String): File? {
        if (!isModelDownloaded(languageCode)) return null

        val modelInfo = LANGUAGE_MODELS[languageCode] ?: return null
        return File(modelsDir, modelInfo.dirName)
    }

    /**
     * Get the list of available language models
     */
    fun getAvailableLanguages(): List<ModelInfo> {
        return LANGUAGE_MODELS.entries
            .map { it.value.copy(isDownloaded = isModelDownloaded(it.key), languageCode = it.key) }
            .sortedBy { it.displayName }
    }

    /**
     * Download a model for the specified language
     * @param languageCode The language code to download
     * @param onProgress Callback for download progress (0-100)
     * @param onComplete Callback when download is complete
     */
    fun downloadModel(
        languageCode: String,
        onProgress: (Int) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        val modelInfo = LANGUAGE_MODELS[languageCode]
        if (modelInfo == null) {
            Log.e(TAG, "No model info found for language: $languageCode")
            onComplete(false)
            return
        }

        // Check if we already have this model
        if (isModelDownloaded(languageCode)) {
            Log.d(TAG, "Model for $languageCode is already downloaded")
            onComplete(true)
            return
        }

        // Schedule download using WorkManager if on Wi-Fi, otherwise inform user
        if (isWifiConnected()) {
            scheduleModelDownload(languageCode, onComplete)
        } else {
            // If not on Wi-Fi, we can either ask the user or wait
            Log.d(TAG, "Not on Wi-Fi, not downloading model for $languageCode")
            onComplete(false)
        }
    }

    /**
     * Download a model using a worker
     */
    private fun scheduleModelDownload(languageCode: String, onComplete: (Boolean) -> Unit) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi
            .setRequiresBatteryNotLow(true)
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("language_code" to languageCode))
            .build()

        WorkManager.getInstance(context)
            .enqueue(downloadWorkRequest)

        Log.d(TAG, "Scheduled model download for language: $languageCode")
    }

    /**
     * Download a model directly (synchronously) - use with caution
     */
    fun downloadModelSync(languageCode: String): Boolean {
        val modelInfo = LANGUAGE_MODELS[languageCode] ?: return false

        // Create a temporary directory for the download
        val tempDir = File(context.cacheDir, "model_download")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        // Download the zip file
        val zipFile = File(tempDir, "${modelInfo.dirName}.zip")
        try {
            URL(modelInfo.url).openStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Extract the zip file
            val modelDir = File(modelsDir, modelInfo.dirName)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            extractZipFile(zipFile, modelsDir)

            // Clean up
            zipFile.delete()

            Log.d(TAG, "Model downloaded and extracted for language: $languageCode")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model for language: $languageCode", e)
            return false
        }
    }

    /**
     * Extract a zip file
     */
    private fun extractZipFile(zipFile: File, destinationDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val filePath = File(destinationDir, entry.name)

                if (!entry.isDirectory) {
                    // Create parent directories if they don't exist
                    filePath.parentFile?.mkdirs()

                    // Extract file
                    FileOutputStream(filePath).use { output ->
                        zipIn.copyTo(output)
                    }
                } else {
                    // Create directory
                    filePath.mkdirs()
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    /**
     * Check if the device is connected to Wi-Fi
     */
    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    /**
     * Initialize a Vosk model
     */
    fun initializeModel(languageCode: String): Model? {
        val modelDir = getModelDirectory(languageCode) ?: return null

        return try {
            Model(modelDir.absolutePath)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize model for language: $languageCode", e)
            null
        }
    }

    /**
     * Delete a model to free up space
     */
    fun deleteModel(languageCode: String): Boolean {
        if (!isModelDownloaded(languageCode)) return true // Already deleted

        val modelInfo = LANGUAGE_MODELS[languageCode] ?: return false
        val modelDir = File(modelsDir, modelInfo.dirName)

        return try {
            modelDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model for language: $languageCode", e)
            false
        }
    }
}

data class ModelInfo(
    val url: String,
    val dirName: String,
    val displayName: String,
    val isDownloaded: Boolean = false,
    val languageCode: String = ""
)

