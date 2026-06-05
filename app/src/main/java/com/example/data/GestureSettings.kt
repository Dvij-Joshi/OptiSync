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
    val browSquintThreshold: Float = 0.25f,

    // Full calibration flow — captured baseline + peak values
    val restBrowHeightRatio: Float = 0.32f,   // neutral resting brow-to-eye ratio
    val peakBrowUpRatio: Float = 0.48f,        // peak ratio when eyebrows fully raised
    val peakBrowDownRatio: Float = 0.20f,      // peak ratio when eyebrows fully furrowed
    val calibrationVersion: Int = 0            // increments each completed calibration
)
