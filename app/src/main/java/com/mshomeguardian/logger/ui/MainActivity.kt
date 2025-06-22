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
import com.mshomeguardian.logger.utils.AuthManager
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

import com.mshomeguardian.logger.utils.OptimizedLogger

/**
 * MainActivity with custom authentication (no FirebaseUI dependency)
 */
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    // Request codes for permission handling
    private val ALL_PERMISSIONS_REQUEST_CODE = 104
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 105
    private val FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 107
    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 106

    // Update interval for status information (10 seconds)
    private val STATUS_UPDATE_INTERVAL = 10000L

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var permissionsButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var accountInfoText: TextView
    private lateinit var syncButton: Button
    private lateinit var recordingButton: Button
    private lateinit var liveTranscriptionButton: Button
    private lateinit var signOutButton: Button

    // Status text views
    private lateinit var locationStatusText: TextView
    private lateinit var callLogsStatusText: TextView
    private lateinit var messagesStatusText: TextView
    private lateinit var audioStatusText: TextView

    // Handler for periodic updates
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            safeExecute("updateDataCollectionStatus") {
                updateDataCollectionStatus()
            }
            updateHandler.postDelayed(this, STATUS_UPDATE_INTERVAL)
        }
    }

    // Comprehensive permission arrays
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

    private val android10PlusPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }

    private val android13PlusPermissions = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    private val android14PlusPermissions = if (Build.VERSION.SDK_INT >= 34) {
        arrayOf(
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        )
    } else {
        emptyArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        safeExecute("onCreate") {
            // Check authentication first
            if (!AuthManager.isSignedIn()) {
                OptimizedLogger.d(TAG, "User not signed in, starting SignInActivity")
                startSignInActivity()
                return@safeExecute
            }

            OptimizedLogger.d(TAG, "User signed in: ${AuthManager.getCurrentUser()?.email}")
            setContentView(R.layout.activity_main)

            initializeUI()
            checkBatteryOptimizations()
        }
    }

    /**
     * Fail-safe execution wrapper
     */
    private fun safeExecute(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error in $operation", e)
            handleError("Error in $operation: ${e.message}")
        }
    }

    /**
     * Handle errors gracefully without crashing
     */
    private fun handleError(message: String) {
        runOnUiThread {
            try {
                Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                OptimizedLogger.e(TAG, message)
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error showing error message", e)
            }
        }
    }

    private fun initializeUI() {
        try {
            // Initialize UI elements
            statusText = findViewById(R.id.statusText)
            permissionsButton = findViewById(R.id.permissionsButton)
            deviceIdText = findViewById(R.id.deviceIdText)
            accountInfoText = findViewById(R.id.accountInfoText)
            syncButton = findViewById(R.id.syncButton)
            recordingButton = findViewById(R.id.recordingButton)
            liveTranscriptionButton = findViewById(R.id.liveTranscriptionButton)
            signOutButton = findViewById(R.id.signOutButton)

            // Status text views
            locationStatusText = findViewById(R.id.locationStatusText)
            callLogsStatusText = findViewById(R.id.callLogsStatusText)
            messagesStatusText = findViewById(R.id.messagesStatusText)
            audioStatusText = findViewById(R.id.audioStatusText)

            // Set device ID and account info
            val deviceId = DeviceIdentifier.getPersistentDeviceId(applicationContext)
            deviceIdText.text = "Device ID: $deviceId"

            // Display current user email
            val currentUser = AuthManager.getCurrentUser()
            val userEmail = currentUser?.email ?: "Unknown"
            accountInfoText.text = "Account: $userEmail"

            setupButtonListeners()
            updatePermissionStatus()

            // If all permissions are granted, ensure services are running
            if (areAllRequiredPermissionsGranted()) {
                startBackgroundServices()
            }
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error initializing UI", e)
            handleError("Failed to initialize UI")
        }
    }

    private fun setupButtonListeners() {
        safeExecute("setupButtonListeners") {
            permissionsButton.setOnClickListener {
                safeExecute("permissionsButton.click") {
                    requestAllPermissions()
                }
            }

            syncButton.setOnClickListener {
                safeExecute("syncButton.click") {
                    if (areAllRequiredPermissionsGranted()) {
                        Toast.makeText(this, "Starting manual sync...", Toast.LENGTH_SHORT).show()
                        DataSyncManager.syncAll(applicationContext)
                        updateWidgets()
                        updateDataCollectionStatus()
                    } else {
                        Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_LONG).show()
                        updatePermissionStatus()
                    }
                }
            }

            recordingButton.setOnClickListener {
                safeExecute("recordingButton.click") {
                    if (canUseAudioFeatures()) {
                        toggleRecordingService()
                    } else {
                        requestAudioPermissions()
                    }
                }
            }

            liveTranscriptionButton.setOnClickListener {
                safeExecute("liveTranscriptionButton.click") {
                    if (canUseAudioFeatures()) {
                        val intent = Intent(this, LiveTranscriptionActivity::class.java)
                        startActivity(intent)
                    } else {
                        requestAudioPermissions()
                    }
                }
            }

            signOutButton.setOnClickListener {
                safeExecute("signOutButton.click") {
                    signOut()
                }
            }
        }
    }

    private fun startSignInActivity() {
        safeExecute("startSignInActivity") {
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun signOut() {
        safeExecute("signOut") {
            // Stop all services first
            DataSyncManager.stopAllServices(applicationContext)

            // Sign out using custom AuthManager
            AuthManager.signOut()

            // Clear saved credentials
            AuthManager.clearSavedCredentials(this)

            OptimizedLogger.d(TAG, "User signed out successfully")
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            startSignInActivity()
        }
    }

    private fun toggleRecordingService() {
        safeExecute("toggleRecordingService") {
            val isRecording = DataSyncManager.isRecordingServiceRunning()
            if (isRecording) {
                DataSyncManager.toggleRecordingService(applicationContext, false)
                recordingButton.text = "Start Audio Recording"
                audioStatusText.text = "Audio Recording: Off"
            } else {
                DataSyncManager.toggleRecordingService(applicationContext, true)
                recordingButton.text = "Stop Audio Recording"
                audioStatusText.text = "Audio Recording: On"
            }
        }
    }

    private fun checkBatteryOptimizations() {
        safeExecute("checkBatteryOptimizations") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val packageName = packageName

                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    AlertDialog.Builder(this)
                        .setTitle("Battery Optimization")
                        .setMessage("To ensure Home Guardian works properly in the background, please disable battery optimization for this app.")
                        .setPositiveButton("Settings") { _, _ ->
                            safeExecute("batteryOptimizationSettings") {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                startActivity(intent)
                            }
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        safeExecute("onResume") {
            // Check if user is still signed in
            if (!AuthManager.isSignedIn()) {
                startSignInActivity()
                return@safeExecute
            }

            updatePermissionStatus()
            updateHandler.post(updateRunnable)

            if (areAllRequiredPermissionsGranted()) {
                DataSyncManager.checkTriggers(applicationContext)
            }

            updateRecordingButtonState()
        }
    }

    override fun onPause() {
        super.onPause()
        safeExecute("onPause") {
            updateHandler.removeCallbacks(updateRunnable)
        }
    }

    /**
     * Request all necessary permissions with fail-safe handling
     */
    private fun requestAllPermissions() {
        safeExecute("requestAllPermissions") {
            OptimizedLogger.d(TAG, "Requesting all permissions")

            // Check if we already have all permissions
            if (areAllRequiredPermissionsGranted()) {
                Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
                return@safeExecute
            }

            // Request core permissions first
            val missingCorePermissions = corePermissions.filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }

            if (missingCorePermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missingCorePermissions.toTypedArray(),
                    ALL_PERMISSIONS_REQUEST_CODE
                )
            } else {
                // Core permissions are granted, check others
                requestAdditionalPermissions()
            }
        }
    }

    private fun requestAdditionalPermissions() {
        safeExecute("requestAdditionalPermissions") {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                    requestBackgroundLocationPermission()
                }
                Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                    requestNotificationPermission()
                }
                Build.VERSION.SDK_INT >= 34 -> {
                    requestForegroundServicePermissions()
                }
                else -> {
                    startBackgroundServices()
                }
            }
        }
    }

    /**
     * Check if audio features can be used safely
     */
    private fun canUseAudioFeatures(): Boolean {
        return try {
            val hasRecordAudio = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val hasForegroundServiceMicrophone = if (Build.VERSION.SDK_INT >= 34) {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            hasRecordAudio && hasForegroundServiceMicrophone
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error checking audio permissions", e)
            false
        }
    }

    private fun requestAudioPermissions() {
        safeExecute("requestAudioPermissions") {
            val permissions = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }

            if (Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }

            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        safeExecute("onRequestPermissionsResult") {
            when (requestCode) {
                ALL_PERMISSIONS_REQUEST_CODE -> {
                    val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) {
                        OptimizedLogger.d(TAG, "Core permissions granted")
                        requestAdditionalPermissions()
                    } else {
                        OptimizedLogger.w(TAG, "Some core permissions denied")
                        updatePermissionStatus()
                        showPermissionDeniedDialog()
                    }
                }

                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestNotificationPermission()
                    } else if (Build.VERSION.SDK_INT >= 34) {
                        requestForegroundServicePermissions()
                    } else {
                        startBackgroundServices()
                    }
                    updatePermissionStatus()
                }

                NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                    if (Build.VERSION.SDK_INT >= 34) {
                        requestForegroundServicePermissions()
                    } else {
                        startBackgroundServices()
                    }
                    updatePermissionStatus()
                }

                FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE -> {
                    startBackgroundServices()
                    updatePermissionStatus()
                }

                RECORD_AUDIO_PERMISSION_REQUEST_CODE -> {
                    val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) {
                        Toast.makeText(this, "Audio permissions granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Audio permissions denied. Some features may not work.", Toast.LENGTH_LONG).show()
                    }
                    updatePermissionStatus()
                }
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        safeExecute("showPermissionDeniedDialog") {
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("Home Guardian needs these permissions to function properly. You can grant them manually in Settings > Apps > Home Guardian > Permissions.")
                .setPositiveButton("Open Settings") { _, _ ->
                    safeExecute("openAppSettings") {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Continue Anyway") { _, _ ->
                    // Allow the user to continue with limited functionality
                    updatePermissionStatus()
                }
                .show()
        }
    }

    private fun requestBackgroundLocationPermission() {
        safeExecute("requestBackgroundLocationPermission") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                        requestAdditionalPermissions()
                    }
                    .show()
            }
        }
    }

    private fun requestNotificationPermission() {
        safeExecute("requestNotificationPermission") {
            if (Build.VERSION.SDK_INT >= 33) {
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
                        if (Build.VERSION.SDK_INT >= 34) {
                            requestForegroundServicePermissions()
                        } else {
                            startBackgroundServices()
                        }
                    }
                    .show()
            }
        }
    }

    private fun requestForegroundServicePermissions() {
        safeExecute("requestForegroundServicePermissions") {
            if (Build.VERSION.SDK_INT >= 34) {
                val missingPermissions = android14PlusPermissions.filter { permission ->
                    ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPermissions.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Foreground Service Permissions Needed")
                        .setMessage("Android 14+ requires explicit permissions to run location and audio services in the background. These are essential for Home Guardian to function properly.")
                        .setPositiveButton("Continue") { _, _ ->
                            ActivityCompat.requestPermissions(
                                this,
                                missingPermissions.toTypedArray(),
                                FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE
                            )
                        }
                        .setNegativeButton("Skip") { _, _ ->
                            startBackgroundServices()
                        }
                        .show()
                } else {
                    startBackgroundServices()
                }
            } else {
                startBackgroundServices()
            }
        }
    }

    private fun areAllRequiredPermissionsGranted(): Boolean {
        return try {
            val coreGranted = corePermissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }

            val foregroundServiceGranted = if (Build.VERSION.SDK_INT >= 34) {
                android14PlusPermissions.all { permission ->
                    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                }
            } else {
                true
            }

            coreGranted && foregroundServiceGranted
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error checking permissions", e)
            false
        }
    }

    private fun updatePermissionStatus() {
        safeExecute("updatePermissionStatus") {
            val status = StringBuilder()
            status.append("Permission Status:\n\n")

            // Core permissions
            for (permission in corePermissions) {
                val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                val permissionName = getReadablePermissionName(permission)
                status.append("• $permissionName: ${if (isGranted) "✓" else "✗"}\n")
            }

            // Additional permissions based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                status.append("• Background Location: ${if (backgroundLocationGranted) "✓" else "✗"}\n")
            }

            if (Build.VERSION.SDK_INT >= 33) {
                val notificationPermissionGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                status.append("• Notifications: ${if (notificationPermissionGranted) "✓" else "✗"}\n")
            }

            if (Build.VERSION.SDK_INT >= 34) {
                for (permission in android14PlusPermissions) {
                    val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
                    val permissionName = getReadablePermissionName(permission)
                    status.append("• $permissionName: ${if (isGranted) "✓" else "✗"}\n")
                }
            }

            status.append("\n")

            // Summary
            if (areAllRequiredPermissionsGranted()) {
                status.append("✅ All essential permissions granted.\nServices are running in the background.")
                permissionsButton.text = "Permissions: All Granted"
                syncButton.isEnabled = true
                updateAudioButtonStates()
            } else {
                status.append("⚠️ Some permissions are missing.\nThe app will work with limited functionality.")
                permissionsButton.text = "Grant Missing Permissions"
                syncButton.isEnabled = false
                updateAudioButtonStates()
            }

            statusText.text = status.toString()
        }
    }

    private fun updateAudioButtonStates() {
        safeExecute("updateAudioButtonStates") {
            val canUseAudio = canUseAudioFeatures()
            recordingButton.isEnabled = canUseAudio
            liveTranscriptionButton.isEnabled = canUseAudio

            if (canUseAudio) {
                updateRecordingButtonState()
            } else {
                audioStatusText.text = "Audio Recording: Permissions Required"
            }
        }
    }

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
            Manifest.permission.FOREGROUND_SERVICE_LOCATION -> "Location Service"
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE -> "Audio Service"
            else -> permission.substring(permission.lastIndexOf('.') + 1)
        }
    }

    private fun updateRecordingButtonState() {
        safeExecute("updateRecordingButtonState") {
            val isRecording = DataSyncManager.isRecordingServiceRunning()
            if (isRecording) {
                recordingButton.text = "Stop Audio Recording"
                audioStatusText.text = "Audio Recording: On"
            } else {
                recordingButton.text = "Start Audio Recording"
                audioStatusText.text = "Audio Recording: Off"
            }
        }
    }

    private fun updateDataCollectionStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)

                val locations = withContext(Dispatchers.IO) {
                    db.locationDao().getAllLocations()
                }

                val callLogsCount = withContext(Dispatchers.IO) {
                    val lastDay = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                    db.callLogDao().getCallLogsCountSince(lastDay)
                }

                val messagesCount = withContext(Dispatchers.IO) {
                    val lastDay = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                    db.messageDao().getMessagesCountSince(lastDay)
                }

                val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

                // Update UI safely
                runOnUiThread {
                    safeExecute("updateDataCollectionUI") {
                        if (locations.isNotEmpty()) {
                            val latestLocation = locations.maxByOrNull { it.timestamp }
                            locationStatusText.text = "Location: ${dateFormat.format(Date(latestLocation!!.timestamp))}"
                        } else {
                            locationStatusText.text = "Location: Never"
                        }

                        callLogsStatusText.text = if (callLogsCount > 0) {
                            "Call Logs: $callLogsCount in last 24h"
                        } else {
                            "Call Logs: Never"
                        }

                        messagesStatusText.text = if (messagesCount > 0) {
                            "Messages: $messagesCount in last 24h"
                        } else {
                            "Messages: Never"
                        }
                    }
                }

            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Error updating data collection status", e)
                handleError("Failed to update data collection status")
            }
        }
    }

    private fun startBackgroundServices() {
        safeExecute("startBackgroundServices") {
            if (areAllRequiredPermissionsGranted()) {
                OptimizedLogger.d(TAG, "Starting background services")

                DataSyncManager.initialize(applicationContext)
                updateWidgets()
                updatePermissionStatus()

                Toast.makeText(this, "Home Guardian is now monitoring your device", Toast.LENGTH_SHORT).show()

                // Preload TranscriptionManager
                lifecycleScope.launch {
                    try {
                        val transcriptionManager = TranscriptionManager.getInstance(applicationContext)
                        val availableLanguages = withContext(Dispatchers.IO) {
                            transcriptionManager.getAvailableLanguages()
                        }

                        val downloadedLanguages = availableLanguages.filter { it.isDownloaded }
                        if (downloadedLanguages.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "Download language models for live transcription",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        OptimizedLogger.e(TAG, "Error checking transcription models", e)
                    }
                }
            } else {
                OptimizedLogger.d(TAG, "Not all permissions granted, running with limited functionality")
                updatePermissionStatus()
            }
        }
    }

    private fun updateWidgets() {
        safeExecute("updateWidgets") {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(this, HomeGuardianWidget::class.java)
            )

            if (appWidgetIds.isNotEmpty()) {
                val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = ComponentName(applicationContext, HomeGuardianWidget::class.java)
                }
                sendBroadcast(updateIntent)
            }
        }
    }
}