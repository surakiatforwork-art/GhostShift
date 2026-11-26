package com.phantom.ghostshift.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.first
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
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
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.phantom.ghostshift.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(
    outputDirectory: File,
    onImageCaptured: (Uri, Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) {
        var current: Context? = context
        while (current is android.content.ContextWrapper && current !is MainActivity) {
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        current as? MainActivity
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var captureLocked by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var capturedResult by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    val screenMotion = remember { Animatable(0f) }
    val lensCover = remember { Animatable(1f) }
    val shutterFlash = remember { Animatable(0f) }
    val shutterScale = remember { Animatable(1f) }
    var shutterAnimating by remember { mutableStateOf(false) }
    var cameraBound by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var canSwitch by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }
    val preview = remember { Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build() }
    val imageCapture = remember { ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build() }
    val latestCapture by rememberUpdatedState(onImageCaptured)
    val latestClose by rememberUpdatedState(onClose)

    // Keep camera and its layout alive until the exit transition completes.
    // Fade the controls and cover the preview instead of resizing the viewport.
    LaunchedEffect(closing) {
        if (closing) {
            screenMotion.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            val result = capturedResult
            if (result != null) latestCapture(result.first, result.second) else latestClose()
        } else {
            screenMotion.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        }
    }

    BackHandler { if (!captureLocked && !closing) closing = true }

    DisposableEffect(previewView, lifecycleOwner) {
        val listener = android.view.View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            previewSize = IntSize(right - left, bottom - top)
        }
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { state ->
            streaming = state == PreviewView.StreamState.STREAMING
        }
        previewView.addOnLayoutChangeListener(listener)
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose {
            previewView.removeOnLayoutChangeListener(listener)
            previewView.previewStreamState.removeObserver(observer)
        }
    }

    LaunchedEffect(previewView, previewSize, lensFacing, lifecycleOwner) {
        if (previewSize.width <= 0 || previewSize.height <= 0) return@LaunchedEffect
        cameraBound = false
        error = null
        var provider: ProcessCameraProvider? = null
        try {
            provider = context.getCameraProvider()
            canSwitch = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) &&
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val requested = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!provider.hasCamera(requested)) {
                if (lensFacing == CameraSelector.LENS_FACING_FRONT && provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    return@LaunchedEffect
                }
                error = "ไม่พบกล้องที่ใช้งานได้"
                return@LaunchedEffect
            }
            val viewport = previewView.viewPort ?: throw IllegalStateException("Camera viewport is not ready")
            val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
            preview.targetRotation = rotation
            imageCapture.targetRotation = rotation
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val group = UseCaseGroup.Builder()
                .setViewPort(viewport)
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .build()
            // Shared sensor crop: exactly the visible 3:4 frame, not the full phone screen.
            // PreviewView already mirrors the front camera; the existing repository
            // mirrors the saved front-camera image once, so no extra TextureView flip.
            provider.bindToLifecycle(lifecycleOwner, requested, group)
            cameraBound = true
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("CameraScreen", "Unable to bind camera", e)
            error = "ไม่สามารถเปิดกล้องได้"
        } finally {
            cameraBound = false
            provider?.unbind(preview, imageCapture)
        }
    }

    // Fade out before changing the lens; reveal only after its preview streams.
    // The black cover remains outside the JPEG capture pipeline.
    LaunchedEffect(switching, closing) {
        if (!switching || closing) return@LaunchedEffect
        lensCover.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
        cameraBound = false
        streaming = false
        error = null
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        snapshotFlow { (cameraBound && streaming) || error != null }.first { it }
        lensCover.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        switching = false
    }
    LaunchedEffect(cameraBound, streaming, error, switching, closing) {
        if (switching || closing) return@LaunchedEffect
        lensCover.animateTo(if ((cameraBound && streaming) || error != null) 0f else 1f,
            tween(240, easing = FastOutSlowInEasing))
    }

    val ready = cameraBound && streaming && !captureLocked && !switching && !closing && !shutterAnimating

    // One path for touch and both volume keys, with a synchronous lock
    // against held buttons, rapid taps and simultaneous inputs.
    fun triggerShutter() {
        if (!cameraBound || !streaming || captureLocked || switching || closing || shutterAnimating) return
        captureLocked = true
        shutterAnimating = true
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scope.launch {
            try {
                shutterFlash.snapTo(.92f)
                shutterScale.snapTo(.90f)
                delay(75)
                launch { shutterScale.animateTo(1f, tween(135, easing = FastOutSlowInEasing)) }
                shutterFlash.animateTo(0f, tween(135, easing = FastOutSlowInEasing))
            } finally {
                shutterAnimating = false
            }
        }
        takePhoto(
            filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
            imageCapture = imageCapture,
            outputDirectory = outputDirectory,
            mirrorHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT,
            executor = ContextCompat.getMainExecutor(context),
            onImageCaptured = { uri, mirror -> capturedResult = uri to mirror },
            onError = {
                captureLocked = false
                android.util.Log.e("CameraScreen", "Capture failed", it)
            }
        )
    }
    val latestShutter by rememberUpdatedState(newValue = { triggerShutter() })
    DisposableEffect(activity) {
        val action: () -> Unit = { latestShutter() }
        activity?.cameraShutterAction = action
        onDispose {
            if (activity?.cameraShutterAction === action) activity.cameraShutterAction = null
        }
    }
    LaunchedEffect(capturedResult, shutterAnimating) {
        if (capturedResult != null && !shutterAnimating) closing = true
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101012))
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).graphicsLayer {
                alpha = screenMotion.value
                translationY = -8.dp.toPx() * (1f - screenMotion.value)
            }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { closing = true }, enabled = !captureLocked && !closing) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text("3:4", color = Color.White.copy(alpha = .75f),
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.size(48.dp))
        }

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Fit the full portrait frame inside the available area.
            // Outside the frame is opaque UI background, never part of the photo.
            val frameWidth = minOf(maxWidth, maxHeight * .75f)
            val frameHeight = frameWidth / .75f
            Box(Modifier.size(frameWidth, frameHeight).background(Color.Black),
                contentAlignment = Alignment.Center) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Box(Modifier.matchParentSize().graphicsLayer {
                    alpha = maxOf(lensCover.value, 1f - screenMotion.value)
                }.background(Color(0xFF101012)))
                // Shutter blackout is UI only; never applied to the output image.
                Box(Modifier.matchParentSize().graphicsLayer {
                    alpha = shutterFlash.value
                }.background(Color.Black))
                if (error != null && !closing) {
                    Text(error.orEmpty(), color = Color.White, modifier = Modifier.padding(16.dp))
                } else if ((!cameraBound || !streaming) && !switching && !closing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(112.dp).graphicsLayer {
                alpha = screenMotion.value
                translationY = 10.dp.toPx() * (1f - screenMotion.value)
            }.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(48.dp))
            Button(
                onClick = { triggerShutter() },
                enabled = ready,
                modifier = Modifier.size(80.dp).graphicsLayer {
                    scaleX = shutterScale.value
                    scaleY = shutterScale.value
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White,
                    disabledContainerColor = if (captureLocked || shutterAnimating) Color.White
                        else Color.White.copy(alpha = .35f))
            ) {}
            IconButton(
                modifier = Modifier.offset(x = 14.dp),
                enabled = ready && canSwitch,
                onClick = { if (ready && canSwitch) switching = true }
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera",
                    tint = Color.White.copy(alpha = if (ready && canSwitch) 1f else .4f),
                    modifier = Modifier.size(32.dp))
            }
        }
    }
}

private fun takePhoto(
    filenameFormat: String,
    imageCapture: ImageCapture,
    outputDirectory: File,
    mirrorHorizontally: Boolean,
    executor: Executor,
    onImageCaptured: (Uri, Boolean) -> Unit,
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
                onImageCaptured(android.net.Uri.fromFile(photoFile), mirrorHorizontally)
            }
        }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        if (continuation.isActive) {
            try { continuation.resume(future.get()) }
            catch (e: Exception) { continuation.resumeWithException(e) }
        }
    }, ContextCompat.getMainExecutor(this))
}
