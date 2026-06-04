package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gesture_settings")
data class GestureSettings(
    @PrimaryKey val id: Int = 1,
    val pointerSensitivity: Float = 6.0f,
    val smileClickThreshold: Float = 0.65f,
    val blinkThreshold: Float = 0.20f,
    val calibrationEyeDistance: Float = 100.0f, // Store eye distance calibrated at optimal positioning
    val enableLeftEyeClick: Boolean = true,
    val enableRightEyeClick: Boolean = true,
    val enableSmileClick: Boolean = true,
    val enableMouthOpenAction: Boolean = false,
    val mouthOpenThreshold: Float = 0.35f,
    val hapticFeedbackEnabled: Boolean = true,
    val lowLatencyModeEnabled: Boolean = true,
    val frameSkipCount: Int = 0, // Processing every frame by default for near-instant latency
    
    // Eyebrow scrolling configurations
    val enableEyebrowScroll: Boolean = true,
    val browRaiseThreshold: Float = 0.38f,
    val browSquintThreshold: Float = 0.25f
)
