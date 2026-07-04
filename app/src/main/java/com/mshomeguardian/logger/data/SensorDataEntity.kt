package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_data",
    indices = [
        Index("timestamp"),
        Index("uploadedToCloud")
    ]
)
data class SensorDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val deviceId: String,
    // Accelerometer (m/s²)
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    // Gyroscope (rad/s)
    val gyroX: Float? = null,
    val gyroY: Float? = null,
    val gyroZ: Float? = null,
    // Magnetic field (µT)
    val magX: Float? = null,
    val magY: Float? = null,
    val magZ: Float? = null,
    // Gravity (m/s²)
    val gravityX: Float? = null,
    val gravityY: Float? = null,
    val gravityZ: Float? = null,
    // Linear acceleration (m/s²)
    val linearAccelX: Float? = null,
    val linearAccelY: Float? = null,
    val linearAccelZ: Float? = null,
    // Rotation vector
    val rotX: Float? = null,
    val rotY: Float? = null,
    val rotZ: Float? = null,
    val rotScalar: Float? = null,
    // Environmental
    val pressure: Float? = null,        // hPa
    val humidity: Float? = null,        // %
    val ambientTemperature: Float? = null, // °C
    val light: Float? = null,           // lux
    val proximity: Float? = null,       // cm
    // Activity
    val steps: Float? = null,
    val stepDetected: Float? = null,
    val heartRate: Float? = null,       // bpm
    val uploadedToCloud: Boolean = false,
    val uploadTimestamp: Long? = null
)
