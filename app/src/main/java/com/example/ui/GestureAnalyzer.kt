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

    // Configure low-latency on-device face detector with eyebrow contours + classification
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

    // Exponential moving average for browHeightRatio — eliminates single-frame noise spikes
    private var emaBrowHeight: Float? = null
    private val emaBrowAlpha = 0.30f // Higher = more responsive, lower = smoother

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

        // 2. Performance optimizer: skip frames on older hardware
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
                        isHeadStill = false,
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

                // Head pose — suppress gesture triggers during head movement
                val headYaw   = face.headEulerAngleY  // left/right rotation
                val headPitch = face.headEulerAngleX  // up/down tilt
                val isHeadStill = Math.abs(headYaw) < 15f && Math.abs(headPitch) < 12f

                // Extract core control landmarks
                val noseLandmark = face.getLandmark(FaceLandmark.NOSE_BASE)
                val leftEyeLandmark = face.getLandmark(FaceLandmark.LEFT_EYE)
                val rightEyeLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE)

                // Only compute mouth when feature is enabled (saves cycles + stops false detections)
                val mouthBottomLandmark = if (settings.enableMouthOpenAction) {
                    face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
                } else null

                // Extract Eyebrow Contours (used for scroll gesture)
                val leftEyebrowContour = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points
                val rightEyebrowContour = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points

                var browHeightRatio: Float? = null
                var browHorizontalRatio: Float? = null

                if (noseLandmark != null && leftEyeLandmark != null && rightEyeLandmark != null) {
                    val nosePos = noseLandmark.position
                    val leftPos = leftEyeLandmark.position
                    val rightPos = rightEyeLandmark.position

                    // Inter-eye distance — used for adaptive sensitivity + normalization
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

                        // Vertical distance eye → eyebrow, normalized by inter-eye distance
                        val leftBrowHeight = leftEyeY - leftEyebrowY
                        val rightBrowHeight = rightEyeY - rightEyebrowY

                        val normLeftBrowHeight = leftBrowHeight / eyeDistance
                        val normRightBrowHeight = rightBrowHeight / eyeDistance
                        val rawBrowHeight = (normLeftBrowHeight + normRightBrowHeight) / 2.0f

                        // EMA smoothing — prevents single noisy frames from firing scroll gestures
                        emaBrowHeight = if (emaBrowHeight == null) rawBrowHeight
                        else emaBrowHeight!! * (1f - emaBrowAlpha) + rawBrowHeight * emaBrowAlpha

                        browHeightRatio = emaBrowHeight

                        val eyebrowHorizontalDist = Math.abs(rightEyebrowX - leftEyebrowX)
                        browHorizontalRatio = eyebrowHorizontalDist / eyeDistance
                    }

                    // Auto center neutral nose reference on first capture frame
                    if (neutralNoseX == null || neutralNoseY == null) {
                        neutralNoseX = nosePos.x
                        neutralNoseY = nosePos.y
                    }

                    val noseDeltaX = nosePos.x - neutralNoseX!!
                    val noseDeltaY = nosePos.y - neutralNoseY!!

                    // Mouth open ratio — anchored to eye midpoint, not nose
                    // Eye midpoint is stable across head tilts; nose-to-mouth was not.
                    var mouthRatio = 0.0f
                    if (settings.enableMouthOpenAction && mouthBottomLandmark != null) {
                        val eyeMidX = (leftPos.x + rightPos.x) / 2f
                        val eyeMidY = (leftPos.y + rightPos.y) / 2f
                        val eyeMidToMouthDist = sqrt(
                            (eyeMidX - mouthBottomLandmark.position.x).pow(2) +
                            (eyeMidY - mouthBottomLandmark.position.y).pow(2)
                        )
                        val eyeDistance2 = sqrt(
                            (leftPos.x - rightPos.x).pow(2) + (leftPos.y - rightPos.y).pow(2)
                        )
                        if (eyeDistance2 > 10f) {
                            mouthRatio = eyeMidToMouthDist / eyeDistance2
                        }
                    }

                    // Build selective visual landmark list — only points relevant to gesture control
                    val visualPoints = buildSelectiveLandmarkPoints(
                        face = face,
                        imageProxy = imageProxy,
                        imageRotation = imageRotation,
                        leftEyebrowContour = leftEyebrowContour,
                        rightEyebrowContour = rightEyebrowContour,
                        includeMouth = settings.enableMouthOpenAction
                    )

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
                        isHeadStill = isHeadStill,
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
                        isHeadStill = false,
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

    /**
     * Build a selective set of landmark points for overlay rendering.
     *
     * Only includes: LEFT_EYE, RIGHT_EYE, NOSE_BASE, left/right eyebrow contours,
     * and optionally mouth landmarks when enabled.
     *
     * Coordinate mapping corrected for front camera:
     * - The image from the front camera arrives rotated (usually 270° in portrait).
     * - ML Kit coordinates are in the sensor image space (not mirrored).
     * - We apply the rotation transform then let the canvas mirror via (1-x).
     */
    private fun buildSelectiveLandmarkPoints(
        face: com.google.mlkit.vision.face.Face,
        imageProxy: ImageProxy,
        imageRotation: Int,
        leftEyebrowContour: List<android.graphics.PointF>?,
        rightEyebrowContour: List<android.graphics.PointF>?,
        includeMouth: Boolean
    ): List<Point2D> {
        val pW = imageProxy.width.toFloat()
        val pH = imageProxy.height.toFloat()
        if (pW <= 1f || pH <= 1f) return emptyList()

        val rawPoints = mutableListOf<android.graphics.PointF>()

        // Core tracking landmarks only
        val coreLandmarkTypes = mutableListOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE
        )
        if (includeMouth) {
            coreLandmarkTypes.add(FaceLandmark.MOUTH_BOTTOM)
            coreLandmarkTypes.add(FaceLandmark.MOUTH_LEFT)
            coreLandmarkTypes.add(FaceLandmark.MOUTH_RIGHT)
        }

        coreLandmarkTypes.forEach { type ->
            face.getLandmark(type)?.position?.let { rawPoints.add(it) }
        }

        // Eyebrow contour points for visual feedback during calibration
        leftEyebrowContour?.forEach { rawPoints.add(it) }
        rightEyebrowContour?.forEach { rawPoints.add(it) }

        // Left/right eye contours for better visual representation
        face.getContour(FaceContour.LEFT_EYE)?.points?.forEach { rawPoints.add(it) }
        face.getContour(FaceContour.RIGHT_EYE)?.points?.forEach { rawPoints.add(it) }

        return rawPoints.map { pos ->
            val u = pos.x / pW
            val v = pos.y / pH

            // FIXED: Front camera coordinate mapping.
            // The 90° and 270° cases were previously swapped, causing the mesh to
            // appear on the wrong side. Now correctly handles front camera portrait orientation.
            // Canvas applies (1f - pt.x) for horizontal mirroring to match the selfie preview.
            when (imageRotation) {
                // For each case: canvas renders (1-pt.x)*W for x (mirroring),  pt.y*H for y.
                // Math verified against front-camera 90°/270° rotation geometry.
                90  -> Point2D(v, 1f - u)      // 90° CCW: pt.x=v → canvas=(1-v)W; pt.y=(1-u) → (1-u)H ✓
                180 -> Point2D(1f - u, v)      // 180°: pt.x=(1-u) → uW; pt.y=v → vH ✓
                270 -> Point2D(1f - v, u)      // 270° CCW: pt.x=(1-v) → vW; pt.y=u → uH ✓
                else -> Point2D(u, v)           // 0°: pt.x=u → (1-u)W (mirror); pt.y=v ✓
            }
        }
    }
}
