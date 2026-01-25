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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    outputDirectory: File,
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var scaleType by remember { mutableStateOf(PreviewView.ScaleType.FILL_CENTER) }
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

    // Remember PreviewView instance to apply scaleX directly
    // COMPATIBLE mode uses TextureView which supports scaleX transform
    val previewView = remember {
        PreviewView(context).apply {
            this.scaleType = PreviewView.ScaleType.FIT_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // COMPATIBLE uses TextureView internally, which supports scaleX for mirror
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    
    // Connect preview to PreviewView
    LaunchedEffect(preview) {
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }
    
    // Apply mirror effect when camera changes
    // COMPATIBLE mode uses TextureView which supports scaleX transform for mirror
    LaunchedEffect(lensFacing) {
        val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
        val scale = if (isFront) -1f else 1f
        
        // Debug: Check what implementation is actually used
        val childView = previewView.getChildAt(0)
        val viewType = childView?.javaClass?.simpleName ?: "null"
        println("CameraScreen: PreviewView child type = $viewType")
        
        // Apply scaleX to PreviewView - works because COMPATIBLE uses TextureView
        previewView.scaleX = scale
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview with mirror effect for front camera
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { previewView },
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
                    takePhoto(
                        filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
                        imageCapture = imageCapture,
                        outputDirectory = outputDirectory,
                        executor = ContextCompat.getMainExecutor(context),
                        onImageCaptured = onImageCaptured,
                        onError = { /* Handle error? */ },
                        isFrontFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                    )
                },
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
    onError: (ImageCaptureException) -> Unit,
    isFrontFacing: Boolean
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
                // Process image: fix rotation, enforce portrait 4:3, mirror if front camera
                var bitmap: android.graphics.Bitmap? = null
                var processed: android.graphics.Bitmap? = null
                try {
                    // Read EXIF orientation
                    val exif = android.media.ExifInterface(photoFile.absolutePath)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    
                    // Decode with sample size to save memory
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, options)
                    options.inSampleSize = calculateInSampleSize(options, 1600, 1600)
                    options.inJustDecodeBounds = false
                    
                    bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, options)
                    if (bitmap != null) {
                        val matrix = android.graphics.Matrix()
                        
                        // Apply EXIF rotation to fix orientation issues on some devices
                        when (orientation) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                        }
                        
                        // For front camera: mirror horizontally and rotate 180° to fix upside-down
                        if (isFrontFacing) {
                            matrix.preScale(-1f, 1f)
                            matrix.postRotate(180f)
                        }
                        
                        // Apply transformations
                        var rotated = android.graphics.Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                        if (rotated != bitmap) bitmap.recycle()
                        
                        // Ensure portrait orientation (height > width)
                        if (rotated.width > rotated.height) {
                            val rotateMatrix = android.graphics.Matrix().apply { postRotate(90f) }
                            val portrait = android.graphics.Bitmap.createBitmap(
                                rotated, 0, 0, rotated.width, rotated.height, rotateMatrix, true
                            )
                            rotated.recycle()
                            rotated = portrait
                        }
                        
                        // Crop to 3:4 aspect ratio (portrait)
                        val targetRatio = 3f / 4f
                        val currentRatio = rotated.width.toFloat() / rotated.height.toFloat()
                        
                        processed = if (kotlin.math.abs(currentRatio - targetRatio) > 0.01f) {
                            val cropW: Int
                            val cropH: Int
                            if (currentRatio > targetRatio) {
                                // Too wide, crop width
                                cropH = rotated.height
                                cropW = (cropH * targetRatio).toInt()
                            } else {
                                // Too tall, crop height
                                cropW = rotated.width
                                cropH = (cropW / targetRatio).toInt()
                            }
                            val startX = (rotated.width - cropW) / 2
                            val startY = (rotated.height - cropH) / 2
                            val cropped = android.graphics.Bitmap.createBitmap(rotated, startX, startY, cropW, cropH)
                            if (cropped != rotated) rotated.recycle()
                            cropped
                        } else {
                            rotated
                        }
                        
                        // Save processed image
                        java.io.FileOutputStream(photoFile).use { out ->
                            processed.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        
                        // Clear EXIF orientation since we already rotated
                        val newExif = android.media.ExifInterface(photoFile.absolutePath)
                        newExif.setAttribute(
                            android.media.ExifInterface.TAG_ORIENTATION,
                            android.media.ExifInterface.ORIENTATION_NORMAL.toString()
                        )
                        newExif.saveAttributes()
                    }
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    processed?.recycle()
                    bitmap?.recycle()
                    System.gc()
                }
                onImageCaptured(android.net.Uri.fromFile(photoFile))
            }
        }
    )
}

private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        continuation.resume(cameraProviderFuture.get())
    }, ContextCompat.getMainExecutor(this))
}
