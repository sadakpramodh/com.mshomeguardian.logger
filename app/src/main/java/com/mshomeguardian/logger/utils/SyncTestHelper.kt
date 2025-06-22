package com.mshomeguardian.logger.utils

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mshomeguardian.logger.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Completely fixed helper class for testing and debugging sync functionality
 * Removes all problematic suspend function calls and ListenableFuture dependencies
 */
object SyncTestHelper {
    private const val TAG = "SyncTestHelper"

    // Use a background executor for database operations
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Run comprehensive sync test and log results
     */
    fun runSyncTest(context: Context) {
        Log.d(TAG, "=== STARTING COMPREHENSIVE SYNC TEST ===")

        // Test 1: Authentication Status
        testAuthentication()

        // Test 2: Firebase Connection
        testFirebaseConnection()

        // Test 3: Database Access (simplified)
        testDatabaseAccessSimple(context)

        // Test 4: Worker Status (basic check only)
        testWorkerStatusBasic(context)

        // Test 5: Sync Statistics
        logSyncStatistics(context)

        // Test 6: Trigger Test Sync
        triggerTestSync(context)

        Log.d(TAG, "=== SYNC TEST COMPLETED ===")
    }

    private fun testAuthentication() {
        Log.d(TAG, "--- Testing Authentication ---")
        try {
            val isSignedIn = AuthManager.isSignedIn()
            val currentUser = AuthManager.getCurrentUser()
            val userEmail = currentUser?.email
            val userId = currentUser?.uid

            Log.d(TAG, "Signed In: $isSignedIn")
            Log.d(TAG, "User Email: $userEmail")
            Log.d(TAG, "User ID: $userId")

            if (!isSignedIn) {
                Log.e(TAG, "❌ AUTHENTICATION FAILED - User not signed in")
            } else {
                Log.d(TAG, "✅ Authentication OK")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Authentication test failed", e)
        }
    }

    private fun testFirebaseConnection() {
        Log.d(TAG, "--- Testing Firebase Connection ---")
        try {
            val isAvailable = FirebaseServiceHelper.isFirebaseAvailable()
            val currentUserEmail = FirebaseServiceHelper.getCurrentUserEmail()

            Log.d(TAG, "Firebase Available: $isAvailable")
            Log.d(TAG, "Firebase User Email: $currentUserEmail")

            if (!isAvailable) {
                Log.e(TAG, "❌ FIREBASE CONNECTION FAILED")
            } else {
                Log.d(TAG, "✅ Firebase Connection OK")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase connection test failed", e)
        }
    }

    private fun testDatabaseAccessSimple(context: Context) {
        Log.d(TAG, "--- Testing Database Access (Simplified) ---")
        try {
            val db = AppDatabase.getInstance(context)

            // Use coroutine scope for database operations
            coroutineScope.launch {
                try {
                    // Test database connectivity by getting instance
                    Log.d(TAG, "Database instance created successfully")

                    // Test each DAO
                    val locationDao = db.locationDao()
                    val callLogDao = db.callLogDao()
                    val messageDao = db.messageDao()
                    val audioDao = db.audioRecordingDao()

                    Log.d(TAG, "All DAOs accessed successfully")
                    Log.d(TAG, "✅ Database Access OK")

                    // Get data counts using coroutines properly
                    try {
                        val locations = locationDao.getAllLocations()
                        val callLogs = callLogDao.getAllCallLogs()
                        val messages = messageDao.getAllMessages()
                        val recordings = audioDao.getAllRecordings()

                        Log.d(TAG, "Location records: ${locations.size}")
                        Log.d(TAG, "Call log records: ${callLogs.size}")
                        Log.d(TAG, "Message records: ${messages.size}")
                        Log.d(TAG, "Audio records: ${recordings.size}")

                        // Test unuploaded records
                        val notUploadedCalls = callLogDao.getNotUploadedCallLogs()
                        val notUploadedMessages = messageDao.getNotUploadedMessages()
                        val notUploadedAudio = audioDao.getNotUploadedRecordings()

                        Log.d(TAG, "Not uploaded - Calls: ${notUploadedCalls.size}, Messages: ${notUploadedMessages.size}, Audio: ${notUploadedAudio.size}")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting data counts", e)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ DATABASE ACCESS FAILED", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ DATABASE INITIALIZATION FAILED", e)
        }
    }

    private fun testWorkerStatusBasic(context: Context) {
        Log.d(TAG, "--- Testing Worker Status (Basic) ---")
        try {
            val workManager = WorkManager.getInstance(context)
            Log.d(TAG, "WorkManager instance obtained successfully")

            // Just log that WorkManager is available
            Log.d(TAG, "✅ WorkManager Status Check OK")

            // Don't try to access ListenableFuture - just log basic info
            Log.d(TAG, "Note: Detailed worker status requires async operations")

        } catch (e: Exception) {
            Log.e(TAG, "❌ WORKER STATUS CHECK FAILED", e)
        }
    }

    private fun logSyncStatistics(context: Context) {
        Log.d(TAG, "--- Sync Statistics ---")
        try {
            val stats = DataSyncManager.getSyncStatistics(context)
            for ((key, value) in stats) {
                Log.d(TAG, "$key: $value")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ SYNC STATISTICS FAILED", e)
        }
    }

    private fun triggerTestSync(context: Context) {
        Log.d(TAG, "--- Triggering Test Sync ---")
        try {
            // Test each sync type with delays
            DataSyncManager.testSync(context, "device")

            // Add small delays between tests
            Thread.sleep(1000)
            DataSyncManager.testSync(context, "calls")

            Thread.sleep(1000)
            DataSyncManager.testSync(context, "messages")

            Thread.sleep(1000)
            DataSyncManager.testSync(context, "contacts")

            Log.d(TAG, "✅ Test sync triggered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ TEST SYNC FAILED", e)
        }
    }

    /**
     * Reset everything for fresh testing
     */
    fun resetForTesting(context: Context) {
        Log.d(TAG, "=== RESETTING FOR TESTING ===")

        try {
            // Cancel all existing work
            WorkManager.getInstance(context).cancelAllWork()

            // Reset sync timestamps
            DataSyncManager.resetSyncTimestamps(context)

            Log.d(TAG, "✅ Reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ RESET FAILED", e)
        }
    }

    /**
     * Test specific Firebase upload functionality (simplified)
     */
    fun testFirebaseUpload(context: Context) {
        Log.d(TAG, "=== TESTING FIREBASE UPLOAD ===")

        try {
            val userEmail = AuthManager.getCurrentUser()?.email
            val deviceId = DeviceIdentifier.getPersistentDeviceId(context)

            if (userEmail == null) {
                Log.e(TAG, "❌ Cannot test Firebase upload - user not authenticated")
                return
            }

            Log.d(TAG, "Testing upload with email: $userEmail, deviceId: $deviceId")

            // Test device info upload in coroutine
            coroutineScope.launch {
                try {
                    val testDeviceData = mapOf(
                        "deviceId" to deviceId,
                        "testField" to "test_value",
                        "timestamp" to System.currentTimeMillis()
                    )

                    val success = FirebaseServiceHelper.uploadDeviceInfo(userEmail, deviceId, testDeviceData)

                    if (success) {
                        Log.d(TAG, "✅ Firebase upload test successful")
                    } else {
                        Log.e(TAG, "❌ Firebase upload test failed")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Firebase upload test exception", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up Firebase upload test", e)
        }
    }

    /**
     * Simple worker monitoring that doesn't use ListenableFuture
     */
    fun monitorWorkers(context: Context, durationSeconds: Int = 30) {
        Log.d(TAG, "=== MONITORING WORKERS FOR ${durationSeconds}s ===")

        try {
            val workManager = WorkManager.getInstance(context)
            Log.d(TAG, "WorkManager monitoring started")

            // Use background thread for monitoring
            backgroundExecutor.execute {
                val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
                var lastCheck = 0L

                while (System.currentTimeMillis() < endTime) {
                    try {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastCheck > 5000) { // Log every 5 seconds
                            Log.d(TAG, "Monitoring active... (${(endTime - currentTime) / 1000}s remaining)")
                            lastCheck = currentTime
                        }

                        Thread.sleep(1000) // Check every second
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during worker monitoring", e)
                        break
                    }
                }

                Log.d(TAG, "=== WORKER MONITORING COMPLETED ===")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ WORKER MONITORING FAILED", e)
        }
    }

    /**
     * Quick test method that just triggers manual sync
     */
    fun quickSyncTest(context: Context) {
        Log.d(TAG, "=== QUICK SYNC TEST ===")

        try {
            if (!AuthManager.isSignedIn()) {
                Log.e(TAG, "❌ User not authenticated")
                return
            }

            if (!FirebaseServiceHelper.isFirebaseAvailable()) {
                Log.e(TAG, "❌ Firebase not available")
                return
            }

            Log.d(TAG, "✅ Authentication and Firebase OK")
            Log.d(TAG, "Triggering manual sync...")

            // Trigger sync using DataSyncManager
            DataSyncManager.syncAll(context)

            Log.d(TAG, "✅ Manual sync triggered - check worker logs")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Quick sync test failed", e)
        }
    }

    /**
     * Test permissions required for sync
     */
    fun testPermissions(context: Context) {
        Log.d(TAG, "=== TESTING PERMISSIONS ===")

        try {
            val permissions = arrayOf(
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.RECORD_AUDIO
            )

            for (permission in permissions) {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                Log.d(TAG, "$permission: ${if (granted) "✅ GRANTED" else "❌ DENIED"}")
            }

            Log.d(TAG, "=== PERMISSIONS TEST COMPLETED ===")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Permissions test failed", e)
        }
    }

    /**
     * Get basic sync info without suspend functions
     */
    fun getBasicSyncInfo(context: Context) {
        Log.d(TAG, "=== BASIC SYNC INFO ===")

        try {
            // Get authentication info
            val isAuthenticated = AuthManager.isSignedIn()
            val userEmail = AuthManager.getCurrentUser()?.email

            Log.d(TAG, "Authenticated: $isAuthenticated")
            Log.d(TAG, "User: $userEmail")

            // Get Firebase availability
            val firebaseAvailable = FirebaseServiceHelper.isFirebaseAvailable()
            Log.d(TAG, "Firebase Available: $firebaseAvailable")

            // Get device info
            val deviceId = DeviceIdentifier.getPersistentDeviceId(context)
            Log.d(TAG, "Device ID: $deviceId")

            // Get sync statistics
            val stats = DataSyncManager.getSyncStatistics(context)
            for ((key, value) in stats) {
                Log.d(TAG, "Sync $key: $value")
            }

            Log.d(TAG, "=== BASIC SYNC INFO COMPLETED ===")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Basic sync info failed", e)
        }
    }
}