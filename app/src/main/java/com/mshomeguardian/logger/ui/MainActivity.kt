package com.mshomeguardian.logger.ui

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.services.LocationMonitoringService
import com.mshomeguardian.logger.services.RecordingService
import com.mshomeguardian.logger.utils.DataSyncManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.widget.HomeGuardianWidget
import com.mshomeguardian.logger.workers.WorkerScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val LOCATION_PERMISSION_REQUEST_CODE = 101
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
    private val CALL_SMS_PERMISSION_REQUEST_CODE = 103
    private val ALL_PERMISSIONS_REQUEST_CODE = 104
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 105
    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 106

    // Update interval for status information (10 seconds)
    private val STATUS_UPDATE_INTERVAL = 10000L

    private lateinit var statusText: TextView
    private lateinit var permissionsButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var syncButton: Button
    private lateinit var recordingButton: Button

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

    // All required permissions
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.RECORD_AUDIO // Added RECORD_AUDIO permission
    )

    // Additional background location permission for Android 10+
    private val backgroundLocationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else null

    // Notification permission for Android 13+
    private val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        statusText = findViewById(R.id.statusText)
        permissionsButton = findViewById(R.id.permissionsButton)
        deviceIdText = findViewById(R.id.deviceIdText)
        syncButton = findViewById(R.id.syncButton)
        recordingButton = findViewById(R.id.recordingButton)

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
            if (areAllPermissionsGranted()) {
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
            if (areAllPermissionsGranted()) {
                val isRecording = DataSyncManager.isRecordingServiceRunning()
                if (isRecording) {
                    // Stop recording
                    DataSyncManager.toggleRecordingService(applicationContext, false)
                    recordingButton.text = "Start Audio Recording"
                    audioStatusText.text = "Audio Recording: Off"
                } else {
                    // Request RECORD_AUDIO permission if not granted
                    if (ContextCompat.checkSelfPermission(
                            this, Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            RECORD_AUDIO_PERMISSION_REQUEST_CODE
                        )
                    } else {
                        // Start recording
                        startRecording()
                    }
                }
            } else {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_LONG).show()
                updatePermissionStatus()
            }
        }

        // Check permissions on startup
        updatePermissionStatus()

        // If all permissions are granted, ensure services are running
        if (areAllPermissionsGranted()) {
            startBackgroundServices()
        }
    }

    private fun startRecording() {
        // Start recording
        DataSyncManager.toggleRecordingService(applicationContext, true)
        recordingButton.text = "Stop Audio Recording"
        audioStatusText.text = "Audio Recording: On"
    }

    override fun onResume() {
        super.onResume()
        // Update permission status each time activity is resumed
        // This handles the case where user grants permissions from Settings
        updatePermissionStatus()

        // Start periodic updates
        updateHandler.post(updateRunnable)

        // Also check for pending syncs
        if (areAllPermissionsGranted()) {
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

    private fun requestAllPermissions() {
        Log.d(TAG, "Requesting all permissions")

        // Standard permissions first
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
                // Check notification permission on Android 13+
                checkNotificationPermission()
            }
        } else {
            // Check notification permission for Android 13+
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                // All permissions granted, start services
                startBackgroundServices()
            }
        } else {
            // No notification permission needed, start services
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
                Log.d(TAG, "Standard permissions result: allGranted=$allGranted")

                if (allGranted) {
                    // Now check for background permission if needed
                    if (backgroundLocationPermission != null) {
                        checkBackgroundLocationPermission()
                    } else {
                        checkNotificationPermission()
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
                    Log.d(TAG, "Background location permission granted")
                    checkNotificationPermission()
                } else {
                    // Update UI to show missing permission
                    Log.d(TAG, "Background location permission denied")
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
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                // Even if notification permission is denied, we can still start the services
                Log.d(TAG, "Notification permission result: ${if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) "granted" else "denied"}")
                startBackgroundServices()
            }
            RECORD_AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Record audio permission granted, start recording
                    startRecording()
                } else {
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

        // Notification permission is not critical, so we don't check it here

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

        // Check record audio permission
        val recordAudioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        status.append("• Record Audio: ${if (recordAudioGranted) "✓" else "✗"}\n")

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            status.append("• Notifications: ${if (notificationGranted) "✓" else "✗"}\n")
        }

        status.append("\n")

        // Summary
        if (areAllPermissionsGranted()) {
            status.append("All permissions granted.\nService is running in the background.")
            permissionsButton.text = "Permissions: All Granted"
            syncButton.isEnabled = true
            recordingButton.isEnabled = true

            // Update recording button text
            updateRecordingButtonState()
        } else {
            status.append("Some permissions are missing.\nPlease grant all permissions for full functionality.")
            permissionsButton.text = "Grant Permissions"
            syncButton.isEnabled = false

            // Enable recording button if at least RECORD_AUDIO is granted
            recordingButton.isEnabled = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        statusText.text = status.toString()
    }

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

    private fun startBackgroundServices() {
        // Only start if all permissions are granted
        if (areAllPermissionsGranted()) {
            Log.d(TAG, "Starting background services")

            // Initialize the DataSyncManager with all services
            DataSyncManager.initialize(applicationContext)

            // Start location monitoring service
            try {
                Log.d(TAG, "Starting location monitoring service")
                val locationIntent = Intent(this, LocationMonitoringService::class.java)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(locationIntent)
                } else {
                    startService(locationIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start location service", e)
            }

            // Update all widgets
            updateWidgets()

            // Update UI
            updatePermissionStatus()
            Toast.makeText(this, "Home Guardian is now monitoring your device", Toast.LENGTH_SHORT).show()
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