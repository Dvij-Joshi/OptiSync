package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GestureSettings
import kotlin.math.sqrt

@Composable
fun CalibrationScreen(viewModel: MainViewModel) {
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()
    val eyeDistance by viewModel.currentEyeDistance.collectAsState()
    val settings by viewModel.settingsState.collectAsState(initial = GestureSettings())
    val facePoints by viewModel.facePoints.collectAsState()

    // Wizard step state: 0=Center, 1=Left Wink, 2=Right Wink, 3=Smile, 4=Mouth Open, 5=Complete
    val currentStep by viewModel.calibrationStep.collectAsState()

    // Live variables for the metrics
    val liveLeftEyeOpen by viewModel.liveLeftEyeOpen.collectAsState()
    val liveRightEyeOpen by viewModel.liveRightEyeOpen.collectAsState()
    val liveSmileProb by viewModel.liveSmileProbability.collectAsState()
    val liveMouthOpenRatio by viewModel.liveMouthOpenRatio.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 56.dp)
    ) {
        // App Header Title
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Intuitive Calibrator",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 25.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Perform actions to customize threshold limits for your face",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // High-Tech Cyber-Scan Facial Network Overlay
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF030914))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "LIVE CYBER-MESH DATA SCANNER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF070F1E))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    ) {
                        // Render physical front camera video stream feed behind the neon fiber network!
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { context ->
                                androidx.camera.view.PreviewView(context).apply {
                                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                                }
                            },
                            update = { previewView ->
                                viewModel.getCameraPreviewUseCase()?.setSurfaceProvider(previewView.surfaceProvider)
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Render subtle cyber line matrix grids
                            val columnsCount = 10
                            val colsSpacing = size.width / columnsCount
                            for (c in 0..columnsCount) {
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.06f),
                                    start = Offset(c * colsSpacing, 0f),
                                    end = Offset(c * colsSpacing, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            val rowsCount = 8
                            val rowsSpacing = size.height / rowsCount
                            for (r in 0..rowsCount) {
                                drawLine(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.06f),
                                    start = Offset(0f, r * rowsSpacing),
                                    end = Offset(size.width, r * rowsSpacing),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // If face is found, plot triangulated fiber mesh and glowing points
                            if (isFaceDetected && facePoints.isNotEmpty()) {
                                // Convert normalized landmarks bounds (usually 0 to 1) to canvas aspect ratio
                                val mappedPoints = facePoints.map { pt ->
                                    // Invert horizontal mapping since front camera is self-mirrored
                                    val x = (1f - pt.x) * size.width
                                    val y = pt.y * size.height
                                    Offset(x, y)
                                }

                                // 1. Draw web-like triangulated face network using distance-based nearest-neighbors
                                for (i in mappedPoints.indices) {
                                    for (j in i + 1 until mappedPoints.size) {
                                        val p1 = mappedPoints[i]
                                        val p2 = mappedPoints[j]
                                        val dx = p1.x - p2.x
                                        val dy = p1.y - p2.y
                                        val dist = sqrt(dx * dx + dy * dy)
                                        // Connect adjacent facial segments dynamically
                                        if (dist < 42.dp.toPx()) {
                                            drawLine(
                                                color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                                                start = p1,
                                                end = p2,
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    }
                                }

                                // 2. Draw outer core nodes
                                mappedPoints.forEach { pt ->
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                        radius = 4.5f.dp.toPx(),
                                        center = pt,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f.dp.toPx())
                                    )
                                }
                            } else {
                                // Draw locking scanning system overlay
                                val center = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(
                                    color = Color(0xFFFF2D55).copy(alpha = 0.12f),
                                    radius = 35.dp.toPx(),
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFFF2D55).copy(alpha = 0.25f),
                                    radius = 45.dp.toPx(),
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                                drawLine(
                                    color = Color(0xFFFF2D55).copy(alpha = 0.35f),
                                    start = Offset(center.x - 50.dp.toPx(), center.y),
                                    end = Offset(center.x + 50.dp.toPx(), center.y),
                                    strokeWidth = 1.25.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0xFFFF2D55).copy(alpha = 0.35f),
                                    start = Offset(center.x, center.y - 50.dp.toPx()),
                                    end = Offset(center.x, center.y + 50.dp.toPx()),
                                    strokeWidth = 1.25.dp.toPx()
                                )
                            }
                        }

                        // Text indicator inside canvas area
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isFaceDetected) "MEDIAPIPE CONNECTIVITY LOCK: YES" else "SEARCHING FOR SUBJECT...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    color = if (isFaceDetected) Color(0xFF00E5FF) else Color(0xFFFF2D55)
                                )
                            )
                        }
                    }
                }
            }
        }

        // STEP-BY-STEP CALIBRATION WIZARD CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Wizard Header step circles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GUIDED ACTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (i in 0..5) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == currentStep) MaterialTheme.colorScheme.primary
                                            else if (i < currentStep) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                        )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Step specific rendering
                    when (currentStep) {
                        0 -> { // Center Neutral
                            WizardStepBody(
                                icon = Icons.Default.AccountCircle,
                                title = "Step 1: Default Relaxed Posture",
                                desc = "Establish your center anchor. Sit upright at a comfortable distance (30-50cm) and look straight at the screen naturally, then lock the center coordinates.",
                                content = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Live Eye Space", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                            Text(if (isFaceDetected) "${eyeDistance.toInt()} px" else "Scanning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Reference Base", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                            Text("${settings.calibrationEyeDistance.toInt()} px", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { viewModel.captureNeutralBaseline() },
                                        enabled = isFaceDetected,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("LOCK ZERO-POSITION CENTER", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        1 -> { // Left Wink
                            WizardStepBody(
                                icon = Icons.Default.Star,
                                title = "Step 2: Calibrate Left Eye Wink",
                                desc = "Wink your LEFT eye (shut it tightly while keeping your right eye open) and hold it, then tap record to define your customized left squint threshold.",
                                content = {
                                    val currentVal = liveLeftEyeOpen ?: 1.0f
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Left Eye Open Ratio: ${String.format("%.2f", currentVal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Configured Limit: ${settings.blinkThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        LinearProgressIndicator(
                                            progress = { currentVal },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                            color = if (currentVal < settings.blinkThreshold) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.captureLeftWinkThreshold() },
                                        enabled = isFaceDetected && liveLeftEyeOpen != null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("RECORD LEFT-WINK SENSITIVITY", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        2 -> { // Right Wink
                            WizardStepBody(
                                icon = Icons.Default.Check,
                                title = "Step 3: Calibrate Right Eye Wink",
                                desc = "Wink your RIGHT eye and hold it, then press record below. This sets the threshold point specifically matching your winking profile.",
                                content = {
                                    val currentVal = liveRightEyeOpen ?: 1.0f
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Right Eye Open Ratio: ${String.format("%.2f", currentVal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Configured Limit: ${settings.blinkThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        LinearProgressIndicator(
                                            progress = { currentVal },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                            color = if (currentVal < settings.blinkThreshold) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.captureRightWinkThreshold() },
                                        enabled = isFaceDetected && liveRightEyeOpen != null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("RECORD RIGHT-WINK SENSITIVITY", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        3 -> { // Smile
                            WizardStepBody(
                                icon = Icons.Default.FavoriteBorder,
                                title = "Step 4: Calibration Smile Click",
                                desc = "Smile as you normally would to confirm action targets. Tap record below when smiling to establish the click sensitivity threshold.",
                                content = {
                                    val currentVal = liveSmileProb ?: 0.0f
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Smile Intensity: ${String.format("%.2f", currentVal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Configured Limit: ${settings.smileClickThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        LinearProgressIndicator(
                                            progress = { currentVal },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                            color = if (currentVal > settings.smileClickThreshold) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.captureSmileThreshold() },
                                        enabled = isFaceDetected && liveSmileProb != null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("RECORD SMILE INTENSITY", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        4 -> { // Mouth Open
                            WizardStepBody(
                                icon = Icons.Default.Face,
                                title = "Step 5: Jaw Gap / Opened mouth",
                                desc = "Stretch your jaw downward or open your mouth comfortable, then record. Mouth opening launches the home panel or resets targets easily.",
                                content = {
                                    val currentVal = liveMouthOpenRatio ?: 0.0f
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Jaw Gap Ratio: ${String.format("%.2f", currentVal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Configured Limit: ${settings.mouthOpenThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        LinearProgressIndicator(
                                            progress = { (currentVal * 2).coerceIn(0f, 1f) }, // scaled for view responsiveness
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                            color = if (currentVal > settings.mouthOpenThreshold) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.captureMouthOpenThreshold() },
                                        enabled = isFaceDetected && liveMouthOpenRatio != null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("RECORD JAW-STRETCH SECTOR", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        5 -> { // Eyebrow Raise (Scroll Up)
                            WizardStepBody(
                                icon = Icons.Default.KeyboardArrowUp,
                                title = "Step 6: Raise Eyebrows (Scroll Up)",
                                desc = "Raise your eyebrows high up naturally (which increases spacing between eyes and eyebrows). Tap record to save this threshold for scrolling menus upwards.",
                                content = {
                                    val currentVal = viewModel.liveBrowHeightRatio.collectAsState().value ?: 0.38f
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Eyebrow Height Ratio: ${String.format("%.2f", currentVal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Configured Limit: ${settings.browRaiseThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        LinearProgressIndicator(
                                            progress = { ((currentVal - 0.2f) * 2f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                            color = if (currentVal > settings.browRaiseThreshold) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.captureBrowRaiseThreshold() },
                                        enabled = isFaceDetected,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("RECORD RAISE EYEBROW THRESHOLD", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        6 -> { // Eyebrow Squint / Frown (Scroll Down)
                            WizardStepBody(
                                icon = Icons.Default.KeyboardArrowDown,
                                title = "Step 7: Squint Eyebrows (Scroll Down)",
                                desc = "Squint, frown, or furrow your eyebrows downward tightly. This reduces eyebrow spacing below normal. Tap record to map this for scrolling menus downward.",
                                content = {
                                    val currentVal = viewModel.liveBrowHeightRatio.collectAsState().value ?: 0.38f
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Eyebrow Height Ratio: ${String.format("%.2f", currentVal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Configured Limit: ${settings.browSquintThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        LinearProgressIndicator(
                                            progress = { ((0.55f - currentVal) * 2f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                            color = if (currentVal < settings.browSquintThreshold) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.captureBrowSquintThreshold() },
                                        enabled = isFaceDetected,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("RECORD SQUINT EYEBROW THRESHOLD", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        7 -> { // Complete
                            WizardStepBody(
                                icon = Icons.Default.CheckCircle,
                                title = "Calibration Successful!",
                                desc = "Outstanding. Your modern face layout parameters are completely locked into the sync repository. Close calibrator and head to playground!",
                                content = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                                            .padding(12.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        ConfigDisplayRow(name = "Baseline Depth Profile", value = "${settings.calibrationEyeDistance.toInt()} px")
                                        ConfigDisplayRow(name = "Blink Squint Threshold", value = String.format("%.2f", settings.blinkThreshold))
                                        ConfigDisplayRow(name = "Smile Click Sensitivity", value = String.format("%.2f", settings.smileClickThreshold))
                                        ConfigDisplayRow(name = "Mouth Trigger Ratio", value = String.format("%.2f", settings.mouthOpenThreshold))
                                        ConfigDisplayRow(name = "Eyebrow Raise Level", value = String.format("%.2f", settings.browRaiseThreshold))
                                        ConfigDisplayRow(name = "Eyebrow Squint Level", value = String.format("%.2f", settings.browSquintThreshold))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.resetCalibrationWizard() },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("RESTART FULL CALIBRATOR WIZARD", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            )
                        }
                    }

                    // Stepper Forward/Backward navigation triggers
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.previousCalibration() },
                            enabled = currentStep > 0,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(100.dp)
                        ) {
                            Text("BACK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = if (currentStep < 7) "Step ${currentStep + 1} of 7" else "Active profile",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Button(
                            onClick = { viewModel.advanceCalibration() },
                            enabled = currentStep < 7,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(100.dp)
                        ) {
                            Text("FORWARD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WizardStepBody(
    icon: ImageVector = Icons.Default.Info,
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
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
fun ConfigDisplayRow(name: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
