package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "network_usage",
    indices = [Index("uploadedToCloud")]
)
data class NetworkUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val rxBytes: Long,
    val txBytes: Long,
    val timestamp: Long,
    val deviceId: String,
    val uploadedToCloud: Boolean = false,
    val uploadTimestamp: Long? = null
)
