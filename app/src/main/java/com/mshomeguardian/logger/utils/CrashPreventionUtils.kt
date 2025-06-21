package com.mshomeguardian.logger.utils

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Comprehensive crash prevention and safe operation utilities
 * Handles permission checks, service management, and error recovery
 */
object CrashPreventionUtils {
    private const val TAG = "CrashPreventionUtils"

    // Scope for background operations that shouldn't crash the app
    private val safeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Permission groups for easier management
     */
    object PermissionGroups {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val BACKGROUND_LOCATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyArray()
        }

        val COMMUNICATION_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )

        val AUDIO_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )

        val NOTIFICATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

        val FOREGROUND_SERVICE_PERMISSIONS = if (Build.VERSION.SDK_INT >= 34) {
            arrayOf(
                Manifest.permission.FOREGROUND_SERVICE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            )
        } else {
            emptyArray()
        }

        val ALL_CORE_PERMISSIONS = LOCATION_PERMISSIONS +
                COMMUNICATION_PERMISSIONS +
                AUDIO_PERMISSIONS

        val ALL_PERMISSIONS = ALL_CORE_PERMISSIONS +
                BACKGROUND_LOCATION_PERMISSIONS +
                NOTIFICATION_PERMISSIONS +
                FOREGROUND_SERVICE_PERMISSIONS
    }

    /**
     * Safe permission checking that never throws exceptions
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return try {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission $permission", e)
            false
        }
    }

    /**
     * Check if all permissions in a group are granted
     */
    fun hasPermissionGroup(context: Context, permissions: Array<String>): Boolean {
        return try {
            permissions.all { permission ->
                hasPermission(context, permission)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission group", e)
            false
        }
    }

    /**
     * Check if any permission in a group is granted
     */
    fun hasAnyPermissionInGroup(context: Context, permissions: Array<String>): Boolean {
        return try {
            permissions.any { permission ->
                hasPermission(context, permission)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking any permission in group", e)
            false
        }
    }

    /**
     * Get missing permissions from a group
     */
    fun getMissingPermissions(context: Context, permissions: Array<String>): List<String> {
        return try {
            permissions.filter { permission ->
                !hasPermission(context, permission)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting missing permissions", e)
            permissions.toList()
        }
    }

    /**
     * Safe service operations that handle exceptions
     */
    object ServiceOperations {

        /**
         * Safely start a foreground service
         */
        fun safeStartForegroundService(
            context: Context,
            serviceClass: Class<out Service>,
            action: String? = null,
            onSuccess: () -> Unit = {},
            onFailure: (Exception) -> Unit = {}
        ) {
            safeScope.launch {
                try {
                    val intent = Intent(context, serviceClass).apply {
                        action?.let { this.action = it }
                    }

                    // Check if we have required permissions before starting
                    val canStartService = when {
                        serviceClass.simpleName.contains("Location") -> {
                            canStartLocationService(context)
                        }
                        serviceClass.simpleName.contains("Audio") || serviceClass.simpleName.contains("Recording") -> {
                            canStartAudioService(context)
                        }
                        else -> true
                    }

                    if (!canStartService) {
                        val error = SecurityException("Missing required permissions for ${serviceClass.simpleName}")
                        withContext(Dispatchers.Main) { onFailure(error) }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        onSuccess()
                    }

                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException starting service ${serviceClass.simpleName}", e)
                    withContext(Dispatchers.Main) { onFailure(e) }
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException starting service ${serviceClass.simpleName}", e)
                    withContext(Dispatchers.Main) { onFailure(e) }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error starting service ${serviceClass.simpleName}", e)
                    withContext(Dispatchers.Main) { onFailure(e) }
                }
            }
        }

        /**
         * Safely stop a service
         */
        fun safeStopService(
            context: Context,
            serviceClass: Class<out Service>,
            onSuccess: () -> Unit = {},
            onFailure: (Exception) -> Unit = {}
        ) {
            try {
                val intent = Intent(context, serviceClass)
                context.stopService(intent)
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping service ${serviceClass.simpleName}", e)
                onFailure(e)
            }
        }
    }

    /**
     * Check if location service can be started safely
     */
    fun canStartLocationService(context: Context): Boolean {
        return try {
            // Check basic location permissions
            val hasLocationPermission = hasAnyPermissionInGroup(context, PermissionGroups.LOCATION_PERMISSIONS)

            // Check foreground service permission for Android 14+
            val hasForegroundServicePermission = if (Build.VERSION.SDK_INT >= 34) {
                hasPermission(context, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            } else {
                true
            }

            val result = hasLocationPermission && hasForegroundServicePermission
            Log.d(TAG, "Can start location service: $result (location: $hasLocationPermission, fgs: $hasForegroundServicePermission)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if can start location service", e)
            false
        }
    }

    /**
     * Check if audio service can be started safely
     */
    fun canStartAudioService(context: Context): Boolean {
        return try {
            // Check audio permission
            val hasAudioPermission = hasPermission(context, Manifest.permission.RECORD_AUDIO)

            // Check foreground service permission for Android 14+
            val hasForegroundServicePermission = if (Build.VERSION.SDK_INT >= 34) {
                hasPermission(context, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            } else {
                true
            }

            val result = hasAudioPermission && hasForegroundServicePermission
            Log.d(TAG, "Can start audio service: $result (audio: $hasAudioPermission, fgs: $hasForegroundServicePermission)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if can start audio service", e)
            false
        }
    }

    /**
     * Error logging and recovery utilities
     */
    object ErrorHandling {

        /**
         * Log errors safely without crashing
         */
        fun logError(tag: String, message: String, throwable: Throwable? = null) {
            try {
                if (throwable != null) {
                    Log.e(tag, message, throwable)

                    // Also log stack trace for debugging
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    Log.e(tag, "Stack trace: ${sw.toString()}")
                } else {
                    Log.e(tag, message)
                }
            } catch (e: Exception) {
                // If even logging fails, try to at least print to system
                try {
                    System.err.println("$tag: $message")
                    throwable?.printStackTrace()
                } catch (ignored: Exception) {
                    // Nothing more we can do
                }
            }
        }

        /**
         * Safe execution wrapper that catches all exceptions
         */
        inline fun <T> safeExecute(
            tag: String,
            operation: String,
            defaultValue: T,
            block: () -> T
        ): T {
            return try {
                block()
            } catch (e: SecurityException) {
                logError(tag, "SecurityException in $operation", e)
                defaultValue
            } catch (e: IllegalStateException) {
                logError(tag, "IllegalStateException in $operation", e)
                defaultValue
            } catch (e: Exception) {
                logError(tag, "Unexpected error in $operation", e)
                defaultValue
            }
        }

        /**
         * Safe async execution
         */
        fun safeAsync(
            tag: String,
            operation: String,
            onError: (Exception) -> Unit = {},
            block: suspend () -> Unit
        ) {
            safeScope.launch {
                try {
                    block()
                } catch (e: Exception) {
                    logError(tag, "Error in async $operation", e)
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
        }
    }

    /**
     * App state recovery utilities
     */
    object Recovery {

        /**
         * Attempt to recover from service failures
         */
        fun attemptServiceRecovery(
            context: Context,
            serviceClass: Class<out Service>,
            maxRetries: Int = 3,
            onSuccess: () -> Unit = {},
            onFinalFailure: () -> Unit = {}
        ) {
            var retryCount = 0

            fun tryStart() {
                ServiceOperations.safeStartForegroundService(
                    context = context,
                    serviceClass = serviceClass,
                    onSuccess = {
                        Log.d(TAG, "Service recovery successful for ${serviceClass.simpleName}")
                        onSuccess()
                    },
                    onFailure = { exception ->
                        retryCount++
                        Log.w(TAG, "Service recovery attempt $retryCount failed for ${serviceClass.simpleName}", exception)

                        if (retryCount < maxRetries) {
                            // Wait and retry
                            safeScope.launch {
                                kotlinx.coroutines.delay(5000L * retryCount) // Exponential backoff
                                tryStart()
                            }
                        } else {
                            Log.e(TAG, "Service recovery failed after $maxRetries attempts for ${serviceClass.simpleName}")
                            onFinalFailure()
                        }
                    }
                )
            }

            tryStart()
        }

        /**
         * Reset app to safe state
         */
        fun resetToSafeState(context: Context) {
            ErrorHandling.safeExecute(TAG, "resetToSafeState", Unit) {
                // Stop all services safely
                val services = listOf(
                    "com.mshomeguardian.logger.services.LocationMonitoringService",
                    "com.mshomeguardian.logger.services.AudioRecordingService",
                    "com.mshomeguardian.logger.services.RecordingService",
                    "com.mshomeguardian.logger.services.MonitoringService"
                )

                services.forEach { serviceName ->
                    try {
                        val serviceClass = Class.forName(serviceName) as Class<out Service>
                        ServiceOperations.safeStopService(context, serviceClass)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not stop service $serviceName", e)
                    }
                }

                // Clear any problematic shared preferences
                try {
                    val prefsToReset = listOf(
                        "location_sync",
                        "call_log_sync",
                        "message_sync",
                        "audio_recording_sync"
                    )

                    prefsToReset.forEach { prefsName ->
                        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error clearing preferences during reset", e)
                }

                Log.i(TAG, "App reset to safe state")
            }
        }
    }

    /**
     * Permission status reporting
     */
    object PermissionStatus {

        data class PermissionReport(
            val corePermissions: Map<String, Boolean>,
            val locationPermissions: Map<String, Boolean>,
            val audioPermissions: Map<String, Boolean>,
            val foregroundServicePermissions: Map<String, Boolean>,
            val allGranted: Boolean,
            val canStartServices: Boolean
        )

        fun generateReport(context: Context): PermissionReport {
            return ErrorHandling.safeExecute(TAG, "generatePermissionReport",
                PermissionReport(emptyMap(), emptyMap(), emptyMap(), emptyMap(), false, false)) {

                val corePermissions = PermissionGroups.ALL_CORE_PERMISSIONS.associateWith {
                    hasPermission(context, it)
                }

                val locationPermissions = (PermissionGroups.LOCATION_PERMISSIONS +
                        PermissionGroups.BACKGROUND_LOCATION_PERMISSIONS).associateWith {
                    hasPermission(context, it)
                }

                val audioPermissions = PermissionGroups.AUDIO_PERMISSIONS.associateWith {
                    hasPermission(context, it)
                }

                val foregroundServicePermissions = PermissionGroups.FOREGROUND_SERVICE_PERMISSIONS.associateWith {
                    hasPermission(context, it)
                }

                val allGranted = corePermissions.values.all { it } &&
                        (Build.VERSION.SDK_INT < 34 || foregroundServicePermissions.values.all { it })

                val canStartServices = canStartLocationService(context) && canStartAudioService(context)

                PermissionReport(
                    corePermissions = corePermissions,
                    locationPermissions = locationPermissions,
                    audioPermissions = audioPermissions,
                    foregroundServicePermissions = foregroundServicePermissions,
                    allGranted = allGranted,
                    canStartServices = canStartServices
                )
            }
        }

        fun logPermissionReport(context: Context) {
            val report = generateReport(context)
            Log.i(TAG, "=== PERMISSION REPORT ===")
            Log.i(TAG, "All granted: ${report.allGranted}")
            Log.i(TAG, "Can start services: ${report.canStartServices}")
            Log.i(TAG, "Core permissions: ${report.corePermissions}")
            Log.i(TAG, "Location permissions: ${report.locationPermissions}")
            Log.i(TAG, "Audio permissions: ${report.audioPermissions}")
            Log.i(TAG, "Foreground service permissions: ${report.foregroundServicePermissions}")
            Log.i(TAG, "=== END REPORT ===")
        }
    }

    /**
     * Initialize crash prevention system
     */
    fun initialize(context: Context) {
        ErrorHandling.safeExecute(TAG, "initialize", Unit) {
            // Set up global exception handler
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                ErrorHandling.logError(TAG, "Uncaught exception in thread ${thread.name}", throwable)

                // Try to reset to safe state
                try {
                    Recovery.resetToSafeState(context)
                } catch (e: Exception) {
                    ErrorHandling.logError(TAG, "Error during crash recovery", e)
                }

                // Let the system handle the crash
                System.exit(1)
            }

            Log.i(TAG, "Crash prevention system initialized")
        }
    }
}