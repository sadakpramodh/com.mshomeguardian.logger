package com.mshomeguardian.logger.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

data class AdminAction(val id: String, val action: String)

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
                val userDocRef = firestoreInstance.document(userDocPath)
                userDocRef.set(userData, SetOptions.merge()).await()

                // Initialize device document
                val deviceData = mapOf(
                    "deviceId" to deviceId,
                    "registeredAt" to System.currentTimeMillis(),
                    "lastActive" to System.currentTimeMillis(),
                    "isActive" to true
                )

                val deviceDocPath = getDeviceDocumentPath(userEmail, deviceId)
                val deviceDocRef = firestoreInstance.document(deviceDocPath)
                deviceDocRef.set(deviceData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "User account initialized for $userEmail at ${userDocRef.path} with device ${deviceDocRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                docRef.set(locationData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Location uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(callId)
                docRef.set(callLogData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Call log uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(messageId)
                docRef.set(messageData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Message uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(contactId)
                docRef.set(contactData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Contact uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.document(deviceDocPath)
                docRef.set(deviceData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Device info uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(recordingId)
                docRef.set(recordingData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Audio recording metadata uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                docRef.set(weatherData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Weather data uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(id)
                docRef.set(phoneStateData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Phone state uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(packageName)
                docRef.set(appData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Installed app uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(packageName)
                docRef.set(usageData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "App usage uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(packageName)
                docRef.set(usageData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Network usage uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                docRef.set(batteryData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Battery status uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                docRef.set(metricsData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "System metrics uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                docRef.set(sensorData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Sensor data uploaded successfully for $userEmail at ${docRef.path}"
                )
                true
            },
            operationName = "Upload sensor data",
            defaultValue = false
        )
    }

    /**
     * Upload Health Connect vitals.
     */
    suspend fun uploadHealthVital(
        userEmail: String,
        deviceId: String,
        vitalData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false
                val entryId = vitalData["entryId"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "health_vitals")

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(entryId)
                docRef.set(vitalData, SetOptions.merge()).await()
                true
            },
            operationName = "Upload health vital",
            defaultValue = false
        )
    }

    /**
     * Upload Digital Wellbeing snapshot.
     */
    suspend fun uploadDigitalWellbeing(
        userEmail: String,
        deviceId: String,
        wellbeingData: Map<String, Any>
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false
                val snapshotId = wellbeingData["snapshotId"] as? String ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "digital_wellbeing")

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(snapshotId)
                docRef.set(wellbeingData, SetOptions.merge()).await()
                true
            },
            operationName = "Upload digital wellbeing",
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

                val docRef = firestoreInstance.collection(collectionPath)
                    .document(timestamp.toString())
                docRef.set(eventData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "System event uploaded successfully for $userEmail at ${docRef.path}"
                )
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

                val docRef = firestoreInstance.document(deviceDocPath)
                docRef.set(updateData, SetOptions.merge()).await()

                Log.d(
                    TAG,
                    "Device last active updated for $userEmail at ${docRef.path}"
                )
                true
            },
            operationName = "Update device last active",
            defaultValue = false
        )
    }

    /**
     * Fetch pending admin actions for this device
     */
    suspend fun fetchPendingAdminActions(userEmail: String, deviceId: String): List<AdminAction> {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation emptyList()

                val collectionPath = getCollectionPath(userEmail, deviceId, "admin_actions")
                val snapshot = firestoreInstance.collection(collectionPath)
                    .whereEqualTo("executed", false)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { doc ->
                    val action = doc.getString("action")
                    if (action != null) AdminAction(doc.id, action) else null
                }
            },
            operationName = "Fetch admin actions",
            defaultValue = emptyList()
        )
    }

    /**
     * Mark an admin action as executed
     */
    suspend fun markAdminActionExecuted(
        userEmail: String,
        deviceId: String,
        actionId: String
    ): Boolean {
        return safeFirestoreOperation(
            operation = {
                val firestoreInstance = firestore ?: return@safeFirestoreOperation false
                val collectionPath = getCollectionPath(userEmail, deviceId, "admin_actions")
                firestoreInstance.collection(collectionPath)
                    .document(actionId)
                    .update("executed", true)
                    .await()
                true
            },
            operationName = "Mark admin action executed",
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