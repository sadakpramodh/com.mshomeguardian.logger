package com.mshomeguardian.logger.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Updated FirebaseServiceHelper with consistent user-based structure
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
     * Safe execution wrapper for Firestore operations
     */
    private suspend fun <T> safeFirestoreOperation(
        operation: suspend () -> T,
        operationName: String,
        defaultValue: T
    ): T {
        return try {
            operation()
        } catch (e: Exception) {
            when {
                e.message?.contains("UNAVAILABLE") == true -> {
                    Log.d(TAG, "$operationName temporarily unavailable - will retry automatically")
                }

                e.message?.contains("permission", ignoreCase = true) == true -> {
                    Log.w(TAG, "$operationName permission denied - check authentication and rules")
                }

                e.message?.contains("NOT_FOUND") == true -> {
                    Log.w(
                        TAG,
                        "$operationName document not found - this is expected for new documents"
                    )
                }

                else -> {
                    Log.e(TAG, "$operationName failed: ${e.message}")
                }
            }
            defaultValue
        }
    }

    /**
     * Sanitize email address for use as Firestore document ID
     * This must match exactly what you see in Firebase Console
     */
    /**
     * Sanitize an email address so it can be safely used as a Firestore
     * document ID. This helper is public so workers can share the logic
     * and remain consistent with the console structure.
     */
    fun sanitizeEmailForFirestore(email: String): String {
        return email.replace(".", "_dot_")
            .replace("@", "_at_")
            .replace("/", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("*", "_")
            .replace("?", "_")
    }

    /**
     * Get the user document path
     */
    private fun getUserDocumentPath(userEmail: String): String {
        return "users/${sanitizeEmailForFirestore(userEmail)}"
    }

    /**
     * Get the device document path within user's collection
     */
    private fun getDeviceDocumentPath(userEmail: String, deviceId: String): String {
        return "${getUserDocumentPath(userEmail)}/devices/$deviceId"
    }

    /**
     * Get collection path for specific data type
     */
    private fun getCollectionPath(userEmail: String, deviceId: String, collection: String): String {
        return "${getDeviceDocumentPath(userEmail, deviceId)}/$collection"
    }

    /**
     * Initialize user account in Firestore
     */
    suspend fun initializeUserAccount(userEmail: String, deviceId: String): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                // Create user document
                val userData = mapOf(
                    "email" to userEmail,
                    "createdAt" to System.currentTimeMillis(),
                    "lastUpdated" to System.currentTimeMillis(),
                    "deviceCount" to 1
                )

                val userDocPath = getUserDocumentPath(userEmail)
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

                Log.d(TAG, "User account initialized for $userEmail with device $deviceId")
                true
            },
            operationName = "Initialize user account",
            defaultValue = false
        )
    }

    /**
     * Upload location data with user-based structure
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
     * Upload call log data with user-based structure
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
     * Upload message data with user-based structure
     */
    suspend fun uploadMessage(
        userEmail: String,
        deviceId: String,
        messageData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val messageId =
                    messageData["messageId"] as? String ?: return@safeFirestoreOperation false
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
     * Upload contact data with user-based structure
     */
    suspend fun uploadContact(
        userEmail: String,
        deviceId: String,
        contactData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val contactId =
                    contactData["contactId"] as? String ?: return@safeFirestoreOperation false
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
     * Upload device info with user-based structure
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
     * Upload audio recording metadata with user-based structure
     */
    suspend fun uploadAudioRecording(
        userEmail: String,
        deviceId: String,
        recordingData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val recordingId =
                    recordingData["recordingId"] as? String ?: return@safeFirestoreOperation false
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
     * Upload weather data with user-based structure
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
                Log.d(TAG, "Using weather collection path: $collectionPath")

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
     * Upload phone state data with user-based structure
     */
    suspend fun uploadPhoneState(
        userEmail: String,
        deviceId: String,
        id: String,
        phoneStateData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val collectionPath = getCollectionPath(userEmail, deviceId, "phone_state")

                firestoreInstance.collection(collectionPath)
                    .document(id)
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
     * Upload installed app information
     */
    suspend fun uploadInstalledApp(
        userEmail: String,
        deviceId: String,
        appData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val packageName = appData["packageName"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "installed_apps")

                firestoreInstance.collection(collectionPath)
                    .document(packageName)
                    .set(appData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Installed app uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload installed app",
            defaultValue = false
        )
    }

    /**
     * Upload app usage statistics
     */
    suspend fun uploadAppUsage(
        userEmail: String,
        deviceId: String,
        usageData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val packageName = usageData["packageName"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "app_usage")

                firestoreInstance.collection(collectionPath)
                    .document(packageName)
                    .set(usageData, SetOptions.merge())
                    .await()

                Log.d(TAG, "App usage uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload app usage",
            defaultValue = false
        )
    }

    /**
     * Upload network usage statistics
     */
    suspend fun uploadNetworkUsage(
        userEmail: String,
        deviceId: String,
        usageData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val packageName = usageData["packageName"] as? String
                    ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "network_usage")

                firestoreInstance.collection(collectionPath)
                    .document(packageName)
                    .set(usageData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Network usage uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload network usage",
            defaultValue = false
        )
    }

    /**
     * Upload battery status information
     */
    suspend fun uploadBatteryStatus(
        userEmail: String,
        deviceId: String,
        batteryData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val timestamp = batteryData["timestamp"] as? Long ?: System.currentTimeMillis()
                val collectionPath = getCollectionPath(userEmail, deviceId, "battery_status")

                firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                    .set(batteryData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Battery status uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload battery status",
            defaultValue = false
        )
    }

    /**
     * Upload system metrics information (storage, memory, cpu, display, security)
     */
    suspend fun uploadSystemMetrics(
        userEmail: String,
        deviceId: String,
        metricsData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val timestamp = metricsData["timestamp"] as? Long ?: System.currentTimeMillis()
                val collectionPath = getCollectionPath(userEmail, deviceId, "system_metrics")

                firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                    .set(metricsData, SetOptions.merge())
                    .await()

                Log.d(TAG, "System metrics uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload system metrics",
            defaultValue = false
        )
    }

    /**
     * Upload sensor data from available sensors
     */
    suspend fun uploadSensorData(
        userEmail: String,
        deviceId: String,
        sensorData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val timestamp = sensorData["timestamp"] as? Long ?: System.currentTimeMillis()
                val collectionPath = getCollectionPath(userEmail, deviceId, "sensor_data")

                firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                    .set(sensorData, SetOptions.merge())
                    .await()

                Log.d(TAG, "Sensor data uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload sensor data",
            defaultValue = false
        )
    }

    /**
     * Upload system events like boot or shutdown
     */
    suspend fun uploadSystemEvent(
        userEmail: String,
        deviceId: String,
        eventData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false

                val timestamp = eventData["timestamp"] as? Long ?: System.currentTimeMillis()
                val collectionPath = getCollectionPath(userEmail, deviceId, "system_events")

                firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                    .set(eventData, SetOptions.merge())
                    .await()

                Log.d(TAG, "System event uploaded successfully for $userEmail")
                true
            },
            operationName = "Upload system event",
            defaultValue = false
        )
    }

    /**
     * Update device last active timestamp
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

                firestoreInstance.document(deviceDocPath)
                    .set(updateData, SetOptions.merge())
                    .await()

                true
            },
            operationName = "Update device last active",
            defaultValue = false
        )
    }

    /**
     * Get Firebase Storage reference with user-based structure
     */
    fun getAudioStorageReference(userEmail: String, deviceId: String, filename: String) =
        storage?.reference?.child("users")
            ?.child(sanitizeEmailForFirestore(userEmail))
            ?.child("devices")
            ?.child(deviceId)
            ?.child("audio")
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
}