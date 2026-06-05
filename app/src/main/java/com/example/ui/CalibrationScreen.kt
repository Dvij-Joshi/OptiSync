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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GestureSettings
import kotlinx.coroutines.delay
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Step metadata
// ─────────────────────────────────────────────────────────────────────────────
private data class CalibStep(
    val emoji: String,
    val title: String,
    val instruction: String,
    val hint: String
)

private val CALIB_STEPS = listOf(
    CalibStep("😐", "Neutral Rest",
        "Sit 30–50 cm away, look directly at the screen with a completely relaxed, neutral face.",
        "Don't move your head — this captures your baseline brow position."),
    CalibStep("🤨", "Raise Eyebrows",
        "Raise both eyebrows as HIGH as you can, as if surprised. Hold the position steady.",
        "Your forehead should wrinkle. Hold it — we need 30 readings!"),
    CalibStep("😠", "Furrow / Squint",
        "Pull your eyebrows DOWN and TOGETHER tightly, like you're very frustrated or angry.",
        "Think 'scowl'. Your brows should come down noticeably from rest."),
    CalibStep("😉", "Wink RIGHT Eye",
        "Close ONLY your RIGHT eye tightly, while keeping your LEFT eye wide open.",
        "Keep your left eye fully open — asymmetry is what we're detecting."),
    CalibStep("🤔", "Wink LEFT Eye",
        "Close ONLY your LEFT eye tightly, while keeping your RIGHT eye wide open.",
        "Keep your right eye fully open — asymmetry is key.")
)

private const val SAMPLES_NEEDED = 30

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CalibrationScreen(viewModel: MainViewModel) {
    val isFaceDetected  by viewModel.isFaceDetected.collectAsState()
    val eyeDistance     by viewModel.currentEyeDistance.collectAsState()
    val settings        by viewModel.settingsState.collectAsState(initial = GestureSettings())
    val facePoints      by viewModel.facePoints.collectAsState()
    val isHeadStill     by viewModel.isHeadStill.collectAsState()

    val liveBrowHeight   by viewModel.liveBrowHeightRatio.collectAsState()
    val liveLeftEyeOpen  by viewModel.liveLeftEyeOpen.collectAsState()
    val liveRightEyeOpen by viewModel.liveRightEyeOpen.collectAsState()

    // ── Calibration local state ────────────────────────────────────────────
    var currentStepIdx  by remember { mutableStateOf(0) }
    var sampleCount     by remember { mutableStateOf(0) }
    var isCapturing     by remember { mutableStateOf(false) }
    var isComplete      by remember { mutableStateOf(false) }
    var autoStartCountdown by remember { mutableStateOf(3) }  // seconds

    // Accumulated sums per step (divided by SAMPLES_NEEDED at the end)
    var sumRest         by remember { mutableStateOf(0f) }
    var sumBrowUp       by remember { mutableStateOf(0f) }
    var sumBrowDown     by remember { mutableStateOf(0f) }
    var sumWinkR        by remember { mutableStateOf(0f) }
    var sumWinkL        by remember { mutableStateOf(0f) }
    var capturedEyeDist by remember { mutableStateOf(0f) }

    // ── Gesture validity per step ──────────────────────────────────────────
    // Compute rest avg from sumRest (only after step 0 is done)
    val restAvg = if (currentStepIdx > 0 && sumRest > 0f) sumRest / SAMPLES_NEEDED else (liveBrowHeight ?: 0.32f)

    fun isGestureValid(stepIdx: Int): Boolean {
        return when (stepIdx) {
            0 -> isFaceDetected  // any face is fine for REST
            1 -> isFaceDetected && (liveBrowHeight ?: 0f) > (restAvg + 0.05f)   // brows UP
            2 -> isFaceDetected && (liveBrowHeight ?: 1f) < (restAvg - 0.04f)   // brows DOWN
            3 -> isFaceDetected && (liveRightEyeOpen ?: 1f) < 0.28f && (liveLeftEyeOpen ?: 0f) > 0.60f
            4 -> isFaceDetected && (liveLeftEyeOpen ?: 1f) < 0.28f && (liveRightEyeOpen ?: 0f) > 0.60f
            else -> false
        }
    }

    fun liveLabel(stepIdx: Int): String = when (stepIdx) {
        0, 1, 2 -> "Brow ratio: %.3f".format(liveBrowHeight ?: 0f)
        3 -> "R: %.2f  L: %.2f".format(liveRightEyeOpen ?: 1f, liveLeftEyeOpen ?: 1f)
        4 -> "L: %.2f  R: %.2f".format(liveLeftEyeOpen ?: 1f, liveRightEyeOpen ?: 1f)
        else -> ""
    }

    // ── Auto-start countdown ────────────────────────────────────────────────
    // When face is detected and we're idle (not capturing, not complete),
    // count down 3 seconds then auto-start collecting. Resets if face lost.
    LaunchedEffect(isFaceDetected, currentStepIdx, isCapturing, isComplete) {
        if (!isFaceDetected || isCapturing || isComplete) {
            autoStartCountdown = 3
            return@LaunchedEffect
        }
        autoStartCountdown = 3
        repeat(3) {
            delay(1000L)
            autoStartCountdown--
        }
        // Auto-start once countdown finishes and conditions still hold
        if (isFaceDetected && !isCapturing && !isComplete) {
            sampleCount = 0
            isCapturing = true
        }
    }

    // ── Main sample collection loop ─────────────────────────────────────────
    // KEY FIX: Only restart when isCapturing or currentStepIdx changes.
    // Do NOT put live values (browHeight etc.) as keys — that causes the
    // coroutine to restart every frame, preventing samples from accumulating.
    LaunchedEffect(isCapturing, currentStepIdx) {
        if (!isCapturing) return@LaunchedEffect

        while (sampleCount < SAMPLES_NEEDED) {
            delay(50L)  // ~20 fps sampling rate

            // Skip frame if gesture is not currently valid
            if (!isGestureValid(currentStepIdx)) continue

            // Collect sample for current step
            val browVal  = liveBrowHeight   ?: continue
            val leftVal  = liveLeftEyeOpen  ?: continue
            val rightVal = liveRightEyeOpen ?: continue

            when (currentStepIdx) {
                0 -> { sumRest     += browVal;  capturedEyeDist = eyeDistance }
                1 ->   sumBrowUp   += browVal
                2 ->   sumBrowDown += browVal
                3 ->   sumWinkR    += rightVal
                4 ->   sumWinkL    += leftVal
            }
            sampleCount++
        }

        // All samples collected for this step
        isCapturing = false
        delay(900L)  // brief pause before advancing

        if (currentStepIdx < CALIB_STEPS.lastIndex) {
            currentStepIdx++
            sampleCount = 0
            // Auto-restart for next step happens via the countdown LaunchedEffect above
        } else {
            // Final step done — compute and save all thresholds
            val n = SAMPLES_NEEDED.toFloat()
            viewModel.saveFullCalibration(
                restBrow     = sumRest   / n,
                peakBrowUp   = sumBrowUp / n,
                peakBrowDown = sumBrowDown / n,
                peakBlinkR   = sumWinkR  / n,
                peakBlinkL   = sumWinkL  / n,
                refEyeDist   = capturedEyeDist
            )
            isComplete = true
        }
    }

    // ── UI ──────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isComplete) {
            CalibCompleteScreen(settings = settings) {
                currentStepIdx = 0; sampleCount = 0; isCapturing = false
                isComplete = false; autoStartCountdown = 3
                sumRest = 0f; sumBrowUp = 0f; sumBrowDown = 0f; sumWinkR = 0f; sumWinkL = 0f
            }
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
                    "Face Calibration",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Step ${currentStepIdx + 1} of ${CALIB_STEPS.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CALIB_STEPS.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentStepIdx) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    i < currentStepIdx  -> MaterialTheme.colorScheme.primary
                                    i == currentStepIdx -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(0.15f)
                                }
                            )
                    )
                }
            }
        }

        // ── Camera preview with mesh (aspect ratio matches camera: 3:4) ─
        // Using aspectRatio(3/4) ensures FILL_CENTER won't crop, so landmark
        // coordinates map correctly to canvas coordinates without offset math.
        CalibCameraPreview(
            viewModel = viewModel,
            isFaceDetected = isFaceDetected,
            isHeadStill = isHeadStill,
            facePoints = facePoints,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Step card ────────────────────────────────────────────────────
        val step = CALIB_STEPS[currentStepIdx]
        val gestureValid = isGestureValid(currentStepIdx)

        CalibStepCard(
            step = step,
            isCapturing = isCapturing,
            gestureValid = gestureValid,
            isHeadStill = isHeadStill,
            isFaceDetected = isFaceDetected,
            sampleCount = sampleCount,
            samplesNeeded = SAMPLES_NEEDED,
            liveLabel = liveLabel(currentStepIdx),
            autoStartCountdown = autoStartCountdown,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Manual override button (fallback if auto-start fails) ────────
        if (!isCapturing) {
            Button(
                onClick = { sampleCount = 0; isCapturing = true; autoStartCountdown = 0 },
                enabled = isFaceDetected && gestureValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (gestureValid) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(0.12f)
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when {
                        !isFaceDetected -> "Waiting for face…"
                        !gestureValid   -> "Perform the gesture to enable"
                        else            -> "Capture Now (auto in ${autoStartCountdown}s)"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
        }

        // Back / Restart row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStepIdx > 0 && !isCapturing) {
                TextButton(onClick = {
                    currentStepIdx--; sampleCount = 0; isCapturing = false
                    when (currentStepIdx) {
                        0 -> sumRest = 0f
                        1 -> sumBrowUp = 0f
                        2 -> sumBrowDown = 0f
                        3 -> sumWinkR = 0f
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            if (!isCapturing) {
                TextButton(onClick = {
                    currentStepIdx = 0; sampleCount = 0; isCapturing = false; autoStartCountdown = 3
                    sumRest = 0f; sumBrowUp = 0f; sumBrowDown = 0f; sumWinkR = 0f; sumWinkL = 0f
                }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restart", fontWeight = FontWeight.SemiBold)
                }
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
    isHeadStill: Boolean,
    facePoints: List<Point2D>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF050D1A))
            .border(1.dp, Color(0xFF00E5FF).copy(if (isFaceDetected && isHeadStill) 0.5f else 0.15f), RoundedCornerShape(20.dp))
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            update = { pv -> viewModel.getCameraPreviewUseCase()?.setSurfaceProvider(pv.surfaceProvider) },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isFaceDetected && facePoints.isNotEmpty()) {
                val mapped = facePoints.map { pt ->
                    Offset((1f - pt.x) * size.width, pt.y * size.height)
                }
                // Connecting lines
                for (i in mapped.indices) {
                    for (j in i + 1 until mapped.size) {
                        val dx = mapped[i].x - mapped[j].x
                        val dy = mapped[i].y - mapped[j].y
                        if (sqrt(dx * dx + dy * dy) < 40.dp.toPx()) {
                            drawLine(
                                color = Color(0xFF00E5FF).copy(if (isHeadStill) 0.4f else 0.15f),
                                start = mapped[i], end = mapped[j], strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
                // Dots
                mapped.forEach { pt ->
                    drawCircle(Color.White, radius = 2.5.dp.toPx(), center = pt)
                    drawCircle(
                        Color(0xFF00E5FF).copy(if (isHeadStill) 0.5f else 0.2f),
                        radius = 5.dp.toPx(), center = pt, style = Stroke(1.dp.toPx())
                    )
                }
            } else {
                val cx = size.width / 2f; val cy = size.height / 2f
                drawCircle(Color(0xFFFF2D55).copy(0.12f), radius = 40.dp.toPx(), center = Offset(cx, cy))
                drawCircle(Color(0xFFFF2D55).copy(0.3f), radius = 40.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
                drawLine(Color(0xFFFF2D55).copy(0.4f), Offset(cx - 55.dp.toPx(), cy), Offset(cx + 55.dp.toPx(), cy), 1.2.dp.toPx())
                drawLine(Color(0xFFFF2D55).copy(0.4f), Offset(cx, cy - 55.dp.toPx()), Offset(cx, cy + 55.dp.toPx()), 1.2.dp.toPx())
            }
        }

        // Status chips
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusChip(
                label = if (isFaceDetected) "FACE LOCKED" else "SEARCHING…",
                color = if (isFaceDetected) Color(0xFF00E5FF) else Color(0xFFFF2D55)
            )
            if (isFaceDetected) {
                StatusChip(
                    label = if (isHeadStill) "HEAD STILL" else "MOVE SLOWLY",
                    color = if (isHeadStill) Color(0xFF00FF8C) else Color(0xFFFFCC00)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(0.72f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = color)
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
    isHeadStill: Boolean,
    isFaceDetected: Boolean,
    sampleCount: Int,
    samplesNeeded: Int,
    liveLabel: String,
    autoStartCountdown: Int,
    modifier: Modifier = Modifier
) {
    val progress = (sampleCount.toFloat() / samplesNeeded.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.45f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (gestureValid && isHeadStill) MaterialTheme.colorScheme.primary.copy(0.5f)
            else MaterialTheme.colorScheme.onSurface.copy(0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Emoji ring + title + badge row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        if (progress > 0f) {
                            drawArc(
                                color = Color(0xFF00E5FF),
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
                    Text(step.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    val (badgeColor, badgeText) = when {
                        !isFaceDetected     -> Color(0xFFFF2D55) to "No face detected"
                        isCapturing         -> Color(0xFF00E5FF) to "Collecting… $sampleCount/$samplesNeeded"
                        !isHeadStill        -> Color(0xFFFFCC00) to "Hold your head still"
                        gestureValid        -> Color(0xFF00FF8C) to "✓ Gesture detected — auto-starting in ${autoStartCountdown}s"
                        else                -> Color(0xFFFFCC00) to "Perform the gesture above"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor.copy(0.12f)).padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 1.5.dp, color = badgeColor)
                        } else {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(badgeColor))
                        }
                        Text(badgeText, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = badgeColor))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.08f))

            Text(step.instruction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.85f), lineHeight = 20.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                Text(step.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        liveLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (gestureValid && isHeadStill) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    if (isCapturing) {
                        Text("$sampleCount / $samplesNeeded", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = if (gestureValid && isHeadStill) Color(0xFF00E5FF) else Color(0xFFFFCC00),
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
private fun CalibCompleteScreen(settings: GestureSettings, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF00E5FF).copy(0.15f))
                drawCircle(Color(0xFF00E5FF), radius = size.width / 2f, style = Stroke(3.dp.toPx()))
            }
            Text("✓", fontSize = 44.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Black)
        }
        Text("Calibration Complete!", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("Your personal gesture thresholds have been computed and saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(0.7f), textAlign = TextAlign.Center)

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SAVED PROFILE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.08f))
                SummaryRow("Calibration #",          "${settings.calibrationVersion}")
                SummaryRow("Eye distance ref",        "${settings.calibrationEyeDistance.toInt()} px")
                SummaryRow("Rest brow ratio",         "%.3f".format(settings.restBrowHeightRatio))
                SummaryRow("Brow raise threshold",    "%.3f".format(settings.browRaiseThreshold))
                SummaryRow("Brow squint threshold",   "%.3f".format(settings.browSquintThreshold))
                SummaryRow("Blink threshold",         "%.3f".format(settings.blinkThreshold))
            }
        }
        OutlinedButton(onClick = onRestart, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Re-Calibrate", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

// Legacy stubs for backward compat (referenced by SettingsScreen)
@Composable
fun WizardStepBody(
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Info,
    title: String, desc: String, content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
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
