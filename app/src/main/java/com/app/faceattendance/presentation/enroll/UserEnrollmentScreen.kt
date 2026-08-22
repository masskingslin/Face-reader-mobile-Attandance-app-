package com.app.faceattendance.presentation.enroll

import android.graphics.Bitmap
import android.util.Size as AndroidSize
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.app.faceattendance.data.local.UserEntity
import com.app.faceattendance.data.ml.FaceNetModel
import com.app.faceattendance.data.ml.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEnrollmentScreen(
    faceNetModel: FaceNetModel,
    onUserEnrolled: (UserEntity) -> Unit,
    onNavigateBack: () -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Position face clearly and click Capture") }
    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enroll New Employee", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("Employee ID (e.g., EMP-101)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val executor = Executors.newSingleThreadExecutor()

                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder()
                                .setTargetResolution(AndroidSize(720, 1280))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analysis.setAnalyzer(executor) { proxy ->
                                latestBitmap = ImageUtils.imageProxyToUprightBitmap(proxy, isFrontCamera = true)
                                proxy.close()
                            }

                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(statusText, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val bmp = latestBitmap
                    if (userId.isBlank() || userName.isBlank() || bmp == null) {
                        statusText = "Please fill in ID, Name, and ensure camera preview is active."
                        return@Button
                    }

                    val detector = FaceDetection.getClient(
                        FaceDetectorOptions.Builder()
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                            .build()
                    )
                    val inputImg = InputImage.fromBitmap(bmp, 0)

                    detector.process(inputImg).addOnSuccessListener { faces ->
                        val face = faces.firstOrNull()
                        if (face != null) {
                            val crop = ImageUtils.cropFaceBitmap(bmp, face.boundingBox)
                            val emb = faceNetModel.getFaceEmbedding(crop)
                            val embString = emb.joinToString(",")

                            val newUser = UserEntity(userId.trim(), userName.trim(), embString)
                            onUserEnrolled(newUser)
                        } else {
                            statusText = "No clear face detected. Look directly at the camera."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Capture & Enroll Template")
            }
        }
    }
}
