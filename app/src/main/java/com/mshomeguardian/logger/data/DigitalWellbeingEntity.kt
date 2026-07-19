package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.TimeZone

@Entity(
    tableName = "digital_wellbeing",
    indices = [
        Index("snapshotId", unique = true),
        Index("intervalEnd"),
        Index(value = ["uploadedToCloud", "intervalEnd"], name = "index_digital_wellbeing_upload_interval")
    ]
)
data class DigitalWellbeingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snapshotId: String,
    val intervalStart: Long,
    val intervalEnd: Long,
    val totalScreenTimeMs: Long,
    val appLaunchCount: Int,
    val unlockCount: Int,
    val notificationInterruptions: Int,
    val uniqueAppsUsed: Int,
    val topAppPackage: String?,
    val topAppScreenTimeMs: Long,
    val timezone: String = TimeZone.getDefault().id,
    val deviceId: String,
    val uploadedToCloud: Boolean = false,
    val uploadTimestamp: Long? = null
)
