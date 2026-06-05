package com.example.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.GestureSettings
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.sqrt

class CalibrationActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF050D1A)
                ) {
                    CalibrationFlowScreen(viewModel, onFinish = { finish() })
                }
            }
        }

        startCamera()
    }

    private fun startCamera() {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val analyzer = GestureAnalyzer(viewModel)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            viewModel.setCameraPreviewUseCase(preview)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            @Suppress("DEPRECATION")
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetResolution(android.util.Size(360, 480))
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}

@Composable
fun CalibrationFlowScreen(viewModel: MainViewModel, onFinish: () -> Unit) {
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()
    val isHeadStill by viewModel.isHeadStill.collectAsState()
    val facePoints by viewModel.facePoints.collectAsState()
    val imageDimensions by viewModel.imageDimensions.collectAsState()
    
    val liveBrowHeight by viewModel.liveBrowHeightRatio.collectAsState()
    val liveLeftEyeOpen by viewModel.liveLeftEyeOpen.collectAsState()
    val liveRightEyeOpen by viewModel.liveRightEyeOpen.collectAsState()
    val eyeDistance by viewModel.currentEyeDistance.collectAsState()

    val SAMPLES_NEEDED = 30
    var currentStepIdx by remember { mutableStateOf(0) }
    var sampleCount by remember { mutableStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var isComplete by remember { mutableStateOf(false) }

    // Aggregates
    var sumRest by remember { mutableStateOf(0f) }
    var sumBrowUp by remember { mutableStateOf(0f) }
    var sumBrowDown by remember { mutableStateOf(0f) }
    var sumWinkR by remember { mutableStateOf(0f) }
    var sumWinkL by remember { mutableStateOf(0f) }
    var capturedEyeDist by remember { mutableStateOf(0f) }

    val restAvg = if (currentStepIdx > 0 && sumRest > 0f) sumRest / SAMPLES_NEEDED else (liveBrowHeight ?: 0.32f)

    fun isGestureValid(stepIdx: Int): Boolean {
        if (!isFaceDetected) return false
        return when (stepIdx) {
            0 -> true
            1 -> (liveBrowHeight ?: 0f) > (restAvg + 0.05f)
            2 -> (liveBrowHeight ?: 1f) < (restAvg - 0.04f)
            3 -> (liveRightEyeOpen ?: 1f) < 0.28f && (liveLeftEyeOpen ?: 0f) > 0.60f
            4 -> (liveLeftEyeOpen ?: 1f) < 0.28f && (liveRightEyeOpen ?: 0f) > 0.60f
            else -> false
        }
    }

    // Step state machine logic
    LaunchedEffect(currentStepIdx) {
        if (currentStepIdx > 4) {
            isComplete = true
            isCapturing = false
            return@LaunchedEffect
        }
        
        isCapturing = false
        // Give the user 2 seconds to prepare for the next step
        delay(2000L)
        
        // Auto-start capturing
        sampleCount = 0
        isCapturing = true
        
        while (sampleCount < SAMPLES_NEEDED) {
            delay(50L) // 20 FPS
            if (!isGestureValid(currentStepIdx)) continue
            
            val browVal = liveBrowHeight ?: continue
            val rEye = liveRightEyeOpen ?: continue
            val lEye = liveLeftEyeOpen ?: continue
            
            when (currentStepIdx) {
                0 -> { sumRest += browVal; capturedEyeDist = eyeDistance }
                1 -> sumBrowUp += browVal
                2 -> sumBrowDown += browVal
                3 -> sumWinkR += rEye
                4 -> sumWinkL += lEye
            }
            sampleCount++
        }
        
        // Finished capturing this step, proceed to next
        isCapturing = false
        delay(500L) // Small pause before jumping to next instruction
        currentStepIdx++
    }

    val instructions = listOf(
        Pair("Neutral Face", "Look straight at the camera. Don't smile or blink."),
        Pair("Raise Eyebrows", "Lift both eyebrows as high as you can and hold."),
        Pair("Squint/Furrow", "Pull your eyebrows down (like you are angry) and hold."),
        Pair("Wink Right Eye", "Close your right eye tightly, keep left open."),
        Pair("Wink Left Eye", "Close your left eye tightly, keep right open.")
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OptiSync Calibration", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        if (isComplete) {
            Icon(Icons.Default.CheckCircle, "Done", tint = Color(0xFF00E5FF), modifier = Modifier.size(120.dp).padding(20.dp))
            Text("Calibration Complete!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            
            val calRest = sumRest / SAMPLES_NEEDED
            val calUp = sumBrowUp / SAMPLES_NEEDED
            val calDown = sumBrowDown / SAMPLES_NEEDED
            val calWinkR = sumWinkR / SAMPLES_NEEDED
            val calWinkL = sumWinkL / SAMPLES_NEEDED

            val browUpThreshold = calRest + (calUp - calRest) * 0.65f
            val browDownThreshold = calRest + (calDown - calRest) * 0.65f

            Button(
                onClick = {
                    viewModel.updateCalibration(
                        restBrow = calRest,
                        upBrow = calUp,
                        downBrow = calDown,
                        eyeDist = capturedEyeDist
                    )
                    onFinish()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Save & Finish", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            val stepInfo = instructions.getOrNull(currentStepIdx) ?: Pair("", "")
            
            Text("Step ${currentStepIdx + 1} of 5", color = Color(0xFF00E5FF), fontSize = 16.sp)
            Text(stepInfo.first, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(stepInfo.second, color = Color.LightGray, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

            // Progress bar for current step
            LinearProgressIndicator(
                progress = if (isCapturing) sampleCount.toFloat() / SAMPLES_NEEDED else 0f,
                modifier = Modifier.fillMaxWidth().height(12.dp).padding(bottom = 24.dp),
                color = Color(0xFF00E5FF),
                trackColor = Color.DarkGray
            )

            // Warning message
            if (isCapturing && !isGestureValid(currentStepIdx)) {
                Text("⚠ Please perform the gesture correctly.", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
            } else if (isCapturing) {
                Text("✓ Hold it...", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            } else {
                Text("Get ready...", color = Color.LightGray)
            }

            Spacer(Modifier.height(24.dp))

            // Camera preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(2.dp, if (isFaceDetected && isHeadStill) Color(0xFF00E5FF) else Color.DarkGray, RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    update = { pv -> viewModel.getCameraPreviewUseCase()?.setSurfaceProvider(pv.surfaceProvider) },
                    modifier = Modifier.fillMaxSize()
                )

                // Face Mesh Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (isFaceDetected && facePoints.isNotEmpty()) {
                        val rotatedW = imageDimensions.second.toFloat()
                        val rotatedH = imageDimensions.first.toFloat()
                        
                        val scale = maxOf(size.width / rotatedW, size.height / rotatedH)
                        val scaledW = rotatedW * scale
                        val scaledH = rotatedH * scale
                        
                        val offsetX = (size.width - scaledW) / 2f
                        val offsetY = (size.height - scaledH) / 2f
                        
                        fun mapPoint(pt: Point2D): Offset {
                            val rotU = 1f - pt.y // 270 deg front camera rotation + mirror
                            val rotV = pt.x
                            return Offset(offsetX + rotU * scaledW, offsetY + rotV * scaledH)
                        }

                        val mapped = facePoints.map { mapPoint(it) }

                        // Draw connecting lines between close points
                        for (i in mapped.indices) {
                            for (j in i + 1 until mapped.size) {
                                val dx = mapped[i].x - mapped[j].x
                                val dy = mapped[i].y - mapped[j].y
                                if (sqrt(dx * dx + dy * dy) < 40.dp.toPx()) {
                                    drawLine(
                                        color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                        start = mapped[i], end = mapped[j], strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }
                        }

                        mapped.forEach { pt ->
                            drawCircle(Color.White, radius = 2.5f.dp.toPx(), center = pt)
                        }
                    }
                }
            }
        }
    }
}
