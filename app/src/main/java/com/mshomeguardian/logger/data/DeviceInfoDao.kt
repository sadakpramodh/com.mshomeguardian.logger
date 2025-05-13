package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface DeviceInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceInfo(deviceInfo: DeviceInfoEntity): Long

    @Update
    suspend fun updateDeviceInfo(deviceInfo: DeviceInfoEntity)

    @Query("SELECT * FROM device_info WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceInfo(deviceId: String): DeviceInfoEntity?

    @Query("SELECT COUNT(*) FROM device_info")
    suspend fun getDeviceCount(): Int

    @Query("UPDATE device_info SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE deviceId = :deviceId")
    suspend fun markDeviceInfoAsUploaded(deviceId: String, uploadTime: Long)

    @Query("UPDATE device_info SET isActive = 0, lastUpdated = :lastUpdated WHERE deviceId = :deviceId")
    suspend fun markDeviceAsInactive(deviceId: String, lastUpdated: Long)

    @Query("UPDATE device_info SET lastUpdated = :lastUpdated WHERE deviceId = :deviceId")
    suspend fun updateDeviceLastUpdated(deviceId: String, lastUpdated: Long)
}