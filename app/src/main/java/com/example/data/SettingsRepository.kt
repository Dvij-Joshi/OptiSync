package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val gestureSettingsDao: GestureSettingsDao) {
    
    // Expose settings flow. If database is empty, we return a default object.
    val settingsFlow: Flow<GestureSettings> = gestureSettingsDao.getSettingsFlow()
        .map { it ?: GestureSettings() }

    suspend fun getSettings(): GestureSettings {
        return gestureSettingsDao.getSettings() ?: GestureSettings()
    }

    suspend fun saveSettings(settings: GestureSettings) {
        gestureSettingsDao.updateSettings(settings)
    }
}
