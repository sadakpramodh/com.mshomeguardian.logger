package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BatteryStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(battery: BatteryStatusEntity): Long

    @Query("SELECT * FROM battery_status WHERE uploadedToCloud = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getNotUploaded(limit: Int = 100): List<BatteryStatusEntity>

    @Query("UPDATE battery_status SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>, uploadTime: Long)

    @Query("SELECT * FROM battery_status ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<BatteryStatusEntity>
}
