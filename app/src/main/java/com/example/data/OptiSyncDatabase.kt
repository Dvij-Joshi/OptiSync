package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GestureSettings::class], version = 2, exportSchema = false)
abstract class OptiSyncDatabase : RoomDatabase() {
    abstract fun gestureSettingsDao(): GestureSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: OptiSyncDatabase? = null

        fun getDatabase(context: Context): OptiSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OptiSyncDatabase::class.java,
                    "optisync_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
