package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SensorDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sensorData: SensorDataEntity): Long

    @Query("SELECT * FROM sensor_data WHERE uploadedToCloud = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getNotUploaded(limit: Int = 100): List<SensorDataEntity>

    @Query("UPDATE sensor_data SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>, uploadTime: Long)

    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<SensorDataEntity>
}
