package com.example.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.pow
import kotlin.math.sqrt

class GestureAnalyzer(
    private val viewModel: MainViewModel
) : ImageAnalysis.Analyzer {

    // Configure low-latency on-device face detector with eyebrow contours enabled
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.18f)
        .build()

    private val detector = FaceDetection.getClient(options)

    // Baseline centered nose reference to track relative displacement delta
    private var neutralNoseX: Float? = null
    private var neutralNoseY: Float? = null

    // Real-time calculations for performance overlays
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var activeCameraFps = 0
    private var processFrameCount = 0
    private var activeProcessFps = 0

    // Frame skip configuration for extreme lag reduction on slow hardware
    private var frameTokenCounter = 0

    // Reset neutral camera center anchor
    fun recalibrateCenter() {
        neutralNoseX = null
        neutralNoseY = null
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        frameCount++

        // Check for dynamic center recalibration request
        if (viewModel.recalibrateCenterRequested.value) {
            neutralNoseX = null
            neutralNoseY = null
            viewModel.onRecalibrateCenterProcessed()
        }

        // 1. Calculate general Camera capture FPS
        if (currentTime - lastFpsTimestamp >= 1000) {
            activeCameraFps = frameCount
            frameCount = 0
            activeProcessFps = processFrameCount
            processFrameCount = 0
            lastFpsTimestamp = currentTime
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // 2. Performance optimizer: skip frames if we want to reduce processing loads on older hardware
        val settings = viewModel.settingsState.value
        val frameSkip = settings.frameSkipCount
        if (frameSkip > 0) {
            frameTokenCounter++
            if (frameTokenCounter % (frameSkip + 1) != 0) {
                imageProxy.close()
                return
            }
        }

        // 3. Convert frame to InputImage with rotation metadata
        val imageRotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, imageRotation)

        val startTime = System.currentTimeMillis()

        // 4. Run ML Kit Face detection
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                processFrameCount++
                val latency = System.currentTimeMillis() - startTime

                if (faces.isEmpty()) {
                    viewModel.updateTrackData(
                        faceDetected = false,
                        noseX = null,
                        noseY = null,
                        eyeDist = 0f,
                        leftEyeOpen = null,
                        rightEyeOpen = null,
                        smileProb = null,
                        mouthOpenRatio = null,
                        browHeightRatio = null,
                        browHorizontalRatio = null,
                        latency = latency,
                        procFps = activeProcessFps,
                        camFps = activeCameraFps,
                        landmarks = emptyList()
                    )
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                // Pick the largest/closest face
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: faces[0]

                // Extract core control landmarks
                val noseLandmark = face.getLandmark(FaceLandmark.NOSE_BASE)
                val leftEyeLandmark = face.getLandmark(FaceLandmark.LEFT_EYE)
                val rightEyeLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE)
                val mouthBottomLandmark = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)

                // Extract Eyebrows Contours (LEFT_EYEBROW_TOP and RIGHT_EYEBROW_TOP)
                val leftEyebrowContour = face.getContour(com.google.mlkit.vision.face.FaceContour.LEFT_EYEBROW_TOP)?.points
                val rightEyebrowContour = face.getContour(com.google.mlkit.vision.face.FaceContour.RIGHT_EYEBROW_TOP)?.points

                var browHeightRatio: Float? = null
                var browHorizontalRatio: Float? = null

                if (noseLandmark != null && leftEyeLandmark != null && rightEyeLandmark != null) {
                    val nosePos = noseLandmark.position
                    val leftPos = leftEyeLandmark.position
                    val rightPos = rightEyeLandmark.position

                    // Estimate camera eye distance in pixels
                    val eyeDistance = sqrt(
                        (leftPos.x - rightPos.x).pow(2) + (leftPos.y - rightPos.y).pow(2)
                    )

                    if (eyeDistance > 10f && !leftEyebrowContour.isNullOrEmpty() && !rightEyebrowContour.isNullOrEmpty()) {
                        val leftEyebrowY = leftEyebrowContour.map { it.y }.average().toFloat()
                        val rightEyebrowY = rightEyebrowContour.map { it.y }.average().toFloat()

                        val leftEyebrowX = leftEyebrowContour.map { it.x }.average().toFloat()
                        val rightEyebrowX = rightEyebrowContour.map { it.x }.average().toFloat()

                        val leftEyeY = leftPos.y
                        val rightEyeY = rightPos.y

                        // Vertical distance from eye (larger Y in screen space coords since Y is down) to eyebrow (smaller Y)
                        val leftBrowHeight = leftEyeY - leftEyebrowY
                        val rightBrowHeight = rightEyeY - rightEyebrowY

                        val normLeftBrowHeight = leftBrowHeight / eyeDistance
                        val normRightBrowHeight = rightBrowHeight / eyeDistance
                        browHeightRatio = (normLeftBrowHeight + normRightBrowHeight) / 2.0f

                        // Horizontal normalized space between eyebrows
                        val eyebrowHorizontalDist = Math.abs(rightEyebrowX - leftEyebrowX)
                        browHorizontalRatio = eyebrowHorizontalDist / eyeDistance
                    }

                    // Auto center neutral nose reference on first capture frame
                    if (neutralNoseX == null || neutralNoseY == null) {
                        neutralNoseX = nosePos.x
                        neutralNoseY = nosePos.y
                    }

                    // Displacement from centered anchor
                    val noseDeltaX = nosePos.x - neutralNoseX!!
                    val noseDeltaY = nosePos.y - neutralNoseY!!

                    // Normalized Mouth opened mouth height extraction using ratio scaling to prevent distance variance
                    var mouthRatio = 0.0f
                    if (mouthBottomLandmark != null) {
                        val noseToMouthDist = sqrt(
                            (nosePos.x - mouthBottomLandmark.position.x).pow(2) +
                            (nosePos.y - mouthBottomLandmark.position.y).pow(2)
                        )
                        // Distance normalized against current eyeDistance
                        if (eyeDistance > 10f) {
                            mouthRatio = noseToMouthDist / eyeDistance
                        }
                    }

                    // Convert basic landmarks to 2D coordinates normalized for custom visualization
                    val visualPoints = mutableListOf<Point2D>()
                    val pW = imageProxy.width.toFloat()
                    val pH = imageProxy.height.toFloat()
                    if (pW > 1f && pH > 1f) {
                        val rawPoints = mutableListOf<android.graphics.PointF>()
                        
                        // Add standard landmarks
                        face.allLandmarks.forEach { rawPoints.add(it.position) }
                        
                        // Add eyebrow contour points to show active eyebrow tracking visually
                        leftEyebrowContour?.forEach { rawPoints.add(it) }
                        rightEyebrowContour?.forEach { rawPoints.add(it) }
                        
                        rawPoints.forEach { pos ->
                            val u = pos.x / pW
                            val v = pos.y / pH
                            
                            val mapped = when (imageRotation) {
                                90 -> Point2D(1f - v, u)
                                180 -> Point2D(1f - u, 1f - v)
                                270 -> Point2D(v, 1f - u)
                                else -> Point2D(u, v)
                            }
                            visualPoints.add(mapped)
                        }
                    }

                    // Update ViewModel tracking data
                    viewModel.updateTrackData(
                        faceDetected = true,
                        noseX = noseDeltaX,
                        noseY = noseDeltaY,
                        eyeDist = eyeDistance,
                        leftEyeOpen = face.leftEyeOpenProbability,
                        rightEyeOpen = face.rightEyeOpenProbability,
                        smileProb = face.smilingProbability,
                        mouthOpenRatio = mouthRatio,
                        browHeightRatio = browHeightRatio,
                        browHorizontalRatio = browHorizontalRatio,
                        latency = latency,
                        procFps = activeProcessFps,
                        camFps = activeCameraFps,
                        landmarks = visualPoints
                    )
                } else {
                    viewModel.updateTrackData(
                        faceDetected = false,
                        noseX = null,
                        noseY = null,
                        eyeDist = 0f,
                        leftEyeOpen = null,
                        rightEyeOpen = null,
                        smileProb = null,
                        mouthOpenRatio = null,
                        browHeightRatio = null,
                        browHorizontalRatio = null,
                        latency = latency,
                        procFps = activeProcessFps,
                        camFps = activeCameraFps,
                        landmarks = emptyList()
                    )
                }
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                Log.e("OptiSyncAnal", "Face detection execution failure", e)
                imageProxy.close()
            }
    }
}
