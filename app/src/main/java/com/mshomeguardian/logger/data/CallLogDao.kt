package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogs(callLogs: List<CallLogEntity>): List<Long>

    @Update
    suspend fun updateCallLog(callLog: CallLogEntity)

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    suspend fun getAllCallLogs(): List<CallLogEntity>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogsAsFlow(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE uploadedToCloud = 0 ORDER BY timestamp DESC")
    suspend fun getNotUploadedCallLogs(): List<CallLogEntity>

    @Query("SELECT * FROM call_logs WHERE callId = :callId LIMIT 1")
    suspend fun getCallLogByCallId(callId: String): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getCallLogsByTimeRange(startTime: Long, endTime: Long): List<CallLogEntity>

    @Query("SELECT * FROM call_logs WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    suspend fun getCallLogsByPhoneNumber(phoneNumber: String): List<CallLogEntity>

    @Query("UPDATE call_logs SET deletedLocally = 1, syncTimestamp = :syncTime WHERE callId = :callId")
    suspend fun markCallLogAsDeletedLocally(callId: String, syncTime: Long)

    @Query("UPDATE call_logs SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE id = :id")
    suspend fun markCallLogAsUploaded(id: Long, uploadTime: Long)

    @Query("SELECT COUNT(*) FROM call_logs WHERE timestamp >= :since")
    suspend fun getCallLogsCountSince(since: Long): Int
}