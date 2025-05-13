package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing comprehensive device information
 */
@Entity(tableName = "device_info")
data class DeviceInfoEntity(
    @PrimaryKey
    val deviceId: String,                 // The persistent device identifier

    // Registration details
    val firstRegistered: Long,            // When this device was first registered
    val lastUpdated: Long,                // When this device info was last updated

    // Device hardware info
    val manufacturer: String,             // Device manufacturer (e.g., Samsung)
    val brand: String,                    // Device brand
    val model: String,                    // Device model name
    val product: String,                  // Device product name
    val device: String,                   // Device codename
    val hardware: String,                 // Hardware name

    // Android info
    val androidVersion: String,           // Android OS version
    val sdkVersion: String,               // Android SDK version
    val buildId: String,                  // Build ID
    val androidId: String,                // Android ID (changes on factory reset)

    // SIM/Carrier info
    val networkOperatorName: String?,     // Carrier name
    val networkOperator: String?,         // MCC+MNC
    val networkCountryIso: String?,       // Country code
    val simOperator: String?,             // SIM operator MCC+MNC
    val simOperatorName: String?,         // SIM operator name
    val simCountryIso: String?,           // SIM country code

    // Additional identifiers (might be restricted)
    val imei: String?,                    // IMEI if available
    val phoneType: String?,               // GSM, CDMA, etc.

    // Status flags
    val isActive: Boolean = true,         // Whether this device is still active
    val uploadedToCloud: Boolean = false, // If this record was uploaded to Firestore
    val uploadTimestamp: Long? = null     // When this record was uploaded
)