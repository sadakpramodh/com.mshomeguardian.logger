package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_table")
data class LocationEntity(
    @PrimaryKey val timestamp: Long,
    val latitude: Double,
    val longitude: Double
)