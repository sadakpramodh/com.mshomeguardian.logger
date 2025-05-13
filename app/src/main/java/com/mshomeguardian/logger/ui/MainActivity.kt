package com.mshomeguardian.logger.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.workers.WorkerScheduler

class MainActivity : AppCompatActivity() {
    private val LOCATION_PERMISSION_REQUEST_CODE = 101
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
    private val CALL_SMS_PERMISSION_REQUEST_CODE = 103
    private val ALL_PERMISSIONS_REQUEST_CODE = 104

    private lateinit var statusText: TextView
    private lateinit var permissionsButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var syncButton: Button

    // All required permissions
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS
    )


    // Additional background location permission for Android 10+
    private val backgroundLocationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        permissionsButton = findViewById(R.id.permissionsButton)
        deviceIdText = findViewById(R.id.deviceIdText)
        syncButton = findViewById(R.id.syncButton)

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
            } else {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_LONG).show()
                updatePermissionStatus()
            }
        }

        // Check permissions on startup
        updatePermissionStatus()
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
        status.append("• Contacts: ${if (contactsGranted) "✓" else "✗"}\n\n")

        // Summary
        if (areAllPermissionsGranted()) {
            status.append("All permissions granted.\nService is running in the background.")
            permissionsButton.text = "Permissions: All Granted"
            syncButton.isEnabled = true
        } else {
            status.append("Some permissions are missing.\nPlease grant all permissions for full functionality.")
            permissionsButton.text = "Grant Permissions"
            syncButton.isEnabled = false
        }

        statusText.text = status.toString()
    }

    private fun startBackgroundServices() {
        // Only start if all permissions are granted
        if (areAllPermissionsGranted()) {
            // Start all workers
            WorkerScheduler.runAllWorkersOnce(applicationContext)

            // Update UI
            updatePermissionStatus()
            Toast.makeText(this, "Home Guardian is now monitoring your device", Toast.LENGTH_SHORT).show()
        } else {
            updatePermissionStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update permission status each time activity is resumed
        // This handles the case where user grants permissions from Settings
        updatePermissionStatus()
    }
}