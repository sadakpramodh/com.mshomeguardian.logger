package com.mshomeguardian.logger.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Helper class for Firebase operations with new structure:
 * Root -> users/{email} -> devices/{deviceId} -> {collections}
 * Enhanced with better error handling for Firestore connection issues
 */
object FirebaseServiceHelper {
    private const val TAG = "FirebaseServiceHelper"

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore", e)
            null
        }
    }

    private val storage: FirebaseStorage? by lazy {
        try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Storage", e)
            null
        }
    }

    /**
     * Safe execution wrapper that handles Firestore connection errors gracefully
     */
    private suspend fun <T> safeFirestoreOperation(
        operation: suspend () -> T,
        operationName: String,
        defaultValue: T
    ): T {
        return try {
            operation()
        } catch (e: Exception) {
            // Handle expected Firestore connection issues gracefully
            when {
                e.message?.contains("UNAVAILABLE") == true -> {
                    Log.d(TAG, "$operationName temporarily unavailable - will retry automatically")
                }
                e.message?.contains("Stream closed") == true -> {
                    Log.d(TAG, "$operationName stream closed - Firestore will reconnect")
                }
                e.message?.contains("permission", ignoreCase = true) == true -> {
                    Log.w(TAG, "$operationName permission denied - check security rules")
                }
                e.message?.contains("NOT_FOUND") == true -> {
                    Log.w(TAG, "$operationName document not found - this is expected for new documents")
                }
                else -> {
                    Log.w(TAG, "$operationName failed: ${e.message}")
                }
            }
            defaultValue
        }
    }

    /**
     * Get the base document reference for a user's device
     * Structure: users/{email}/devices/{deviceId}
     */
    private fun getDeviceDocumentPath(userEmail: String, deviceId: String): String {
        // Sanitize email for Firestore document ID (replace invalid characters)
        val sanitizedEmail = sanitizeEmailForFirestore(userEmail)
        return "users/$sanitizedEmail/devices/$deviceId"
    }

    /**
     * Get the collection reference for a specific data type
     * Structure: users/{email}/devices/{deviceId}/{collection}
     */
    private fun getCollectionPath(userEmail: String, deviceId: String, collection: String): String {
        return "${getDeviceDocumentPath(userEmail, deviceId)}/$collection"
    }

    /**
     * Sanitize email address to be valid as Firestore document ID
     * Firestore document IDs cannot contain: / [ ] * ?
     */
    private fun sanitizeEmailForFirestore(email: String): String {
        return email.replace(Regex("[/\\[\\]*?]"), "_")
            .replace(".", "_dot_")
            .replace("@", "_at_")
    }

    /**
     * Upload location data
     */
    suspend fun uploadLocation(
        userEmail: String,
        deviceId: String,
        locationData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val timestamp = locationData["timestamp"] as? Long ?: System.currentTimeMillis()
                val collectionPath = getCollectionPath(userEmail, deviceId, "locations")

                firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                    .set(locationData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Location uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload location",
            defaultValue = false
        )
    }

    /**
     * Upload call log data
     */
    suspend fun uploadCallLog(
        userEmail: String,
        deviceId: String,
        callLogData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val callId = callLogData["callId"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "call_logs")

                firestoreInstance.collection(collectionPath)
                    .document(callId)
                    .set(callLogData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Call log uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload call log",
            defaultValue = false
        )
    }

    /**
     * Upload message data
     */
    suspend fun uploadMessage(
        userEmail: String,
        deviceId: String,
        messageData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val messageId = messageData["messageId"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "messages")

                firestoreInstance.collection(collectionPath)
                    .document(messageId)
                    .set(messageData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Message uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload message",
            defaultValue = false
        )
    }

    /**
     * Upload contact data
     */
    suspend fun uploadContact(
        userEmail: String,
        deviceId: String,
        contactData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val contactId = contactData["contactId"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "contacts")

                firestoreInstance.collection(collectionPath)
                    .document(contactId)
                    .set(contactData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Contact uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload contact",
            defaultValue = false
        )
    }

    /**
     * Upload device info
     */
    suspend fun uploadDeviceInfo(
        userEmail: String,
        deviceId: String,
        deviceData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val deviceDocPath = getDeviceDocumentPath(userEmail, deviceId)

                firestoreInstance.document(deviceDocPath)
                    .set(deviceData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Device info uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload device info",
            defaultValue = false
        )
    }

    /**
     * Upload audio recording metadata
     */
    suspend fun uploadAudioRecording(
        userEmail: String,
        deviceId: String,
        recordingData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val recordingId = recordingData["recordingId"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "audio_recordings")

                firestoreInstance.collection(collectionPath)
                    .document(recordingId)
                    .set(recordingData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Audio recording metadata uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload audio recording",
            defaultValue = false
        )
    }

    /**
     * Upload weather data
     */
    suspend fun uploadWeather(
        userEmail: String,
        deviceId: String,
        weatherData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val timestamp = weatherData["timestamp"] as? Long ?: System.currentTimeMillis()
                val collectionPath = getCollectionPath(userEmail, deviceId, "weather")

                firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                    .set(weatherData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Weather data uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload weather",
            defaultValue = false
        )
    }

    /**
     * Upload phone state data
     */
    suspend fun uploadPhoneState(
        userEmail: String,
        deviceId: String,
        phoneStateData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val timestamp = phoneStateData["timestamp"] as? Long ?: System.currentTimeMillis()
                val collectionPath = getCollectionPath(userEmail, deviceId, "phone_state")

                firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                    .set(phoneStateData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Phone state uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload phone state",
            defaultValue = false
        )
    }

    /**
     * Get Firebase Storage reference for audio files
     * Structure: users/{email}/devices/{deviceId}/audio/{filename}
     */
    fun getAudioStorageReference(userEmail: String, deviceId: String, filename: String) =
        storage?.reference?.child("users")
            ?.child(sanitizeEmailForFirestore(userEmail))
            ?.child("devices")
            ?.child(deviceId)
            ?.child("audio")
            ?.child(filename)

    /**
     * Get Firebase Storage reference for any file type
     * Structure: users/{email}/devices/{deviceId}/{fileType}/{filename}
     */
    fun getFileStorageReference(userEmail: String, deviceId: String, fileType: String, filename: String) =
        storage?.reference?.child("users")
            ?.child(sanitizeEmailForFirestore(userEmail))
            ?.child("devices")
            ?.child(deviceId)
            ?.child(fileType)
            ?.child(filename)

    /**
     * Check if Firebase services are available
     */
    fun isFirebaseAvailable(): Boolean {
        return firestore != null && storage != null
    }

    /**
     * Get current user email safely
     */
    fun getCurrentUserEmail(): String? {
        return try {
            AuthManager.getCurrentUser()?.email
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user email", e)
            null
        }
    }

    /**
     * Create user account document if it doesn't exist
     */
    suspend fun initializeUserAccount(userEmail: String, deviceId: String): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val sanitizedEmail = sanitizeEmailForFirestore(userEmail)
                val userDocPath = "users/$sanitizedEmail"

                // Create user document with basic info
                val userData = mapOf(
                    "email" to userEmail,
                    "createdAt" to System.currentTimeMillis(),
                    "lastUpdated" to System.currentTimeMillis(),
                    "deviceCount" to 1
                )

                firestoreInstance.document(userDocPath)
                    .set(userData, SetOptions.merge())
                    .await()

                // Initialize device document
                val deviceData = mapOf(
                    "deviceId" to deviceId,
                    "registeredAt" to System.currentTimeMillis(),
                    "lastActive" to System.currentTimeMillis(),
                    "isActive" to true
                )

                val deviceDocPath = getDeviceDocumentPath(userEmail, deviceId)
                firestoreInstance.document(deviceDocPath)
                    .set(deviceData, SetOptions.merge())
                    .await()

                Log.d(TAG, "User account initialized for $userEmail")
                true
            },
            operationName = "Initialize user account",
            defaultValue = false
        )
    }

    /**
     * Update device last active timestamp (creates document if it doesn't exist)
     */
    suspend fun updateDeviceLastActive(userEmail: String, deviceId: String): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val deviceDocPath = getDeviceDocumentPath(userEmail, deviceId)
                val updateData = mapOf(
                    "lastActive" to System.currentTimeMillis(),
                    "deviceId" to deviceId,
                    "isActive" to true
                )

                // Use set with merge instead of update to create document if it doesn't exist
                firestoreInstance.document(deviceDocPath)
                    .set(updateData, SetOptions.merge())
                    .await()

                true
            },
            operationName = "Update device last active",
            defaultValue = false
        )
    }
}