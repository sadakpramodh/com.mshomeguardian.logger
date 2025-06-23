package com.mshomeguardian.logger.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Test helper to verify the exact Firebase structure:
 * /users/sadakpramodh_at_yahoo_dot_com/devices/ffffffff-f714-60bc-1437-68a1d95aa476/{call_logs, contacts, messages, weather}
 */
object FirebaseStructureTestHelper {
    private const val TAG = "FirebaseStructureTest"

    /**
     * Test the exact structure you want
     */
    suspend fun testExactStructure(context: Context) {
        Log.d(TAG, "=== TESTING EXACT FIREBASE STRUCTURE ===")

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email == null) {
            Log.e(TAG, "❌ User not authenticated")
            return
        }

        val userEmail = currentUser.email!!
        val deviceId = DeviceIdentifier.getPersistentDeviceId(context)

        // This should create: /users/sadakpramodh_at_yahoo_dot_com/devices/ffffffff-f714-60bc-1437-68a1d95aa476/
        val sanitizedEmail = sanitizeEmailForFirestore(userEmail)

        Log.d(TAG, "Original email: $userEmail")
        Log.d(TAG, "Sanitized email: $sanitizedEmail")
        Log.d(TAG, "Device ID: $deviceId")
        Log.d(TAG, "Expected structure: /users/$sanitizedEmail/devices/$deviceId/")

        val firestore = FirebaseFirestore.getInstance()

        // Test each collection
        val collections = listOf("call_logs", "contacts", "messages", "weather", "locations", "audio_recordings")

        for (collection in collections) {
            try {
                val testData = mapOf(
                    "test" to true,
                    "timestamp" to System.currentTimeMillis(),
                    "collection" to collection,
                    "userEmail" to userEmail,
                    "deviceId" to deviceId
                )

                val documentPath = "users/$sanitizedEmail/devices/$deviceId/$collection/test_${System.currentTimeMillis()}"
                Log.d(TAG, "Testing path: $documentPath")

                firestore.document(documentPath)
                    .set(testData)
                    .await()

                Log.d(TAG, "✅ SUCCESS: $collection collection created")

            } catch (e: Exception) {
                Log.e(TAG, "❌ FAILED: $collection collection - ${e.message}")

                // Detailed error analysis
                when {
                    e.message?.contains("PERMISSION_DENIED") == true -> {
                        Log.e(TAG, "   → Permission denied - check Firestore rules")
                    }
                    e.message?.contains("UNAUTHENTICATED") == true -> {
                        Log.e(TAG, "   → User not authenticated properly")
                    }
                    e.message?.contains("UNAVAILABLE") == true -> {
                        Log.e(TAG, "   → Network/service unavailable")
                    }
                    else -> {
                        Log.e(TAG, "   → Unknown error: ${e.javaClass.simpleName}")
                    }
                }
            }
        }

        // Test reading the structure
        try {
            val userDoc = firestore.document("users/$sanitizedEmail").get().await()
            Log.d(TAG, "✅ User document exists: ${userDoc.exists()}")

            val deviceDoc = firestore.document("users/$sanitizedEmail/devices/$deviceId").get().await()
            Log.d(TAG, "✅ Device document exists: ${deviceDoc.exists()}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading structure: ${e.message}")
        }

        Log.d(TAG, "=== STRUCTURE TEST COMPLETE ===")
    }

    /**
     * Sanitize email exactly as your code does
     */
    private fun sanitizeEmailForFirestore(email: String): String {
        return email.replace(".", "_dot_")
            .replace("@", "_at_")
            .replace("/", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("*", "_")
            .replace("?", "_")
    }

    /**
     * Test a specific collection with real data
     */
    suspend fun testSpecificCollection(context: Context, collectionName: String, testData: Map<String, Any>) {
        Log.d(TAG, "=== TESTING $collectionName COLLECTION ===")

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email == null) {
            Log.e(TAG, "❌ User not authenticated")
            return
        }

        val userEmail = currentUser.email!!
        val deviceId = DeviceIdentifier.getPersistentDeviceId(context)
        val sanitizedEmail = sanitizeEmailForFirestore(userEmail)

        try {
            val firestore = FirebaseFirestore.getInstance()
            val documentId = "test_${System.currentTimeMillis()}"

            val fullPath = "users/$sanitizedEmail/devices/$deviceId/$collectionName/$documentId"
            Log.d(TAG, "Writing to: $fullPath")

            firestore.document(fullPath)
                .set(testData)
                .await()

            Log.d(TAG, "✅ Successfully wrote to $collectionName")

            // Verify we can read it back
            val readDoc = firestore.document(fullPath).get().await()
            if (readDoc.exists()) {
                Log.d(TAG, "✅ Successfully read back from $collectionName")
                Log.d(TAG, "   Data: ${readDoc.data}")
            } else {
                Log.e(TAG, "❌ Could not read back from $collectionName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error with $collectionName: ${e.message}")
        }
    }

    /**
     * Quick test of the exact path from your screenshot
     */
    suspend fun testScreenshotPath() {
        Log.d(TAG, "=== TESTING SCREENSHOT PATH ===")

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email == null) {
            Log.e(TAG, "❌ User not authenticated")
            return
        }

        // Your exact path from screenshot
        val exactPath = "users/sadakpramodh_at_yahoo_dot_com/devices/ffffffff-f714-60bc-1437-68a1d95aa476"

        Log.d(TAG, "Testing exact path: $exactPath")

        try {
            val firestore = FirebaseFirestore.getInstance()

            // Test writing to call_logs
            val callLogData = mapOf(
                "test" to true,
                "timestamp" to System.currentTimeMillis(),
                "phoneNumber" to "+1234567890",
                "duration" to 120L,
                "type" to 1
            )

            firestore.document("$exactPath/call_logs/test_call")
                .set(callLogData)
                .await()

            Log.d(TAG, "✅ Successfully wrote to exact call_logs path")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to write to exact path: ${e.message}")
        }
    }

    /**
     * Compare current user with expected structure
     */
    fun verifyUserEmailMatch() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email == null) {
            Log.e(TAG, "❌ No current user")
            return
        }

        val userEmail = currentUser.email!!
        val sanitizedEmail = sanitizeEmailForFirestore(userEmail)

        Log.d(TAG, "=== EMAIL VERIFICATION ===")
        Log.d(TAG, "Current user email: $userEmail")
        Log.d(TAG, "Sanitized email: $sanitizedEmail")
        Log.d(TAG, "Expected from screenshot: sadakpramodh_at_yahoo_dot_com")
        Log.d(TAG, "Match: ${sanitizedEmail == "sadakpramodh_at_yahoo_dot_com"}")

        if (sanitizedEmail != "sadakpramodh_at_yahoo_dot_com") {
            Log.w(TAG, "⚠️ Email mismatch! You may need to sign in with sadakpramodh@yahoo.com")
        }
    }
}
