package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_table",
    indices = [Index(value = ["timestamp"], name = "index_location_timestamp")]
)
data class LocationEntity(
    @PrimaryKey val timestamp: Long,
    val latitude: Double,
    val longitude: Double
)