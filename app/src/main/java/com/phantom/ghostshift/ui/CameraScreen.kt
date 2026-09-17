package com.phantom.ghostshift.ui

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import android.view.TextureView
import android.util.Log

// Helper functions for TextureView mirror
private fun findTextureView(v: android.view.View): TextureView? {
    if (v is TextureView) return v
    if (v is android.view.ViewGroup) {
        for (i in 0 until v.childCount) {
            val r = findTextureView(v.getChildAt(i))
            if (r != null) return r
        }
    }
    return null
}

private fun applyMirror(previewView: PreviewView, isFront: Boolean) {
    previewView.post {
        val tv = findTextureView(previewView)
        if (tv != null) {
            tv.scaleX = if (isFront) -1f else 1f   // mirror เฉพาะภาพกล้อง
            Log.d("CameraScreen", "Applied mirror to TextureView: scaleX=${tv.scaleX}")
        } else {
            // ถ้าไม่มี TextureView แปลว่ายังเป็น SurfaceView → mirror ด้วย scaleX จะไม่เห็นผล
            Log.w("CameraScreen", "No TextureView found. Likely SurfaceView; mirror won't work.")
        }
    }
}

@Composable
fun CameraScreen(
    outputDirectory: File,
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var captureLocked by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    // Web: Mirror for front cam is typical. Let's support it or just rely on CameraX default.
    // CameraX PreviewView handles mirroring for front camera automatically in view, but the captured image might not.
    // We will just stick to standard capture for now, focusing on Viewfinder UI parity.
    
    val preview = remember { 
        Preview.Builder()
            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
            .build() 
    }
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
            .build() 
    }
    val cameraSelector = remember(lensFacing) { CameraSelector.Builder().requireLensFacing(lensFacing).build() }
    
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )
    }

    LaunchedEffect(lensFacing, previewViewRef) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        applyMirror(previewView, lensFacing == CameraSelector.LENS_FACING_FRONT)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview with mirror effect for front camera
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // AndroidView with proper initialization order - INSIDE the Box
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        // 1. Set implementationMode FIRST (before surface provider)
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        preview.setSurfaceProvider(surfaceProvider)
                        imageCapture.targetRotation = display?.rotation ?: android.view.Surface.ROTATION_0
                        previewViewRef = this
                    }
                },
                update = { previewView ->
                    imageCapture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    previewViewRef = previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // 4:3 Frame Overlay - shows the capture area
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val maxW = maxWidth
                val maxH = maxHeight
                // Calculate 4:3 portrait frame size
                val frameWidth: androidx.compose.ui.unit.Dp
                val frameHeight: androidx.compose.ui.unit.Dp
                
                // Portrait 3:4 (width:height = 3:4)
                val targetRatio = 3f / 4f
                val screenRatio = maxW / maxH
                
                if (screenRatio > targetRatio) {
                    // Screen is wider - fit to height
                    frameHeight = maxH * 0.85f
                    frameWidth = frameHeight * targetRatio
                } else {
                    // Screen is taller - fit to width
                    frameWidth = maxW * 0.9f
                    frameHeight = frameWidth / targetRatio
                }
                
                // Draw frame border
                Box(
                    modifier = Modifier
                        .size(frameWidth, frameHeight)
                        .border(2.dp, Color.White.copy(alpha = 0.7f))
                )
                
                // Corner indicators
                Canvas(
                    modifier = Modifier.size(frameWidth, frameHeight)
                ) {
                    val cornerLength = 30.dp.toPx()
                    val strokeWidth = 4.dp.toPx()
                    val color = Color.White
                    
                    // Top-left corner
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth)
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth)
                    
                    // Top-right corner
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - cornerLength, 0f), strokeWidth)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, cornerLength), strokeWidth)
                    
                    // Bottom-left corner
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(cornerLength, size.height), strokeWidth)
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - cornerLength), strokeWidth)
                    
                    // Bottom-right corner
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - cornerLength, size.height), strokeWidth)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - cornerLength), strokeWidth)
                }
            }
        }

        // Overlay UI
        // Top Bar: Close, Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            // Flash/Mirror toggles could go here if needed
        }

        // Bottom Bar: Capture, Gallery? Switch Cam
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { 
                 lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT 
                 else CameraSelector.LENS_FACING_BACK
            }) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Button(
                onClick = {
                    if (captureLocked) return@Button
                    captureLocked = true
                    takePhoto(
                        filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
                        imageCapture = imageCapture,
                        outputDirectory = outputDirectory,
                        executor = ContextCompat.getMainExecutor(context),
                        onImageCaptured = { uri ->
                            onImageCaptured(uri)
                        },
                        onError = {
                            captureLocked = false
                        }
                    )
                },
                enabled = !captureLocked,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                // Shutter button (inner circle)
            }

            Spacer(Modifier.size(32.dp)) // Spacer to balance layout
        }
    }
}

private fun takePhoto(
    filenameFormat: String,
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val photoFile = File(
        outputDirectory,
        java.text.SimpleDateFormat(filenameFormat, java.util.Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                onError(exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onImageCaptured(android.net.Uri.fromFile(photoFile))
            }
        }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        continuation.resume(cameraProviderFuture.get())
    }, ContextCompat.getMainExecutor(this))
}
