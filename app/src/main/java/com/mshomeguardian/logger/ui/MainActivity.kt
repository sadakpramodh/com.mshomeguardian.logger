package com.mshomeguardian.logger.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.services.AudioRecordingService
import com.mshomeguardian.logger.services.MonitoringService
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.LocationUtils
import com.mshomeguardian.logger.utils.WeatherUtil
import com.mshomeguardian.logger.workers.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val LOCATION_PERMISSION_REQUEST_CODE = 101
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
    private val CALL_SMS_PERMISSION_REQUEST_CODE = 103
    private val ALL_PERMISSIONS_REQUEST_CODE = 104
    private val AUDIO_PERMISSION_REQUEST_CODE = 105

    private lateinit var statusText: TextView
    private lateinit var permissionsButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var syncButton: Button
    private lateinit var recordingToggleButton: Button

    // New UI elements
    private lateinit var weatherCard: CardView
    private lateinit var weatherIcon: ImageView
    private lateinit var temperatureText: TextView
    private lateinit var weatherDescText: TextView
    private lateinit var locationText: TextView
    private lateinit var lastSyncText: TextView
    private lateinit var lastLocationText: TextView
    private lateinit var lastCallLogText: TextView
    private lateinit var lastMessageText: TextView
    private lateinit var lastContactsText: TextView
    private lateinit var lastAudioRecordingText: TextView

    // All required permissions
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO
    )

    // Additional background location permission for Android 10+
    private val backgroundLocationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else null

    // Flag to track if recording service is running
    private var isRecordingServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        statusText = findViewById(R.id.statusText)
        permissionsButton = findViewById(R.id.permissionsButton)
        deviceIdText = findViewById(R.id.deviceIdText)
        syncButton = findViewById(R.id.syncButton)
        recordingToggleButton = findViewById(R.id.recordingToggleButton)

        // Initialize new UI elements
        weatherCard = findViewById(R.id.weatherCard)
        weatherIcon = findViewById(R.id.weatherIcon)
        temperatureText = findViewById(R.id.temperatureText)
        weatherDescText = findViewById(R.id.weatherDescText)
        locationText = findViewById(R.id.locationText)
        lastSyncText = findViewById(R.id.lastSyncText)
        lastLocationText = findViewById(R.id.lastLocationText)
        lastCallLogText = findViewById(R.id.lastCallLogText)
        lastMessageText = findViewById(R.id.lastMessageText)
        lastContactsText = findViewById(R.id.lastContactsText)
        lastAudioRecordingText = findViewById(R.id.lastAudioRecordingText)

        // Create notification channel for foreground service
        createNotificationChannel()

        // Set device ID
        val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
        deviceIdText.text = "Device ID: $deviceId"

        // Set up permission button
        permissionsButton.setOnClickListener {
            requestAllPermissions()
        }

        // Set up sync button
        syncButton.setOnClickListener {
            if (areAllPermissionsGranted()) {
                Toast.makeText(this, "Starting manual sync...", Toast.LENGTH_SHORT).show()
                WorkerScheduler.runAllWorkersOnce(applicationContext)
                updateLastSyncTimes()
            } else {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_LONG).show()
                updatePermissionStatus()
            }
        }

        // Set up recording toggle button
        recordingToggleButton.setOnClickListener {
            toggleRecordingService()
        }

        // Check permissions on startup
        updatePermissionStatus()

        // Load last sync times
        updateLastSyncTimes()

        // Load weather data if permissions are granted
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            loadWeatherData()
        }

        // Start monitoring service if permissions granted
        if (areAllPermissionsGranted()) {
            startBackgroundServices()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Monitoring service channel
            val monitoringChannel = NotificationChannel(
                "monitoring_service",
                "Home Guardian Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for Home Guardian monitoring service"
            }

            // Audio recording service channel
            val recordingChannel = NotificationChannel(
                "recording_service_channel",
                "Audio Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for the audio recording service notifications"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(recordingChannel)
        }
    }

    private fun updateLastSyncTimes() {
        // Get last sync time from shared preferences
        val locationPrefs = getSharedPreferences("location_sync", MODE_PRIVATE)
        val callLogPrefs = getSharedPreferences("call_log_sync", MODE_PRIVATE)
        val messagePrefs = getSharedPreferences("message_sync", MODE_PRIVATE)
        val contactsPrefs = getSharedPreferences("contacts_sync", MODE_PRIVATE)
        val audioPrefs = getSharedPreferences("audio_recording_sync", MODE_PRIVATE)

        val locationTime = locationPrefs.getLong("last_sync_time", 0)
        val callLogTime = callLogPrefs.getLong("last_sync_time", 0)
        val messageTime = messagePrefs.getLong("last_sync_time", 0)
        val contactsTime = contactsPrefs.getLong("last_sync_time", 0)
        val audioTime = audioPrefs.getLong("last_save_time", 0)

        // Format dates
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        lastLocationText.text = "Location: " + if (locationTime > 0) dateFormat.format(Date(locationTime)) else "Never"
        lastCallLogText.text = "Call Logs: " + if (callLogTime > 0) dateFormat.format(Date(callLogTime)) else "Never"
        lastMessageText.text = "Messages: " + if (messageTime > 0) dateFormat.format(Date(messageTime)) else "Never"
        lastContactsText.text = "Contacts: " + if (contactsTime > 0) dateFormat.format(Date(contactsTime)) else "Never"
        lastAudioRecordingText.text = "Audio: " + if (audioTime > 0) dateFormat.format(Date(audioTime)) else "Never"

        // Update overall last sync time
        val lastSync = maxOf(locationTime, callLogTime, messageTime, contactsTime, audioTime)
        if (lastSync > 0) {
            lastSyncText.text = "Last Sync: " + dateFormat.format(Date(lastSync))
        } else {
            lastSyncText.text = "Last Sync: Never"
        }
    }

    private fun loadWeatherData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val location = LocationUtils.getLastKnownLocation(applicationContext)
                if (location != null) {
                    val weatherData = WeatherUtil.getWeatherData(
                        location.latitude,
                        location.longitude
                    )

                    withContext(Dispatchers.Main) {
                        updateWeatherUI(weatherData, location)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        weatherDescText.text = "Location unavailable"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    weatherDescText.text = "Weather unavailable"
                }
            }
        }
    }

    private fun updateWeatherUI(weatherData: WeatherUtil.WeatherData, location: Location) {
        temperatureText.text = "${weatherData.temperature}°C"
        weatherDescText.text = weatherData.description
        locationText.text = "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)}"

        // Set weather icon based on condition
        val weatherIconRes = when {
            weatherData.description.contains("rain", ignoreCase = true) -> R.drawable.ic_weather_rain
            weatherData.description.contains("cloud", ignoreCase = true) -> R.drawable.ic_weather_cloudy
            weatherData.description.contains("clear", ignoreCase = true) -> R.drawable.ic_weather_sunny
            weatherData.description.contains("snow", ignoreCase = true) -> R.drawable.ic_weather_snow
            weatherData.description.contains("thunder", ignoreCase = true) -> R.drawable.ic_weather_thunder
            weatherData.description.contains("fog", ignoreCase = true) -> R.drawable.ic_weather_foggy
            else -> R.drawable.ic_weather_default
        }

        weatherIcon.setImageResource(weatherIconRes)
        weatherCard.visibility = View.VISIBLE
    }

    private fun requestAllPermissions() {
        // First request standard permissions
        ActivityCompat.requestPermissions(
            this,
            requiredPermissions,
            ALL_PERMISSIONS_REQUEST_CODE
        )
    }

    private fun checkBackgroundLocationPermission() {
        // Only for Android 10 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {
                showBackgroundLocationRationale()
            } else {
                // All permissions granted, start services
                startBackgroundServices()
            }
        } else {
            // Pre-Android 10 doesn't need separate background permission
            startBackgroundServices()
        }
    }

    private fun showBackgroundLocationRationale() {
        AlertDialog.Builder(this)
            .setTitle("Background Location Access Needed")
            .setMessage("This app tracks your location in the background to provide home security monitoring. Please select 'Allow all the time' on the next screen.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updatePermissionStatus()
            }
            .create()
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            ALL_PERMISSIONS_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                if (allGranted) {
                    // Now check for background permission if needed
                    if (backgroundLocationPermission != null) {
                        checkBackgroundLocationPermission()
                    } else {
                        startBackgroundServices()
                    }
                } else {
                    // Show which permissions are still needed
                    updatePermissionStatus()

                    // Check if the user clicked "never ask again" on any permission
                    val showRationale = requiredPermissions.any {
                        ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                    }

                    if (!showRationale) {
                        // User clicked "never ask again" for at least one permission
                        showSettingsDialog()
                    }
                }
            }
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Background location permission granted
                    startBackgroundServices()
                } else {
                    // Update UI to show missing permission
                    updatePermissionStatus()

                    // Check if user clicked "never ask again"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(
                            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    ) {
                        showSettingsDialog()
                    }
                }
            }
            AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Audio permission granted, start recording service
                    startRecordingService()
                } else {
                    // Update UI
                    updatePermissionStatus()
                    Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs all the requested permissions to function properly. Please enable them in app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updatePermissionStatus()
            }
            .create()
            .show()
    }

    private fun areAllPermissionsGranted(): Boolean {
        val standardPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        val backgroundPermissionGranted = if (backgroundLocationPermission != null) {
            ContextCompat.checkSelfPermission(
                this, backgroundLocationPermission
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return standardPermissionsGranted && backgroundPermissionGranted
    }

    private fun updatePermissionStatus() {
        val status = StringBuilder()
        status.append("Permission Status:\n\n")

        // Check location permission
        val locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        status.append("• Location: ${if (locationGranted) "✓" else "✗"}\n")

        // Check background location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            status.append("• Background Location: ${if (backgroundLocationGranted) "✓" else "✗"}\n")
        }

        // Check call log permission
        val callLogGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        status.append("• Call Log: ${if (callLogGranted) "✓" else "✗"}\n")

        // Check SMS permission
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        status.append("• SMS: ${if (smsGranted) "✓" else "✗"}\n")

        // Check phone state permission
        val phoneStateGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        status.append("• Phone State: ${if (phoneStateGranted) "✓" else "✗"}\n")

        // Check contacts permission
        val contactsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        status.append("• Contacts: ${if (contactsGranted) "✓" else "✗"}\n")

        // Check audio recording permission
        val audioPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        status.append("• Audio Recording: ${if (audioPermissionGranted) "✓" else "✗"}\n\n")

        // Summary
        if (areAllPermissionsGranted()) {
            status.append("All permissions granted.\nServices are running in the background.")
            permissionsButton.text = "Permissions: All Granted"
            syncButton.isEnabled = true
            recordingToggleButton.isEnabled = true

            // Load weather data if we have location permission
            loadWeatherData()
        } else {
            status.append("Some permissions are missing.\nPlease grant all permissions for full functionality.")
            permissionsButton.text = "Grant Permissions"
            syncButton.isEnabled = false

            // Only enable recording button if audio permission is granted
            recordingToggleButton.isEnabled = audioPermissionGranted
        }

        // Update recording button text
        updateRecordingButtonText()

        statusText.text = status.toString()
    }

    private fun startBackgroundServices() {
        // Only start if all permissions are granted
        if (areAllPermissionsGranted()) {
            // Start all workers
            WorkerScheduler.runAllWorkersOnce(applicationContext)

            // Schedule periodic workers
            WorkerScheduler.schedule(applicationContext)

            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, MonitoringService::class.java))
            } else {
                startService(Intent(this, MonitoringService::class.java))
            }

            // Update UI
            updatePermissionStatus()
            Toast.makeText(this, "Home Guardian is now monitoring your device", Toast.LENGTH_SHORT).show()

            // Update last sync times
            updateLastSyncTimes()
        } else {
            updatePermissionStatus()
        }
    }

    private fun toggleRecordingService() {
        if (isRecordingServiceRunning) {
            stopRecordingService()
        } else {
            // Check for audio permission before starting
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED) {

                // Request audio permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    AUDIO_PERMISSION_REQUEST_CODE
                )
            } else {
                startRecordingService()
            }
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START_RECORDING
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        isRecordingServiceRunning = true
        updateRecordingButtonText()
        Toast.makeText(this, "Audio recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingService() {
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP_RECORDING
        }
        startService(intent)

        isRecordingServiceRunning = false
        updateRecordingButtonText()
        Toast.makeText(this, "Audio recording stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateRecordingButtonText() {
        recordingToggleButton.text = if (isRecordingServiceRunning) {
            "Stop Audio Recording"
        } else {
            "Start Audio Recording"
        }
    }

    override fun onResume() {
        super.onResume()
        // Update permission status each time activity is resumed
        // This handles the case where user grants permissions from Settings
        updatePermissionStatus()

        // Update last sync times
        updateLastSyncTimes()

        // Refresh weather data
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            loadWeatherData()
        }

        // Request to ignore battery optimizations
        requestIgnoreBatteryOptimizations()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to request ignoring battery optimizations", e)
                }
            }
        }
    }
}