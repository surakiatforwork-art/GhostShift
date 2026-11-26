package com.phantom.ghostshift.ui

import android.net.Uri
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import com.phantom.ghostshift.R
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.updateTransition
import com.phantom.ghostshift.domain.NextPhotoVisibility
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.unit.sp
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

private data class NextThumbnailBounds(val id: Long, val top: Float, val bottom: Float)

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
    val addedFocus by viewModel.pendingFocus.collectAsState()
    var requestedLocate by remember { mutableStateOf<Long?>(null) }
    var highlightPhotoId by remember { mutableStateOf<Long?>(null) }
    var pulseOn by remember { mutableStateOf(false) }
    progress?.let { ExportProgressDialog(it, viewModel::dismissExportError) }
    var confirmIn by remember { mutableStateOf<PhotoEntity?>(null) }
    var reservePair by remember { mutableStateOf(false) }
    var pairRemark by remember { mutableStateOf("") }
    confirmIn?.let { photo ->
        GhostDialog(
            onDismissRequest = { confirmIn = null },
            title = { Text("ดาวน์โหลด ${photo.tag}?") },
            text = { Text(photo.remark ?: "พร้อมดาวน์โหลดภาพนี้แล้วหรือยัง?") },
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
            title = { Text("เพิ่มคู่ภาพ") },
            text = { OutlinedTextField(pairRemark, { pairRemark = it.take(120) }, label = { Text("ชื่อสาขาหรือหมายเหตุ (ไม่บังคับ)") }) },
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
    var holdNeutralColors by remember { mutableStateOf(false) }
    LaunchedEffect(reorderMode) {
        if (reorderMode) {
            holdNeutralColors = true
        } else if (holdNeutralColors) {
            delay(300)
            holdNeutralColors = false
        }
    }

    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableIntStateOf(0) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var grabY by remember { mutableFloatStateOf(0f) }
    val dragDensity = LocalDensity.current

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
    val latestState by rememberUpdatedState(state)
    // Measure only the next thumbnail and the list viewport, not every card.
    var viewportBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var nextBounds by remember { mutableStateOf<NextThumbnailBounds?>(null) }
    val nextPhotoId = state.pendingPhotos.firstOrNull()?.id
    val nextPairKey = pendingPairs.firstOrNull { it.inPhoto?.id == nextPhotoId || it.outPhoto?.id == nextPhotoId }?.let { "pending-${it.stableId}" }
    val nextVisible by remember(nextPhotoId, nextPairKey, listState) {
        derivedStateOf {
            val bounds = nextBounds
            val viewport = viewportBounds
            nextPhotoId != null && bounds?.id == nextPhotoId && viewport != null &&
                listState.layoutInfo.visibleItemsInfo.any { it.key == nextPairKey } &&
                NextPhotoVisibility.isVisible(bounds.top, bounds.bottom, viewport.top, viewport.bottom)
        }
    }
    val measureNext: (Long, Float, Float) -> Unit = remember {
        { id, top, bottom -> nextBounds = NextThumbnailBounds(id, top, bottom) }
    }
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
        if (committingOrder) { delay(10000); resetDrag() }
    }
    var awaitingSlot by remember { mutableStateOf<Int?>(null) }
    fun moveDragged() {
        val id = draggedPairId ?: return
        val info = listState.layoutInfo.visibleItemsInfo
        val from = reorderPairs.indexOfFirst { it.stableId == id }
        if (from < 0) return
        awaitingSlot?.let { slot ->
            val current = info.firstOrNull { it.key == "pending-$id" }
            if (current != null && current.index != slot) return
            awaitingSlot = null
        }
        val center = fingerY - grabY + dragHeight / 2f
        val candidates = info.filter { item ->
            val to = reorderPairs.indexOfFirst { "pending-${it.stableId}" == item.key && it.isReorderable }
            to >= 0 && to != from && if (to > from) center > item.offset + item.size / 2f else center < item.offset + item.size / 2f
        }
        val target = candidates.minByOrNull { kotlin.math.abs(center - (it.offset + it.size / 2f)) } ?: return
        val eligible = reorderPairs.filter { it.isReorderable }.toMutableList()
        val f = eligible.indexOfFirst { it.stableId == id }
        val t = eligible.indexOfFirst { "pending-${it.stableId}" == target.key }
        if (f < 0 || t < 0 || f == t) return
        eligible.add(t, eligible.removeAt(f))
        val iterator = eligible.iterator()
        reorderPairs = reorderPairs.map { if (it.isReorderable) iterator.next() else it }
        awaitingSlot = target.index
    }
    LaunchedEffect(draggedPairId) {
        while (draggedPairId != null) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val speed = when {
                fingerY < layout.viewportStartOffset + edgePx ->
                    -((layout.viewportStartOffset + edgePx - fingerY) / edgePx).coerceIn(0f, 1f) * edgePx / 6f
                fingerY > layout.viewportEndOffset - edgePx ->
                    ((fingerY - layout.viewportEndOffset + edgePx) / edgePx).coerceIn(0f, 1f) * edgePx / 6f
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
    var showActivity by remember { mutableStateOf(false) }
    if (showActivity) {
        BackHandler { showActivity = false }
        GhostActivityScreen(state, onBack = { showActivity = false })
        return
    }

    if (showSettings) {
        BackHandler { showSettings = false }
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

    // The pending section has a single header at index 0. Resolve by stable
    // photo ID, not the mutable IN/OUT slot number or current scroll position.
    suspend fun focusPending(id: Long, blink: Boolean, fast: Boolean = false, smooth: Boolean = false): Boolean {
        val pair = latestPending.firstOrNull { it.inPhoto?.id == id || it.outPhoto?.id == id } ?: return false
        val index = latestPending.indexOf(pair) + 1
        val key = "pending-${pair.stableId}"
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        val layout = listState.layoutInfo
        if (visible == null || visible.offset < layout.viewportStartOffset || visible.offset + visible.size > layout.viewportEndOffset) {
            if (smooth) {
                // Locate is independent of export: favor a comfortable eased movement.
                // Keep a nearby card's current position when possible.
                if (visible != null && visible.size <= layout.viewportEndOffset - layout.viewportStartOffset) {
                    val distance = if (visible.offset < layout.viewportStartOffset)
                        visible.offset - layout.viewportStartOffset
                    else visible.offset + visible.size - layout.viewportEndOffset
                    listState.animateScrollBy(distance.toFloat(), tween(340, easing = FastOutSlowInEasing))
                } else {
                    listState.animateScrollToItem(index)
                }
            } else if (fast) {
                // Keep the existing fast path for due-alarm navigation.
                // A nearby clipped card gets a short bounded scroll; a distant
                // card is placed immediately rather than traversing the list.
                if (visible != null && visible.size <= layout.viewportEndOffset - layout.viewportStartOffset) {
                    val distance = when {
                        visible.offset < layout.viewportStartOffset -> visible.offset - layout.viewportStartOffset
                        visible.offset + visible.size > layout.viewportEndOffset -> visible.offset + visible.size - layout.viewportEndOffset
                        else -> 0
                    }
                    listState.animateScrollBy(distance.toFloat(), tween(100, easing = FastOutSlowInEasing))
                } else {
                    listState.scrollToItem(index)
                }
            } else listState.animateScrollToItem(index)
        }
        // Wait for placement after the lazy list has composed the target card.
        withFrameNanos { }
        if (latestPending.none { it.inPhoto?.id == id || it.outPhoto?.id == id }) return false
        if (blink) {
            highlightPhotoId = id
            try {
                pulseOn = true; delay(160)
            } finally { pulseOn = false; highlightPhotoId = null }
        }
        return true
    }

    val alarmPhoto = state.pendingPhotos.firstOrNull()?.takeIf {
        state.scheduleSettings.timerEnabled && state.timer.running && state.gateOpen &&
            state.schedule.nextTag == it.tag && state.schedule.nextAt?.let { due -> state.currentTime >= due } == true
    }
    // Only one focus per due photo; no repeated scroll on the 1s clock.
    var lastAlarmFocus by remember { mutableStateOf<Long?>(null) }
    val focusBlocked = reorderMode || confirmIn != null || reservePair || progress != null ||
        previewPhoto != null || showEditDialog || editChoicePair != null || deleteChoicePair != null ||
        confirmDeleteAll || confirmResetTimer || confirmDeletePendingPhoto != null || confirmDeletePendingPair != null || gateDialog
    val addedReady = addedFocus?.photoId?.let { id -> pendingPairs.any { it.inPhoto?.id == id || it.outPhoto?.id == id } } == true
    // Locate is intentionally navigation only; it can never export or open confirmation.
    LaunchedEffect(requestedLocate, focusBlocked) {
        val id = requestedLocate ?: return@LaunchedEffect
        if (focusBlocked) { requestedLocate = null; return@LaunchedEffect }
        try {
            val candidate = latestState.pendingPhotos.firstOrNull()
            if (candidate?.id != id || candidate.filePath.isBlank() || latestState.isExportingNext) return@LaunchedEffect
            if (focusPending(id, blink = true, smooth = true) &&
                latestState.schedule.nextTag == candidate.tag &&
                latestState.schedule.nextAt?.let { latestState.currentTime >= it } == true) {
                lastAlarmFocus = id
            }
        } finally { requestedLocate = null }
    }
    LaunchedEffect(addedFocus, addedReady, alarmPhoto?.id, focusBlocked, requestedLocate) {
        if (focusBlocked || requestedLocate != null) return@LaunchedEffect
        if (alarmPhoto == null) lastAlarmFocus = null
        if (alarmPhoto != null && lastAlarmFocus != alarmPhoto.id) {
            if (focusPending(alarmPhoto.id, true, fast = true)) lastAlarmFocus = alarmPhoto.id
        }
        if (addedReady) {
            addedFocus?.let { request ->
                if (focusPending(request.photoId, false)) viewModel.acknowledgeFocus(request)
            }
        }
    }

    // Warn once per conflict episode, not on every clock tick or export.
    var overflowAcknowledged by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val overflow = state.schedule.overflowMs
    LaunchedEffect(overflow > 0L) {
        if (overflow <= 0L) overflowAcknowledged = false
    }
    if (overflow > 0L && !overflowAcknowledged && progress == null && confirmIn == null && !reservePair && requestedLocate == null) {
        val finish = state.schedule.items.lastOrNull()?.planAt
        GhostDialog(
            onDismissRequest = { overflowAcknowledged = true },
            title = { Text("เวลาไม่พอสำหรับภาพที่เหลือ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ภาพที่เหลือ: ${state.pendingPhotos.size} ภาพ")
                    Text("เวลาเลิกงาน: ${state.timer.targetAt?.let { fmtTime(it) } ?: "-"}")
                    Text("คาดว่าจะเสร็จ: ${finish?.let { fmtTime(it) } ?: "-"}")
                    Text("เกินเวลา ${(overflow + 59999L) / 60000L} นาที", color = MintWarn)
                    Text("ระบบจะใช้ช่วงเวลาเดิมที่ตั้งไว้ และแจ้งเตือนต่อไปจนกว่าจะครบทุกภาพ")
                }
            },
            confirmButton = { GhostDialogButton(onClick = { overflowAcknowledged = true }) { Text("รับทราบ") } }
        )
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
            title = { Text("เพิ่มภาพจาก") },
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
            title = "แก้ไขภาพ",
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
            title = "ลบภาพ",
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
                locateNext = nextPhotoId != null && !nextVisible,
                locatingNext = requestedLocate != null,
                nextActionBlocked = focusBlocked,
                onDeleteAll = { if (requestedLocate == null) confirmDeleteAll = true },
                onDownloadNext = { locate ->
                    if (requestedLocate == null && !focusBlocked) {
                        val photo = latestState.pendingPhotos.firstOrNull()?.takeIf { it.filePath.isNotBlank() && !latestState.isExportingNext }
                        if (photo != null && photo.id == nextPhotoId) {
                            if (locate || !nextVisible) requestedLocate = photo.id
                            else if (photo.kind.name == "IN") confirmIn = photo
                            else viewModel.exportNextPhoto(photo.id)
                        }
                    }
                },
                onSettings = { showSettings = true },
                onActivity = { showActivity = true }
            )
        },
        floatingActionButton = {
            ClayIconButton(onClick = { pairRemark = ""; reservePair = true }, icon = Icons.Default.Add,
                contentDescription = "เพิ่มคู่ภาพ", modifier = Modifier.size(52.dp), tone = UtilityTone.Aqua)
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
        Box(Modifier.padding(padding).fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        if (!committingOrder && draggedPairId == null) {
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                position.y >= it.offset && position.y < it.offset + it.size &&
                                    latestPending.any { pair -> "pending-${pair.stableId}" == it.key && pair.isReorderable }
                            }
                            val pair = latestPending.firstOrNull { "pending-${it.stableId}" == info?.key }
                            if (info != null && pair != null) {
                                awaitingSlot = null
                                reorderPairs = latestPending
                                originalOrder = reorderPairs.filter { it.isReorderable }.map { it.stableId }
                                fingerY = position.y
                                grabY = position.y - info.offset
                                dragHeight = info.size
                                draggedPairId = pair.stableId
                                reorderMode = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        if (draggedPairId != null) {
                            change.consume()
                            fingerY = change.position.y
                            moveDragged()
                        }
                    },
                    onDragEnd = {
                        if (draggedPairId != null) {
                            val order = reorderPairs.filter { it.isReorderable }.map { it.stableId }
                            draggedPairId = null
                            if (order != originalOrder) {
                                committingOrder = true
                                viewModel.reorderCompletePendingPairs(order)
                            } else resetDrag()
                        }
                    },
                    onDragCancel = { if (draggedPairId != null) resetDrag() }
                )
            }) {
        LazyColumn(
            state = listState,
            userScrollEnabled = draggedPairId == null && requestedLocate == null,
            modifier = Modifier.fillMaxSize().onGloballyPositioned { viewportBounds = it.boundsInWindow() },
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(bottom = 76.dp)
        ) {
            item {
                SectionHeaderCard(
                    title = "รอดาวน์โหลด",
                    count = pendingPairs.size,
                    hint = "กดค้างเพื่อจัดลำดับ"
                )
            }

            if (pendingPairs.isEmpty()) item {
                GroupBody { QuietGhostState(completed = downloadedPairs.isNotEmpty(),
                    onAdd = { pairRemark = ""; reservePair = true }) }
            }

            items(visiblePendingPairs, key = { "pending-${it.stableId}" }) { pair ->
                val isDragged = draggedPairId == pair.stableId
                val reorderModifier = if (!isDragged) Modifier.animateItemPlacement(tween(150)) else Modifier
                if (isDragged) {
                    GroupBody {
                        val recess = MintText.copy(alpha = .18f)
                        val base = MintBg
                        Spacer(Modifier.fillMaxWidth()
                            .height(with(dragDensity) { (dragHeight - 6.dp.roundToPx()).coerceAtLeast(0).toDp() })
                            .clip(RoundedCornerShape(18.dp))
                            .background(base)
                            .drawWithCache {
                                val depth = 10.dp.toPx().coerceAtMost(size.height / 2f)
                                val top = Brush.verticalGradient(listOf(recess, Color.Transparent), endY = depth)
                                val left = Brush.horizontalGradient(listOf(recess.copy(alpha = .12f), Color.Transparent), endX = depth)
                                val right = Brush.horizontalGradient(listOf(Color.Transparent, recess.copy(alpha = .12f)), startX = size.width - depth, endX = size.width)
                                val bottom = Brush.verticalGradient(listOf(Color.Transparent, recess.copy(alpha = .06f)), startY = size.height - depth, endY = size.height)
                                onDrawBehind {
                                    drawRect(top)
                                    drawRect(left)
                                    drawRect(right)
                                    drawRect(bottom)
                                }
                            })
                    }
                } else GroupBody(modifier = reorderModifier) { PhotoPairCard(
                    modifier = Modifier.fillMaxWidth(),
                    pair = pair,
                    planAtByTag = state.schedule.planAtByTag,
                    currentTime = if (state.scheduleSettings.timerEnabled) state.currentTime else 0L,
                    nextPhotoId = state.pendingPhotos.firstOrNull()?.id,
                    highlightPhotoId = if (pulseOn) highlightPhotoId else null,
                    onNextThumbnailBounds = measureNext,
                    onEdit = if (reorderMode) null else { { editChoicePair = pair } },
                    onDelete = if (reorderMode) null else { { deleteChoicePair = pair } },
                    onSwap = if (reorderMode) null else { { viewModel.swapPair(pair.index) } },
                    reorderMode = false,
                    neutralColor = reorderMode || holdNeutralColors,
                    cardElevation = if (isDragged) 16.dp else if (draggedPairId != null) 6.dp else 2.dp,
                    onPreview = { previewPhoto = it }
                ) }
            }
            item { GroupEnd(); Spacer(Modifier.height(10.dp)) }

            item {
                SectionHeaderCard(
                    title = "ดาวน์โหลดแล้ว",
                    count = downloadedPairs.size,
                    hint = if (downloadedPairs.isEmpty()) "ยังไม่มีประวัติครบคู่" else "ดาวน์โหลดครบทั้ง IN และ OUT"
                )
            }

            items(downloadedPairs, key = { "downloaded-${it.index}" }) { pair ->
                GroupBody { PhotoPairCard(
                    pair = pair,
                    planAtByTag = state.schedule.planAtByTag,
                    currentTime = if (state.scheduleSettings.timerEnabled && !pair.fullyDownloaded) state.currentTime else 0L,
                    onEdit = null,
                    onDelete = null,
                    onSwap = null,
                    reorderMode = false,
                    onPreview = { previewPhoto = it }
                ) }
            }

            item {
                GroupEnd()
                Spacer(Modifier.height(8.dp))
            }
        }
        draggedPairId?.let { id ->
            reorderPairs.firstOrNull { it.stableId == id }?.let { pair ->
                Box(Modifier.fillMaxWidth().offset { IntOffset(0, (fingerY - grabY).roundToInt()) }
                    .zIndex(10f).padding(horizontal = 8.dp)) {
                    PhotoPairCard(pair = pair, planAtByTag = state.schedule.planAtByTag,
                        currentTime = if (state.scheduleSettings.timerEnabled) state.currentTime else 0L,
                        onEdit = null, onDelete = null, onSwap = null, reorderMode = false,
                        onPreview = {}, nextPhotoId = state.pendingPhotos.firstOrNull()?.id,
                        cardElevation = 16.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        }
    }
    }

    if (confirmDeleteAll) {
        GhostDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("ลบข้อมูลทั้งหมด") },
            text = { Text("ภาพและข้อมูลทั้งหมดจะถูกลบ พร้อมหยุดจับเวลาและแจ้งเตือน") },
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
            title = { Text("ลบภาพ ${target.tag}") },
            text = { Text("ภาพนี้จะถูกลบ และระบบจะจัดลำดับภาพที่เหลือให้ใหม่") },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(
                    onClick = {
                        confirmDeletePendingPhoto = null
                        viewModel.deletePendingPhoto(target.id)
                    }, tone = UtilityTone.Coral
                ) { Text("ลบภาพ") }
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
            text = { Text("ภาพทั้งคู่จะถูกลบ และระบบจะจัดลำดับภาพที่เหลือให้ใหม่") },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(
                    onClick = {
                        confirmDeletePendingPair = null
                        viewModel.deletePendingPair(pair.index)
                    }, tone = UtilityTone.Coral
                ) { Text("ลบทั้งคู่") }
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
            text = { Text("หยุดจับเวลาและยกเลิกการแจ้งเตือนทั้งหมด") },
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
            title = { Text("เริ่มการแจ้งเตือน") },
            text = {
                Text(
                    "ดาวน์โหลดภาพแรกก่อน ระบบจึงจะเริ่มแจ้งเตือนภาพถัดไป"
                )
            },
            containerColor = MintCardBg,
            confirmButton = {
                GhostDialogButton(onClick = { gateDialog = false }, tone = UtilityTone.Mint) { Text("รับทราบ") }
            }
        )
    }
}

/** The outer button and touch target stay fixed while its label/icon crossfade. */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
private fun NextNavigationButton(
    locate: Boolean,
    exporting: Boolean,
    enabled: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = if (exporting) 2 else if (locate) 1 else 0
    val label = when (mode) { 2 -> "กำลังดาวน์โหลด"; 1 -> "Next"; else -> "Download Next" }
    val transition = updateTransition(mode, label = "nextActionMode")
    // Do not allow a tap on the outgoing Locate label to trigger Download.
    val stable = transition.currentState == transition.targetState && !transition.isRunning
    GlassControl(
        modifier.semantics { contentDescription = label },
        UtilityTone.Primary, enabled, { if (stable) onClick(locate) }
    ) { fg ->
        transition.Crossfade(animationSpec = tween(260, easing = FastOutSlowInEasing)) { current ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (current == 1) Icons.Default.ArrowUpward else Icons.Default.DownloadForOffline,
                    contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(5.dp))
                Text(when (current) { 2 -> "กำลังดาวน์โหลด"; 1 -> "Next"; else -> "Download Next" },
                    color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
fun StatusHeader(
    state: MainUiState,
    onDeleteAll: () -> Unit,
    onDownloadNext: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onActivity: () -> Unit,
    locateNext: Boolean = false,
    locatingNext: Boolean = false,
    nextActionBlocked: Boolean = false
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
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    GhostMark(Modifier.size(36.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "GhostShift",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MintText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onActivity, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.BarChart, "สรุปงาน", tint = MintAccent) }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete all", tint = MintDanger, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MintText, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = (maxWidth * .016f).coerceIn(4.dp, 8.dp)
                val buttonHeight = (maxWidth * .14f).coerceIn(48.dp, 72.dp)
                val elapsedHeight = buttonHeight * .5f
                val headerHeight = buttonHeight + elapsedHeight + gap
                val enabledHeaderHeight = headerHeight * 1.5f
                val enabledButtonHeight = (enabledHeaderHeight - gap) / 2
                val enabledElapsedHeight = enabledButtonHeight * .5f
                val timerHeight = enabledHeaderHeight - enabledElapsedHeight - gap
                val ringSize = minOf((maxWidth - gap * 3) / 4 - 6.dp, timerHeight - 18.dp).coerceAtLeast(30.dp)
                val intervalStartedAt = state.downloadedPhotos.maxOfOrNull { it.downloadedAt ?: Long.MIN_VALUE }
                    ?.takeIf { it != Long.MIN_VALUE } ?: state.timer.startAt
                val elapsed = state.lastNextAt?.takeIf { state.downloadedPhotos.isNotEmpty() }?.let { fmtDuration((state.currentTime - it).coerceAtLeast(0L)) } ?: "--:--:--"
                val nextEnabled = !state.pendingPhotos.firstOrNull()?.filePath.isNullOrBlank() && !state.isExportingNext && !locatingNext && !nextActionBlocked
                if (!state.scheduleSettings.timerEnabled) {
                    Column(Modifier.fillMaxWidth().height(headerHeight), verticalArrangement = Arrangement.spacedBy(gap)) {
                        ElapsedPhotoCard(elapsed, Modifier.fillMaxWidth().height(elapsedHeight))
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            CompactOverview(state, Modifier.weight(1f).fillMaxHeight(), onActivity, horizontal = true)
                            NextNavigationButton(
                                locate = locateNext, exporting = state.isExportingNext,
                                onClick = onDownloadNext, enabled = nextEnabled,
                                modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(enabledHeaderHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                MintCard(Modifier.weight(1f).fillMaxHeight(), containerColor = headerPastel(UtilityTone.Mint), contentPadding = 1.dp) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        TargetProgressCard(state.timer.targetAt, state.targetCoverageProgress, ringSize = ringSize)
                                    }
                                }
                                MintCard(Modifier.weight(1f).fillMaxHeight(), containerColor = headerPastel(UtilityTone.Sky), contentPadding = 1.dp) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        NextCountdownCard(state.schedule.nextTag, state.schedule.nextAt, intervalStartedAt, state.currentTime, ringSize = ringSize)
                                    }
                                }
                            }
                            ElapsedPhotoCard(elapsed, Modifier.fillMaxWidth().height(enabledElapsedHeight))
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            CompactOverview(state, Modifier.fillMaxWidth().weight(1f), onActivity, horizontal = true)
                            NextNavigationButton(
                                locate = locateNext, exporting = state.isExportingNext,
                                onClick = onDownloadNext, enabled = nextEnabled,
                                modifier = Modifier.fillMaxWidth().height(enabledButtonHeight))
                        }
                    }
                }
            }
            WorkflowStatus(state)
            if (state.schedule.overflowMs > 0L) {
                val minutes = (state.schedule.overflowMs + 59999L) / 60000L
                Text(if (state.currentTime >= (state.timer.targetAt ?: Long.MAX_VALUE))
                    "เลยเวลาเลิกงานแล้ว · คาดว่าจะเกิน $minutes นาที"
                    else "คาดว่าจะเกินเวลา $minutes นาที",
                    color = MintWarn, style = MaterialTheme.typography.labelMedium)
            }

        }
    }

}

@Composable
private fun ElapsedPhotoCard(elapsed: String, modifier: Modifier = Modifier) {
    val dark = LocalGhostDark.current
    val ink = if (dark) Color(0xFFFFCBA4) else Color(0xFF934B25)
    MintCard(modifier, containerColor = headerPastel(UtilityTone.Peach), contentPadding = 0.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 220.dp
            val iconSize = (maxHeight * .72f).coerceIn(16.dp, 28.dp)
            val gap = (maxWidth * .022f).coerceIn(3.dp, 9.dp)
            Row(Modifier.fillMaxSize().padding(horizontal = gap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.size(iconSize).clip(CircleShape).background(ink.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = ink,
                        modifier = Modifier.size(iconSize * .63f))
                }
                Text("จากภาพล่าสุด", color = ink,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(9.dp)).background(ink.copy(alpha = .08f))
                    .padding(horizontal = gap, vertical = 1.dp), contentAlignment = Alignment.Center) {
                    Text(elapsed, color = ink, fontWeight = FontWeight.Bold,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1)
                }
            }
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
                    modifier = Modifier.weight(1.15f).height(48.dp)
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
    GroupHeader {
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
            return@GroupHeader
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
            Surface(
                color = if (LocalGhostDark.current) Color(0xFF343842) else Color(0xFFF0F1F4),
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text("$count", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MintMuted, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium)
            }
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
    modifier: Modifier = Modifier,
    nextPhotoId: Long? = null,
    cardElevation: androidx.compose.ui.unit.Dp = 1.dp,
    neutralColor: Boolean = false,
    highlightPhotoId: Long? = null,
    onNextThumbnailBounds: ((Long, Float, Float) -> Unit)? = null
) {
    val canSwap = pair.inPhoto != null && pair.outPhoto != null &&
        pair.inPhoto.downloadedAt == null && pair.outPhoto.downloadedAt == null
    val active = nextPhotoId != null && (pair.inPhoto?.id == nextPhotoId || pair.outPhoto?.id == nextPhotoId)
    val cardColor by animateColorAsState(pairPastel(pair.index, active, pair.fullyDownloaded, neutralColor), tween(durationMillis = if (neutralColor) 180 else 480, easing = FastOutSlowInEasing), label = "pairColor")
    val pairHighlighted = !pair.fullyDownloaded && highlightPhotoId != null &&
        (pair.inPhoto?.id == highlightPhotoId || pair.outPhoto?.id == highlightPhotoId)
    val pulseColor = MintAccent
    val pulseModifier = if (pairHighlighted) Modifier.drawWithCache {
        val width = 3.dp.toPx()
        val radius = 18.dp.toPx() - width / 2
        onDrawWithContent {
            drawContent()
            drawRoundRect(
                color = pulseColor,
                topLeft = Offset(width / 2, width / 2),
                size = Size((size.width - width).coerceAtLeast(0f), (size.height - width).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width = width)
            )
        }
    }.semantics { contentDescription = "กำลังเน้นคู่ภาพ ${pair.index}" } else Modifier
    MintCard(modifier = modifier.then(pulseModifier), containerColor = cardColor, contentPadding = 12.dp,
        accentBorder = if (cardElevation >= 16.dp || pairHighlighted) MintAccent else if (active && !neutralColor) MintAccent.copy(alpha = .65f) else null,
        elevation = cardElevation) {
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
                        if (!pair.fullyDownloaded) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = { onEdit?.invoke() }, enabled = onEdit != null, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "แก้ไข", modifier = Modifier.size(22.dp))
                            }
                            IconButton(onClick = { onDelete?.invoke() }, enabled = onDelete != null, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "ลบภาพ", tint = MintDanger, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                Box(Modifier.weight(0.85f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PairPhoto("IN-${pair.index}", pair.inPhoto, planAtByTag[pair.inPhoto?.tag], currentTime, onPreview, Modifier.weight(1f), nextPhotoId, highlightPhotoId, onNextThumbnailBounds)
                        PairPhoto("OUT-${pair.index}", pair.outPhoto, planAtByTag[pair.outPhoto?.tag], currentTime, onPreview, Modifier.weight(1f), nextPhotoId, highlightPhotoId, onNextThumbnailBounds)
                    }
                    if (!reorderMode && !pair.fullyDownloaded) {
                        ClayIconButton(
                            onClick = { onSwap?.invoke() },
                            enabled = canSwap && onSwap != null,
                            icon = Icons.Default.SwapHoriz,
                            contentDescription = "Swap IN and OUT",
                            modifier = Modifier.align(Alignment.Center).size(44.dp)
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
    modifier: Modifier = Modifier,
    nextPhotoId: Long? = null,
    highlightPhotoId: Long? = null,
    onNextThumbnailBounds: ((Long, Float, Float) -> Unit)? = null
) {
    val contextForImage = LocalContext.current
    val downloaded = photo?.downloadedAt != null
    val due = dueAt != null && currentTime >= dueAt && !downloaded
    val isNext = nextPhotoId != null && photo?.id == nextPhotoId
    val highlighted = highlightPhotoId != null && photo?.id == highlightPhotoId && !downloaded
    val measureModifier = if (isNext && photo != null && onNextThumbnailBounds != null) {
        Modifier.onGloballyPositioned { coordinates ->
            val y = coordinates.positionInWindow().y
            onNextThumbnailBounds(photo.id, y, y + coordinates.size.height)
        }
    } else Modifier
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .then(measureModifier)
                .clip(MaterialTheme.shapes.small)
                .background(MintSoft, MaterialTheme.shapes.small)
                .then(
                    if (due) {
                        Modifier.border(2.dp, MintDanger, MaterialTheme.shapes.small)
                    } else if (isNext) {
                        Modifier.border(2.dp, MintAccent, MaterialTheme.shapes.small)
                    } else Modifier
                )
                .clickable(enabled = !photo?.filePath.isNullOrBlank()) { photo?.let(onPreview) }
        ) {
            if (photo != null && photo.filePath.isNotBlank()) {
                AsyncImage(
                    model = remember(photo.filePath) { ImageRequest.Builder(contextForImage).data("file://${photo.filePath}").crossfade(false).build() },
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize().alpha(if (downloaded) 0.32f else 1f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MintMuted, modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
            if (highlighted) {
                Box(Modifier.matchParentSize()
                    .border(3.dp, MintAccent, MaterialTheme.shapes.small)
                    .background(MintAccent.copy(alpha = .12f))
                    .semantics { contentDescription = "กำลังเน้น $label" })
            }
            if (downloaded) {
                MintBadge(text = "✓ ดาวน์โหลดแล้ว", type = "downloaded", modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            if (isNext && !due && !downloaded) MintBadge("ถัดไป", "ok", Modifier.align(Alignment.TopStart).padding(4.dp))
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
            downloaded -> Text("$label • ดาวน์โหลดแล้ว", style = MaterialTheme.typography.labelSmall, color = MintMuted, maxLines = 1)
            else -> Text("$label • พร้อม", style = MaterialTheme.typography.labelSmall, color = MintMuted, maxLines = 1)
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
                        label = { Text("หมายเหตุ เช่น เลขสาขา") },
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
                    ) { Text("ลบทั้งคู่") }
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
