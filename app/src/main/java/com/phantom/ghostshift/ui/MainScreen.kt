package com.phantom.ghostshift.ui

import android.net.Uri
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
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
    
    // Internal Camera Output
    fun getOutputDirectory(context: Context): File {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, "GhostShiftPhotos").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
    }

    // Handlers
    fun onPhotoCaptured(uri: Uri) {
        showCamera = false
        val rid = editTargetId
        if (rid != null) {
            viewModel.replacePhoto(rid, uri)
            editTargetId = null
        } else {
            viewModel.addPhotoFromPicker(uri)
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
    
    // Gallery Launcher
    val pickerLauncher = rememberLauncherForActivityResult(
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
            onImageCaptured = { uri -> onPhotoCaptured(uri) },
            onClose = { showCamera = false }
        )
        return // Show only camera
    }

    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            state = state,
            onSoundSelected = { uri -> viewModel.setSound(uri) },
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
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Choose Option") },
            text = { Text("Take a new photo or choose from gallery?") },
            confirmButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    launchCamera(editTargetId)
                }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
            }
        )
    }

    Scaffold(
        containerColor = MintBg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            StatusHeader(
                state = state,
                onResetTimer = {
                    if (state.isTimerLocked) {
                        // locked
                    } else {
                        confirmResetTimer = true
                    }
                },
                onDeleteAll = { confirmDeleteAll = true },
                onDownloadNext = { viewModel.exportNextPhoto() },
                onRequestGateHelp = { gateDialog = true },
                onSettings = { showSettings = true }
            )
        },
        bottomBar = {
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
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            item {
                SectionHeaderCard(
                    title = "Pending (ยังไม่ Export)",
                    count = state.pendingPhotos.size,
                    hint = if (state.pendingPhotos.isEmpty()) "ยังไม่มีรายการ Pending" else "แตะรูปเพื่อดูเต็มจอ / กด Edit เพื่อแก้ไข"
                )
            }

            items(state.pendingPhotos, key = { it.id }) { photo ->
                val dueAt = state.schedule.planAtByTag[photo.tag]
                PhotoCard(
                    photo = photo,
                    isDownloaded = false,
                    dueAt = dueAt,
                    currentTime = state.currentTime,
                    onEdit = {
                        editTargetId = photo.id
                        showEditDialog = true
                    },
                    onDownload = { viewModel.exportPhoto(photo) },
                    onPreview = { previewPhoto = photo }
                )
            }

            item {
                SectionHeaderCard(
                    title = "Downloaded (Exported)",
                    count = state.downloadedPhotos.size,
                    hint = if (state.downloadedPhotos.isEmpty()) "ยังไม่มีรายการ Downloaded" else "รายการที่ Export แล้ว"
                )
            }

            items(state.downloadedPhotos, key = { it.id }) { photo ->
                val dueAt = state.schedule.planAtByTag[photo.tag]
                PhotoCard(
                    photo = photo,
                    isDownloaded = true,
                    dueAt = dueAt,
                    currentTime = state.currentTime,
                    onEdit = {
                        // Spec v1.1: downloaded list generally should not be edited
                    },
                    onDownload = null,
                    onPreview = { previewPhoto = photo }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("ล้างข้อมูลทั้งหมด") },
            text = { Text("จะลบรูป/ข้อมูลทั้งหมด และรีเซ็ตตัวจับเวลา/การแจ้งเตือนทั้งหมด\n\nยืนยันหรือไม่?") },
            containerColor = MintCardBg,
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        viewModel.deleteAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MintDanger)
                ) { Text("ลบทั้งหมด") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDeleteAll = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MintText)
                ) { Text("ยกเลิก") }
            }
        )
    }

    if (confirmResetTimer) {
        AlertDialog(
            onDismissRequest = { confirmResetTimer = false },
            title = { Text("รีเซ็ตตัวจับเวลา") },
            text = { Text("จะหยุดการจับเวลาและยกเลิกการแจ้งเตือน (Alarm) ทั้งหมด\n\nยืนยันหรือไม่?") },
            containerColor = MintCardBg,
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResetTimer = false
                        viewModel.resetTimer()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MintDanger)
                ) { Text("รีเซ็ต") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmResetTimer = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MintText)
                ) { Text("ยกเลิก") }
            }
        )
    }

    if (gateDialog) {
        AlertDialog(
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
                TextButton(onClick = { gateDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = MintAccent)) { Text("เข้าใจแล้ว") }
            }
        )
    }
}

@Composable
fun StatusHeader(
    state: MainUiState,
    onResetTimer: () -> Unit,
    onDeleteAll: () -> Unit,
    onDownloadNext: () -> Unit,
    onRequestGateHelp: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MintBg, // blend with background
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Title + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "GhostShift",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MintText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Mint Light • Native Android",
                        style = MaterialTheme.typography.labelMedium,
                        color = MintMuted
                    )
                }

                val badgeText = "${state.downloadedPhotos.size}/${state.pendingPhotos.size + state.downloadedPhotos.size}"
                MintBadge(text = badgeText, type = if (state.gateOpen) "ok" else "wait")
                
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MintText)
                }
            }

            // Row 2: Core status line
            MintCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = MintText)
                        Spacer(Modifier.width(8.dp))
                        
                        // Phase 1: Absolute Target Time Header
                        val headerText = if (state.timer.running && state.timer.targetAt != null) {
                            "Target: ${fmtTime(state.timer.targetAt!!)}"
                        } else {
                            "Ready to Start"
                        }
                        
                        Text(
                            headerText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MintText,
                            modifier = Modifier.weight(1f)
                        )

                        val lockLabel = if (state.isTimerLocked) "LOCK" else "UNLOCK"
                        AssistChip(
                            onClick = { /* display only */ },
                            enabled = false,
                            label = { Text(lockLabel) },
                            leadingIcon = {
                                Icon(
                                    if (state.isTimerLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledLabelColor = MintMuted2
                            )
                        )
                    }

                    // Gate / progress to unlock
                    val pairs = max(0, state.exportedPairsContiguous)
                    val progress = (pairs.coerceAtMost(20) / 20f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (state.gateOpen) MintAccent else MintMuted2)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.gateOpen) "Gate: OPEN" else "Gate: CLOSED",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MintText,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRequestGateHelp) { Text("Help?", color = MintAccent) }
                    }

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MintAccent,
                        trackColor = MintLine
                    )
                    Text(
                        "ปลดล็อกเมื่อ Export ครบคู่ต่อเนื่อง 1..20 (ตอนนี้: $pairs/20)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MintMuted
                    )

                    // Schedule block
                    ScheduleBlock(state = state)

                    Divider(color = MintLine, thickness = 1.dp)

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownloadNext,
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = MintAccent, contentColor = Color.White),
                            enabled = state.pendingPhotos.isNotEmpty()
                        ) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download Next")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResetTimer,
                            enabled = !state.isTimerLocked,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintText)
                        ) {
                            Text("Reset")
                        }

                        OutlinedButton(
                            onClick = onDeleteAll,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintDanger)
                        ) {
                            Text("Delete All")
                        }
                    }
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
            Text(
                schedule.warn ?: "Schedule waiting...",
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
                // Phase 1: Absolute Target Time in Button
                val hypotheticTarget = System.currentTimeMillis() + (9 * 3600 * 1000L) + (10 * 60 * 1000L)
                val btnLabel = if (!state.timer.running) "Start (Finish ${fmtTime(hypotheticTarget)})" else "Running..."

                Button(
                    onClick = onStart,
                    enabled = state.canStartTimer,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = MintAccent, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(btnLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onCam,
                    modifier = Modifier.weight(0.8f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MintText),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Cam")
                }

                OutlinedButton(
                    onClick = onUpload,
                    modifier = Modifier.weight(0.8f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MintText),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Up")
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderCard(
    title: String,
    count: Int,
    hint: String
) {
    MintCard {
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
        }
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MintMuted)
    }
}

@Composable
private fun PhotoCard(
    photo: PhotoEntity,
    isDownloaded: Boolean,
    dueAt: Long?,
    currentTime: Long,
    onEdit: () -> Unit,
    onDownload: (() -> Unit)?,
    onPreview: () -> Unit
) {
    val isDue = dueAt != null && currentTime >= dueAt
    val countdown = if (dueAt != null && dueAt > currentTime) dueAt - currentTime else null
    val cardColor = if (isDue && !isDownloaded) MintWarn.copy(alpha = 0.1f) else MintCardBg

    MintCard(containerColor = cardColor) {
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
                         MintBadge(text = "DUE NOW", type = "error")
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
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp), // fit content
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintText),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Edit", style = MaterialTheme.typography.labelMedium)
                    }

                    if (!isDownloaded && onDownload != null) {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintAccent, contentColor = Color.White),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Download", style = MaterialTheme.typography.labelMedium)
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

private fun fmtDuration(ms: Long): String {
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
