package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NetworkUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(networkUsage: NetworkUsageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<NetworkUsageEntity>)

    @Query("SELECT * FROM network_usage WHERE uploadedToCloud = 0 LIMIT :limit")
    suspend fun getNotUploaded(limit: Int = 100): List<NetworkUsageEntity>

    @Query("UPDATE network_usage SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>, uploadTime: Long)
}
