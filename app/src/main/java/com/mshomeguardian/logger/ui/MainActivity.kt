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
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.LocationMonitoringService
import com.mshomeguardian.logger.utils.DataSyncManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.QuickDebugSetup
import com.mshomeguardian.logger.utils.initDebugFeatures
import com.mshomeguardian.logger.widget.HomeGuardianWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.appwidget.AppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.mshomeguardian.logger.transcription.TranscriptionManager

import com.mshomeguardian.logger.utils.OptimizedLogger
import com.mshomeguardian.logger.utils.CrashPreventionUtils
import com.mshomeguardian.logger.utils.UpdateManager

/**
 * Crash-Safe MainActivity with comprehensive permission handling
 */
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    // Request codes for permission handling
    private val ALL_PERMISSIONS_REQUEST_CODE = 104
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 105
    private val FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 107
    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 106

    // UI elements
    private lateinit var permissionsButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var accountInfoText: TextView
    private lateinit var syncButton: Button
    private lateinit var recordingButton: Button
    private lateinit var liveTranscriptionButton: Button
    private lateinit var signOutButton: Button
    

    // Permission arrays with crash-safe access
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

        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "onCreate", Unit) {
            // Initialize crash prevention system first
            CrashPreventionUtils.initialize(this)

            // Check authentication with crash protection
            if (!safeAuthCheck()) {
                OptimizedLogger.d(TAG, "User not signed in, starting SignInActivity")
                startSignInActivity()
                return@safeExecute
            }

            OptimizedLogger.d(TAG, "User signed in: ${AuthManager.getCurrentUser()?.email}")
            setContentView(R.layout.activity_main)

            // Initialize UI with crash protection
            initializeUIWithCrashProtection()

            // Check battery optimizations safely
            safeCheckBatteryOptimizations()
        }
    }

    /**
     * Safe authentication check that never crashes
     */
    private fun safeAuthCheck(): Boolean {
        return CrashPreventionUtils.ErrorHandling.safeExecute(
            TAG, "safeAuthCheck", false
        ) {
            AuthManager.isSignedIn()
        }
    }

    /**
     * Initialize UI with comprehensive crash protection
     */
    private fun initializeUIWithCrashProtection() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "initializeUI", Unit) {
            try {
                // Initialize UI elements with null checks
                permissionsButton = findViewById(R.id.permissionsButton)
                deviceIdText = findViewById(R.id.deviceIdText)
                accountInfoText = findViewById(R.id.accountInfoText)
                syncButton = findViewById(R.id.syncButton)
                recordingButton = findViewById(R.id.recordingButton)
                liveTranscriptionButton = findViewById(R.id.liveTranscriptionButton)
                signOutButton = findViewById(R.id.signOutButton)

                // Set device ID safely
                val deviceId = CrashPreventionUtils.ErrorHandling.safeExecute(
                    TAG, "getDeviceId", "Unknown"
                ) {
                    DeviceIdentifier.getPersistentDeviceId(applicationContext)
                }
                deviceIdText.text = "Device ID: $deviceId"

                // Display current user email safely
                val userEmail = CrashPreventionUtils.ErrorHandling.safeExecute(
                    TAG, "getCurrentUser", "Unknown"
                ) {
                    AuthManager.getCurrentUser()?.email ?: "Unknown"
                }
                accountInfoText.text = "Account: $userEmail"

                val rootContainer = findViewById<ViewGroup>(android.R.id.content)
                initDebugFeatures(rootContainer, deviceIdText)

                setupButtonListeners()
                updatePermissionStatusSafely()

                // Start services only if permissions are granted
                if (areAllRequiredPermissionsGrantedSafely()) {
                    startBackgroundServicesSafely()
                } else {
                    // Show permission request immediately if no permissions
                    showInitialPermissionDialog()
                }

                // Check GitHub for app updates in the background
                lifecycleScope.launch {
                    UpdateManager.checkForUpdates(this@MainActivity)
                }

            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Critical error in UI initialization", e)
                showFallbackUI()
            }
        }
    }

    /**
     * Show initial permission dialog when no permissions are granted
     */
    private fun showInitialPermissionDialog() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "showInitialPermissionDialog", Unit) {
            AlertDialog.Builder(this)
                .setTitle("Welcome to Home Guardian")
                .setMessage("To protect your device and data, Home Guardian needs several permissions. Please grant the required permissions to continue.")
                .setPositiveButton("Grant Permissions") { _, _ ->
                    requestAllPermissionsSafely()
                }
                .setNegativeButton("Learn More") { _, _ ->
                    showPermissionExplanationDialog()
                }
                .setCancelable(false)
                .show()
        }
    }

    /**
     * Show detailed permission explanation
     */
    private fun showPermissionExplanationDialog() {
        val message = """
            Home Guardian requires the following permissions to protect your device:
            
            📍 Location: Track device location for security
            📞 Phone Access: Monitor calls and messages
            👥 Contacts: Identify callers and message senders  
            🎤 Microphone: Optional audio recording
            🔔 Notifications: Keep you informed of app status
            
            All data is encrypted and stored securely. You can revoke permissions at any time in Settings.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Why These Permissions?")
            .setMessage(message)
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestAllPermissionsSafely()
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .show()
    }

    /**
     * Fallback UI when normal initialization fails
     */
    private fun showFallbackUI() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "showFallbackUI", Unit) {
            try {
                permissionsButton?.text = "Restart App"
                permissionsButton?.setOnClickListener {
                    recreate() // Try to restart the activity
                }
            } catch (e: Exception) {
                OptimizedLogger.e(TAG, "Even fallback UI failed", e)
                // Last resort - show toast and finish
                Toast.makeText(this, "Critical error. Please reinstall the app.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupButtonListeners() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "setupButtonListeners", Unit) {
            permissionsButton.setOnClickListener {
                requestAllPermissionsSafely()
            }

            syncButton.setOnClickListener {
                if (areAllRequiredPermissionsGrantedSafely()) {
                    Toast.makeText(this, "Starting manual sync...", Toast.LENGTH_SHORT).show()
                    CrashPreventionUtils.ErrorHandling.safeAsync(
                        TAG, "manual sync"
                    ) {
                        DataSyncManager.syncAll(applicationContext)
                        withContext(Dispatchers.Main) {
                            updateWidgetsSafely()
                        }
                    }
                } else {
                    Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_LONG).show()
                    updatePermissionStatusSafely()
                }
            }

            recordingButton.setOnClickListener {
                if (canUseAudioFeaturesSafely()) {
                    toggleRecordingServiceSafely()
                } else {
                    requestAudioPermissionsSafely()
                }
            }

            liveTranscriptionButton.setOnClickListener {
                if (canUseAudioFeaturesSafely()) {
                    CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "startLiveTranscription", Unit) {
                        val intent = Intent(this, LiveTranscriptionActivity::class.java)
                        startActivity(intent)
                    }
                } else {
                    requestAudioPermissionsSafely()
                }
            }

            signOutButton.setOnClickListener {
                signOutSafely()
            }
        }
    }

    /**
     * Safe permission checking that never crashes
     */
    private fun areAllRequiredPermissionsGrantedSafely(): Boolean {
        return CrashPreventionUtils.ErrorHandling.safeExecute(
            TAG, "areAllRequiredPermissionsGranted", false
        ) {
            val coreGranted = corePermissions.all { permission ->
                CrashPreventionUtils.hasPermission(this, permission)
            }

            val foregroundServiceGranted = if (Build.VERSION.SDK_INT >= 34) {
                android14PlusPermissions.all { permission ->
                    CrashPreventionUtils.hasPermission(this, permission)
                }
            } else {
                true
            }

            coreGranted && foregroundServiceGranted
        }
    }

    /**
     * Safe audio permission checking
     */
    private fun canUseAudioFeaturesSafely(): Boolean {
        return CrashPreventionUtils.ErrorHandling.safeExecute(
            TAG, "canUseAudioFeatures", false
        ) {
            CrashPreventionUtils.canStartAudioService(this)
        }
    }

    /**
     * Safe permission request that handles all edge cases
     */
    private fun requestAllPermissionsSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "requestAllPermissions", Unit) {
            OptimizedLogger.d(TAG, "Requesting all permissions safely")

            // Check if we already have all permissions
            if (areAllRequiredPermissionsGrantedSafely()) {
                Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
                return@safeExecute
            }

            // Get missing core permissions safely
            val missingCorePermissions = CrashPreventionUtils.getMissingPermissions(this, corePermissions)

            if (missingCorePermissions.isNotEmpty()) {
                OptimizedLogger.d(TAG, "Requesting ${missingCorePermissions.size} missing permissions")
                ActivityCompat.requestPermissions(
                    this,
                    missingCorePermissions.toTypedArray(),
                    ALL_PERMISSIONS_REQUEST_CODE
                )
            } else {
                // Core permissions are granted, check others
                requestAdditionalPermissionsSafely()
            }
        }
    }

    private fun requestAdditionalPermissionsSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "requestAdditionalPermissions", Unit) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        !CrashPreventionUtils.hasPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> {
                    requestBackgroundLocationPermissionSafely()
                }
                Build.VERSION.SDK_INT >= 33 &&
                        !CrashPreventionUtils.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestNotificationPermissionSafely()
                }
                Build.VERSION.SDK_INT >= 34 -> {
                    requestForegroundServicePermissionsSafely()
                }
                else -> {
                    startBackgroundServicesSafely()
                }
            }
        }
    }

    private fun requestAudioPermissionsSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "requestAudioPermissions", Unit) {
            val missingPermissions = mutableListOf<String>()

            if (!CrashPreventionUtils.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
                missingPermissions.add(Manifest.permission.RECORD_AUDIO)
            }

            if (Build.VERSION.SDK_INT >= 34 &&
                !CrashPreventionUtils.hasPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)) {
                missingPermissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }

            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun requestBackgroundLocationPermissionSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "requestBackgroundLocationPermission", Unit) {
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
                        requestAdditionalPermissionsSafely()
                    }
                    .show()
            }
        }
    }

    private fun requestNotificationPermissionSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "requestNotificationPermission", Unit) {
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
                            requestForegroundServicePermissionsSafely()
                        } else {
                            startBackgroundServicesSafely()
                        }
                    }
                    .show()
            }
        }
    }

    private fun requestForegroundServicePermissionsSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "requestForegroundServicePermissions", Unit) {
            if (Build.VERSION.SDK_INT >= 34) {
                val missingPermissions = CrashPreventionUtils.getMissingPermissions(this, android14PlusPermissions)

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
                            startBackgroundServicesSafely()
                        }
                        .show()
                } else {
                    startBackgroundServicesSafely()
                }
            } else {
                startBackgroundServicesSafely()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "onRequestPermissionsResult", Unit) {
            when (requestCode) {
                ALL_PERMISSIONS_REQUEST_CODE -> {
                    val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) {
                        OptimizedLogger.d(TAG, "Core permissions granted")
                        Toast.makeText(this, "Core permissions granted!", Toast.LENGTH_SHORT).show()
                        requestAdditionalPermissionsSafely()
                    } else {
                        OptimizedLogger.w(TAG, "Some core permissions denied")
                        updatePermissionStatusSafely()
                        showPermissionDeniedDialogSafely()
                    }
                }

                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                    val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        Toast.makeText(this, "Background location granted!", Toast.LENGTH_SHORT).show()
                    }

                    if (Build.VERSION.SDK_INT >= 33) {
                        requestNotificationPermissionSafely()
                    } else if (Build.VERSION.SDK_INT >= 34) {
                        requestForegroundServicePermissionsSafely()
                    } else {
                        startBackgroundServicesSafely()
                    }
                    updatePermissionStatusSafely()
                }

                NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                    val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
                    }

                    if (Build.VERSION.SDK_INT >= 34) {
                        requestForegroundServicePermissionsSafely()
                    } else {
                        startBackgroundServicesSafely()
                    }
                    updatePermissionStatusSafely()
                }

                FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE -> {
                    val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) {
                        Toast.makeText(this, "Foreground service permissions granted!", Toast.LENGTH_SHORT).show()
                    }
                    startBackgroundServicesSafely()
                    updatePermissionStatusSafely()
                }

                RECORD_AUDIO_PERMISSION_REQUEST_CODE -> {
                    val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    if (allGranted) {
                        Toast.makeText(this, "Audio permissions granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Audio permissions denied. Some features may not work.", Toast.LENGTH_LONG).show()
                    }
                    updatePermissionStatusSafely()
                }
            }
        }
    }

    private fun showPermissionDeniedDialogSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "showPermissionDeniedDialog", Unit) {
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("Home Guardian needs these permissions to function properly. You can grant them manually in Settings > Apps > Home Guardian > Permissions.")
                .setPositiveButton("Open Settings") { _, _ ->
                    CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "openAppSettings", Unit) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Continue Anyway") { _, _ ->
                    updatePermissionStatusSafely()
                }
                .show()
        }
    }

    /**
     * Safe permission status update that never crashes
     */
    private fun updatePermissionStatusSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "updatePermissionStatus", Unit) {
            val allGranted = areAllRequiredPermissionsGrantedSafely()
            if (allGranted) {
                permissionsButton.text = "Permissions: All Granted"
                syncButton.isEnabled = true
            } else {
                permissionsButton.text = "Grant Missing Permissions"
                syncButton.isEnabled = false
            }
            updateAudioButtonStatesSafely()
        }
    }

    private fun updateAudioButtonStatesSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "updateAudioButtonStates", Unit) {
            val canUseAudio = canUseAudioFeaturesSafely()
            recordingButton.isEnabled = canUseAudio
            liveTranscriptionButton.isEnabled = canUseAudio

            if (canUseAudio) {
                updateRecordingButtonStateSafely()
            }
        }
    }

    private fun updateRecordingButtonStateSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "updateRecordingButtonState", Unit) {
            val isRecording = CrashPreventionUtils.ErrorHandling.safeExecute(
                TAG, "isRecordingServiceRunning", false
            ) {
                DataSyncManager.isRecordingServiceRunning()
            }

            if (isRecording) {
                recordingButton.text = "Stop Audio Recording"
            } else {
                recordingButton.text = "Start Audio Recording"
            }
        }
    }

    private fun toggleRecordingServiceSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "toggleRecordingService", Unit) {
            val isRecording = CrashPreventionUtils.ErrorHandling.safeExecute(
                TAG, "checkRecordingStatus", false
            ) {
                DataSyncManager.isRecordingServiceRunning()
            }

            if (isRecording) {
                DataSyncManager.toggleRecordingService(applicationContext, false)
                recordingButton.text = "Start Audio Recording"
            } else {
                DataSyncManager.toggleRecordingService(applicationContext, true)
                recordingButton.text = "Stop Audio Recording"
            }
        }
    }

    /**
     * Safe background services startup
     */
    private fun startBackgroundServicesSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "startBackgroundServices", Unit) {
            if (areAllRequiredPermissionsGrantedSafely()) {
                OptimizedLogger.d(TAG, "Starting background services safely")

                CrashPreventionUtils.ErrorHandling.safeAsync(TAG, "initializeServices") {
                    DataSyncManager.initialize(applicationContext)

                    withContext(Dispatchers.Main) {
                        updateWidgetsSafely()
                        updatePermissionStatusSafely()
                        Toast.makeText(this@MainActivity, "Home Guardian is now monitoring your device", Toast.LENGTH_SHORT).show()
                    }
                }

                // Preload TranscriptionManager safely
                lifecycleScope.launch {
                    CrashPreventionUtils.ErrorHandling.safeAsync(TAG, "preloadTranscription") {
                        val transcriptionManager = TranscriptionManager.getInstance(applicationContext)
                        val availableLanguages = transcriptionManager.getAvailableLanguages()
                        val downloadedLanguages = availableLanguages.filter { it.isDownloaded }

                        withContext(Dispatchers.Main) {
                            if (downloadedLanguages.isEmpty()) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Download language models for live transcription",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            } else {
                OptimizedLogger.d(TAG, "Not all permissions granted, running with limited functionality")
                updatePermissionStatusSafely()
            }
        }
    }

    /**
     * Safe battery optimization check
     */
    private fun safeCheckBatteryOptimizations() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "checkBatteryOptimizations", Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val packageName = packageName

                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    // Delay showing the dialog to avoid overwhelming the user
                    Handler(Looper.getMainLooper()).postDelayed({
                        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "showBatteryDialog", Unit) {
                            AlertDialog.Builder(this)
                                .setTitle("Battery Optimization")
                                .setMessage("To ensure Home Guardian works properly in the background, please disable battery optimization for this app.")
                                .setPositiveButton("Settings") { _, _ ->
                                    CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "openBatterySettings", Unit) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:$packageName")
                                        }
                                        startActivity(intent)
                                    }
                                }
                                .setNegativeButton("Later", null)
                                .show()
                        }
                    }, 3000) // 3 second delay
                }
            }
        }
    }

    private fun updateWidgetsSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "updateWidgets", Unit) {
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

    private fun signOutSafely() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "signOut", Unit) {
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

    private fun startSignInActivity() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "startSignInActivity", Unit) {
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "onResume", Unit) {
            // Check if user is still signed in
            if (!safeAuthCheck()) {
                startSignInActivity()
                return@safeExecute
            }

            updatePermissionStatusSafely()

            if (areAllRequiredPermissionsGrantedSafely()) {
                CrashPreventionUtils.ErrorHandling.safeAsync(TAG, "checkTriggers") {
                    DataSyncManager.checkTriggers(applicationContext)
                }
            }

            updateRecordingButtonStateSafely()
        }
    }

    override fun onPause() {
        super.onPause()
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "onPause", Unit) {}
    }

    override fun onDestroy() {
        CrashPreventionUtils.ErrorHandling.safeExecute(TAG, "onDestroy", Unit) {
            QuickDebugSetup.destroy()
        }
        super.onDestroy()
    }
}