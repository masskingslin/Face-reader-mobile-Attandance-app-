package com.app.faceattendance.presentation.camera

import android.graphics.Bitmap
import android.util.Size as AndroidSize
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.app.faceattendance.data.ml.*
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

enum class PunchMode { IN, OUT }

@Composable
fun CameraScreen(
    faceNetModel: FaceNetModel,
    onAttendancePunched: (userId: String, name: String, type: PunchMode, frame: Bitmap) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToEnroll: () -> Unit
) {
    AutoBrightnessEffect(lowLightThresholdLux = 15f, restoreThresholdLux = 40f)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var punchMode by remember { mutableStateOf(PunchMode.IN) }
    var detectedFace by remember { mutableStateOf<FaceBoxUI?>(null) }
    var livenessStatus by remember { mutableStateOf<LivenessStatus>(LivenessStatus.Idle) }
    var timeString by remember { mutableStateOf("") }

    val livenessDetector = remember { LivenessDetector() }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy • HH:mm:ss", Locale.getDefault())
        while (true) {
            timeString = sdf.format(Date())
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val executor = Executors.newSingleThreadExecutor()

                val detector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build()
                )

                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(AndroidSize(720, 1280))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val analyzer = FaceRecognitionAnalyzer(
                        detector = detector,
                        faceNet = faceNetModel,
                        livenessDetector = livenessDetector,
                        onFaceAnalyzed = { face -> detectedFace = face },
                        onLivenessStatusChanged = { status -> livenessStatus = status },
                        onFaceMatched = { match, frame ->
                            onAttendancePunched(match.user.id, match.user.name, punchMode, frame)
                        }
                    )

                    analysis.setAnalyzer(executor, analyzer)

                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedFace?.let { face ->
                val scale = maxOf(size.width / face.frameWidth, size.height / face.frameHeight)
                val offX = (size.width - face.frameWidth * scale) / 2f
                val offY = (size.height - face.frameHeight * scale) / 2f

                val left = size.width - (face.rect.right * scale + offX)
                val top = face.rect.top * scale + offY
                val boxWidth = face.rect.width() * scale
                val boxHeight = face.rect.height() * scale

                val strokeColor = when {
                    livenessStatus is LivenessStatus.Passed && face.isMatched -> Color(0xFF00E676)
                    face.isMatched -> Color(0xFF29B6F6)
                    else -> Color(0xFFFFEB3B)
                }

                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToEnroll,
                    modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Enroll Employee", tint = Color.White)
                }

                Surface(color = Color.Black.copy(0.65f), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        text = timeString,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.background(Color.Black.copy(0.6f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.History, contentDescription = "Attendance Logs", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (val status = livenessStatus) {
                is LivenessStatus.InProgress -> {
                    Surface(
                        color = Color(0xFF1976D2).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = status.progressMessage,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                is LivenessStatus.Passed -> {
                    Surface(
                        color = Color(0xFF00C853).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "✓ Liveness Verified • ${detectedFace?.name ?: "Recognized"}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                is LivenessStatus.Failed -> {
                    Surface(
                        color = Color(0xFFD32F2F).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = status.reason,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                LivenessStatus.Idle -> {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Align Face within the Frame",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            color = Color(0xFF1F1F1F).copy(0.9f),
            shape = RoundedCornerShape(30.dp)
        ) {
            Row(modifier = Modifier.padding(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (punchMode == PunchMode.IN) Color(0xFF00C853) else Color.Transparent)
                        .clickable { punchMode = PunchMode.IN }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "PUNCH IN",
                        fontWeight = FontWeight.Bold,
                        color = if (punchMode == PunchMode.IN) Color.White else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (punchMode == PunchMode.OUT) Color(0xFFD50000) else Color.Transparent)
                        .clickable { punchMode = PunchMode.OUT }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "PUNCH OUT",
                        fontWeight = FontWeight.Bold,
                        color = if (punchMode == PunchMode.OUT) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}
