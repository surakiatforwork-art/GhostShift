package com.phantom.ghostshift.ui

import android.net.Uri
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.phantom.ghostshift.data.PhotoEntity
import com.phantom.ghostshift.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File

private data class PhotoPair(val index: Int, val inPhoto: PhotoEntity?, val outPhoto: PhotoEntity?) {
    val stableId: Long get() = inPhoto?.id ?: outPhoto?.id ?: index.toLong()
    val remark: String? get() = inPhoto?.remark?.takeIf { it.isNotBlank() } ?: outPhoto?.remark?.takeIf { it.isNotBlank() }
    val fullyDownloaded: Boolean get() = inPhoto?.downloadedAt != null && outPhoto?.downloadedAt != null
    val isReorderable: Boolean get() = !inPhoto?.filePath.isNullOrBlank() && !outPhoto?.filePath.isNullOrBlank() &&
        inPhoto?.downloadedAt == null && outPhoto?.downloadedAt == null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(), themeMode: String = "system", onThemeModeChanged: (String) -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val progress by viewModel.exportProgress.collectAsState()
    progress?.let { ExportProgressDialog(it, viewModel::dismissExportError) }
    var confirmIn by remember { mutableStateOf<PhotoEntity?>(null) }
    var reservePair by remember { mutableStateOf(false) }
    var pairRemark by remember { mutableStateOf("") }
    confirmIn?.let { photo ->
        GhostDialog(
            onDismissRequest = { confirmIn = null },
            title = { Text("ดาวน์โหลด ${photo.tag}?") },
            text = { Text(photo.remark ?: "ยืนยันเมื่อพร้อมใช้ภาพ IN นี้") },
            confirmButton = { GhostDialogButton(onClick = {
                confirmIn = null
                viewModel.exportNextPhoto(photo.id, confirmedIn = true)
            }) { Text("ดาวน์โหลด") } },
            dismissButton = { GhostDialogButton(tone = UtilityTone.Neutral, onClick = { confirmIn = null }) { Text("ยกเลิก") } }
        )
    }
    if (reservePair) {
        GhostDialog(
            onDismissRequest = { reservePair = false },
            title = { Text("เพิ่มคู่ภาพเปล่า") },
            text = { OutlinedTextField(pairRemark, { pairRemark = it.take(120) }, label = { Text("ชื่อสาขา / Remark (ไม่บังคับ)") }) },
            confirmButton = { GhostDialogButton(tone = UtilityTone.Aqua, onClick = {
                viewModel.reservePair(pairRemark)
                reservePair = false
            }) { Text("เพิ่มคู่") } },
            dismissButton = { GhostDialogButton(tone = UtilityTone.Neutral, onClick = { reservePair = false }) { Text("ยกเลิก") } }
        )
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snack = remember { SnackbarHostState() }

    // State for Camera/Edit
    var showCamera by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editTargetId by remember { mutableStateOf<Long?>(null) } // if null, adding new; if set, replacing
    
    // Dialog States
    var gateDialog by remember { mutableStateOf(false) }
    var confirmResetTimer by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDeletePendingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }
    var confirmDeletePendingPair by remember { mutableStateOf<PhotoPair?>(null) }
    var editChoicePair by remember { mutableStateOf<PhotoPair?>(null) }
    var deleteChoicePair by remember { mutableStateOf<PhotoPair?>(null) }
    var reorderMode by remember { mutableStateOf(false) }
    var reorderPairs by remember { mutableStateOf<List<PhotoPair>>(emptyList()) }
    var draggedPairId by remember { mutableStateOf<Long?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var committingOrder by remember { mutableStateOf(false) }
    var originalOrder by remember { mutableStateOf<List<Long>>(emptyList()) }

    val allPairs = remember(state.pendingPhotos, state.downloadedPhotos) { (state.pendingPhotos + state.downloadedPhotos)
        .groupBy { it.idx }
        .toSortedMap()
        .map { (index, photos) ->
            PhotoPair(index, photos.firstOrNull { it.kind.name == "IN" }, photos.firstOrNull { it.kind.name == "OUT" })
        }
    }
    val pendingPairs = remember(allPairs) { allPairs.filterNot { it.fullyDownloaded } }
    val downloadedPairs = remember(allPairs) { allPairs.filter { it.fullyDownloaded } }
    val visiblePendingPairs = if (reorderMode) reorderPairs else pendingPairs

    val latestPending by rememberUpdatedState(pendingPairs)
    fun resetDrag() {
        draggedPairId = null
        draggedOffsetY = 0f
        reorderMode = false
        committingOrder = false
    }
    LaunchedEffect(pendingPairs, committingOrder) {
        if (committingOrder && pendingPairs.filter { it.isReorderable }.map { it.stableId } ==
            reorderPairs.filter { it.isReorderable }.map { it.stableId }) resetDrag()
        if (reorderMode && pendingPairs.map { it.stableId }.toSet() != reorderPairs.map { it.stableId }.toSet()) resetDrag()
    }
    LaunchedEffect(committingOrder) {
        if (committingOrder) { delay(2000); resetDrag() }
    }
    fun moveDragged() {
        val id = draggedPairId ?: return
        val info = listState.layoutInfo.visibleItemsInfo
        val dragged = info.firstOrNull { it.key == "pending-$id" } ?: return
        draggedOffsetY = dragCenter - dragged.offset - dragged.size / 2f
        val from = reorderPairs.indexOfFirst { it.stableId == id }
        val target = info.firstOrNull { item ->
            val targetId = (item.key as? String)?.removePrefix("pending-")?.toLongOrNull()
            val to = reorderPairs.indexOfFirst { it.stableId == targetId && it.isReorderable }
            item.key != dragged.key && to >= 0 && from >= 0 &&
                if (to > from) dragCenter >= item.offset + item.size / 2f && dragCenter <= item.offset + item.size
                else dragCenter <= item.offset + item.size / 2f && dragCenter >= item.offset
        } ?: return
        val to = reorderPairs.indexOfFirst { "pending-${it.stableId}" == target.key }
        if (from != to && from >= 0 && to >= 0) {
            // Preserve the slots of incomplete/exported pairs.
            val eligible = reorderPairs.filter { it.isReorderable }.toMutableList()
            val f = eligible.indexOfFirst { it.stableId == id }
            val t = eligible.indexOfFirst { "pending-${it.stableId}" == target.key }
            if (f < 0 || t < 0) return
            eligible.add(t, eligible.removeAt(f))
            val iterator = eligible.iterator()
            reorderPairs = reorderPairs.map { if (it.isReorderable) iterator.next() else it }
            draggedOffsetY = dragCenter - target.offset - dragged.size / 2f
        }
    }
    LaunchedEffect(draggedPairId) {
        while (draggedPairId != null) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val speed = when {
                dragCenter < layout.viewportStartOffset + edgePx ->
                    -((layout.viewportStartOffset + edgePx - dragCenter) / edgePx).coerceIn(0f, 1f) * edgePx / 6f
                dragCenter > layout.viewportEndOffset - edgePx ->
                    ((dragCenter - layout.viewportEndOffset + edgePx) / edgePx).coerceIn(0f, 1f) * edgePx / 6f
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed)
            moveDragged()
        }
    }

    // Internal Camera Output
    fun getOutputDirectory(context: Context): File {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, "GhostShiftPhotos").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
    }

    // Handlers
    fun onPhotoCaptured(uri: Uri, mirrorHorizontally: Boolean) {
        showCamera = false
        val rid = editTargetId
        if (rid != null) {
            viewModel.replacePhoto(rid, uri, mirrorHorizontally)
            editTargetId = null
        } else {
            viewModel.addPhotoFromPicker(uri, mirrorHorizontally)
        }
    }

    // Permission launcher for Camera
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showCamera = true
    }

    fun launchCamera(targetId: Long? = null) {
        editTargetId = targetId
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
             showCamera = true
        } else {
             permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // Gallery Launcher for single-select (replace/edit)
    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val rid = editTargetId
        if (rid != null) {
            viewModel.replacePhoto(rid, uri)
            editTargetId = null
        } else {
            viewModel.addPhotoFromPicker(uri)
        }
    }

    // Gallery launcher for multi-select (add)
    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        viewModel.addPhotosFromPicker(uris)
    }
    
    // Notification Permission (Alarm)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
             viewModel.startTimer910()
        } else {
             scope.launch { snack.showSnackbar("Warning: Notifications are disabled.") }
             viewModel.startTimer910()
        }
    }

    // Preview State
    var previewPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    if (showCamera) {
        CameraScreen(
            outputDirectory = getOutputDirectory(context),
            onImageCaptured = { uri, mirrorHorizontally -> onPhotoCaptured(uri, mirrorHorizontally) },
            onClose = { showCamera = false }
        )
        return // Show only camera
    }

    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            state = state,
            onSoundSelected = { uri -> viewModel.setSound(uri) },
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            onTargetMinutesSelected = { viewModel.setTargetMinutes(it) },
            onFinishTimeSelected = { viewModel.setFinishTime(it) },
            onTimerEnabledChanged = { viewModel.setTimerEnabled(it) },
            onScheduleSettingsChanged = { settings -> viewModel.saveScheduleSettings(settings) },
            onResetTimer = { viewModel.resetTimer() },
            onBack = { showSettings = false }
        )
        return
    }

    // Preview Dialog
    if (previewPhoto != null) {
        val p = previewPhoto!!
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().background(Color.Black),
            title = null,
            text = null,
            confirmButton = {},
            dismissButton = {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("file://${p.filePath}")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { previewPhoto = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha=0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        )
    }

    if (showEditDialog) {
        GhostDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("เลือกแหล่งภาพ") },
            text = { Text("ถ่ายภาพใหม่หรือเลือกจากแกลเลอรี่") },
            confirmButton = {
                GhostDialogButton(tone = UtilityTone.Sky, onClick = {
                    showEditDialog = false
                    launchCamera(editTargetId)
                }) { Text("ถ่ายภาพ") }
            },
            dismissButton = {
                GhostDialogButton(tone = UtilityTone.Lavender, onClick = {
                    showEditDialog = false
                    singlePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("แกลเลอรี่") }
            }
        )
    }

    editChoicePair?.let { pair ->
        PhotoChoiceDialog(
            title = "เลือกภาพที่ต้องการแก้ไข",
            pair = pair,
            allowDownloaded = false,
            onRemarkSaved = { remark -> viewModel.savePairRemark(pair.index, remark) },
            onDismiss = { editChoicePair = null },
            onSelected = { photo ->
                editChoicePair = null
                editTargetId = photo.id
                showEditDialog = true
            }
        )
    }

    deleteChoicePair?.let { pair ->
        PhotoChoiceDialog(
            title = "เลือกภาพที่ต้องการลบ",
            pair = pair,
            allowDownloaded = false,
            onDismiss = { deleteChoicePair = null },
            onSelected = { photo ->
                deleteChoicePair = null
                confirmDeletePendingPhoto = photo
            },
            onDeleteBoth = {
                deleteChoicePair = null
                confirmDeletePendingPair = pair
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MintBg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            StatusHeader(
                state = state,
                onDeleteAll = { confirmDeleteAll = true },
                onDownloadNext = {
                    state.pendingPhotos.firstOrNull()?.takeIf { it.filePath.isNotBlank() && !state.isExportingNext }?.let { photo ->
                        if (photo.kind.name == "IN") confirmIn = photo
                        else viewModel.exportNextPhoto(photo.id)
                    }
                },
                onSettings = { showSettings = true }
            )
        },
        floatingActionButton = {
            ClayIconButton(onClick = { pairRemark = ""; reservePair = true }, icon = Icons.Default.Add,
                contentDescription = "เพิ่มคู่ภาพเปล่า", modifier = Modifier.size(52.dp), tone = UtilityTone.Aqua)
        },
        bottomBar = {
            Column {
            StickyBottomBar(
                state = state,
                onStart = {
                    // Web T1: Start is NOT gated. Alarm is gated in VM.
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.startTimer910()
                    }
                },
                onCam = {
                    launchCamera()
                },
                onUpload = {
                    editTargetId = null
                    multiPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 76.dp)
        ) {
            item {
                SectionHeaderCard(
                    title = "Pending",
                    count = pendingPairs.size,
                    hint = "กดค้างแล้วลากเพื่อจัดเรียง"
                )
            }

            items(visiblePendingPairs, key = { "pending-${it.stableId}" }) { pair ->
                val isDragged = draggedPairId == pair.stableId
                val reorderModifier = Modifier
                    .zIndex(if (isDragged) 2f else 0f)
                    .then(if (!isDragged) Modifier.animateItemPlacement(tween(170)) else Modifier)
                    .graphicsLayer {
                        translationY = if (draggedPairId == pair.stableId) draggedOffsetY else 0f
                        scaleX = if (draggedPairId == pair.stableId) 1.015f else 1f
                        scaleY = scaleX
                        shadowElevation = if (draggedPairId == pair.stableId) 8.dp.toPx() else 0f
                    }
                    .then(if (pair.isReorderable) Modifier.pointerInput(pair.stableId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                if (!committingOrder && draggedPairId == null) {
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "pending-${pair.stableId}" }
                                    if (info != null) {
                                        reorderPairs = latestPending
                                        originalOrder = reorderPairs.filter { it.isReorderable }.map { it.stableId }
                                        dragCenter = info.offset + info.size / 2f
                                        draggedOffsetY = 0f
                                        draggedPairId = pair.stableId
                                        reorderMode = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                            onDrag = { change, delta ->
                                if (draggedPairId == pair.stableId) {
                                    change.consume()
                                    dragCenter += delta.y
                                    moveDragged()
                                }
                            },
                            onDragEnd = {
                                if (draggedPairId == pair.stableId) {
                                    val order = reorderPairs.filter { it.isReorderable }.map { it.stableId }
                                    draggedPairId = null
                                    draggedOffsetY = 0f
                                    if (order != originalOrder) {
                                        committingOrder = true
                                        viewModel.reorderCompletePendingPairs(order)
                                    } else resetDrag()
                                }
                            },
                            onDragCancel = { if (draggedPairId == pair.stableId) resetDrag() }
                        )
                    } else Modifier)
                PhotoPairCard(
                    modifier = Modifier.fillMaxWidth().then(reorderModifier),
                    pair = pair,
                    planAtByTag = state.schedule.planAtByTag,
                    currentTime = if (state.scheduleSettings.timerEnabled && !pair.fullyDownloaded) state.currentTime else 0L,
                    onEdit = if (reorderMode) null else { { editChoicePair = pair } },
                    onDelete = if (reorderMode) null else { { deleteChoicePair = pair } },
                    onSwap = if (reorderMode) null else { { viewModel.swapPair(pair.index) } },
                    reorderMode = false,
                    onPreview = { previewPhoto = it }
                )
            }

            item {
                SectionHeaderCard(
                    title = "Downloaded (Exported)",
                    count = downloadedPairs.size,
                    hint = if (downloadedPairs.isEmpty()) "ยังไม่มีคู่ที่ Export ครบ" else "คู่ที่ Export ครบทั้ง IN และ OUT"
                )
            }

            items(downloadedPairs, key = { "downloaded-${it.index}" }) { pair ->
                PhotoPairCard(
                    pair = pair,
                    planAtByTag = state.schedule.planAtByTag,
                    currentTime = if (state.scheduleSettings.timerEnabled && !pair.fullyDownloaded) state.currentTime else 0L,
                    onEdit = null,
                    onDelete = null,
                    onSwap = null,
                    reorderMode = false,
                    onPreview = { previewPhoto = it }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    }

    if (confirmDeleteAll) {
        GhostDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("ล้างข้อมูลทั้งหมด") },
            text = { Text("จะลบรูป/ข้อมูลทั้งหมด และรีเซ็ตตัวจับเวลา/การแจ้งเตือนทั้งหมด\n\nยืนยันหรือไม่?") },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(
                    onClick = {
                        confirmDeleteAll = false
                        viewModel.deleteAll()
                    }, tone = UtilityTone.Coral
                ) { Text("ลบทั้งหมด") }
            },
            dismissButton = {
                GhostDialogButton(
                    onClick = { confirmDeleteAll = false }, tone = UtilityTone.Neutral
                ) { Text("ยกเลิก") }
            }
        )
    }

    if (confirmDeletePendingPhoto != null) {
        val target = confirmDeletePendingPhoto!!
        GhostDialog(
            onDismissRequest = { confirmDeletePendingPhoto = null },
            title = { Text("ลบรูป ${target.tag}") },
            text = { Text("รูปนี้จะถูกลบ และรูป pending ถัดไปจะเลื่อนลำดับมาแทนที่อัตโนมัติ\n\nยืนยันหรือไม่?") },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(
                    onClick = {
                        confirmDeletePendingPhoto = null
                        viewModel.deletePendingPhoto(target.id)
                    }, tone = UtilityTone.Coral
                ) { Text("ลบรูป") }
            },
            dismissButton = {
                GhostDialogButton(
                    onClick = { confirmDeletePendingPhoto = null }, tone = UtilityTone.Neutral
                ) { Text("ยกเลิก") }
            }
        )
    }

    if (confirmDeletePendingPair != null) {
        val pair = confirmDeletePendingPair!!
        GhostDialog(
            onDismissRequest = { confirmDeletePendingPair = null },
            title = { Text("ลบทั้งคู่ IN-${pair.index} และ OUT-${pair.index}") },
            text = { Text("รูป pending ทั้ง 2 รูปจะถูกลบ และรูป pending ถัดไปจะเลื่อนลำดับมาแทนที่อัตโนมัติ\n\nยืนยันหรือไม่?") },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(
                    onClick = {
                        confirmDeletePendingPair = null
                        viewModel.deletePendingPair(pair.index)
                    }, tone = UtilityTone.Coral
                ) { Text("ลบทั้ง 2 รูป") }
            },
            dismissButton = {
                GhostDialogButton(
                    onClick = { confirmDeletePendingPair = null }, tone = UtilityTone.Neutral
                ) { Text("ยกเลิก") }
            }
        )
    }

    if (confirmResetTimer) {
        GhostDialog(
            onDismissRequest = { confirmResetTimer = false },
            title = { Text("รีเซ็ตตัวจับเวลา") },
            text = { Text("จะหยุดการจับเวลาและยกเลิกการแจ้งเตือน (Alarm) ทั้งหมด\n\nยืนยันหรือไม่?") },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(
                    onClick = {
                        confirmResetTimer = false
                        viewModel.resetTimer()
                    }, tone = UtilityTone.Coral
                ) { Text("รีเซ็ต") }
            },
            dismissButton = {
                GhostDialogButton(
                    onClick = { confirmResetTimer = false }, tone = UtilityTone.Neutral
                ) { Text("ยกเลิก") }
            }
        )
    }

    if (gateDialog) {
        GhostDialog(
            onDismissRequest = { gateDialog = false },
            title = { Text("เงื่อนไขก่อนเริ่มตารางแจ้งเตือน") },
            text = {
                Text(
                    "ต้อง Export (Download) รูปอย่างน้อย 1 รูปก่อน\n" +
                        "เพื่อให้ระบบ “เปิด Gate” และเริ่มคำนวณตารางแจ้งเตือนแบบแม่นยำ\n\n" +
                        "หมายเหตุ: คุณยังสามารถเพิ่ม/แก้รูปได้ตามปกติ แต่การตั้ง Alarm จะยังไม่เริ่มจนกว่าจะ Export อย่างน้อย 1 รูป"
                )
            },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(onClick = { gateDialog = false }, tone = UtilityTone.Mint) { Text("เข้าใจแล้ว") }
            }
        )
    }
}

@Composable
fun StatusHeader(
    state: MainUiState,
    onDeleteAll: () -> Unit,
    onDownloadNext: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MintBg, // blend with background
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "GhostShift",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MintText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val badgeText = "${state.downloadedPhotos.size}/${state.pendingPhotos.size + state.downloadedPhotos.size}"
                MintBadge(text = badgeText, type = if (state.gateOpen) "ok" else "wait")
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete all", tint = MintDanger, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MintText, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                // Align Next with the ring itself, not with the labels below the rings.
                verticalAlignment = Alignment.Top
            ) {
                val target = state.timer.targetAt
                val nextAt = state.schedule.nextAt
                val nextTag = state.schedule.nextTag
                val intervalStartedAt = state.downloadedPhotos
                    .maxOfOrNull { it.downloadedAt ?: Long.MIN_VALUE }
                    ?.takeIf { it != Long.MIN_VALUE }
                    ?: state.timer.startAt
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.scheduleSettings.timerEnabled) {
                    TargetProgressCard(targetAt = target, progress = state.targetCoverageProgress)
                    NextCountdownCard(
                        nextTag = nextTag,
                        nextAt = nextAt,
                        intervalStartedAt = intervalStartedAt,
                        now = state.currentTime
                    )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("หลัง Next ล่าสุด", color = MintMuted, style = MaterialTheme.typography.bodySmall)
                            Text(state.lastNextAt?.let { fmtDuration((state.currentTime - it).coerceAtLeast(0L)) } ?: "--:--:--", color = MintText, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                ClayActionButton(
                    text = if (state.isExportingNext) "กำลังบันทึก" else "Download Next",
                    onClick = onDownloadNext,
                    enabled = !state.pendingPhotos.firstOrNull()?.filePath.isNullOrBlank() && !state.isExportingNext,
                    icon = Icons.Default.DownloadForOffline,
                    modifier = Modifier.weight(1f).height(64.dp)
                )
            }
            if (state.scheduleSettings.timerEnabled) Text(
                state.lastNextAt?.let { "Next ล่าสุด: " + fmtDuration((state.currentTime - it).coerceAtLeast(0L)) + " ที่ผ่านมา" }
                    ?: "Next ล่าสุด: ยังไม่ได้ดาวน์โหลด",
                color = MintMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

}

@Composable
private fun ScheduleBlock(state: MainUiState) {
    val schedule = state.schedule

    val nextAt = schedule.nextAt
    val nextTag = schedule.nextTag

    val countdownMs = if (nextAt != null) max(0L, nextAt - state.currentTime) else null

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MintText)
            Spacer(Modifier.width(8.dp))
            Text(
                "Schedule",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MintText
            )
            Spacer(Modifier.weight(1f))

            val alarmTone = if (state.alarmActive) "ok" else "wait"
            MintBadge(text = if (state.alarmActive) "ALARM ON" else "ALARM OFF", type = alarmTone)
        }

        if (!schedule.ok) {
            val scheduleMessage = when {
                schedule.error.isNotBlank() -> schedule.error
                schedule.warn.isNotBlank() -> schedule.warn
                else -> "Schedule waiting..."
            }
            Text(
                scheduleMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MintWarn
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPill(
                    label = "Next",
                    value = nextTag ?: "-",
                    icon = Icons.Default.Flag,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "When",
                    value = if (nextAt != null) fmtTime(nextAt) else "-",
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Countdown",
                    value = if (countdownMs != null) fmtDuration(countdownMs) else "-",
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StickyBottomBar(
    state: MainUiState,
    onStart: () -> Unit,
    onCam: () -> Unit,
    onUpload: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MintCardBg // Make it distinct
    ) {
        Column {
            Divider(color = MintLine, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayTarget = state.scheduleSettings.finishOverrideAt?.takeIf { it > state.currentTime }
                    ?: (state.currentTime + state.scheduleSettings.targetMinutes * 60_000L)
                val finalPhotoAt = state.schedule.items.lastOrNull()?.planAt
                val btnLabel = when {
                    !state.timer.running -> "Start (Finish ${fmtTime(displayTarget)})"
                    finalPhotoAt != null -> "Last photo in ${fmtDuration((finalPhotoAt - state.currentTime).coerceAtLeast(0L))}"
                    else -> "Running..."
                }

                if (state.scheduleSettings.timerEnabled) ClayActionButton(
                    text = btnLabel,
                    onClick = onStart,
                    enabled = state.canStartTimer,
                    icon = Icons.Default.PlayArrow,
                    emphasized = false,
                    tone = UtilityTone.Peach,
                    modifier = Modifier.weight(1.5f).height(48.dp)
                )
                if (!state.scheduleSettings.timerEnabled) {
                    ClayActionButton(text = "ถ่ายภาพ", onClick = onCam, icon = Icons.Default.PhotoCamera, tone = UtilityTone.Sky, modifier = Modifier.weight(2.3f).height(48.dp))
                } else ClayIconButton(
                    onClick = onCam,
                    icon = Icons.Default.PhotoCamera,
                    contentDescription = "ถ่ายภาพ",
                    tone = UtilityTone.Sky,
                    modifier = Modifier.weight(0.8f).height(48.dp)
                )
                ClayIconButton(
                    onClick = onUpload,
                    icon = Icons.Default.Upload,
                    contentDescription = "เลือกภาพจากแกลเลอรี่",
                    tone = UtilityTone.Lavender,
                    modifier = Modifier.weight(0.8f).height(48.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderCard(
    title: String,
    count: Int,
    hint: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    compactActionOnly: Boolean = false
) {
    MintCard(contentPadding = if (compactActionOnly) 2.dp else 8.dp) {
        if (compactActionOnly && !actionText.isNullOrBlank() && onAction != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = MintSoft, contentColor = MintOkText),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) { Text(actionText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall) }
            }
            return@MintCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MintText,
                modifier = Modifier.weight(1f)
            )
            MintBadge(text = "$count", type = if (count > 0) "ok" else "wait")
            if (!actionText.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = MintSoft, contentColor = MintOkText),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) { Text(actionText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MintMuted, maxLines = 1)
    }
}

@Composable
private fun PhotoPairCard(
    pair: PhotoPair,
    planAtByTag: Map<String, Long>,
    currentTime: Long,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onSwap: (() -> Unit)?,
    reorderMode: Boolean,
    onPreview: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val canSwap = pair.inPhoto != null && pair.outPhoto != null &&
        pair.inPhoto.downloadedAt == null && pair.outPhoto.downloadedAt == null
    MintCard(modifier = modifier, containerColor = MintCardBg, contentPadding = 10.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                    if (reorderMode) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = if (pair.isReorderable) "Drag to reorder" else "Pair cannot be reordered",
                            tint = if (pair.isReorderable) MintAccent else MintMuted,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconButton(onClick = { onEdit?.invoke() }, enabled = onEdit != null, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(26.dp))
                            }
                            IconButton(onClick = { onDelete?.invoke() }, enabled = onDelete != null, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MintDanger, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
                Box(Modifier.weight(0.85f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PairPhoto("IN-${pair.index}", pair.inPhoto, planAtByTag[pair.inPhoto?.tag], currentTime, onPreview, Modifier.weight(1f))
                        PairPhoto("OUT-${pair.index}", pair.outPhoto, planAtByTag[pair.outPhoto?.tag], currentTime, onPreview, Modifier.weight(1f))
                    }
                    if (!reorderMode && onSwap != null) {
                        ClayIconButton(
                            onClick = onSwap,
                            enabled = canSwap,
                            icon = Icons.Default.SwapHoriz,
                            contentDescription = "Swap IN and OUT",
                            modifier = Modifier.align(Alignment.Center).size(50.dp)
                        )
                    }
                }
            }
            pair.remark?.let { remark ->
                Text(
                    text = remark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .semantics { contentDescription = "Remark: $remark" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MintText,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PairPhoto(
    label: String,
    photo: PhotoEntity?,
    dueAt: Long?,
    currentTime: Long,
    onPreview: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val contextForImage = LocalContext.current
    val downloaded = photo?.downloadedAt != null
    val due = dueAt != null && currentTime >= dueAt && !downloaded
    val dueBorderAlpha = if (due) rememberInfiniteTransition(label = "dueBorder").animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dueBorderAlpha"
    ).value else 1f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .background(MintSoft, MaterialTheme.shapes.small)
                .then(
                    if (due) {
                        Modifier.border(3.dp, MintDanger.copy(alpha = dueBorderAlpha), MaterialTheme.shapes.small)
                    } else {
                        Modifier
                    }
                )
                .then(if (downloaded) Modifier.alpha(0.42f) else Modifier)
                .clickable(enabled = !photo?.filePath.isNullOrBlank()) { photo?.let(onPreview) }
        ) {
            if (photo != null && photo.filePath.isNotBlank()) {
                AsyncImage(
                    model = remember(photo.filePath) { ImageRequest.Builder(contextForImage).data("file://${photo.filePath}").crossfade(false).build() },
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MintMuted, modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
            if (downloaded) {
                MintBadge(text = "EXPORTED", type = "ok", modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            if (due) {
                Surface(
                    color = MintDanger,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        "ถึงเวลาแล้ว",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        when {
            photo == null || photo.filePath.isBlank() -> Text("$label • ยังไม่มีภาพ", style = MaterialTheme.typography.labelSmall, color = MintMuted, maxLines = 1)
            due -> Text("$label • ถึงเวลาแล้ว", style = MaterialTheme.typography.labelSmall, color = MintDanger, maxLines = 1)
            dueAt != null && !downloaded -> Text("$label • ${fmtTime(dueAt)}", style = MaterialTheme.typography.labelSmall, color = MintAccent, maxLines = 1)
            downloaded -> Text("$label • Exported", style = MaterialTheme.typography.labelSmall, color = MintMuted, maxLines = 1)
            else -> Text("$label • Pending", style = MaterialTheme.typography.labelSmall, color = MintMuted, maxLines = 1)
        }
    }
}

@Composable
private fun PhotoChoiceDialog(
    title: String,
    pair: PhotoPair,
    allowDownloaded: Boolean,
    onRemarkSaved: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSelected: (PhotoEntity) -> Unit,
    onDeleteBoth: (() -> Unit)? = null
) {
    var remark by remember(pair.index, pair.remark) { mutableStateOf(pair.remark.orEmpty()) }
    GhostDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRemarkSaved != null) {
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it.take(120) },
                        label = { Text("Remark เช่น เลขสาขา") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("IN-${pair.index}" to pair.inPhoto, "OUT-${pair.index}" to pair.outPhoto).forEach { (label, photo) ->
                        OutlinedButton(
                            onClick = {
                                onRemarkSaved?.invoke(remark)
                                photo?.let(onSelected)
                            },
                            enabled = photo != null && (allowDownloaded || photo.downloadedAt == null),
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    }
                }
                if (onDeleteBoth != null) {
                    Button(
                        onClick = onDeleteBoth,
                        enabled = pair.inPhoto?.downloadedAt == null && pair.outPhoto?.downloadedAt == null &&
                            pair.inPhoto != null && pair.outPhoto != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MintDanger, contentColor = Color.White)
                    ) { Text("ลบทั้ง 2 รูป") }
                }
            }
        },
        confirmButton = {
            if (onRemarkSaved != null) {
                GhostDialogButton(onClick = {
                    onRemarkSaved(remark)
                    onDismiss()
                }) { Text("บันทึกหมายเหตุ") }
            }
        },
        dismissButton = { GhostDialogButton(tone = UtilityTone.Neutral, onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

@Composable
private fun LegacyPhotoCard(
    modifier: Modifier = Modifier,
    photo: PhotoEntity,
    isDownloaded: Boolean,
    dueAt: Long?,
    currentTime: Long,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    reorderMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onPreview: () -> Unit
) {
    val isDue = dueAt != null && currentTime >= dueAt
    val countdown = if (dueAt != null && dueAt > currentTime) dueAt - currentTime else null
    val cardColor = if (isDue && !isDownloaded) MintWarn.copy(alpha = 0.1f) else MintCardBg

    MintCard(
        modifier = modifier,
        containerColor = cardColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onPreview() },
            verticalAlignment = Alignment.Top
        ) {
            // Thumb
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file://${photo.filePath}")
                    .crossfade(true)
                    .build(),
                contentDescription = photo.tag,
                modifier = Modifier
                    .size(80.dp) // larger thumb
                    .background(Color.Gray),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Row 1: Tag + Badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        photo.tag,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MintText,
                        modifier = Modifier.weight(1f)
                    )

                    if (isDownloaded) {
                        MintBadge(text = "EXPORTED", type = "ok")
                    } else if (isDue) {
                        MintBadge(text = "DUE NOW", type = "err")
                    } else if (countdown != null) {
                        // Phase 2: Per-Card Countdown
                        val cd = fmtDuration(countdown)
                        MintBadge(text = "In $cd", type = "wait")
                    } else {
                        MintBadge(text = "PENDING", type = "wait")
                    }
                }

                // Phase 2: Prominent Plan Time
                if (dueAt != null && !isDownloaded) {
                    Text(
                        "Plan: ${fmtTime(dueAt)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDue) MintDanger else MintAccent
                    )
                }

                // Row 3: Meta
                val metaLine = buildString {
                    append("idx: ${photo.idx} • ${photo.kind}")
                    if (photo.editedAt != null) append(" • edited")
                }
                Text(
                    metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MintMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isDownloaded) {
                    val at = photo.downloadedAt
                    Text(
                        if (at != null) "DownloadedAt: ${fmtDateTime(at)}" else "DownloadedAt: -",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintMuted2
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDownloaded && reorderMode) {
                        OutlinedButton(
                            onClick = { onMoveUp?.invoke() },
                            enabled = canMoveUp && onMoveUp != null,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintText),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                            Spacer(Modifier.width(4.dp))
                            Text("ขึ้น", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onMoveDown?.invoke() },
                            enabled = canMoveDown && onMoveDown != null,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintText),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                            Spacer(Modifier.width(4.dp))
                            Text("ลง", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        if (onEdit != null) {
                            OutlinedButton(
                                onClick = onEdit,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp), // fit content
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MintText),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Edit", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (!isDownloaded && onDelete != null) {
                            Button(
                                onClick = onDelete,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MintDanger, contentColor = Color.White),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Delete", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* -----------------------------
   File-private Mint helpers
------------------------------ */


@Composable
private fun MetricPill(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier // default empty
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MintSoft,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MintMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MintMuted)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MintText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* -----------------------------
   Formatting helpers
------------------------------ */

private fun fmtTime(ms: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}

private fun fmtDateTime(ms: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ms))
}

private fun fmtDateTimeShort(ms: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}

private fun fmtDuration(ms: Long): String {
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
