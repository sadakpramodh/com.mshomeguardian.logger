package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.TimeZone

@Entity(
    tableName = "battery_status",
    indices = [
        Index("timestamp"),
        Index("uploadedToCloud")
    ]
)
data class BatteryStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: Int,
    val isCharging: Boolean,
    val chargingSource: String,
    val health: Int,
    val temperature: Int,
    val voltage: Int,
    val present: Boolean,
    val capacityPercent: Int,
    val chargeCounter: Int,
    val currentNow: Int,
    val powerSaveMode: Boolean,
    val timestamp: Long,
    val timezone: String = TimeZone.getDefault().id,
    val deviceId: String,
    val uploadedToCloud: Boolean = false,
    val uploadTimestamp: Long? = null
)
