package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.GestureSettings
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainLayout(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val pointerPos by viewModel.pointerPosition.collectAsState()
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()
    val settings by viewModel.settingsState.collectAsState(initial = GestureSettings())

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    MyApplicationTheme {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("main_nav_bar"),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { viewModel.setTab(0) },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Playground") },
                        label = { Text("Playground") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { viewModel.setTab(1) },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "Calibrate") },
                        label = { Text("Calibrate") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { viewModel.setTab(2) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (hasCameraPermission) {
                    // Hidden/Miniature Camera Handler bound directly to Compose lifecycle
                    CameraHandler(viewModel = viewModel)

                    // Core responsive workspace matching chosen tab
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            0 -> DashboardScreen(viewModel)
                            1 -> CalibrationScreen(viewModel)
                            2 -> SettingsScreen(viewModel)
                        }
                    }

                    // Floating Pointer - stylized interactive cursor matching nose movement deltas
                    FloatingNosePointer(pointerPos = pointerPos, isDetected = isFaceDetected)

                    // Performance Monitor Overlay (Persistent floating widget)
                    PerformanceMonitorOverlay(viewModel = viewModel)

                    // Real-time Gestures Activity Notification Toast alert
                    GestureToastAlert(viewModel = viewModel)
                } else {
                    CameraPermissionDeniedView {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.CameraHandler(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val analyzer = remember { GestureAnalyzer(viewModel) }

    // Start running CameraX bound to lifecycle
    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build()
        
        // Save preview use-case context for page dual-view overlays
        viewModel.setCameraPreviewUseCase(preview)
        
        // Front tracking selector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        // Ultra-low overhead lightweight resolution to process facial landmarks and contours at top speed
        @Suppress("DEPRECATION")
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetResolution(android.util.Size(360, 480))
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, analyzer)
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("OptiSyncCamera", "Camera binding failure", exc)
        }
    }

    // Mini camera tracker portal rendering in top right corner.
    // Styled beautifully as an abstract radar view to conserve render overhead and fit tech branding
    Box(
        modifier = Modifier
            .padding(12.dp)
            .size(72.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .align(Alignment.TopEnd)
            .testTag("camera_viewport")
    ) {
        // Simple graphical tracking circle to show detection status without wasting energy processing preview views
        val isFaceDetected by viewModel.isFaceDetected.collectAsState()
        val facePoints by viewModel.facePoints.collectAsState()
        val imageDimensions by viewModel.imageDimensions.collectAsState()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // Outer dynamic radar pulse
            drawCircle(
                color = if (isFaceDetected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFFFF2D55).copy(alpha = 0.1f),
                radius = size.width / 2f,
                center = center
            )

            if (isFaceDetected && facePoints.isNotEmpty()) {
                // FILL_CENTER Transform Logic:
                // Image from camera is landscape (pW x pH, e.g. 480x360).
                // The front camera is rotated 270 degrees.
                // In 270-deg rotation, the landscape image's Y axis maps to portrait X axis, X maps to Y.
                // After 270-deg CCW rotation and horizontal mirror (for front camera):
                // rotated_u = 1.0 - v
                // rotated_v = u
                //
                // Next, the PreviewView applies FILL_CENTER scaling to fill the Canvas.
                // It scales by max(canvas_width / rotated_width, canvas_height / rotated_height).
                val rotatedW = imageDimensions.second.toFloat() // 360
                val rotatedH = imageDimensions.first.toFloat()  // 480
                
                val scale = maxOf(size.width / rotatedW, size.height / rotatedH)
                val scaledW = rotatedW * scale
                val scaledH = rotatedH * scale
                
                // The scaled image is centered in the Canvas.
                val offsetX = (size.width - scaledW) / 2f
                val offsetY = (size.height - scaledH) / 2f
                
                // Map a raw sensor point (u, v) to canvas coordinates
                fun mapPoint(pt: Point2D): Offset {
                    val rotU = 1f - pt.y // 1.0 - v
                    val rotV = pt.x      // u
                    return Offset(
                        x = offsetX + rotU * scaledW,
                        y = offsetY + rotV * scaledH
                    )
                }

                // Nose Tip
                val nosePoint = facePoints.firstOrNull()
                if (nosePoint != null) {
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 4.dp.toPx(),
                        center = mapPoint(nosePoint)
                    )
                }

                // Eyes & Mouth Bounds
                facePoints.drop(1).forEach { pt ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = 2.5f.dp.toPx(),
                        center = mapPoint(pt)
                    )
                }
            } else {
                // Dead lock crosshairs
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun FloatingNosePointer(pointerPos: Point2D, isDetected: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Derive coordinates relative to layout margins
        val layoutX = (pointerPos.x / 1000f) * widthPx
        val layoutY = (pointerPos.y / 1000f) * heightPx

        // Pointer dot representation
        Box(
            modifier = Modifier
                .offset(
                    x = (layoutX / LocalContext.current.resources.displayMetrics.density).dp - 12.dp,
                    y = (layoutY / LocalContext.current.resources.displayMetrics.density).dp - 12.dp
                )
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isDetected) listOf(
                            Color(0xFF00E5FF),
                            Color(0xFF007BFF).copy(alpha = 0.4f),
                            Color.Transparent
                        ) else listOf(
                            Color(0xFFFF2D55),
                            Color(0xFFFF2D55).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Small core light ring
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isDetected) Color.White else Color(0xFFFF2D55))
            )
        }
    }
}

@Composable
fun PerformanceMonitorOverlay(viewModel: MainViewModel) {
    val latency by viewModel.trackingLatencyMs.collectAsState()
    val procFps by viewModel.processingFps.collectAsState()
    val camFps by viewModel.cameraFps.collectAsState()
    val eyeDistance by viewModel.currentEyeDistance.collectAsState()

    // Smooth floating stats drawer in top left corner
    Box(
        modifier = Modifier
            .padding(12.dp)
            .width(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .testTag("perf_monitor_overlay")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PERF MONITOR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (procFps > 10) Color(0xFF00E5FF) else Color(0xFFFF2D55))
                )
            }
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)
            
            PerfStatRow(label = "Latency:", value = "${latency} ms", statusColor = when {
                latency < 20 -> Color(0xFF00E5FF)
                latency < 45 -> Color(0xFFFFCC00)
                else -> Color(0xFFFF2D55)
            })
            
            PerfStatRow(label = "Proc Rate:", value = "$procFps fps", statusColor = if (procFps > 15) Color(0xFF00E5FF) else Color(0xFFFFCC00))
            PerfStatRow(label = "Cam Rate:", value = "$camFps fps", statusColor = Color.White.copy(alpha = 0.6f))
            
            // Scale bar for dynamic eye distance sensing
            Text(
                text = "Eye Distance Scale: ${eyeDistance.toInt()}px",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            // Linear indicator representation of spatial depth
            LinearProgressIndicator(
                progress = (eyeDistance / 240f).coerceIn(0.1f, 1.0f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun PerfStatRow(label: String, value: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = statusColor
        )
    }
}

@Composable
fun GestureToastAlert(viewModel: MainViewModel) {
    val lastGesture by viewModel.lastGestureTriggered.collectAsState()

    AnimatedVisibility(
        visible = lastGesture != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -40 }),
        modifier = Modifier
            .padding(top = 96.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 24.dp)
            .testTag("gesture_toast")
    ) {
        if (lastGesture != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0055FF).copy(0.9f),
                                Color(0xFF00C2FF).copy(0.9f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                    .clickable { viewModel.clearGestureNotification() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Triggered",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = lastGesture!!,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Automate clearing toast notification after 2 seconds
            LaunchedEffect(lastGesture) {
                kotlinx.coroutines.delay(2000)
                viewModel.clearGestureNotification()
            }
        }
    }
}

@Composable
fun CameraPermissionDeniedView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "Camera Permission Required",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "OptiSync requests on-device camera frames to calculate real-time face movements. Your images are never saved, recorded, or sent online.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("request_camera_btn")
        ) {
            Text("Grant Camera Permission")
        }
    }
}
