package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ExportLogEntity
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity

@Database(
    entities = [
        PhotoSessionEntity::class,
        PhotoItemEntity::class,
        ExportLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FotoSyncDatabase : RoomDatabase() {
    abstract fun dao(): FotoSyncDao

    companion object {
        @Volatile
        private var INSTANCE: FotoSyncDatabase? = null

        fun getInstance(context: Context): FotoSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FotoSyncDatabase::class.java,
                    "fotosync_studio.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
