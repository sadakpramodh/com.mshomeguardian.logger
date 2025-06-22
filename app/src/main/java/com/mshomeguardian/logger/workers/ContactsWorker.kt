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
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class ContactsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    companion object {
        private const val TAG = "ContactsWorker"
        private const val SYNC_LIMIT = 50 // Reduced to avoid excessive processing
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check authentication first
            val userEmail = AuthManager.getCurrentUser()?.email
            if (userEmail == null) {
                Log.w(TAG, "User not authenticated, skipping contacts sync")
                return@withContext Result.success()
            }

            // Check permissions
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing READ_CONTACTS permission")
                return@withContext Result.failure()
            }

            // Check if Firebase is available
            if (!FirebaseServiceHelper.isFirebaseAvailable()) {
                Log.w(TAG, "Firebase not available, skipping contacts sync")
                return@withContext Result.success()
            }

            // Get last sync time from shared preferences
            val prefs = applicationContext.getSharedPreferences("contacts_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time", 0)
            val currentTime = System.currentTimeMillis()

            // Get previously synced contact IDs
            val syncedContactIds = loadSyncedContactIds()

            // Sync contacts
            val syncCount = syncContacts(userEmail, lastSyncTime, currentTime, syncedContactIds)

            // Update last sync time if successful
            prefs.edit().putLong("last_sync_time", currentTime).apply()

            Log.d(TAG, "Incremental contact sync completed. Synced $syncCount contacts.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing contacts", e)
            Result.retry()
        }
    }

    private fun loadSyncedContactIds(): Set<String> {
        val prefs = applicationContext.getSharedPreferences("synced_contacts", Context.MODE_PRIVATE)
        return prefs.getStringSet("contact_ids", HashSet()) ?: HashSet()
    }

    private fun saveSyncedContactId(contactId: String) {
        val prefs = applicationContext.getSharedPreferences("synced_contacts", Context.MODE_PRIVATE)
        val contactIds = prefs.getStringSet("contact_ids", HashSet()) ?: HashSet()
        val newContactIds = HashSet(contactIds)
        newContactIds.add(contactId)
        prefs.edit().putStringSet("contact_ids", newContactIds).apply()
    }

    private suspend fun syncContacts(
        userEmail: String,
        lastSyncTime: Long,
        currentTime: Long,
        syncedContactIds: Set<String>
    ): Int {
        var syncCount = 0
        var cursor: Cursor? = null

        try {
            // Query for contacts
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.LAST_TIME_CONTACTED,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
            )

            // Try to use the CONTACT_LAST_UPDATED_TIMESTAMP column to get only updated contacts
            var selection: String? = null
            var selectionArgs: Array<String>? = null

            // First, try to query based on last updated timestamp if available
            if (lastSyncTime > 0) {
                try {
                    selection = "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} > ?"
                    selectionArgs = arrayOf(lastSyncTime.toString())

                    // Test if the column exists by running a query
                    applicationContext.contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.Contacts._ID),
                        selection,
                        selectionArgs,
                        null
                    )?.close()
                } catch (e: Exception) {
                    Log.d(TAG, "CONTACT_LAST_UPDATED_TIMESTAMP not available, falling back to ID-based sync")
                    // Reset selection if the column doesn't exist
                    selection = null
                    selectionArgs = null
                }
            }

            cursor = applicationContext.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val photoUriIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val lastContactedIndex = it.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)

                while (it.moveToNext() && syncCount < SYNC_LIMIT) {
                    if (idIndex < 0) continue

                    val contactId = it.getString(idIndex)

                    // Skip contacts we've already synced (if we're not using timestamp-based filtering)
                    if (selection == null && syncedContactIds.contains(contactId)) {
                        continue
                    }

                    val contactName = if (nameIndex >= 0 && !it.isNull(nameIndex)) it.getString(nameIndex) else "Unknown"
                    val hasPhone = if (hasPhoneIndex >= 0) it.getInt(hasPhoneIndex) == 1 else false
                    val photoUri = if (photoUriIndex >= 0 && !it.isNull(photoUriIndex)) it.getString(photoUriIndex) else null
                    val lastContacted = if (lastContactedIndex >= 0 && !it.isNull(lastContactedIndex)) it.getLong(lastContactedIndex) else 0L

                    // Get phone numbers for this contact
                    val phoneNumbers = if (hasPhone) getPhoneNumbers(contactId) else emptyList()

                    // Get emails for this contact
                    val emails = getEmails(contactId)

                    // Create contact map for Firebase
                    val contactData = mapOf(
                        "contactId" to contactId,
                        "displayName" to contactName,
                        "phoneNumbers" to phoneNumbers,
                        "emails" to emails,
                        "photoUri" to (photoUri ?: ""),
                        "lastContactedTimestamp" to lastContacted,
                        "syncTimestamp" to currentTime,
                        "deviceId" to deviceId
                    )

                    // Upload to Firebase using new structure
                    try {
                        val success = FirebaseServiceHelper.uploadContact(userEmail, deviceId, contactData)
                        if (success) {
                            // Mark this contact as synced
                            saveSyncedContactId(contactId)
                            syncCount++
                            Log.d(TAG, "Contact $contactId uploaded successfully")
                        } else {
                            Log.w(TAG, "Failed to upload contact $contactId")
                        }
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
                    val phoneNumber = if (numberIndex >= 0 && !it.isNull(numberIndex)) it.getString(numberIndex) else ""
                    val phoneType = if (typeIndex >= 0 && !it.isNull(typeIndex)) {
                        val type = it.getInt(typeIndex)
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            applicationContext.resources, type, ""
                        ).toString()
                    } else "Other"

                    val phoneMap = mapOf(
                        "number" to phoneNumber,
                        "type" to phoneType
                    )
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
                    val emailAddress = if (addressIndex >= 0 && !it.isNull(addressIndex)) it.getString(addressIndex) else ""
                    val emailType = if (typeIndex >= 0 && !it.isNull(typeIndex)) {
                        val type = it.getInt(typeIndex)
                        ContactsContract.CommonDataKinds.Email.getTypeLabel(
                            applicationContext.resources, type, ""
                        ).toString()
                    } else "Other"

                    val emailMap = mapOf(
                        "address" to emailAddress,
                        "type" to emailType
                    )
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
}