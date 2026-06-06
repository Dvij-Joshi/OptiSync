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
import kotlin.math.abs
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

    // Euler angles alone miss plain head translation, which can look like
    // eyebrow motion. Keep a tiny motion history to gate calibration/actions.
    private var lastFrameNose: Point2D? = null
    private var lastFrameEyeDistance: Float? = null

    // Reset neutral camera center anchor
    fun recalibrateCenter() {
        neutralNoseX = null
        neutralNoseY = null
        lastFrameNose = null
        lastFrameEyeDistance = null
    }

    fun close() {
        detector.close()
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
                    lastFrameNose = null
                    lastFrameEyeDistance = null
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
                val isPoseStill = abs(headYaw) < 12f && abs(headPitch) < 10f

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

                // Normalize nose position to 0..1 screen space where (0.5, 0.5) is dead center
                val mappedNose = noseLandmark?.let { getNormalizedPoint(it.position, imageProxy, imageRotation, true) }
                val nosePos = mappedNose // For compatibility with existing code

                var browHeightRatio: Float? = null
                var browHorizontalRatio: Float? = null

                if (noseLandmark != null && leftEyeLandmark != null && rightEyeLandmark != null) {
                    val leftPos = leftEyeLandmark.position
                    val rightPos = rightEyeLandmark.position

                    // Inter-eye distance — used for adaptive sensitivity + normalization
                    val eyeDistance = sqrt(
                        (leftPos.x - rightPos.x).pow(2) + (leftPos.y - rightPos.y).pow(2)
                    )

                    val isHeadStill = nosePos?.let {
                        updateHeadStillness(isPoseStill, it, eyeDistance)
                    } ?: false

                    if (nosePos == null) {
                        updateNoLandmarkTrackData(latency, activeProcessFps, activeCameraFps)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

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
                    lastFrameNose = null
                    lastFrameEyeDistance = null
                    updateNoLandmarkTrackData(latency, activeProcessFps, activeCameraFps)
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
            FaceLandmark.NOSE_BASE,
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE
        )
        if (includeMouth) {
            coreLandmarkTypes.add(FaceLandmark.MOUTH_BOTTOM)
            coreLandmarkTypes.add(FaceLandmark.MOUTH_LEFT)
            coreLandmarkTypes.add(FaceLandmark.MOUTH_RIGHT)
        }

        coreLandmarkTypes.forEach { type ->
            face.getLandmark(type)?.position?.let { rawPoints.add(it) }
        }

        // Eyebrow contour points for visual feedback during calibration (thinned out)
        leftEyebrowContour?.filterIndexed { index, _ -> index % 3 == 0 }?.forEach { rawPoints.add(it) }
        rightEyebrowContour?.filterIndexed { index, _ -> index % 3 == 0 }?.forEach { rawPoints.add(it) }

        // Left/right eye contours for better visual representation (thinned out)
        face.getContour(FaceContour.LEFT_EYE)?.points?.filterIndexed { index, _ -> index % 3 == 0 }?.forEach { rawPoints.add(it) }
        face.getContour(FaceContour.RIGHT_EYE)?.points?.filterIndexed { index, _ -> index % 3 == 0 }?.forEach { rawPoints.add(it) }

        return rawPoints.map { pos ->
            getNormalizedPoint(pos, imageProxy, imageRotation, true)
        }
    }

    private fun getNormalizedPoint(
        pos: android.graphics.PointF,
        imageProxy: ImageProxy,
        rotation: Int,
        isFrontCamera: Boolean
    ): Point2D {
        val rawU = pos.x / imageProxy.width.toFloat()
        val rawV = pos.y / imageProxy.height.toFloat()
        
        var mappedX = rawU
        var mappedY = rawV

        when (rotation) {
            90  -> { mappedX = rawV; mappedY = 1f - rawU }
            180 -> { mappedX = 1f - rawU; mappedY = 1f - rawV }
            270 -> { mappedX = 1f - rawV; mappedY = rawU }
        }

        if (isFrontCamera) {
            mappedX = 1f - mappedX
        }
        
        return Point2D(mappedX.coerceIn(0f, 1f), mappedY.coerceIn(0f, 1f))
    }

    private fun updateHeadStillness(isPoseStill: Boolean, nose: Point2D, eyeDistance: Float): Boolean {
        val previousNose = lastFrameNose
        val previousEyeDistance = lastFrameEyeDistance

        lastFrameNose = nose
        if (eyeDistance > 10f) {
            lastFrameEyeDistance = eyeDistance
        }

        if (!isPoseStill || previousNose == null || previousEyeDistance == null || eyeDistance <= 10f) {
            return false
        }

        val noseVelocity = sqrt(
            (nose.x - previousNose.x).pow(2) + (nose.y - previousNose.y).pow(2)
        )
        val depthChange = abs(eyeDistance - previousEyeDistance) / previousEyeDistance
        return noseVelocity < 0.018f && depthChange < 0.08f
    }

    private fun updateNoLandmarkTrackData(latency: Long, procFps: Int, camFps: Int) {
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
            procFps = procFps,
            camFps = camFps,
            landmarks = emptyList()
        )
    }
}
