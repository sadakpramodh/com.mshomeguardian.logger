package com.mshomeguardian.logger.workers

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mshomeguardian.logger.data.AppDatabase
import com.mshomeguardian.logger.data.HealthVitalEntity
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.DeviceIdentifier
import com.mshomeguardian.logger.utils.FirebaseServiceHelper
import com.mshomeguardian.logger.utils.HealthConnectHelper
import com.mshomeguardian.logger.utils.OptimizedLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import java.time.Instant

class HealthVitalsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HealthVitalsWorker"
        private const val PREF_NAME = "health_connect_sync"
        private const val LAST_SYNC_KEY = "last_sync_time"
    }

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val deviceId = DeviceIdentifier.getPersistentDeviceId(context.applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userEmail = AuthManager.getCurrentUser()?.email
            if (userEmail == null || !FirebaseServiceHelper.isFirebaseAvailable()) {
                OptimizedLogger.w(TAG, "User not authenticated or Firebase unavailable")
                return@withContext Result.success()
            }

            if (!HealthConnectHelper.isSdkAvailable(applicationContext)) {
                OptimizedLogger.w(TAG, "Health Connect SDK unavailable")
                return@withContext Result.success()
            }

            val grantedPermissions = HealthConnectHelper.getGrantedPermissions(applicationContext)
            if (grantedPermissions.isEmpty()) {
                OptimizedLogger.w(TAG, "Health Connect permissions not granted")
                return@withContext Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val previousSync = prefs.getLong(LAST_SYNC_KEY, 0L)
            val endTime = System.currentTimeMillis()
            val startTime = if (previousSync > 0L) previousSync else endTime - (24L * 60L * 60L * 1000L)
            val timeRange = TimeRangeFilter.between(
                Instant.ofEpochMilli(startTime),
                Instant.ofEpochMilli(endTime)
            )

            val client = HealthConnectClient.getOrCreate(applicationContext)
            val entities = mutableListOf<HealthVitalEntity>()

            if (grantedPermissions.contains(HealthConnectHelper.heartRatePermission)) {
                entities += readHeartRate(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.restingHeartRatePermission)) {
                entities += readRestingHeartRate(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.bloodPressurePermission)) {
                entities += readBloodPressure(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.oxygenSaturationPermission)) {
                entities += readOxygenSaturation(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.respiratoryRatePermission)) {
                entities += readRespiratoryRate(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.bodyTemperaturePermission)) {
                entities += readBodyTemperature(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.weightPermission)) {
                entities += readWeight(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.heightPermission)) {
                entities += readHeight(client, timeRange)
            }
            if (grantedPermissions.contains(HealthConnectHelper.stepsPermission)) {
                entities += readSteps(client, timeRange)
            }

            if (entities.isNotEmpty()) {
                db.healthVitalDao().insertAll(entities)
            }

            val pending = db.healthVitalDao().getNotUploaded()
            val uploadedIds = mutableListOf<Long>()
            pending.forEach { vital ->
                val payload = mapOf(
                    "entryId" to vital.entryId,
                    "recordType" to vital.recordType,
                    "metricName" to vital.metricName,
                    "metricValue" to vital.metricValue,
                    "unit" to vital.unit,
                    "recordedAt" to vital.recordedAt,
                    "sourcePackage" to (vital.sourcePackage ?: ""),
                    "deviceId" to vital.deviceId
                )
                if (FirebaseServiceHelper.uploadHealthVital(userEmail, deviceId, payload)) {
                    uploadedIds.add(vital.id)
                }
            }

            if (uploadedIds.isNotEmpty()) {
                db.healthVitalDao().markAsUploaded(uploadedIds, System.currentTimeMillis())
            }

            prefs.edit().putLong(LAST_SYNC_KEY, endTime).apply()
            OptimizedLogger.d(TAG, "Health vitals synced. Stored=${entities.size}, Uploaded=${uploadedIds.size}")
            Result.success()
        } catch (e: Exception) {
            OptimizedLogger.e(TAG, "Error syncing health vitals", e)
            Result.retry()
        }
    }

    private suspend fun readHeartRate(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.flatMap { record ->
            record.samples.mapIndexed { index, sample ->
                createVital(
                    entryId = "${record.metadata.id}_hr_${sample.time.toEpochMilli()}_$index",
                    recordType = "heart_rate",
                    metricName = "beats_per_minute",
                    metricValue = sample.beatsPerMinute.toDouble(),
                    unit = "bpm",
                    recordedAt = sample.time.toEpochMilli(),
                    sourcePackage = record.metadata.dataOrigin.packageName
                )
            }
        }
    }

    private suspend fun readRestingHeartRate(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.map { record ->
            createVital(
                entryId = "${record.metadata.id}_resting_hr",
                recordType = "resting_heart_rate",
                metricName = "beats_per_minute",
                metricValue = record.beatsPerMinute.toDouble(),
                unit = "bpm",
                recordedAt = record.time.toEpochMilli(),
                sourcePackage = record.metadata.dataOrigin.packageName
            )
        }
    }

    private suspend fun readBloodPressure(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.flatMap { record ->
            listOf(
                createVital(
                    entryId = "${record.metadata.id}_systolic",
                    recordType = "blood_pressure",
                    metricName = "systolic",
                    metricValue = record.systolic.inMillimetersOfMercury,
                    unit = "mmHg",
                    recordedAt = record.time.toEpochMilli(),
                    sourcePackage = record.metadata.dataOrigin.packageName
                ),
                createVital(
                    entryId = "${record.metadata.id}_diastolic",
                    recordType = "blood_pressure",
                    metricName = "diastolic",
                    metricValue = record.diastolic.inMillimetersOfMercury,
                    unit = "mmHg",
                    recordedAt = record.time.toEpochMilli(),
                    sourcePackage = record.metadata.dataOrigin.packageName
                )
            )
        }
    }

    private suspend fun readOxygenSaturation(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.map { record ->
            createVital(
                entryId = "${record.metadata.id}_spo2",
                recordType = "oxygen_saturation",
                metricName = "percentage",
                metricValue = record.percentage.value,
                unit = "percent",
                recordedAt = record.time.toEpochMilli(),
                sourcePackage = record.metadata.dataOrigin.packageName
            )
        }
    }

    private suspend fun readRespiratoryRate(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.map { record ->
            createVital(
                entryId = "${record.metadata.id}_resp_rate",
                recordType = "respiratory_rate",
                metricName = "breaths_per_minute",
                metricValue = record.rate,
                unit = "brpm",
                recordedAt = record.time.toEpochMilli(),
                sourcePackage = record.metadata.dataOrigin.packageName
            )
        }
    }

    private suspend fun readBodyTemperature(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = BodyTemperatureRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.map { record ->
            createVital(
                entryId = "${record.metadata.id}_body_temp",
                recordType = "body_temperature",
                metricName = "temperature",
                metricValue = record.temperature.inCelsius,
                unit = "celsius",
                recordedAt = record.time.toEpochMilli(),
                sourcePackage = record.metadata.dataOrigin.packageName
            )
        }
    }

    private suspend fun readWeight(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.map { record ->
            createVital(
                entryId = "${record.metadata.id}_weight",
                recordType = "weight",
                metricName = "body_weight",
                metricValue = record.weight.inKilograms,
                unit = "kg",
                recordedAt = record.time.toEpochMilli(),
                sourcePackage = record.metadata.dataOrigin.packageName
            )
        }
    }

    private suspend fun readHeight(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeightRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.map { record ->
            createVital(
                entryId = "${record.metadata.id}_height",
                recordType = "height",
                metricName = "body_height",
                metricValue = record.height.inMeters,
                unit = "meters",
                recordedAt = record.time.toEpochMilli(),
                sourcePackage = record.metadata.dataOrigin.packageName
            )
        }
    }

    private suspend fun readSteps(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HealthVitalEntity> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRange
            )
        )
        return response.records.map { record ->
            createVital(
                entryId = "${record.metadata.id}_steps",
                recordType = "steps",
                metricName = "count",
                metricValue = record.count.toDouble(),
                unit = "count",
                recordedAt = record.endTime.toEpochMilli(),
                sourcePackage = record.metadata.dataOrigin.packageName
            )
        }
    }

    private fun createVital(
        entryId: String,
        recordType: String,
        metricName: String,
        metricValue: Double,
        unit: String,
        recordedAt: Long,
        sourcePackage: String?
    ): HealthVitalEntity {
        return HealthVitalEntity(
            entryId = entryId,
            recordType = recordType,
            metricName = metricName,
            metricValue = metricValue,
            unit = unit,
            recordedAt = recordedAt,
            sourcePackage = sourcePackage,
            deviceId = deviceId
        )
    }
}
