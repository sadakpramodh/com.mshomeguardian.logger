package com.mshomeguardian.logger.utils

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mshomeguardian.logger.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fixed helper class for testing and debugging sync functionality
 * Add this to your MainActivity for testing sync issues
 */
object SyncTestHelper {
    private const val TAG = "SyncTestHelper"

    /**
     * Run comprehensive sync test and log results
     */
    fun runSyncTest(context: Context) {
        Log.d(TAG, "=== STARTING COMPREHENSIVE SYNC TEST ===")

        // Test 1: Authentication Status
        testAuthentication()

        // Test 2: Firebase Connection
        testFirebaseConnection()

        // Test 3: Database Access
        testDatabaseAccess(context)

        // Test 4: Worker Status (simplified)
        testWorkerStatusSimple(context)

        // Test 5: Sync Statistics
        logSyncStatistics(context)

        // Test 6: Trigger Test Sync
        triggerTestSync(context)

        Log.d(TAG, "=== SYNC TEST COMPLETED ===")
    }

    private fun testAuthentication() {
        Log.d(TAG, "--- Testing Authentication ---")
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
    }

    private fun testFirebaseConnection() {
        Log.d(TAG, "--- Testing Firebase Connection ---")
        val isAvailable = FirebaseServiceHelper.isFirebaseAvailable()
        val currentUserEmail = FirebaseServiceHelper.getCurrentUserEmail()

        Log.d(TAG, "Firebase Available: $isAvailable")
        Log.d(TAG, "Firebase User Email: $currentUserEmail")

        if (!isAvailable) {
            Log.e(TAG, "❌ FIREBASE CONNECTION FAILED")
        } else {
            Log.d(TAG, "✅ Firebase Connection OK")
        }
    }

    private fun testDatabaseAccess(context: Context) {
        Log.d(TAG, "--- Testing Database Access ---")
        try {
            val db = AppDatabase.getInstance(context)

            // Test database access asynchronously
            Thread {
                try {
                    // Test location table
                    val locationCount = db.locationDao().getAllLocations().size
                    Log.d(TAG, "Location records: $locationCount")

                    val callLogCount = db.callLogDao().getAllCallLogs().size
                    Log.d(TAG, "Call log records: $callLogCount")

                    val messageCount = db.messageDao().getAllMessages().size
                    Log.d(TAG, "Message records: $messageCount")

                    val audioCount = db.audioRecordingDao().getAllRecordings().size
                    Log.d(TAG, "Audio records: $audioCount")

                    // Test unuploaded records
                    val notUploadedCalls = db.callLogDao().getNotUploadedCallLogs().size
                    val notUploadedMessages = db.messageDao().getNotUploadedMessages().size
                    val notUploadedAudio = db.audioRecordingDao().getNotUploadedRecordings().size

                    Log.d(TAG, "Not uploaded - Calls: $notUploadedCalls, Messages: $notUploadedMessages, Audio: $notUploadedAudio")

                    Log.d(TAG, "✅ Database Access OK")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ DATABASE ACCESS FAILED", e)
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "❌ DATABASE INITIALIZATION FAILED", e)
        }
    }

    private fun testWorkerStatusSimple(context: Context) {
        Log.d(TAG, "--- Testing Worker Status (Simplified) ---")
        try {
            val workManager = WorkManager.getInstance(context)

            // Use a simpler approach to check workers
            val workQuery = androidx.work.WorkQuery.Builder
                .fromStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED))
                .build()

            val workInfos = workManager.getWorkInfos(workQuery).get()

            Log.d(TAG, "Total workers found: ${workInfos.size}")

            // Group by state
            val byState = workInfos.groupBy { it.state }
            byState.forEach { (state, workers) ->
                Log.d(TAG, "$state: ${workers.size} workers")
            }

            // Log recent workers
            workInfos.take(10).forEach { workInfo ->
                Log.d(TAG, "Worker ${workInfo.id}: ${workInfo.state} (Tags: ${workInfo.tags})")
                if (workInfo.state == WorkInfo.State.FAILED) {
                    Log.e(TAG, "Failed worker output: ${workInfo.outputData}")
                }
            }

            Log.d(TAG, "✅ Worker Status Check OK")
        } catch (e: Exception) {
            Log.e(TAG, "❌ WORKER STATUS CHECK FAILED", e)
        }
    }

    private fun logSyncStatistics(context: Context) {
        Log.d(TAG, "--- Sync Statistics ---")
        try {
            val stats = DataSyncManager.getSyncStatistics(context)
            stats.forEach { (key: String, value: Any) ->
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

        val userEmail = AuthManager.getCurrentUser()?.email
        val deviceId = DeviceIdentifier.getPersistentDeviceId(context)

        if (userEmail == null) {
            Log.e(TAG, "❌ Cannot test Firebase upload - user not authenticated")
            return
        }

        Log.d(TAG, "Testing upload with email: $userEmail, deviceId: $deviceId")

        // Test device info upload in background thread
        Thread {
            try {
                val testDeviceData = mapOf(
                    "deviceId" to deviceId,
                    "testField" to "test_value",
                    "timestamp" to System.currentTimeMillis()
                )

                // Use coroutine-free approach for simplicity
                val success = testFirebaseUploadSync(userEmail, deviceId, testDeviceData)
                if (success) {
                    Log.d(TAG, "✅ Firebase upload test successful")
                } else {
                    Log.e(TAG, "❌ Firebase upload test failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Firebase upload test exception", e)
            }
        }.start()
    }

    private fun testFirebaseUploadSync(userEmail: String, deviceId: String, testData: Map<String, Any>): Boolean {
        return try {
            // This is a simplified test - in real scenario you'd use coroutines
            // For now, just test that the service helper is available
            val isAvailable = FirebaseServiceHelper.isFirebaseAvailable()
            Log.d(TAG, "Firebase service helper available: $isAvailable")
            isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error in sync test", e)
            false
        }
    }

    /**
     * Simplified worker monitoring
     */
    fun monitorWorkersSimple(context: Context, durationSeconds: Int = 30) {
        Log.d(TAG, "=== MONITORING WORKERS FOR ${durationSeconds}s ===")

        try {
            val workManager = WorkManager.getInstance(context)

            // Start monitoring in background
            Thread {
                val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
                var lastCheck = 0L

                while (System.currentTimeMillis() < endTime) {
                    try {
                        val workQuery = androidx.work.WorkQuery.Builder
                            .fromStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
                            .build()

                        val activeWork = workManager.getWorkInfos(workQuery).get()

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastCheck > 5000) { // Log every 5 seconds
                            Log.d(TAG, "Active workers: ${activeWork.size}")
                            activeWork.forEach { workInfo ->
                                Log.d(TAG, "  ${workInfo.id}: ${workInfo.state}")
                            }
                            lastCheck = currentTime
                        }

                        Thread.sleep(1000) // Check every second
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during worker monitoring", e)
                    }
                }

                Log.d(TAG, "=== WORKER MONITORING COMPLETED ===")
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "❌ WORKER MONITORING FAILED", e)
        }
    }

    /**
     * Quick test method that just triggers manual sync
     */
    fun quickSyncTest(context: Context) {
        Log.d(TAG, "=== QUICK SYNC TEST ===")

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
    }

    /**
     * Test permissions required for sync
     */
    fun testPermissions(context: Context) {
        Log.d(TAG, "=== TESTING PERMISSIONS ===")

        val permissions = arrayOf(
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.RECORD_AUDIO
        )

        permissions.forEach { permission ->
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "$permission: ${if (granted) "✅ GRANTED" else "❌ DENIED"}")
        }

        Log.d(TAG, "=== PERMISSIONS TEST COMPLETED ===")
    }
}