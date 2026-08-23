package com.app.faceattendance.data.ml

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector

data class FaceBoxUI(
    val rect: Rect,
    val name: String?,
    val isMatched: Boolean,
    val frameWidth: Int,
    val frameHeight: Int
)

class FaceRecognitionAnalyzer(
    private val detector: FaceDetector,
    private val faceNet: FaceNetModel,
    private val livenessDetector: LivenessDetector,
    private val onFaceAnalyzed: (FaceBoxUI?) -> Unit,
    private val onLivenessStatusChanged: (LivenessStatus) -> Unit,
    private val onFaceMatched: (MatchResult, Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    private var isBusy = false
    private var lastPunchTime = 0L
    private val punchCooldown = 3000L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isBusy) {
            imageProxy.close()
            return
        }

        isBusy = true
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        val isRotated = rotation == 90 || rotation == 270
        val frameWidth = if (isRotated) imageProxy.height else imageProxy.width
        val frameHeight = if (isRotated) imageProxy.width else imageProxy.height

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val primaryFace = faces.firstOrNull()
                if (primaryFace == null) {
                    livenessDetector.resetChallenge()
                    onLivenessStatusChanged(LivenessStatus.Idle)
                    onFaceAnalyzed(null)
                    return@addOnSuccessListener
                }

                val livenessStatus = livenessDetector.processFace(primaryFace)
                onLivenessStatusChanged(livenessStatus)

                val uprightBitmap = ImageUtils.imageProxyToUprightBitmap(imageProxy, isFrontCamera = true)
                val faceCrop = ImageUtils.cropFaceBitmap(uprightBitmap, primaryFace.boundingBox)
                val embedding = faceNet.getFaceEmbedding(faceCrop)
                val match = faceNet.findMatch(embedding, threshold = 0.72f)

                onFaceAnalyzed(
                    FaceBoxUI(
                        rect = primaryFace.boundingBox,
                        name = match?.user?.name,
                        isMatched = match != null,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight
                    )
                )

                if (livenessStatus is LivenessStatus.Passed && match != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastPunchTime > punchCooldown) {
                        lastPunchTime = now
                        onFaceMatched(match, uprightBitmap)
                        livenessDetector.resetChallenge()
                    }
                }
            }
            .addOnFailureListener {
                onFaceAnalyzed(null)
            }
            .addOnCompleteListener {
                isBusy = false
                imageProxy.close()
            }
    }
}