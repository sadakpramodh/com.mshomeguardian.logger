package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthVitalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vitals: List<HealthVitalEntity>): List<Long>

    @Query("SELECT * FROM health_vitals WHERE uploadedToCloud = 0 ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun getNotUploaded(limit: Int = 250): List<HealthVitalEntity>

    @Query("UPDATE health_vitals SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>, uploadTime: Long)
}

