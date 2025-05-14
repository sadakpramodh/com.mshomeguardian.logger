package com.mshomeguardian.logger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing information about audio recordings and their transcriptions
 */
@Entity(
    tableName = "audio_recordings",
    indices = [
        Index("recordingId", unique = true),
        Index("startTime"),
        Index("endTime")
    ]
)
data class AudioRecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Recording identification
    val recordingId: String,                   // Unique ID for the recording
    val filePath: String,                      // Path to the audio file on device
    val fileName: String,                      // Name of the audio file

    // Recording metadata
    val startTime: Long,                       // When the recording started
    val endTime: Long,                         // When the recording ended
    val duration: Long,                        // Duration in milliseconds
    val fileSize: Long,                        // Size of the recording file in bytes
    val lastUpdated: Long = System.currentTimeMillis(), // Last time this record was updated

    // Transcription status and data
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.PENDING,
    val transcription: String? = null,         // The text transcription of the audio
    val transcriptionLanguage: String = "te-IN", // Language code (Telugu-India)
    val transcriptionConfidence: Float? = null, // Confidence score of transcription
    val transcriptionTimestamp: Long? = null,  // When the transcription was done

    // Status flags
    val deletedLocally: Boolean = false,       // If the recording was deleted on device
    val uploadedToCloud: Boolean = false,      // If this recording was uploaded to Storage
    val uploadTimestamp: Long? = null,         // When this recording was uploaded

    // Device Info
    val deviceId: String                       // The persistent device ID
) {
    enum class TranscriptionStatus {
        PENDING,       // Not yet transcribed
        IN_PROGRESS,   // Currently being transcribed
        COMPLETED,     // Successfully transcribed
        FAILED,        // Transcription failed
        UNSUPPORTED    // Language or audio not supported for transcription
    }
}