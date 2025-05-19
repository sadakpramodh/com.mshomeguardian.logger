package com.mshomeguardian.logger.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.services.LocationMonitoringService
import com.mshomeguardian.logger.utils.DataSyncManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.widget.HomeGuardianWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.appwidget.AppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.mshomeguardian.logger.transcription.TranscriptionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main activity for app configuration and status
 * Updated with Live Transcription feature
 */
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    // Request codes for permission handling
    private val LOCATION_PERMISSION_REQUEST_CODE = 101
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
    private val CALL_SMS_PERMISSION_REQUEST_CODE = 103
    private val ALL_PERMISSIONS_REQUEST_CODE = 104
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 105
    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 106

    // Update interval for status information (10 seconds)
    private val STATUS_UPDATE_INTERVAL = 10000L

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var permissionsButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var syncButton: Button
    private lateinit var recordingButton: Button
    private lateinit var liveTranscriptionButton: Button  // New button for live transcription

    // Status text views
    private lateinit var locationStatusText: TextView
    private lateinit var callLogsStatusText: TextView
    private lateinit var messagesStatusText: TextView
    private lateinit var audioStatusText: TextView

    // Handler for periodic updates
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDataCollectionStatus()
            updateHandler.postDelayed(this, STATUS_UPDATE_INTERVAL)
        }
    }

    // Core permissions that all Android versions need
    private val corePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.RECORD_AUDIO
    )

    // Special permissions that need separate handling for Android 10+
    private val androidQPlusPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }

    // Notification permission for Android 13+
    private val android13PlusPermissions = if (Build.VERSION.SDK_INT >= 33) { // Android 13 = API 33
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        statusText = findViewById(R.id.statusText)
        permissionsButton = findViewById(R.id.permissionsButton)
        deviceIdText = findViewById(R.id.deviceIdText)
        syncButton = findViewById(R.id.syncButton)
        recordingButton = findViewById(R.id.recordingButton)
        liveTranscriptionButton = findViewById(R.id.liveTranscriptionButton)  // Initialize new button

        // Status text views
        locationStatusText = findViewById(R.id.locationStatusText)
        callLogsStatusText = findViewById(R.id.callLogsStatusText)
        messagesStatusText = findViewById(R.id.messagesStatusText)
        audioStatusText = findViewById(R.id.audioStatusText)

        // Set device ID
        val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
        deviceIdText.text = "Device ID: $deviceId"

        // Set up permission button
        permissionsButton.setOnClickListener {
            requestAllPermissions()
        }

        // Set up sync button
        syncButton.setOnClickListener {
            if (areAllCorePermissionsGranted()) {
                Toast.makeText(this, "Starting manual sync...", Toast.LENGTH_SHORT).show()
                DataSyncManager.syncAll(applicationContext)
                updateWidgets()

                // Update status immediately after sync
                updateDataCollectionStatus()
            } else {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_LONG).show()
                updatePermissionStatus()
            }
        }

        // Set up recording button
        recordingButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED) {
                toggleRecordingService()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        }

        // Set up live transcription button
// In the liveTranscriptionButton click listener:
        liveTranscriptionButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this, LiveTranscriptionActivity::class.java)
                startActivity(intent)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        }
        // Check permissions on startup
        updatePermissionStatus()

        // If all permissions are granted, ensure services are running
        if (areAllCorePermissionsGranted()) {
            startBackgroundServices()
        }

        // On Android 8+, check if we need to prompt about special power saving features
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            checkBatteryOptimizations()
        }
    }

    /**
     * Start live transcription activity
     */
    private fun startLiveTranscription() {
        val intent = Intent(this, LiveTranscriptionActivity::class.java)
        startActivity(intent)
    }

    /**
     * Toggle recording service on/off based on current state
     */
    private fun toggleRecordingService() {
        val isRecording = DataSyncManager.isRecordingServiceRunning()
        if (isRecording) {
            // Stop recording
            DataSyncManager.toggleRecordingService(applicationContext, false)
            recordingButton.text = "Start Audio Recording"
            audioStatusText.text = "Audio Recording: Off"
        } else {
            // Start recording
            DataSyncManager.toggleRecordingService(applicationContext, true)
            recordingButton.text = "Stop Audio Recording"
            audioStatusText.text = "Audio Recording: On"
        }
    }

    /**
     * Check if the device is ignoring battery optimizations for the app
     * This is needed for reliable background operation on Android 8+
     */
    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("To ensure Home Guardian works properly in the background, please disable battery optimization for this app.")
                    .setPositiveButton("Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update permission status each time activity is resumed
        updatePermissionStatus()

        // Start periodic updates
        updateHandler.post(updateRunnable)

        // Also check for pending syncs
        if (areAllCorePermissionsGranted()) {
            DataSyncManager.checkTriggers(applicationContext)
        }

        // Update recording button state
        updateRecordingButtonState()
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic updates
        updateHandler.removeCallbacks(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Request permissions in the proper sequence for Android 8+
     */
    private fun requestAllPermissions() {
        Log.d(TAG, "Requesting all permissions")

        // First, request core permissions
        ActivityCompat.requestPermissions(
            this,
            corePermissions,
            ALL_PERMISSIONS_REQUEST_CODE
        )

        // Special permissions like background location will be requested
        // in follow-up dialogs after the core permissions are granted
    }

    /**
     * Check for permission result and request additional permissions as needed
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            ALL_PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "Core permissions granted")

                    // For Android 10+, background location needs separate request
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        requestBackgroundLocationPermission()
                    } else if (Build.VERSION.SDK_INT >= 33) { // Android 13+
                        requestNotificationPermission()
                    } else {
                        // All permissions granted, start services
                        startBackgroundServices()
                    }
                } else {
                    // Some permissions denied
                    updatePermissionStatus()

                    // Check if user selected "never ask again"
                    if (!shouldShowRationaleForAnyPermission(corePermissions)) {
                        showSettingsDialog()
                    }
                }
            }

            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (Build.VERSION.SDK_INT >= 33) { // Android 13+
                    requestNotificationPermission()
                } else {
                    // All permissions processed, start services
                    startBackgroundServices()
                }
                updatePermissionStatus()
            }

            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                // Final permission processed, start services
                startBackgroundServices()
                updatePermissionStatus()
            }

            RECORD_AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // First check which button was clicked
                    if (recordingButton.isPressed) {
                        toggleRecordingService()
                    } else if (liveTranscriptionButton.isPressed) {
                        startLiveTranscription()
                    }
                } else {
                    Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Request background location permission (Android 10+)
     */
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

                // Show explanation dialog
                AlertDialog.Builder(this)
                    .setTitle("Background Location Needed")
                    .setMessage("Home Guardian needs background location access to monitor your location even when the app is closed. On the next screen, please select 'Allow all the time'.")
                    .setPositiveButton("Continue") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        // Move to next permission or start services
                        if (Build.VERSION.SDK_INT >= 33) {
                            requestNotificationPermission()
                        } else {
                            startBackgroundServices()
                        }
                    }
                    .show()
            } else {
                // Already granted, move to next permission
                if (Build.VERSION.SDK_INT >= 33) {
                    requestNotificationPermission()
                } else {
                    startBackgroundServices()
                }
            }
        }
    }

    /**
     * Request notification permission (Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

                // Show explanation dialog
                AlertDialog.Builder(this)
                    .setTitle("Notifications Needed")
                    .setMessage("Home Guardian uses notifications to keep you informed of its status and to run reliably in the background.")
                    .setPositiveButton("Continue") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            NOTIFICATION_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        // Start services even without notification permission
                        startBackgroundServices()
                    }
                    .show()
            } else {
                // Already granted, start services
                startBackgroundServices()
            }
        }
    }

    /**
     * Check if we should show rationale for any permission
     */
    private fun shouldShowRationaleForAnyPermission(permissions: Array<String>): Boolean {
        return permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
    }

    /**
     * Shows dialog to direct user to app settings when permissions are permanently denied
     */
    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Some necessary permissions have been denied permanently. Please enable them in app settings.")
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Check if all core permissions are granted
     */
    private fun areAllCorePermissionsGranted(): Boolean {
        return corePermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Update UI to show permission status
     */
    private fun updatePermissionStatus() {
        val status = StringBuilder()
        status.append("Permission Status:\n\n")

        // Core permissions
        for (permission in corePermissions) {
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            val permissionName = getReadablePermissionName(permission)
            status.append("• $permissionName: ${if (isGranted) "✓" else "✗"}\n")
        }

        // Background location for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            status.append("• Background Location: ${if (backgroundLocationGranted) "✓" else "✗"}\n")
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= 33) {
            val notificationPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            status.append("• Notifications: ${if (notificationPermissionGranted) "✓" else "✗"}\n")
        }

        status.append("\n")

        // Summary
        if (areAllCorePermissionsGranted()) {
            status.append("All essential permissions granted.\nService is running in the background.")
            permissionsButton.text = "Permissions: All Granted"
            syncButton.isEnabled = true
            recordingButton.isEnabled = true
            liveTranscriptionButton.isEnabled = true

            // Update recording button text
            updateRecordingButtonState()
        } else {
            status.append("Some permissions are missing.\nPlease grant all permissions for full functionality.")
            permissionsButton.text = "Grant Permissions"
            syncButton.isEnabled = false

            // Enable recording and transcription buttons if at least RECORD_AUDIO is granted
            val recordAudioGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            recordingButton.isEnabled = recordAudioGranted
            liveTranscriptionButton.isEnabled = recordAudioGranted
        }

        statusText.text = status.toString()
    }

    /**
     * Get user-friendly permission name
     */
    private fun getReadablePermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
            Manifest.permission.READ_CALL_LOG -> "Call Log"
            Manifest.permission.READ_SMS -> "SMS"
            Manifest.permission.READ_PHONE_STATE -> "Phone State"
            Manifest.permission.READ_CONTACTS -> "Contacts"
            Manifest.permission.RECEIVE_SMS -> "Receive SMS"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background Location"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission.substring(permission.lastIndexOf('.') + 1)
        }
    }

    /**
     * Update recording button state based on service status
     */
    private fun updateRecordingButtonState() {
        val isRecording = DataSyncManager.isRecordingServiceRunning()
        if (isRecording) {
            recordingButton.text = "Stop Audio Recording"
            audioStatusText.text = "Audio Recording: On"
        } else {
            recordingButton.text = "Start Audio Recording"
            audioStatusText.text = "Audio Recording: Off"
        }
    }

    /**
     * Update data collection status UI
     */
    private fun updateDataCollectionStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)

                // Get the latest location timestamp
                val locations = withContext(Dispatchers.IO) {
                    db.locationDao().getAllLocations()
                }

                // Get the latest call log timestamp
                val callLogsCount = withContext(Dispatchers.IO) {
                    val lastDay = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                    db.callLogDao().getCallLogsCountSince(lastDay)
                }

                // Get the latest message timestamp
                val messagesCount = withContext(Dispatchers.IO) {
                    val lastDay = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                    db.messageDao().getMessagesCountSince(lastDay)
                }

                // Format timestamps
                val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

                // Update UI
                if (locations.isNotEmpty()) {
                    val latestLocation = locations.maxByOrNull { it.timestamp }
                    locationStatusText.text = "Location: ${dateFormat.format(Date(latestLocation!!.timestamp))}"
                } else {
                    locationStatusText.text = "Location: Never"
                }

                if (callLogsCount > 0) {
                    callLogsStatusText.text = "Call Logs: $callLogsCount in last 24h"
                } else {
                    callLogsStatusText.text = "Call Logs: Never"
                }

                if (messagesCount > 0) {
                    messagesStatusText.text = "Messages: $messagesCount in last 24h"
                } else {
                    messagesStatusText.text = "Messages: Never"
                }

                // Audio status is updated separately in updateRecordingButtonState()

            } catch (e: Exception) {
                Log.e(TAG, "Error updating data collection status", e)
            }
        }
    }

    /**
     * Start all background services
     */
    private fun startBackgroundServices() {
        // Only start if core permissions are granted
        if (areAllCorePermissionsGranted()) {
            Log.d(TAG, "Starting background services")

            // Initialize the DataSyncManager with all services
            DataSyncManager.initialize(applicationContext)

            // Update all widgets
            updateWidgets()

            // Update UI
            updatePermissionStatus()
            Toast.makeText(this, "Home Guardian is now monitoring your device", Toast.LENGTH_SHORT).show()

            // Preload TranscriptionManager and check for models
            lifecycleScope.launch {
                val transcriptionManager = TranscriptionManager.getInstance(applicationContext)
                // Check if we have required models
                val availableLanguages = withContext(Dispatchers.IO) {
                    transcriptionManager.getAvailableLanguages()
                }

                val downloadedLanguages = availableLanguages.filter { it.isDownloaded }
                if (downloadedLanguages.isEmpty()) {
                    // No language models downloaded
                    Toast.makeText(
                        this@MainActivity,
                        "You'll need to download language models for live transcription",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Log.d(TAG, "Not all permissions granted, cannot start services")
            updatePermissionStatus()
        }
    }

    /**
     * Update all Home Guardian widgets on the home screen
     */
    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, HomeGuardianWidget::class.java)
        )

        // Send broadcast to update widgets
        if (appWidgetIds.isNotEmpty()) {
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                component = ComponentName(applicationContext, HomeGuardianWidget::class.java)
            }
            sendBroadcast(updateIntent)
        }
    }
}