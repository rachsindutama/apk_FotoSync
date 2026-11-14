package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ExportLogEntity
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity

@Database(
    entities = [
        PhotoSessionEntity::class,
        PhotoItemEntity::class,
        ExportLogEntity::class
    ],
    version = 3,
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

        fun getInstance(context: Context): FotoSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FotoSyncDatabase::class.java,
                    "fotosync_studio.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
