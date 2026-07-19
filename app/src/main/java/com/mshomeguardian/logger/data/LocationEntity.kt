package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.TimeZone

@Entity(
    tableName = "location_table",
    indices = [
        Index(value = ["timestamp"], name = "index_location_timestamp"),
        Index(value = ["uploadedToCloud"], name = "index_location_uploaded")
    ]
)
data class LocationEntity(
    @PrimaryKey val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = -1f,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val provider: String = "unknown",
    val timezone: String = TimeZone.getDefault().id,
    val uploadedToCloud: Boolean = false,
    val uploadTimestamp: Long? = null
)