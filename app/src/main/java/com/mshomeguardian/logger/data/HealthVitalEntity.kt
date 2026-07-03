package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_vitals",
    indices = [
        Index("entryId", unique = true),
        Index("recordedAt"),
        Index(value = ["uploadedToCloud", "recordedAt"], name = "index_health_vitals_upload_recorded")
    ]
)
data class HealthVitalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    val recordType: String,
    val metricName: String,
    val metricValue: Double,
    val unit: String,
    val recordedAt: Long,
    val sourcePackage: String?,
    val deviceId: String,
    val uploadedToCloud: Boolean = false,
    val uploadTimestamp: Long? = null
)

