package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>): List<Long>

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM message_logs ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("SELECT * FROM message_logs ORDER BY timestamp DESC")
    fun getAllMessagesAsFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message_logs WHERE uploadedToCloud = 0 ORDER BY timestamp DESC")
    suspend fun getNotUploadedMessages(): List<MessageEntity>

    @Query("SELECT * FROM message_logs WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageByMessageId(messageId: String): MessageEntity?

    @Query("SELECT * FROM message_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getMessagesByTimeRange(startTime: Long, endTime: Long): List<MessageEntity>

    @Query("SELECT * FROM message_logs WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    suspend fun getMessagesByPhoneNumber(phoneNumber: String): List<MessageEntity>

    @Query("UPDATE message_logs SET deletedLocally = 1, syncTimestamp = :syncTime WHERE messageId = :messageId")
    suspend fun markMessageAsDeletedLocally(messageId: String, syncTime: Long)

    @Query("UPDATE message_logs SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE id = :id")
    suspend fun markMessageAsUploaded(id: Long, uploadTime: Long)

    @Query("SELECT COUNT(*) FROM message_logs WHERE timestamp >= :since")
    suspend fun getMessagesCountSince(since: Long): Int
}