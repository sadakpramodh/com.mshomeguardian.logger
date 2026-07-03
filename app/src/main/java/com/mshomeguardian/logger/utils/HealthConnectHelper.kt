package com.mshomeguardian.logger.utils

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord

object HealthConnectHelper {
    private const val TAG = "HealthConnectHelper"
    const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"

    val requiredReadPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    val heartRatePermission: String = HealthPermission.getReadPermission(HeartRateRecord::class)
    val restingHeartRatePermission: String = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    val bloodPressurePermission: String = HealthPermission.getReadPermission(BloodPressureRecord::class)
    val oxygenSaturationPermission: String = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    val respiratoryRatePermission: String = HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    val bodyTemperaturePermission: String = HealthPermission.getReadPermission(BodyTemperatureRecord::class)
    val weightPermission: String = HealthPermission.getReadPermission(WeightRecord::class)
    val heightPermission: String = HealthPermission.getReadPermission(HeightRecord::class)
    val stepsPermission: String = HealthPermission.getReadPermission(StepsRecord::class)

    fun getSdkStatus(context: Context): Int {
        return try {
            HealthConnectClient.getSdkStatus(context)
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Failed to read Health Connect SDK status", e)
            HealthConnectClient.SDK_UNAVAILABLE
        }
    }

    fun isSdkAvailable(context: Context): Boolean {
        return getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun getGrantedPermissions(context: Context): Set<String> {
        return try {
            if (!isSdkAvailable(context)) {
                return emptySet()
            }

            HealthConnectClient.getOrCreate(context)
                .permissionController
                .getGrantedPermissions()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Failed to fetch Health Connect permissions", e)
            emptySet()
        }
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        val granted = getGrantedPermissions(context)
        return granted.containsAll(requiredReadPermissions)
    }

    suspend fun hasAnyPermission(context: Context): Boolean {
        return try {
            getGrantedPermissions(context).isNotEmpty()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Failed to check Health Connect permissions", e)
            false
        }
    }
}
