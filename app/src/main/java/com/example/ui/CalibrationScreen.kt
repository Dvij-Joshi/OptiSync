package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GestureSettings
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Calibration step data model
// ─────────────────────────────────────────────────────────────────────────────
private data class CalibStep(
    val stepIndex: Int,       // 0-based
    val emoji: String,
    val title: String,
    val instruction: String,
    val hint: String
)

private val CALIB_STEPS = listOf(
    CalibStep(0, "😐", "Neutral Rest",
        "Sit upright at a comfortable distance (30–50 cm) and look straight at the screen with a relaxed, neutral expression.",
        "Keep your face still — this captures your baseline."),
    CalibStep(1, "🤨", "Raise Eyebrows",
        "Raise your eyebrows as high as you can and hold the position.",
        "Your forehead should wrinkle. Hold steady!"),
    CalibStep(2, "😠", "Furrow / Squint",
        "Squint or furrow your eyebrows downward tightly, like you're frustrated.",
        "Bring your brows down and together firmly."),
    CalibStep(3, "😉", "Wink Right Eye",
        "Wink your RIGHT eye — close it tightly while keeping your LEFT eye fully open.",
        "Only your right eyelid should move."),
    CalibStep(4, "🤔", "Wink Left Eye",
        "Wink your LEFT eye — close it tightly while keeping your RIGHT eye fully open.",
        "Only your left eyelid should move.")
)

private const val SAMPLES_NEEDED = 30

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CalibrationScreen(viewModel: MainViewModel) {
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()
    val eyeDistance    by viewModel.currentEyeDistance.collectAsState()
    val settings       by viewModel.settingsState.collectAsState(initial = GestureSettings())
    val facePoints     by viewModel.facePoints.collectAsState()

    val liveBrowHeight   by viewModel.liveBrowHeightRatio.collectAsState()
    val liveLeftEyeOpen  by viewModel.liveLeftEyeOpen.collectAsState()
    val liveRightEyeOpen by viewModel.liveRightEyeOpen.collectAsState()

    // ── Calibration state ──────────────────────────────────────────────────
    var currentStepIdx  by remember { mutableStateOf(0) }
    var sampleCount     by remember { mutableStateOf(0) }
    var isCapturing     by remember { mutableStateOf(false) }
    var isComplete      by remember { mutableStateOf(false) }

    // Accumulated averages per step
    var sumRest         by remember { mutableStateOf(0f) }
    var sumBrowUp       by remember { mutableStateOf(0f) }
    var sumBrowDown     by remember { mutableStateOf(0f) }
    var sumWinkR        by remember { mutableStateOf(0f) }  // right eye value while winking right
    var sumWinkL        by remember { mutableStateOf(0f) }  // left eye value while winking left
    var capturedEyeDist by remember { mutableStateOf(0f) }

    // ── Gesture validity per step ──────────────────────────────────────────
    // Returns true only when the user is correctly performing the required gesture
    fun isGestureValid(stepIdx: Int): Boolean {
        return when (stepIdx) {
            0 -> isFaceDetected && liveBrowHeight != null   // just look at camera
            1 -> {  // brow UP: ratio must exceed rest + 0.08
                val rest = if (sumRest > 0f && currentStepIdx > 0) sumRest / SAMPLES_NEEDED.coerceAtLeast(1) else (liveBrowHeight ?: 0f)
                isFaceDetected && liveBrowHeight != null && liveBrowHeight!! > (rest + 0.06f)
            }
            2 -> {  // brow DOWN: ratio must drop below rest - 0.06
                val rest = if (sumRest > 0f) sumRest / SAMPLES_NEEDED.coerceAtLeast(1) else (liveBrowHeight ?: 0.32f)
                isFaceDetected && liveBrowHeight != null && liveBrowHeight!! < (rest - 0.06f)
            }
            3 -> {  // WINK RIGHT: right eye closed < 0.25, left eye open > 0.65
                isFaceDetected && liveRightEyeOpen != null && liveLeftEyeOpen != null &&
                        liveRightEyeOpen!! < 0.25f && liveLeftEyeOpen!! > 0.65f
            }
            4 -> {  // WINK LEFT: left eye closed < 0.25, right eye open > 0.65
                isFaceDetected && liveLeftEyeOpen != null && liveRightEyeOpen != null &&
                        liveLeftEyeOpen!! < 0.25f && liveRightEyeOpen!! > 0.65f
            }
            else -> false
        }
    }

    // ── Live value displayed on the meter for current step ─────────────────
    fun currentLiveValue(stepIdx: Int): Float {
        return when (stepIdx) {
            0    -> liveBrowHeight ?: 0f
            1, 2 -> liveBrowHeight ?: 0f
            3    -> liveRightEyeOpen ?: 1f
            4    -> liveLeftEyeOpen ?: 1f
            else -> 0f
        }
    }

    fun currentLiveLabel(stepIdx: Int): String {
        return when (stepIdx) {
            0    -> "Brow ratio: %.2f".format(liveBrowHeight ?: 0f)
            1, 2 -> "Brow ratio: %.2f".format(liveBrowHeight ?: 0f)
            3    -> "Right eye: %.2f".format(liveRightEyeOpen ?: 1f)
            4    -> "Left eye:  %.2f".format(liveLeftEyeOpen ?: 1f)
            else -> ""
        }
    }

    // ── Auto-sampling effect ────────────────────────────────────────────────
    LaunchedEffect(isCapturing, isFaceDetected, liveBrowHeight, liveLeftEyeOpen, liveRightEyeOpen) {
        if (!isCapturing || sampleCount >= SAMPLES_NEEDED) return@LaunchedEffect
        if (!isGestureValid(currentStepIdx)) return@LaunchedEffect

        kotlinx.coroutines.delay(33L)  // ~30 fps pacing

        val browVal = liveBrowHeight ?: return@LaunchedEffect
        val leftVal = liveLeftEyeOpen ?: return@LaunchedEffect
        val rightVal = liveRightEyeOpen ?: return@LaunchedEffect

        when (currentStepIdx) {
            0 -> { sumRest     += browVal;  capturedEyeDist = eyeDistance }
            1 -> { sumBrowUp   += browVal }
            2 -> { sumBrowDown += browVal }
            3 -> { sumWinkR    += rightVal }
            4 -> { sumWinkL    += leftVal }
        }
        sampleCount++

        if (sampleCount >= SAMPLES_NEEDED) {
            isCapturing = false
            // Brief pause then auto-advance
            kotlinx.coroutines.delay(800L)
            if (currentStepIdx < CALIB_STEPS.lastIndex) {
                currentStepIdx++
                sampleCount = 0
                isCapturing = false
            } else {
                // All steps done — compute + save
                val avgRest   = sumRest     / SAMPLES_NEEDED
                val avgUp     = sumBrowUp   / SAMPLES_NEEDED
                val avgDown   = sumBrowDown / SAMPLES_NEEDED
                val avgWinkR  = sumWinkR    / SAMPLES_NEEDED
                val avgWinkL  = sumWinkL    / SAMPLES_NEEDED

                viewModel.saveFullCalibration(
                    restBrow   = avgRest,
                    peakBrowUp = avgUp,
                    peakBrowDown = avgDown,
                    peakBlinkR = avgWinkR,
                    peakBlinkL = avgWinkL,
                    refEyeDist = capturedEyeDist
                )
                isComplete = true
            }
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isComplete) {
            CalibCompleteScreen(settings = settings, onRestart = {
                currentStepIdx = 0; sampleCount = 0; isCapturing = false; isComplete = false
                sumRest = 0f; sumBrowUp = 0f; sumBrowDown = 0f; sumWinkR = 0f; sumWinkL = 0f
            })
            return@Column
        }

        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Face Calibration",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Step ${currentStepIdx + 1} of ${CALIB_STEPS.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                )
            }
            // Step dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CALIB_STEPS.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentStepIdx) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    i < currentStepIdx -> MaterialTheme.colorScheme.primary
                                    i == currentStepIdx -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(0.15f)
                                }
                            )
                    )
                }
            }
        }

        // ── Camera preview with mesh overlay ────────────────────────────
        CalibCameraPreview(
            viewModel = viewModel,
            isFaceDetected = isFaceDetected,
            facePoints = facePoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Step instruction card ────────────────────────────────────────
        val step = CALIB_STEPS[currentStepIdx]
        val gestureValid = isGestureValid(currentStepIdx)

        CalibStepCard(
            step = step,
            isCapturing = isCapturing,
            gestureValid = gestureValid,
            sampleCount = sampleCount,
            samplesNeeded = SAMPLES_NEEDED,
            liveLabel = currentLiveLabel(currentStepIdx),
            isFaceDetected = isFaceDetected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Status + Action button ───────────────────────────────────────
        val btnEnabled = isFaceDetected && !isCapturing

        AnimatedContent(
            targetState = isCapturing,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) { capturing ->
            if (capturing) {
                // Progress pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(0.12f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Collecting samples… ${sampleCount}/$SAMPLES_NEEDED",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(
                    onClick = { sampleCount = 0; isCapturing = true },
                    enabled = btnEnabled && gestureValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gestureValid) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(0.12f)
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !isFaceDetected -> "Waiting for face detection…"
                            !gestureValid   -> "Perform the gesture first"
                            else            -> "Start Capturing Samples"
                        },
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
            }
        }

        // Skip step / restart row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStepIdx > 0) {
                TextButton(onClick = {
                    currentStepIdx--; sampleCount = 0; isCapturing = false
                    // Undo last step's sum
                    when (currentStepIdx) {
                        0 -> sumRest = 0f
                        1 -> sumBrowUp = 0f
                        2 -> sumBrowDown = 0f
                        3 -> sumWinkR = 0f
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            TextButton(onClick = {
                currentStepIdx = 0; sampleCount = 0; isCapturing = false
                sumRest = 0f; sumBrowUp = 0f; sumBrowDown = 0f; sumWinkR = 0f; sumWinkL = 0f
            }) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restart", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera preview with face mesh overlay
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CalibCameraPreview(
    viewModel: MainViewModel,
    isFaceDetected: Boolean,
    facePoints: List<Point2D>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF050D1A))
            .border(1.dp, Color(0xFF00E5FF).copy(0.25f), RoundedCornerShape(20.dp))
    ) {
        // Live camera feed
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            update = { previewView ->
                viewModel.getCameraPreviewUseCase()?.setSurfaceProvider(previewView.surfaceProvider)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Mesh overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isFaceDetected && facePoints.isNotEmpty()) {
                val mapped = facePoints.map { pt ->
                    Offset((1f - pt.x) * size.width, pt.y * size.height)
                }

                // Connecting lines between nearby points
                for (i in mapped.indices) {
                    for (j in i + 1 until mapped.size) {
                        val dx = mapped[i].x - mapped[j].x
                        val dy = mapped[i].y - mapped[j].y
                        if (sqrt(dx * dx + dy * dy) < 40.dp.toPx()) {
                            drawLine(
                                color = Color(0xFF00E5FF).copy(0.35f),
                                start = mapped[i], end = mapped[j],
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
                // Points
                mapped.forEach { pt ->
                    drawCircle(Color.White, radius = 2.5.dp.toPx(), center = pt)
                    drawCircle(
                        Color(0xFF00E5FF).copy(0.45f), radius = 5.dp.toPx(), center = pt,
                        style = Stroke(1.dp.toPx())
                    )
                }
            } else {
                // No face: scanning reticle
                val cx = size.width / 2f; val cy = size.height / 2f
                drawCircle(Color(0xFFFF2D55).copy(0.15f), radius = 40.dp.toPx(), center = Offset(cx, cy))
                drawCircle(Color(0xFFFF2D55).copy(0.3f), radius = 40.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
                drawLine(Color(0xFFFF2D55).copy(0.4f), Offset(cx - 55.dp.toPx(), cy), Offset(cx + 55.dp.toPx(), cy), 1.2.dp.toPx())
                drawLine(Color(0xFFFF2D55).copy(0.4f), Offset(cx, cy - 55.dp.toPx()), Offset(cx, cy + 55.dp.toPx()), 1.2.dp.toPx())
            }
        }

        // Status chip
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(0.72f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape)
                        .background(if (isFaceDetected) Color(0xFF00E5FF) else Color(0xFFFF2D55))
                )
                Text(
                    text = if (isFaceDetected) "FACE LOCKED" else "SEARCHING…",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (isFaceDetected) Color(0xFF00E5FF) else Color(0xFFFF2D55)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step instruction card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CalibStepCard(
    step: CalibStep,
    isCapturing: Boolean,
    gestureValid: Boolean,
    sampleCount: Int,
    samplesNeeded: Int,
    liveLabel: String,
    isFaceDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = sampleCount.toFloat() / samplesNeeded.toFloat()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (gestureValid) MaterialTheme.colorScheme.primary.copy(0.5f)
            else MaterialTheme.colorScheme.onSurface.copy(0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emoji + title row + progress ring
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circular progress ring around emoji
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 4.dp.toPx()
                        val inset = stroke / 2f
                        drawArc(
                            color = Color.White.copy(0.08f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                        if (isCapturing || progress > 0f) {
                            drawArc(
                                color = if (gestureValid) Color(0xFF00E5FF) else Color(0xFFFFCC00),
                                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Text(text = step.emoji, fontSize = 30.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Gesture status badge
                    val (badgeColor, badgeText) = when {
                        !isFaceDetected -> Color(0xFFFF2D55) to "No face detected"
                        gestureValid    -> Color(0xFF00E5FF) to "✓ Gesture detected!"
                        else            -> Color(0xFFFFCC00) to "Perform the gesture"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(0.12f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(badgeColor))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = badgeColor
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.08f))

            // Instruction text
            Text(
                text = step.instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                lineHeight = 20.sp
            )

            // Hint
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
                Text(
                    text = step.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            // Live metric bar
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = liveLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (gestureValid) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    if (isCapturing) {
                        Text(
                            text = "$sampleCount / $samplesNeeded samples",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = if (gestureValid) Color(0xFF00E5FF) else Color(0xFFFFCC00),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Completion screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CalibCompleteScreen(
    settings: GestureSettings,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Success ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF00E5FF).copy(0.15f))
                drawCircle(Color(0xFF00E5FF), radius = size.width / 2f, style = Stroke(3.dp.toPx()))
            }
            Text("✓", fontSize = 44.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Black)
        }

        Text(
            "Calibration Complete!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            "Your personal gesture thresholds have been computed and saved. Head to Playground to test them!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "SAVED PROFILE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.08f))
                SummaryRow("Calibration #",     "${settings.calibrationVersion}")
                SummaryRow("Eye distance ref",  "${settings.calibrationEyeDistance.toInt()} px")
                SummaryRow("Rest brow ratio",   "%.3f".format(settings.restBrowHeightRatio))
                SummaryRow("Brow raise threshold", "%.3f".format(settings.browRaiseThreshold))
                SummaryRow("Brow squint threshold","%.3f".format(settings.browSquintThreshold))
                SummaryRow("Blink threshold",   "%.3f".format(settings.blinkThreshold))
            }
        }

        OutlinedButton(
            onClick = onRestart,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Re-Calibrate", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(name: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy helper composables kept for backward-compat (SettingsScreen uses these)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun WizardStepBody(
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info,
    title: String,
    desc: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), lineHeight = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
fun ConfigDisplayRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
