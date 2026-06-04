package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GestureSettingsDao {
    @Query("SELECT * FROM gesture_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<GestureSettings?>

    @Query("SELECT * FROM gesture_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): GestureSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: GestureSettings)
}
