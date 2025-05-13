package com.mshomeguardian.logger.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mshomeguardian.logger.utils.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class ContactsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firestore", e)
        null
    }

    companion object {
        private const val TAG = "ContactsWorker"
        private const val MAX_CONTACTS_TO_SYNC = 100 // Reduced sync limit to avoid too many database operations
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing READ_CONTACTS permission")
                return@withContext Result.failure()
            }

            // Get last sync time from shared preferences
            val prefs = applicationContext.getSharedPreferences("contacts_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time", 0)
            val currentTime = System.currentTimeMillis()

            // Only sync if we haven't synced in the last 24 hours
            if (lastSyncTime > 0 && (currentTime - lastSyncTime) < 24 * 60 * 60 * 1000) {
                // It's been less than 24 hours since our last sync, skip full sync and just check for changes
                val syncCount = syncChangedContactsOnly(lastSyncTime, currentTime)
                Log.d(TAG, "Incremental contact sync completed. Synced $syncCount contacts.")
            } else {
                // First time sync or it's been more than 24 hours, do a more complete sync
                val syncCount = syncContacts(lastSyncTime, currentTime)
                Log.d(TAG, "Full contact sync completed. Synced $syncCount contacts.")
            }

            // Update last sync time if successful
            prefs.edit().putLong("last_sync_time", currentTime).apply()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing contacts", e)
            Result.retry()
        }
    }

    private suspend fun syncChangedContactsOnly(lastSyncTime: Long, currentTime: Long): Int {
        var syncCount = 0
        var cursor: Cursor? = null

        try {
            // Try to get last modified contacts - this won't work on all Android versions
            // but we'll try it first as an optimization
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP // Not available on all versions
            )

            // Try to filter by last updated timestamp
            val selection = "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} > ?"
            val selectionArgs = arrayOf(lastSyncTime.toString())

            try {
                cursor = applicationContext.contentResolver.query(
                    uri, projection, selection, selectionArgs, null
                )

                if (cursor == null || cursor.count == 0) {
                    // If this approach doesn't work (older Android versions or no updates),
                    // fall back to a small random sample to keep things fresh
                    cursor?.close()
                    cursor = applicationContext.contentResolver.query(
                        uri,
                        projection,
                        null,
                        null,
                        "RANDOM() LIMIT $MAX_CONTACTS_TO_SYNC"
                    )
                }
            } catch (e: Exception) {
                // CONTACT_LAST_UPDATED_TIMESTAMP might not be available
                Log.d(TAG, "Could not query by update timestamp, using random sample instead")
                cursor?.close()
                cursor = applicationContext.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "RANDOM() LIMIT $MAX_CONTACTS_TO_SYNC"
                )
            }

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val photoUriIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                while (it.moveToNext() && syncCount < MAX_CONTACTS_TO_SYNC) {
                    if (idIndex < 0) continue  // Skip if we can't get ID

                    val contactId = it.getString(idIndex)
                    val contactName = if (nameIndex >= 0 && !it.isNull(nameIndex)) it.getString(nameIndex) else "Unknown"
                    val hasPhone = if (hasPhoneIndex >= 0) it.getInt(hasPhoneIndex) == 1 else false
                    val photoUri = if (photoUriIndex >= 0 && !it.isNull(photoUriIndex)) it.getString(photoUriIndex) else null

                    // Get phone numbers and emails only if needed
                    val phoneNumbers = if (hasPhone) getPhoneNumbers(contactId) else emptyList()
                    val emails = getEmails(contactId)

                    // Create contact map for Firestore
                    val contactMap = HashMap<String, Any>()
                    contactMap["contactId"] = contactId
                    contactMap["displayName"] = contactName
                    contactMap["phoneNumbers"] = phoneNumbers
                    contactMap["emails"] = emails
                    contactMap["photoUri"] = photoUri ?: ""
                    contactMap["syncTimestamp"] = currentTime
                    contactMap["deviceId"] = deviceId

                    // Upload to Firestore
                    try {
                        uploadContact(contactId, contactMap)
                        syncCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading contact $contactId: ${e.message}")
                    }
                }
            }

            return syncCount
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts for incremental sync", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    private suspend fun syncContacts(lastSyncTime: Long, currentTime: Long): Int {
        var syncCount = 0
        var cursor: Cursor? = null

        try {
            // For full sync, just get a limited number of contacts
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.PHOTO_URI
            )

            // No filtering, but limit the number
            cursor = applicationContext.contentResolver.query(
                uri, projection, null, null, "RANDOM() LIMIT $MAX_CONTACTS_TO_SYNC"
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val photoUriIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                while (it.moveToNext() && syncCount < MAX_CONTACTS_TO_SYNC) {
                    if (idIndex < 0) continue  // Skip if we can't get ID

                    val contactId = it.getString(idIndex)
                    val contactName = if (nameIndex >= 0 && !it.isNull(nameIndex)) it.getString(nameIndex) else "Unknown"
                    val hasPhone = if (hasPhoneIndex >= 0) it.getInt(hasPhoneIndex) == 1 else false
                    val photoUri = if (photoUriIndex >= 0 && !it.isNull(photoUriIndex)) it.getString(photoUriIndex) else null

                    // Get phone numbers and emails
                    val phoneNumbers = if (hasPhone) getPhoneNumbers(contactId) else emptyList()
                    val emails = getEmails(contactId)

                    // Create contact map for Firestore
                    val contactMap = HashMap<String, Any>()
                    contactMap["contactId"] = contactId
                    contactMap["displayName"] = contactName
                    contactMap["phoneNumbers"] = phoneNumbers
                    contactMap["emails"] = emails
                    contactMap["photoUri"] = photoUri ?: ""
                    contactMap["syncTimestamp"] = currentTime
                    contactMap["deviceId"] = deviceId

                    // Upload to Firestore
                    try {
                        uploadContact(contactId, contactMap)
                        syncCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading contact $contactId: ${e.message}")
                    }
                }
            }

            return syncCount
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
            throw e
        } finally {
            cursor?.close()
        }
    }

    private fun getPhoneNumbers(contactId: String): List<Map<String, String>> {
        val phoneList = mutableListOf<Map<String, String>>()
        var phoneCursor: Cursor? = null

        try {
            val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )
            val phoneSelection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
            val phoneSelectionArgs = arrayOf(contactId)

            phoneCursor = applicationContext.contentResolver.query(
                phoneUri, phoneProjection, phoneSelection, phoneSelectionArgs, null
            )

            phoneCursor?.let {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (it.moveToNext()) {
                    // Added null checks
                    val phoneNumber = if (numberIndex >= 0 && !it.isNull(numberIndex)) it.getString(numberIndex) else ""
                    val phoneType = if (typeIndex >= 0 && !it.isNull(typeIndex)) {
                        val type = it.getInt(typeIndex)
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            applicationContext.resources, type, ""
                        ).toString()
                    } else "Other"

                    val phoneMap = HashMap<String, String>()
                    phoneMap["number"] = phoneNumber
                    phoneMap["type"] = phoneType
                    phoneList.add(phoneMap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone numbers for contact $contactId", e)
        } finally {
            phoneCursor?.close()
        }

        return phoneList
    }

    private fun getEmails(contactId: String): List<Map<String, String>> {
        val emailList = mutableListOf<Map<String, String>>()
        var emailCursor: Cursor? = null

        try {
            val emailUri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
            val emailProjection = arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE
            )
            val emailSelection = "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?"
            val emailSelectionArgs = arrayOf(contactId)

            emailCursor = applicationContext.contentResolver.query(
                emailUri, emailProjection, emailSelection, emailSelectionArgs, null
            )

            emailCursor?.let {
                val addressIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val typeIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)

                while (it.moveToNext()) {
                    // Added null checks
                    val emailAddress = if (addressIndex >= 0 && !it.isNull(addressIndex)) it.getString(addressIndex) else ""
                    val emailType = if (typeIndex >= 0 && !it.isNull(typeIndex)) {
                        val type = it.getInt(typeIndex)
                        ContactsContract.CommonDataKinds.Email.getTypeLabel(
                            applicationContext.resources, type, ""
                        ).toString()
                    } else "Other"

                    val emailMap = HashMap<String, String>()
                    emailMap["address"] = emailAddress
                    emailMap["type"] = emailType
                    emailList.add(emailMap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting emails for contact $contactId", e)
        } finally {
            emailCursor?.close()
        }

        return emailList
    }

    private suspend fun uploadContact(contactId: String, contactMap: Map<String, Any>) {
        val firestoreInstance = firestore ?: throw IllegalStateException("Firestore not initialized")

        try {
            firestoreInstance.collection("devices")
                .document(deviceId)
                .collection("contacts")
                .document(contactId)
                .set(contactMap, SetOptions.merge())
                .await()

            Log.d(TAG, "Contact $contactId uploaded to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload contact $contactId", e)
            throw e
        }
    }
}