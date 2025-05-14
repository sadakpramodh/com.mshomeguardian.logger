package com.mshomeguardian.logger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioRecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: AudioRecordingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<AudioRecordingEntity>): List<Long>

    @Update
    suspend fun updateRecording(recording: AudioRecordingEntity)

    @Query("SELECT * FROM audio_recordings ORDER BY startTime DESC")
    suspend fun getAllRecordings(): List<AudioRecordingEntity>

    @Query("SELECT * FROM audio_recordings ORDER BY startTime DESC")
    fun getAllRecordingsAsFlow(): Flow<List<AudioRecordingEntity>>

    @Query("SELECT * FROM audio_recordings WHERE uploadedToCloud = 0 ORDER BY startTime DESC")
    suspend fun getNotUploadedRecordings(): List<AudioRecordingEntity>

    @Query("SELECT * FROM audio_recordings WHERE transcriptionStatus = 'PENDING' ORDER BY startTime DESC")
    suspend fun getPendingTranscriptionRecordings(): List<AudioRecordingEntity>

    @Query("SELECT * FROM audio_recordings WHERE recordingId = :recordingId LIMIT 1")
    suspend fun getRecordingById(recordingId: String): AudioRecordingEntity?

    @Query("SELECT * FROM audio_recordings WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY startTime DESC")
    suspend fun getRecordingsByTimeRange(startTime: Long, endTime: Long): List<AudioRecordingEntity>

    @Query("SELECT * FROM audio_recordings WHERE transcription LIKE '%' || :searchTerm || '%' ORDER BY startTime DESC")
    suspend fun searchRecordingsByTranscription(searchTerm: String): List<AudioRecordingEntity>

    @Query("UPDATE audio_recordings SET uploadedToCloud = 1, uploadTimestamp = :uploadTime WHERE recordingId = :recordingId")
    suspend fun markRecordingAsUploaded(recordingId: String, uploadTime: Long)

    @Query("UPDATE audio_recordings SET transcriptionStatus = :status, transcription = :transcription, transcriptionTimestamp = :timestamp WHERE recordingId = :recordingId")
    suspend fun updateTranscription(recordingId: String, transcription: String, status: String, timestamp: Long)

    @Query("UPDATE audio_recordings SET deletedLocally = 1, lastUpdated = :lastUpdatedTime WHERE recordingId = :recordingId")
    suspend fun markRecordingAsDeletedLocally(recordingId: String, lastUpdatedTime: Long)

    @Query("DELETE FROM audio_recordings WHERE startTime < :timestamp AND uploadedToCloud = 1")
    suspend fun deleteOldUploadedRecordings(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM audio_recordings")
    suspend fun getRecordingsCount(): Int

    @Query("SELECT SUM(fileSize) FROM audio_recordings WHERE deletedLocally = 0")
    suspend fun getTotalRecordingsSize(): Long?
}