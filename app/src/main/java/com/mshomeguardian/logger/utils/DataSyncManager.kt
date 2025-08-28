package com.mshomeguardian.logger.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mshomeguardian.logger.utils.LocationMonitoringService
import com.mshomeguardian.logger.services.AudioRecordingService
import com.mshomeguardian.logger.workers.CallLogWorker
import com.mshomeguardian.logger.workers.ContactsWorker
import com.mshomeguardian.logger.workers.DeviceInfoWorker
import com.mshomeguardian.logger.workers.MessageWorker
import com.mshomeguardian.logger.workers.WeatherWorker
import com.mshomeguardian.logger.workers.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced DataSyncManager with better authentication integration and testing capabilities
 */
object DataSyncManager {
    private const val TAG = "DataSyncManager"

    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Start all necessary services and initial sync
     * Requires user to be authenticated
     */
    fun initialize(context: Context, checkPermissions: Boolean = true) {
        Log.d(TAG, "Initializing DataSyncManager")

        // Check authentication first
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, cannot initialize services")
            return
        }

        // Verify that required runtime permissions are granted before proceeding
        if (checkPermissions && !hasRequiredPermissions(context)) {
            Log.w(TAG, "Missing required permissions, cannot initialize services")
            return
        }

        val userEmail = AuthManager.getCurrentUser()?.email
        Log.d(TAG, "User authenticated: $userEmail, proceeding with initialization")

        try {
            // Initialize user account in Firebase if needed
            scope.launch {
                userEmail?.let { email ->
                    val deviceId = DeviceIdentifier.getPersistentDeviceId(context)
                    FirebaseServiceHelper.initializeUserAccount(email, deviceId)
                }
            }

            // Schedule periodic workers
            WorkerScheduler.schedule(context)

            // Start location monitoring service
            startLocationService(context)

            // Run an initial sync to test the setup
            testSyncSetup(context)

            Log.d(TAG, "DataSyncManager initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during DataSyncManager initialization", e)
        }
    }

    /**
     * Test sync setup by running critical workers once
     */
    private fun testSyncSetup(context: Context) {
        Log.d(TAG, "Testing sync setup with immediate worker execution")

        scope.launch {
            try {
                val workManager = WorkManager.getInstance(context)

                // Create constraints for immediate execution
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                // Test with one worker from each category to verify setup
                val testWorkers = listOf(
                    OneTimeWorkRequestBuilder<DeviceInfoWorker>()
                        .setConstraints(constraints)
                        .addTag("test_sync")
                        .build(),
                    OneTimeWorkRequestBuilder<CallLogWorker>()
                        .setConstraints(constraints)
                        .addTag("test_sync")
                        .build(),
                    OneTimeWorkRequestBuilder<MessageWorker>()
                        .setConstraints(constraints)
                        .addTag("test_sync")
                        .build()
                )

                testWorkers.forEach { worker ->
                    workManager.enqueue(worker)
                }

                Log.d(TAG, "Test sync workers enqueued successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error in test sync setup", e)
            }
        }
    }

    /**
     * Start the location monitoring service
     * Only starts if user is authenticated
     */
    private fun startLocationService(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "Cannot start location service - user not authenticated")
            return
        }

        try {
            Log.d(TAG, "Starting location monitoring service")

            // Start location monitoring service
            val locationIntent = Intent(context, LocationMonitoringService::class.java)

            // Use startForegroundService for Android 8.0+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(locationIntent)
            } else {
                context.startService(locationIntent)
            }

            Log.d(TAG, "Location service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location service", e)
        }
    }

    /**
     * Force sync of all data types immediately
     * Requires authentication
     */
    fun syncAll(context: Context) {
        // Check authentication first
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, cannot sync data")
            return
        }

        Log.d(TAG, "Starting manual sync of all data")

        try {
            val workManager = WorkManager.getInstance(context)

            // Create constraints for better success rate
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // Run all workers with constraints
            val workers = listOf(
                OneTimeWorkRequestBuilder<CallLogWorker>()
                    .setConstraints(constraints)
                    .addTag("manual_sync")
                    .build(),
                OneTimeWorkRequestBuilder<MessageWorker>()
                    .setConstraints(constraints)
                    .addTag("manual_sync")
                    .build(),
                OneTimeWorkRequestBuilder<ContactsWorker>()
                    .setConstraints(constraints)
                    .addTag("manual_sync")
                    .build(),
                OneTimeWorkRequestBuilder<DeviceInfoWorker>()
                    .setConstraints(constraints)
                    .addTag("manual_sync")
                    .build(),
                OneTimeWorkRequestBuilder<WeatherWorker>()
                    .setConstraints(constraints)
                    .addTag("manual_sync")
                    .build()
            )

            workers.forEach { worker ->
                workManager.enqueue(worker)
            }

            Log.d(TAG, "Manual sync workers enqueued successfully")

            // Update widgets
            updateWidgets(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error during manual sync", e)
        }
    }

    /**
     * Update the Home Guardian widget
     */
    private fun updateWidgets(context: Context) {
        try {
            // Send broadcast to update widgets
            val intent = Intent("com.mshomeguardian.logger.widget.ACTION_UPDATE")
            context.sendBroadcast(intent)
            Log.d(TAG, "Widget update broadcast sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widgets", e)
        }
    }

    /**
     * Check if any data type needs syncing based on thresholds
     * Only performs checks if user is authenticated
     */
    fun checkTriggers(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, skipping trigger checks")
            return
        }

        scope.launch {
            try {
                var shouldSync = false

                // Check if calls reached threshold
                if (withContext(Dispatchers.IO) { CallLogWorker.shouldSync(context) }) {
                    Log.d(TAG, "Call log threshold reached, triggering sync")
                    withContext(Dispatchers.Main) {
                        val workManager = WorkManager.getInstance(context)
                        workManager.enqueue(
                            OneTimeWorkRequestBuilder<CallLogWorker>()
                                .addTag("threshold_sync")
                                .build()
                        )
                    }
                    shouldSync = true
                }

                // Check if messages reached threshold
                if (withContext(Dispatchers.IO) { MessageWorker.shouldSync(context) }) {
                    Log.d(TAG, "Message threshold reached, triggering sync")
                    withContext(Dispatchers.Main) {
                        val workManager = WorkManager.getInstance(context)
                        workManager.enqueue(
                            OneTimeWorkRequestBuilder<MessageWorker>()
                                .addTag("threshold_sync")
                                .build()
                        )
                    }
                    shouldSync = true
                }

                // Run device info worker if any other sync occurred
                if (shouldSync) {
                    withContext(Dispatchers.Main) {
                        val workManager = WorkManager.getInstance(context)
                        workManager.enqueue(
                            OneTimeWorkRequestBuilder<DeviceInfoWorker>()
                                .addTag("threshold_sync")
                                .build()
                        )

                        // Update widget
                        updateWidgets(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkTriggers", e)
            }
        }
    }

    /**
     * Should be called when a new call is detected
     * Only syncs if user is authenticated
     */
    fun onCallDetected(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, skipping call detection sync")
            return
        }

        Log.d(TAG, "New call detected, triggering immediate sync")

        // Always sync on new call detection, don't check threshold
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    val workManager = WorkManager.getInstance(context)
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<CallLogWorker>()
                            .addTag("call_detected")
                            .build()
                    )

                    // Update widget
                    updateWidgets(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCallDetected", e)
            }
        }
    }

    /**
     * Should be called when a new message is detected
     * Only syncs if user is authenticated
     */
    fun onMessageDetected(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "User not authenticated, skipping message detection sync")
            return
        }

        Log.d(TAG, "New message detected, triggering immediate sync")

        // Always sync on new message detection, don't check threshold
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    val workManager = WorkManager.getInstance(context)
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<MessageWorker>()
                            .addTag("message_detected")
                            .build()
                    )

                    // Update widget
                    updateWidgets(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onMessageDetected", e)
            }
        }
    }

    /**
     * Start or stop the recording service
     * Authentication is checked but recording can work offline if already authenticated
     */
    fun toggleRecordingService(context: Context, start: Boolean) {
        try {
            if (start) {
                // Check authentication for starting recording
                if (!AuthManager.isSignedIn()) {
                    Log.w(TAG, "User not authenticated, cannot start recording service")
                    return
                }

                Log.d(TAG, "Starting recording service")
                val intent = Intent(context, AudioRecordingService::class.java)
                intent.action = AudioRecordingService.ACTION_START_RECORDING

                // Use startForegroundService for Android 8.0+ (API 26+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                // Stopping recording doesn't require authentication check
                Log.d(TAG, "Stopping recording service")
                val intent = Intent(context, AudioRecordingService::class.java)
                intent.action = AudioRecordingService.ACTION_STOP_RECORDING
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling recording service", e)
        }
    }

    /**
     * Check if recording service is running
     */
    fun isRecordingServiceRunning(): Boolean {
        return AudioRecordingService.isRunning()
    }

    /**
     * Stop all services (called when user signs out)
     */
    fun stopAllServices(context: Context) {
        Log.d(TAG, "Stopping all services due to authentication state change")

        try {
            // Stop recording service
            toggleRecordingService(context, false)

            // Cancel all scheduled workers
            WorkerScheduler.cancelAllWork(context)

            // Stop location service
            val locationIntent = Intent(context, LocationMonitoringService::class.java)
            context.stopService(locationIntent)

            Log.d(TAG, "All services stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping services", e)
        }
    }

    /**
     * Restart all services (called when user signs in)
     */
    fun restartAllServices(context: Context) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "Cannot restart services - user not authenticated")
            return
        }

        Log.d(TAG, "Restarting all services after authentication")

        try {
            // Reinitialize everything
            initialize(context)

            Log.d(TAG, "All services restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting services", e)
        }
    }

    /**
     * Check authentication status for external callers
     */
    fun isAuthenticated(): Boolean {
        return AuthManager.isSignedIn()
    }

    /**
     * Get current user ID for logging/debugging
     */
    fun getCurrentUserId(): String? {
        return AuthManager.getCurrentUserId()
    }

    /**
     * Get current user email for logging/debugging
     */
    fun getCurrentUserEmail(): String? {
        return AuthManager.getCurrentUser()?.email
    }

    /**
     * Perform an operation only if authenticated
     */
    fun withAuthentication(operation: String, action: () -> Unit) {
        if (AuthManager.isSignedIn()) {
            Log.d(TAG, "Performing authenticated operation: $operation")
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Error in authenticated operation '$operation'", e)
            }
        } else {
            Log.w(TAG, "Skipping operation '$operation' - user not authenticated")
        }
    }

    /**
     * Force authentication check and restart services if needed
     */
    fun verifyAndRestartServices(context: Context) {
        Log.d(TAG, "Verifying authentication and service state")

        if (AuthManager.isSignedIn()) {
            val currentUserId = AuthManager.getCurrentUserId()
            Log.d(TAG, "User authenticated: $currentUserId")

            // Check if services need to be restarted
            restartAllServices(context)
        } else {
            Log.w(TAG, "User not authenticated, stopping all services")
            stopAllServices(context)
        }
    }

    /**
     * Get sync status for UI display
     */
    fun getSyncStatus(): Map<String, Any> {
        return mapOf(
            "authenticated" to AuthManager.isSignedIn(),
            "user_email" to (AuthManager.getCurrentUser()?.email ?: "none"),
            "user_id" to (AuthManager.getCurrentUserId() ?: "none"),
            "recording_service_running" to isRecordingServiceRunning(),
            "firebase_available" to FirebaseServiceHelper.isFirebaseAvailable(),
            "last_check" to System.currentTimeMillis()
        )
    }

    /**
     * Test specific sync functionality for debugging
     */
    fun testSync(context: Context, syncType: String) {
        if (!AuthManager.isSignedIn()) {
            Log.w(TAG, "Cannot test sync - user not authenticated")
            return
        }

        Log.d(TAG, "Testing $syncType sync")

        try {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            when (syncType.lowercase()) {
                "calls", "calllog" -> {
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<CallLogWorker>()
                            .setConstraints(constraints)
                            .addTag("test_$syncType")
                            .build()
                    )
                }
                "messages", "sms" -> {
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<MessageWorker>()
                            .setConstraints(constraints)
                            .addTag("test_$syncType")
                            .build()
                    )
                }
                "contacts" -> {
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<ContactsWorker>()
                            .setConstraints(constraints)
                            .addTag("test_$syncType")
                            .build()
                    )
                }
                "device", "deviceinfo" -> {
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<DeviceInfoWorker>()
                            .setConstraints(constraints)
                            .addTag("test_$syncType")
                            .build()
                    )
                }
                "weather" -> {
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<WeatherWorker>()
                            .setConstraints(constraints)
                            .addTag("test_$syncType")
                            .build()
                    )
                }
                "all" -> {
                    syncAll(context)
                }
                else -> {
                    Log.w(TAG, "Unknown sync type: $syncType")
                }
            }

            Log.d(TAG, "Test sync for $syncType enqueued")
        } catch (e: Exception) {
            Log.e(TAG, "Error testing $syncType sync", e)
        }
    }

    /**
     * Get detailed sync statistics for debugging
     */
    fun getSyncStatistics(context: Context): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()

        try {
            // Get last sync times from shared preferences
            val callLogPrefs = context.getSharedPreferences("call_log_sync", Context.MODE_PRIVATE)
            val messagePrefs = context.getSharedPreferences("message_sync", Context.MODE_PRIVATE)
            val contactsPrefs = context.getSharedPreferences("contacts_sync", Context.MODE_PRIVATE)
            val locationPrefs = context.getSharedPreferences("location_sync", Context.MODE_PRIVATE)

            stats["call_log_last_sync"] = callLogPrefs.getLong("last_sync_time", 0)
            stats["message_last_sync"] = messagePrefs.getLong("last_sync_time", 0)
            stats["contacts_last_sync"] = contactsPrefs.getLong("last_sync_time", 0)
            stats["location_last_sync"] = locationPrefs.getLong("last_sync_time", 0)

            // Get authentication info
            stats["authenticated"] = AuthManager.isSignedIn()
            stats["user_email"] = AuthManager.getCurrentUser()?.email ?: "none"
            stats["firebase_available"] = FirebaseServiceHelper.isFirebaseAvailable()

            // Get service status
            stats["recording_service_running"] = isRecordingServiceRunning()

            // Get device info
            stats["device_id"] = DeviceIdentifier.getPersistentDeviceId(context)

            stats["generated_at"] = System.currentTimeMillis()

        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync statistics", e)
            stats["error"] = e.message ?: "Unknown error"
        }

        return stats
    }

    /**
     * Reset all sync timestamps (for testing purposes)
     */
    fun resetSyncTimestamps(context: Context) {
        Log.d(TAG, "Resetting all sync timestamps")

        try {
            val prefsToReset = listOf(
                "call_log_sync",
                "message_sync",
                "contacts_sync",
                "location_sync",
                "audio_recording_sync"
            )

            prefsToReset.forEach { prefsName ->
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit().remove("last_sync_time").apply()
            }

            // Also reset synced contacts
            val syncedContactsPrefs = context.getSharedPreferences("synced_contacts", Context.MODE_PRIVATE)
            syncedContactsPrefs.edit().clear().apply()

            Log.d(TAG, "All sync timestamps reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting sync timestamps", e)
        }
    }

    /**
     * Check if all critical runtime permissions are granted
     */
    private fun hasRequiredPermissions(context: Context): Boolean {
        val permissions = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}