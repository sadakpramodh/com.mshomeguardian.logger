package com.mshomeguardian.logger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONException
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.AssetUtils

class LiveTranscriptionActivity : AppCompatActivity(), RecognitionListener {

    private lateinit var resultText: TextView
    private lateinit var startButton: Button
    private lateinit var languageSelector: Button
    private lateinit var statusText: TextView

    private var model: Model? = null
    private var speechService: SpeechService? = null

    private val languages = arrayOf("en", "hi", "te")  // English, Hindi, Telugu
    private val languageNames = arrayOf("English", "Hindi", "Telugu")
    private var currentLanguageIndex = 0

    companion object {
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_transcription)

        // Set up UI elements
        resultText = findViewById(R.id.transcriptionTextView)
        startButton = findViewById(R.id.startButton)
        languageSelector = findViewById(R.id.languageButton)
        statusText = findViewById(R.id.statusTextView)

        // Set up button listeners
        startButton.setOnClickListener { toggleRecognition() }
        languageSelector.setOnClickListener { cycleLanguage() }

        // Set up Vosk
        LibVosk.setLogLevel(LogLevel.INFO)

        // Check permissions
        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
        } else {
            initModel()
        }

        // Show initial language selection
        updateLanguageButton()
    }

    private fun updateLanguageButton() {
        languageSelector.text = "Language: ${languageNames[currentLanguageIndex]}"
    }

    private fun cycleLanguage() {
        // Stop recognition if running
        if (speechService != null) {
            speechService?.stop()
            speechService = null
            startButton.text = "Start"
            statusText.text = "Stopped"
        }

        // Change language
        currentLanguageIndex = (currentLanguageIndex + 1) % languages.size
        updateLanguageButton()

        // Reinitialize model with new language
        initModel()
    }

    private fun initModel() {
        statusText.text = "Initializing ${languageNames[currentLanguageIndex]} model..."

        // Release previous model if exists
        model?.close()
        model = null

        // Start the model initialization in a separate thread
        Thread {
            try {
                val assetManager = assets
                val modelPath = "model-${languages[currentLanguageIndex]}"

                // Check if model exists in assets
                val modelAssets = assetManager.list(modelPath)
                if (modelAssets == null || modelAssets.isEmpty()) {
                    runOnUiThread {
                        statusText.text = "Failed to find model in assets. Please add model to: $modelPath"
                    }
                    return@Thread
                }

                // Copy model from assets to application storage
                val appDir = getExternalFilesDir(null)
                // Change the line with syncAssets to:
                val modelDir = AssetUtils.syncAssets(assetManager, modelPath, appDir?.absolutePath)

                // Initialize the model
                model = Model(modelDir)

                runOnUiThread {
                    statusText.text = "${languageNames[currentLanguageIndex]} model loaded. Ready to start."
                    startButton.isEnabled = true
                }
            } catch (e: IOException) {
                runOnUiThread {
                    statusText.text = "Failed to initialize model: ${e.message}"
                }
            }
        }.start()
    }

    private fun toggleRecognition() {
        if (speechService == null) {
            // Start recognition
            if (model == null) {
                statusText.text = "Model not initialized yet"
                return
            }

            try {
                val recognizer = Recognizer(model, 16000.0f)
                speechService = SpeechService(recognizer, 16000.0f)
                speechService?.startListening(this)

                startButton.text = "Stop"
                statusText.text = "Listening..."
            } catch (e: IOException) {
                statusText.text = "Failed to start recognition: ${e.message}"
            }
        } else {
            // Stop recognition
            speechService?.stop()
            speechService = null

            startButton.text = "Start"
            statusText.text = "Stopped"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize the model
                initModel()
            } else {
                // Permission denied
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Free model resources
        if (speechService != null) {
            speechService?.stop()
            speechService = null
        }

        if (model != null) {
            model?.close()
            model = null
        }
    }

    // Recognition Listener implementation
    override fun onPartialResult(hypothesis: String) {
        if (hypothesis == null) return

        try {
            val jsonResult = JSONObject(hypothesis)
            val partialText = jsonResult.getString("partial")
            statusText.text = "Recognizing: $partialText"
        } catch (e: JSONException) {
            // Handle JSON parsing error
        }
    }

    override fun onResult(hypothesis: String) {
        if (hypothesis == null) return

        try {
            val jsonResult = JSONObject(hypothesis)
            val text = jsonResult.getString("text")

            if (text.isNotEmpty()) {
                val current = resultText.text.toString()
                resultText.text = if (current.isEmpty()) text else "$current\n$text"
            }
        } catch (e: JSONException) {
            // Handle JSON parsing error
        }
    }

    override fun onFinalResult(hypothesis: String) {
        // Similar to onResult but called when recognition session is done
        if (hypothesis == null) return

        try {
            val jsonResult = JSONObject(hypothesis)
            val text = jsonResult.getString("text")

            if (text.isNotEmpty()) {
                val current = resultText.text.toString()
                resultText.text = if (current.isEmpty()) text else "$current\n$text"
            }
        } catch (e: JSONException) {
            // Handle JSON parsing error
        }

        // If we were using speech endpoints, we would restart listening here
    }

    override fun onError(e: Exception) {
        statusText.text = "Error: ${e.message}"
    }

    override fun onTimeout() {
        statusText.text = "Recognition timeout"
    }
}