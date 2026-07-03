package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DigitalWellbeingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snapshot: DigitalWellbeingEntity): Long

    @Query("SELECT * FROM digital_wellbeing WHERE uploadedToCloud = 0 ORDER BY intervalEnd DESC LIMIT :limit")
    suspend fun getNotUploaded(limit: Int = 50): List<DigitalWellbeingEntity>

    @Query("UPDATE digital_wellbeing SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>, uploadTime: Long)
}

