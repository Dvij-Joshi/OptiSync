package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GestureSettings
import com.example.data.OptiSyncDatabase
import com.example.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// Represents a position on screen
data class Point2D(val x: Float, val y: Float)

// Simulated target inside pointing game
data class PointTarget(val id: Int, val label: String, val point: Point2D, val color: Long, val isHit: Boolean = false)

class MainViewModel(
    private val repository: SettingsRepository,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // Flowing Settings
    val settingsState: StateFlow<GestureSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = GestureSettings()
        )

    // Interactive Screen Navigation
    private val _currentTab = MutableStateFlow(0) // 0: Dashboard/Playground, 1: Calibration, 2: Settings
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Real-time tracking positions normalized to 1000x1000
    private val _pointerPosition = MutableStateFlow(Point2D(500f, 500f))
    val pointerPosition: StateFlow<Point2D> = _pointerPosition.asStateFlow()

    // Smooth movement filtering state
    private var rawPointerX = 500f
    private var rawPointerY = 500f

    // Live feedback tracking
    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected.asStateFlow()

    // Current eye distance
    private val _currentEyeDistance = MutableStateFlow(0f)
    val currentEyeDistance: StateFlow<Float> = _currentEyeDistance.asStateFlow()

    // Live performance monitor stats
    private val _trackingLatencyMs = MutableStateFlow(0L)
    val trackingLatencyMs: StateFlow<Long> = _trackingLatencyMs.asStateFlow()

    private val _cameraFps = MutableStateFlow(0)
    val cameraFps: StateFlow<Int> = _cameraFps.asStateFlow()

    private val _processingFps = MutableStateFlow(0)
    val processingFps: StateFlow<Int> = _processingFps.asStateFlow()

    // Last successful gesture trigger message and animation state
    private val _lastGestureTriggered = MutableStateFlow<String?>(null)
    val lastGestureTriggered: StateFlow<String?> = _lastGestureTriggered.asStateFlow()

    // Target Deck Game simulator to make OptiSync fully interactive!
    private val _targets = MutableStateFlow<List<PointTarget>>(emptyList())
    val targets: StateFlow<List<PointTarget>> = _targets.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    // State of face preview landmarks for custom visualization
    private val _facePoints = MutableStateFlow<List<Point2D>>(emptyList())
    val facePoints: StateFlow<List<Point2D>> = _facePoints.asStateFlow()

    // Head-still indicator — true when head pose is within acceptable movement range
    private val _isHeadStill = MutableStateFlow(false)
    val isHeadStill: StateFlow<Boolean> = _isHeadStill.asStateFlow()

    // Live tracking probabilities for custom visualization and interactive calibration
    private val _liveLeftEyeOpen = MutableStateFlow<Float?>(null)
    val liveLeftEyeOpen: StateFlow<Float?> = _liveLeftEyeOpen.asStateFlow()

    private val _liveRightEyeOpen = MutableStateFlow<Float?>(null)
    val liveRightEyeOpen: StateFlow<Float?> = _liveRightEyeOpen.asStateFlow()

    private val _liveSmileProbability = MutableStateFlow<Float?>(null)
    val liveSmileProbability: StateFlow<Float?> = _liveSmileProbability.asStateFlow()

    private val _liveMouthOpenRatio = MutableStateFlow<Float?>(null)
    val liveMouthOpenRatio: StateFlow<Float?> = _liveMouthOpenRatio.asStateFlow()

    // Eyebrow tracking indicators
    private val _liveBrowHeightRatio = MutableStateFlow<Float?>(null)
    val liveBrowHeightRatio: StateFlow<Float?> = _liveBrowHeightRatio.asStateFlow()

    private val _liveBrowHorizontalRatio = MutableStateFlow<Float?>(null)
    val liveBrowHorizontalRatio: StateFlow<Float?> = _liveBrowHorizontalRatio.asStateFlow()

    private val _scrollSignal = MutableStateFlow<String?>(null)
    val scrollSignal: StateFlow<String?> = _scrollSignal.asStateFlow()

    private val _cameraPreviewUseCase = MutableStateFlow<androidx.camera.core.Preview?>(null)
    val cameraPreviewUseCase: StateFlow<androidx.camera.core.Preview?> = _cameraPreviewUseCase.asStateFlow()

    fun setCameraPreviewUseCase(preview: androidx.camera.core.Preview?) {
        _cameraPreviewUseCase.value = preview
    }

    fun triggerScroll(direction: String) {
        viewModelScope.launch {
            _scrollSignal.value = direction
            kotlinx.coroutines.delay(150)
            _scrollSignal.value = null
        }
    }

    // Flag utilized to demand the Camera Analyzer resetting its neutral reference point
    private val _recalibrateCenterRequested = MutableStateFlow(false)
    val recalibrateCenterRequested: StateFlow<Boolean> = _recalibrateCenterRequested.asStateFlow()

    fun requestCenterRecalibration() {
        _recalibrateCenterRequested.value = true
        neutralNoseX = null
        neutralNoseY = null
        rawPointerX = 500f
        rawPointerY = 500f
        _pointerPosition.value = Point2D(500f, 500f)
    }

    fun onRecalibrateCenterProcessed() {
        _recalibrateCenterRequested.value = false
    }

    // Calibration overlay flag — shows CalibrationScreen fullscreen inside MainActivity
    private val _showCalibrationOverlay = MutableStateFlow(false)
    val showCalibrationOverlay: StateFlow<Boolean> = _showCalibrationOverlay.asStateFlow()

    fun showCalibrationOverlay() { _showCalibrationOverlay.value = true }
    fun hideCalibrationOverlay() {
        _showCalibrationOverlay.value = false
        // Re-center after calibration so user starts from a fresh reference point
        requestCenterRecalibration()
    }

    // Neutral nose reference — set on first frame and on Recenter
    // Pointer moves relative to this position, so wherever you look when you tap Recenter = center
    private var neutralNoseX: Float? = null
    private var neutralNoseY: Float? = null

    // Step-by-Step Calibration Wizard:
    // 0: Neutral Comfort Center Focus
    // 1: Left Eye Wink Level Focus
    // 2: Right Eye Wink Level Focus
    // 3: Stylized Smiling Level Focus
    // 4: Mouth Openness Ratio Focus
    // 5: Eyebrow Raise (Scroll Up) Focus
    // 6: Eyebrow Squint / Furrow (Scroll Down) Focus
    // 7: Calibration Completion Scoreboard
    private val _calibrationStep = MutableStateFlow(0)
    val calibrationStep: StateFlow<Int> = _calibrationStep.asStateFlow()

    fun setCalibrationStep(step: Int) {
        _calibrationStep.value = step.coerceIn(0, 7)
    }

    fun advanceCalibration() {
        _calibrationStep.value = (_calibrationStep.value + 1).coerceAtMost(7)
    }

    fun previousCalibration() {
        _calibrationStep.value = (_calibrationStep.value - 1).coerceAtLeast(0)
    }

    fun resetCalibrationWizard() {
        _calibrationStep.value = 0
    }

    fun captureNeutralBaseline() {
        requestCenterRecalibration()
        val currentDist = _currentEyeDistance.value
        val currentBrowHeight = _liveBrowHeightRatio.value
        viewModelScope.launch {
            if (currentDist > 5f) {
                val current = repository.getSettings()
                // Personalized thresholds calculated from the restful baseline state
                val proposedRaise = if (currentBrowHeight != null) (currentBrowHeight + 0.05f).coerceIn(0.32f, 0.55f) else current.browRaiseThreshold
                val proposedSquint = if (currentBrowHeight != null) (currentBrowHeight - 0.05f).coerceIn(0.18f, 0.35f) else current.browSquintThreshold

                repository.saveSettings(
                    current.copy(
                        calibrationEyeDistance = currentDist,
                        browRaiseThreshold = proposedRaise,
                        browSquintThreshold = proposedSquint
                    )
                )
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Calibrated baseline depth and personalized eyebrow thresholds!"
            } else {
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Calibrated center baseline alignment!"
            }
        }
    }

    fun captureLeftWinkThreshold() {
        val leftOpen = _liveLeftEyeOpen.value
        if (leftOpen != null) {
            viewModelScope.launch {
                val current = repository.getSettings()
                val proposed = (leftOpen + 0.12f).coerceIn(0.12f, 0.45f)
                repository.saveSettings(current.copy(blinkThreshold = proposed, enableLeftEyeClick = true))
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Saved Left Blink Threshold: ${String.format("%.2f", proposed)}"
            }
        }
    }

    fun captureRightWinkThreshold() {
        val rightOpen = _liveRightEyeOpen.value
        if (rightOpen != null) {
            viewModelScope.launch {
                val current = repository.getSettings()
                val proposed = (rightOpen + 0.12f).coerceIn(0.12f, 0.45f)
                repository.saveSettings(current.copy(blinkThreshold = proposed, enableRightEyeClick = true))
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Saved Right Blink Threshold: ${String.format("%.2f", proposed)}"
            }
        }
    }

    fun captureSmileThreshold() {
        val smile = _liveSmileProbability.value
        if (smile != null) {
            viewModelScope.launch {
                val current = repository.getSettings()
                val proposed = (smile - 0.08f).coerceIn(0.25f, 0.85f)
                repository.saveSettings(current.copy(smileClickThreshold = proposed, enableSmileClick = true))
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Saved Smile Threshold: ${String.format("%.2f", proposed)}"
            }
        }
    }

    fun captureMouthOpenThreshold() {
        val ratio = _liveMouthOpenRatio.value
        if (ratio != null) {
            viewModelScope.launch {
                val current = repository.getSettings()
                val proposed = (ratio - 0.04f).coerceIn(0.22f, 0.65f)
                repository.saveSettings(current.copy(mouthOpenThreshold = proposed, enableMouthOpenAction = true))
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Saved Mouth Open Threshold: ${String.format("%.2f", proposed)}"
            }
        }
    }

    fun captureBrowRaiseThreshold() {
        val browHeight = _liveBrowHeightRatio.value
        if (browHeight != null) {
            viewModelScope.launch {
                val current = repository.getSettings()
                // Threshold is slightly BELOW peak so any value above it fires the gesture
                val proposed = (browHeight - 0.03f).coerceIn(0.25f, 0.65f)
                repository.saveSettings(current.copy(browRaiseThreshold = proposed, enableEyebrowScroll = true))
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Saved Eyebrow Raise Threshold: ${String.format("%.2f", proposed)}"
            }
        }
    }

    fun captureBrowSquintThreshold() {
        val browHeight = _liveBrowHeightRatio.value
        if (browHeight != null) {
            viewModelScope.launch {
                val current = repository.getSettings()
                // Threshold is slightly ABOVE squint peak so any value below it fires the gesture
                val proposed = (browHeight + 0.03f).coerceIn(0.12f, 0.38f)
                repository.saveSettings(current.copy(browSquintThreshold = proposed, enableEyebrowScroll = true))
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Saved Eyebrow Squint Threshold: ${String.format("%.2f", proposed)}"
            }
        }
    }

    /**
     * Full calibration save — computes all thresholds from captured rest + peak values
     * following the spec formula: threshold = rest + (peak - rest) × 0.65
     *
     * @param restBrow       Average brow height at neutral/rest
     * @param peakBrowUp     Average brow height while fully raising eyebrows
     * @param peakBrowDown   Average brow height while fully squinting/furrowing
     * @param peakBlinkR     Average right eye open probability while winking right (should be low)
     * @param peakBlinkL     Average left eye open probability while winking left (should be low)
     * @param refEyeDist     Eye distance captured at calibration distance
     */
    fun saveFullCalibration(
        restBrow: Float,
        peakBrowUp: Float,
        peakBrowDown: Float,
        peakBlinkR: Float,
        peakBlinkL: Float,
        refEyeDist: Float
    ) {
        viewModelScope.launch {
            val current = repository.getSettings()

            // Brow raise threshold: 65% of the way from rest to peak raise
            val browRaise = restBrow + (peakBrowUp - restBrow) * 0.65f

            // Brow squint threshold: 65% of the way from rest down to peak squint
            val browSquint = restBrow - (restBrow - peakBrowDown) * 0.65f

            // Blink threshold: midpoint between winking (peak = small value) and open (rest ≈ 0.9)
            // Average the two eyes' wink values, then set threshold halfway between wink and open
            val avgWink = (peakBlinkR + peakBlinkL) / 2f
            val blinkThresh = (avgWink + 0.90f) / 2f   // halfway between wink and fully open

            repository.saveSettings(
                current.copy(
                    calibrationEyeDistance = refEyeDist,
                    restBrowHeightRatio = restBrow,
                    peakBrowUpRatio = peakBrowUp,
                    peakBrowDownRatio = peakBrowDown,
                    browRaiseThreshold = browRaise.coerceIn(0.20f, 0.70f),
                    browSquintThreshold = browSquint.coerceIn(0.10f, 0.40f),
                    blinkThreshold = blinkThresh.coerceIn(0.10f, 0.60f),
                    enableEyebrowScroll = true,
                    calibrationVersion = current.calibrationVersion + 1
                )
            )
            requestCenterRecalibration()
            triggerHapticFeedback()
            _lastGestureTriggered.value = "✓ Full calibration saved!"
        }
    }

    // Time-based debouncer block for gesture triggers
    private var lastGestureTimeMs = 0L
    private val gestureCooldownMs = 1200L

    // Dedicated scroll debouncer
    private var lastScrollTimeMs = 0L
    private val scrollCooldownMs = 450L
    private var browGestureCandidate: String? = null
    private var browGestureStartMs = 0L
    private val browGestureHoldMs = 350L  // must hold eyebrow gesture for 350ms before it counts (stops flickers)

    // Mouth open hold-timer — requires sustained detection before triggering (prevents false positives)
    private var mouthOpenStartMs = 0L
    private val mouthOpenHoldMs = 220L  // must hold for 220ms

    init {
        resetGameTargets()
        // Save default settings if database is blank
        viewModelScope.launch {
            val dbSettings = repository.getSettings()
            if (dbSettings.id != 1) { // Fresh database, write default
                repository.saveSettings(GestureSettings())
            }
        }
    }

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    // Update settings in database
    fun updatePointerSensitivity(value: Float) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(pointerSensitivity = value))
        }
    }

    fun updateSmileClickThreshold(value: Float) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(smileClickThreshold = value))
        }
    }

    fun updateBlinkThreshold(value: Float) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(blinkThreshold = value))
        }
    }

    fun updateMouthOpenThreshold(value: Float) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(mouthOpenThreshold = value))
        }
    }

    fun toggleEyebrowScroll(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(enableEyebrowScroll = enabled))
        }
    }

    fun updateBrowRaiseThreshold(value: Float) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(browRaiseThreshold = value))
        }
    }

    fun updateBrowSquintThreshold(value: Float) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(browSquintThreshold = value))
        }
    }

    fun toggleLeftEyeClick(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(enableLeftEyeClick = enabled))
        }
    }

    fun toggleRightEyeClick(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(enableRightEyeClick = enabled))
        }
    }

    fun toggleSmileClick(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(enableSmileClick = enabled))
        }
    }

    fun toggleMouthOpen(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(enableMouthOpenAction = enabled))
        }
    }

    fun toggleHaptics(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(hapticFeedbackEnabled = enabled))
        }
    }

    fun updateFrameSkipCount(count: Int) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.saveSettings(current.copy(frameSkipCount = count))
        }
    }

    // Calibrate baseline positioning
    fun calibrateBaseline() {
        viewModelScope.launch {
            val currentDist = _currentEyeDistance.value
            if (currentDist > 5f) {
                val current = repository.getSettings()
                repository.saveSettings(current.copy(calibrationEyeDistance = currentDist))
                triggerHapticFeedback()
                _lastGestureTriggered.value = "Calibrated baseline eye distance: ${currentDist.toInt()}px"
            }
        }
    }

    // Feed camera tracking outputs
    fun updateTrackData(
        faceDetected: Boolean,
        noseX: Float?,
        noseY: Float?,
        eyeDist: Float,
        leftEyeOpen: Float?,
        rightEyeOpen: Float?,
        smileProb: Float?,
        mouthOpenRatio: Float?,
        browHeightRatio: Float?,
        browHorizontalRatio: Float?,
        isHeadStill: Boolean,
        latency: Long,
        procFps: Int,
        camFps: Int,
        landmarks: List<Point2D>
    ) {
        _isFaceDetected.value = faceDetected
        _isHeadStill.value = isHeadStill
        _trackingLatencyMs.value = latency
        _processingFps.value = procFps
        _cameraFps.value = camFps
        _facePoints.value = landmarks

        if (!faceDetected || noseX == null || noseY == null) {
            _currentEyeDistance.value = 0f
            _liveLeftEyeOpen.value = null
            _liveRightEyeOpen.value = null
            _liveSmileProbability.value = null
            _liveMouthOpenRatio.value = null
            _liveBrowHeightRatio.value = null
            _liveBrowHorizontalRatio.value = null
            mouthOpenStartMs = 0L
            browGestureCandidate = null
            browGestureStartMs = 0L
            return
        }

        _currentEyeDistance.value = eyeDist

        // Feed live variables for interactive calibration meters
        _liveLeftEyeOpen.value = leftEyeOpen
        _liveRightEyeOpen.value = rightEyeOpen
        _liveSmileProbability.value = smileProb
        _liveMouthOpenRatio.value = mouthOpenRatio
        _liveBrowHeightRatio.value = browHeightRatio
        _liveBrowHorizontalRatio.value = browHorizontalRatio

        // Read settings directly from StateFlow
        val settings = settingsState.value

        // Distance scaling: when far away (small eyeDistance), amplify movement so you can still reach corners
        val calEyeDist = if (settings.calibrationEyeDistance > 10f) settings.calibrationEyeDistance else 110f
        val distanceScale = if (eyeDist > 5f) calEyeDist / eyeDist else 1.0f

        // Set neutral reference on first frame (or after Recenter)
        if (neutralNoseX == null || neutralNoseY == null) {
            neutralNoseX = noseX
            neutralNoseY = noseY
        }

        // Delta from neutral: 0 when looking straight ahead, ±~0.2 at extremes
        val deltaX = noseX - neutralNoseX!!
        val deltaY = noseY - neutralNoseY!!

        // Non-linear amplification: precise at center, fast at edges
        val curvedX = deltaX * (1.0f + 3.0f * Math.abs(deltaX))
        val curvedY = deltaY * (1.0f + 3.0f * Math.abs(deltaY))

        // Scale to 0-1000 screen space. sensitivity=6 default * 2500 base = 15000 total.
        // A ±0.2 head turn maps to ±(0.2 * 15000/6) = ±500, filling the whole screen.
        val dynamicSensitivity = settings.pointerSensitivity * distanceScale
        val dX =  curvedX * dynamicSensitivity * 400f  // positive = right (getNormalizedPoint already mirrors front cam X)
        val dY =  curvedY * dynamicSensitivity * 400f

        val targetX = 500f + dX
        val targetY = 500f + dY

        // Exponential low-pass filter for smooth movement
        val alpha = 0.25f
        rawPointerX = rawPointerX * (1f - alpha) + targetX * alpha
        rawPointerY = rawPointerY * (1f - alpha) + targetY * alpha

        val clampedX = max(20f, min(980f, rawPointerX))
        val clampedY = max(20f, min(980f, rawPointerY))

        _pointerPosition.value = Point2D(clampedX, clampedY)

        // Evaluate gesture actions with debounces
        val now = System.currentTimeMillis()
        if (now - lastGestureTimeMs > gestureCooldownMs) {
            var gestureName: String? = null

            // noseY is normalized; around 0.08+ is already a meaningful downward drift.
            val isLookingDown = noseY > 0.08f

            // Only block winks when an eyebrow gesture is *confirmed* (held long enough), not on a fleeting flicker
            val isEyebrowGestureActive = settings.enableEyebrowScroll && isHeadStill &&
                    browGestureCandidate != null && browGestureStartMs > 0L &&
                    (System.currentTimeMillis() - browGestureStartMs) >= browGestureHoldMs

            // 1. Right Eye Wink Action (Click/Trigger) — suppressed when head is moving
            if (settings.enableRightEyeClick && rightEyeOpen != null && leftEyeOpen != null
                    && isHeadStill && !isLookingDown && !isEyebrowGestureActive) {
                if (rightEyeOpen < settings.blinkThreshold && leftEyeOpen > (settings.blinkThreshold + 0.35f)) {
                    gestureName = "Right Eye Wink (Click/Trigger)"
                }
            }

            // 2. Left Eye Wink Action (Click/Trigger) — suppressed when head is moving
            if (settings.enableLeftEyeClick && leftEyeOpen != null && rightEyeOpen != null
                    && isHeadStill && !isLookingDown && !isEyebrowGestureActive) {
                if (leftEyeOpen < settings.blinkThreshold && rightEyeOpen > (settings.blinkThreshold + 0.35f)) {
                    gestureName = "Left Eye Wink (Back/Option)"
                }
            }

            // 3. Smile Action (Main Select click)
            if (settings.enableSmileClick && smileProb != null && isHeadStill) {
                if (smileProb > settings.smileClickThreshold) {
                    gestureName = "Stylized Smile (Confirm/Select)"
                }
            }

            // 4. Mouth Open — requires still head + 220ms hold
            if (settings.enableMouthOpenAction && mouthOpenRatio != null && isHeadStill) {
                if (mouthOpenRatio > settings.mouthOpenThreshold) {
                    if (mouthOpenStartMs == 0L) mouthOpenStartMs = now
                    if (now - mouthOpenStartMs >= mouthOpenHoldMs) {
                        gestureName = "Mouth Opened (Home Menu)"
                        mouthOpenStartMs = 0L
                    }
                } else {
                    mouthOpenStartMs = 0L // reset hold timer if mouth closes
                }
            }

            if (gestureName != null) {
                lastGestureTimeMs = now
                _lastGestureTriggered.value = gestureName
                if (settings.hapticFeedbackEnabled) {
                    triggerHapticFeedback()
                }
                
                // Fire action in game simulation!
                handleSimulatedClick(Point2D(clampedX, clampedY))
            }
        }

        // Evaluate continuous eyebrow scrolling — gated on isHeadStill
        // Head tilts and nods no longer trigger scroll gestures
        if (settings.enableEyebrowScroll && isHeadStill && browHeightRatio != null) {
            val direction = when {
                browHeightRatio > settings.browRaiseThreshold -> "UP"
                browHeightRatio < settings.browSquintThreshold -> "DOWN"
                else -> null
            }

            if (direction == null) {
                browGestureCandidate = null
                browGestureStartMs = 0L
            } else {
                if (browGestureCandidate != direction) {
                    browGestureCandidate = direction
                    browGestureStartMs = now
                }

                if (now - browGestureStartMs >= browGestureHoldMs && now - lastScrollTimeMs > scrollCooldownMs) {
                    lastScrollTimeMs = now
                    _lastGestureTriggered.value = if (direction == "UP") {
                        "Raise Eyebrows (Scroll Up)"
                    } else {
                        "Squint Eyebrows (Scroll Down)"
                    }
                    triggerScroll(direction)
                    if (settings.hapticFeedbackEnabled) {
                        triggerHapticFeedback()
                    }
                }
            }
        } else {
            browGestureCandidate = null
            browGestureStartMs = 0L
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (e: Exception) {
            Log.e("OptiSync", "Haptic feedback error", e)
        }
    }

    // Reset simulator game targets
    fun resetGameTargets() {
        _score.value = 0
        _targets.value = listOf(
            PointTarget(1, "Menu", Point2D(150f, 150f), 0xFFE91E63),
            PointTarget(2, "Photos", Point2D(500f, 250f), 0xFF2196F3),
            PointTarget(3, "Settings", Point2D(850f, 150f), 0xFF4CAF50),
            PointTarget(4, "Messages", Point2D(200f, 500f), 0xFFFF9800),
            PointTarget(5, "OptiPlay", Point2D(800f, 500f), 0xFF9C27B0),
            PointTarget(6, "Browser", Point2D(300f, 800f), 0xFF00BCD4),
            PointTarget(7, "Home Deck", Point2D(700f, 800f), 0xFF009688)
        )
    }

    private fun handleSimulatedClick(pointer: Point2D) {
        val currentTargets = _targets.value.toMutableList()
        var hitIndex = -1
        
        // Detect if pointer is overlapping with any simulated tile (hitbox of 120 width/height)
        for (i in currentTargets.indices) {
            val target = currentTargets[i]
            val xDistance = Math.abs(pointer.x - target.point.x)
            val yDistance = Math.abs(pointer.y - target.point.y)
            if (xDistance < 90f && yDistance < 90f && !target.isHit) {
                hitIndex = i
                break
            }
        }

        if (hitIndex != -1) {
            val target = currentTargets[hitIndex]
            currentTargets[hitIndex] = target.copy(isHit = true)
            _targets.value = currentTargets
            _score.value = _score.value + 1
            
            // Auto respawn target or show custom trigger state
            viewModelScope.launch {
                kotlinx.coroutines.delay(1000)
                // Bring it back with a randomized position for premium gameplay look!
                val currentTargetsRefresh = _targets.value.toMutableList()
                val refreshedTarget = currentTargetsRefresh.firstOrNull { it.id == target.id }
                if (refreshedTarget != null) {
                    val idx = currentTargetsRefresh.indexOf(refreshedTarget)
                    val newX = (150..850).random().toFloat()
                    val newY = (150..850).random().toFloat()
                    currentTargetsRefresh[idx] = refreshedTarget.copy(
                        point = Point2D(newX, newY),
                        isHit = false
                    )
                    _targets.value = currentTargetsRefresh
                }
            }
        }
    }

    fun clearGestureNotification() {
        _lastGestureTriggered.value = null
    }

    class Factory(
        private val repository: SettingsRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
