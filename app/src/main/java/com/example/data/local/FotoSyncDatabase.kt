package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ExportLogEntity
import com.example.data.model.GuestSelectionEntity
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity

@Database(
    entities = [
        PhotoSessionEntity::class,
        PhotoItemEntity::class,
        ExportLogEntity::class,
        GuestSelectionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FotoSyncDatabase : RoomDatabase() {
    abstract fun dao(): FotoSyncDao

    companion object {
        @Volatile
        private var INSTANCE: FotoSyncDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_sessions ADD COLUMN folderUri TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS guest_selections (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        photoId TEXT NOT NULL,
                        clientKey TEXT NOT NULL,
                        clientIp TEXT NOT NULL,
                        deviceLabel TEXT NOT NULL,
                        likedAt INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES photo_sessions(id) ON DELETE CASCADE,
                        FOREIGN KEY(photoId) REFERENCES photo_items(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_guest_selections_sessionId ON guest_selections(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_guest_selections_photoId ON guest_selections(photoId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_guest_selections_sessionId_photoId_clientKey ON guest_selections(sessionId, photoId, clientKey)")
            }
        }

        fun getInstance(context: Context): FotoSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FotoSyncDatabase::class.java,
                    "fotosync_studio.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
