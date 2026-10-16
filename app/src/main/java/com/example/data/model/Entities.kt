package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a photography session organized with YYYY-MM-DD folder convention.
 */
@Entity(tableName = "photo_sessions")
data class PhotoSessionEntity(
    @PrimaryKey val id: String,
    val folderName: String, // e.g. "2026-10-15_Sarah-Wedding_Ceremony"
    val sessionDate: String, // "YYYY-MM-DD"
    val clientName: String,
    val category: String, // "Wedding", "Graduation", "Studio", "Commercial", "Event"
    val clientNotes: String = "",
    val coverPhotoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

/**
 * Individual photo item within a session.
 */
@Entity(
    tableName = "photo_items",
    foreignKeys = [
        ForeignKey(
            entity = PhotoSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class PhotoItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val fileName: String, // e.g. "DSC_0412.JPG"
    val localUri: String, // local path, file uri, or drawable resource tag
    val fileSize: Long = 4500000L, // bytes
    val resolution: String = "6000 x 4000",
    val isClientLiked: Boolean = false,
    val isPhotographerSelected: Boolean = false,
    val rating: Int = 0, // 0 - 5 stars
    val capturedAt: Long = System.currentTimeMillis()
)

/**
 * Record of Google Drive cloud backup confirmations and exports.
 */
@Entity(
    tableName = "export_logs",
    foreignKeys = [
        ForeignKey(
            entity = PhotoSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ExportLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val cloudDestination: String = "Google Drive",
    val folderPath: String,
    val totalPhotosExported: Int,
    val totalBytes: Long,
    val filterType: String, // "ALL" or "CLIENT_LIKED_ONLY"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED" // "COMPLETED", "FAILED"
)
