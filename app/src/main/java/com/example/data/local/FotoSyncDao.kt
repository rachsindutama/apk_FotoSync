package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ExportLogEntity
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FotoSyncDao {
    // --- Sessions ---
    @Query("SELECT * FROM photo_sessions ORDER BY sessionDate DESC, createdAt DESC")
    fun getAllSessions(): Flow<List<PhotoSessionEntity>>

    @Query("SELECT * FROM photo_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: String): Flow<PhotoSessionEntity?>

    @Query("SELECT * FROM photo_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionSync(sessionId: String): PhotoSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PhotoSessionEntity)

    @Update
    suspend fun updateSession(session: PhotoSessionEntity)

    @Query("DELETE FROM photo_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    // --- Photos ---
    @Query("SELECT * FROM photo_items WHERE sessionId = :sessionId ORDER BY fileName ASC")
    fun getPhotosForSession(sessionId: String): Flow<List<PhotoItemEntity>>

    @Query("SELECT * FROM photo_items WHERE sessionId = :sessionId ORDER BY fileName ASC")
    suspend fun getPhotosForSessionSync(sessionId: String): List<PhotoItemEntity>

    @Query("SELECT * FROM photo_items WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: String): PhotoItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoItemEntity)

    @Update
    suspend fun updatePhoto(photo: PhotoItemEntity)

    @Query("UPDATE photo_items SET isClientLiked = :isLiked WHERE id = :photoId")
    suspend fun updateClientLiked(photoId: String, isLiked: Boolean)

    @Query("UPDATE photo_items SET isPhotographerSelected = :isSelected WHERE id = :photoId")
    suspend fun updatePhotographerSelected(photoId: String, isSelected: Boolean)

    @Query("UPDATE photo_items SET rating = :rating WHERE id = :photoId")
    suspend fun updatePhotoRating(photoId: String, rating: Int)

    @Query("DELETE FROM photo_items WHERE id = :photoId")
    suspend fun deletePhoto(photoId: String)

    // --- Counters ---
    @Query("SELECT COUNT(*) FROM photo_items WHERE sessionId = :sessionId")
    fun getPhotoCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM photo_items WHERE sessionId = :sessionId AND isClientLiked = 1")
    fun getClientLikedCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM photo_items WHERE sessionId = :sessionId AND isPhotographerSelected = 1")
    fun getPhotographerSelectedCount(sessionId: String): Flow<Int>

    // --- Export Logs ---
    @Query("SELECT * FROM export_logs WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getExportLogs(sessionId: String): Flow<List<ExportLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportLog(log: ExportLogEntity)
}
