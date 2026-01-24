package com.phantom.ghostshift.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.phantom.ghostshift.data.PhotoEntity
import com.phantom.ghostshift.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Pickers
    val addPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.addPhotoFromPicker(uri) }

    var replaceTargetId by remember { mutableStateOf<Long?>(null) }
    val replacePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && replaceTargetId != null) {
            viewModel.replacePhoto(replaceTargetId!!, uri)
            replaceTargetId = null
        }
    }
    
    // Permissions (Simple request on launch for now, strict UI later)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MintBg,
        topBar = {
             StatusHeader(state)
        },
        bottomBar = {
             StickyBottomBar(
                 state = state,
                 onStart = { viewModel.startTimer910() },
                 onCam = { Toast.makeText(context, "Camera not impl yet", Toast.LENGTH_SHORT).show() },
                 onUpload = { 
                     addPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                 },
                 onReset = { viewModel.resetTimer() },
                 onDeleteAll = { viewModel.deleteAll() }
             )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pending Section
            item {
                MintCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pending (ยังไม่โหลด)", fontWeight = FontWeight.Bold, color = MintText)
                        MintBadge("${state.pendingPhotos.size}", "wait")
                    }
                    if (state.pendingPhotos.isEmpty()) {
                        Text("ยังไม่มีรายการ Pending", color = MintMuted, fontSize = 14.sp)
                    }
                }
            }
            
            items(state.pendingPhotos) { photo ->
                 PendingPhotoItem(
                     photo = photo,
                     onEdit = { 
                         replaceTargetId = photo.id
                         replacePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                     },
                     onDownload = { viewModel.exportPhoto(photo) }
                 )
            }
            
            // Downloaded Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                MintCard {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         Text("Downloaded", fontWeight = FontWeight.Bold, color = MintText)
                         Spacer(modifier = Modifier.weight(1f))
                         MintBadge("${state.downloadedPhotos.size}", "ok")
                     }
                     // Progress Bar
                     val total = state.pendingPhotos.size + state.downloadedPhotos.size
                     val progress = if (total > 0) state.downloadedPhotos.size.toFloat() / 40f else 0f
                     LinearProgressIndicator(
                         progress = progress.coerceIn(0f, 1f),
                         modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                         color = MintAccent,
                         trackColor = MintSoft
                     )
                }
            }
            
            items(state.downloadedPhotos) { photo ->
                DownloadedPhotoItem(photo)
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StatusHeader(state: MainUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MintBg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val nextText = if (state.timer.running && state.gateOpen) state.schedule.nextTag ?: "-" else state.nextSlotTag
            Text("Next: $nextText", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MintText)
            Spacer(modifier = Modifier.weight(1f))
            
            // Download Next Button Logic? Only if available.
            // Simplified: Not strictly specific in wireframe but good usage.
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        MintCard {
            if (state.timer.running) {
                if (!state.gateOpen) {
                     Text("⚠ ต้องดาวน์โหลดรูปอย่างน้อย 1 รูป", color = MintWarn, fontWeight = FontWeight.Bold)
                     Text("เพื่อเริ่มตารางแจ้งเตือน", color = MintMuted, fontSize = 12.sp)
                } else if (!state.schedule.ok) {
                     Text("⚠ ${state.schedule.error}", color = MintDanger, fontWeight = FontWeight.Bold)
                } else {
                     val nextAt = state.schedule.nextAt ?: 0L
                     val left = nextAt - state.currentTime
                     
                     Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                         Column {
                             Text("When", color = MintMuted, fontSize = 12.sp)
                             Text(fmtTime(nextAt), fontSize = 24.sp, fontWeight = FontWeight.Black, color = MintAccent)
                         }
                         Column(horizontalAlignment = Alignment.End) {
                             Text("Countdown", color = MintMuted, fontSize = 12.sp)
                             val cdColor = if (left <= 0) MintDanger else MintText
                             Text(fmtDuration(left), fontSize = 24.sp, fontWeight = FontWeight.Black, color = cdColor)
                         }
                     }
                }
            } else {
                Text("เริ่มนับเวลาก่อน", modifier = Modifier.align(Alignment.CenterHorizontally), color = MintMuted)
            }
        }
    }
}

@Composable
fun PendingPhotoItem(
    photo: PhotoEntity,
    onEdit: () -> Unit,
    onDownload: () -> Unit
) {
    MintCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail
            Image(
                painter = rememberAsyncImagePainter(File(photo.filePath)),
                contentDescription = photo.tag,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    MintBadge(photo.tag, "ok")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(fmtDate(photo.createdAt), fontSize = 12.sp, color = MintMuted)
            }
            
            // Action Buttons
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MintMuted)
            }
            
            var showConfirm by remember { mutableStateOf(false) }
            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = { Text("ยืนยัน Download") },
                    text = { Text("การดาวน์โหลดจะทำให้สถานะเปลี่ยนเป็น Downloaded และไม่สามารถแก้ไขได้อีก") },
                    confirmButton = {
                        Button(onClick = { onDownload(); showConfirm = false }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                    }
                )
            }
            
            IconButton(
                onClick = { showConfirm = true },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MintAccent)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        }
    }
}

@Composable
fun DownloadedPhotoItem(photo: PhotoEntity) {
    MintCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = rememberAsyncImagePainter(File(photo.filePath)),
                contentDescription = photo.tag,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(photo.tag, fontWeight = FontWeight.Bold, color = MintMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Check, contentDescription = null, tint = MintAccent, modifier = Modifier.size(16.dp))
                }
                Text("DL: ${fmtTime(photo.downloadedAt ?: 0)}", fontSize = 12.sp, color = MintMuted2)
            }
        }
    }
}

@Composable
fun StickyBottomBar(
    state: MainUiState,
    onStart: () -> Unit,
    onCam: () -> Unit,
    onUpload: () -> Unit,
    onReset: () -> Unit,
    onDeleteAll: () -> Unit
) {
    val timer = state.timer
    // If running => Start button changes to "Stop 12:34" or "Locked"
    // Reset enabled only if Unlocked
    
    // Bottom Bar Container
    Column(Modifier.background(MintCard).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            // Camera
            FloatingActionButton(
                onClick = onCam,
                containerColor = MintSoft,
                contentColor = MintText,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CameraAlt, "Camera")
            }
            
            // Start/Stop Main Button
            val btnText = if (!timer.running) "⏱️ เริ่มนับเวลา" else "สิ้นสุด ${fmtTime(timer.targetAt ?: 0)}"
            val btnEnabled = !timer.running || !timer.locked
            
            Button(
                onClick = { if (!timer.running) onStart() else onReset() }, // Simplified action logic
                enabled = true, // We handle "Locked" by showing dialog or toast?
                // Spec B5: Reset All must be available even when locked (double confirm)
                // But the main button action usually toggles. 
                // Let's make main button just "Start" if not running. 
                // If Running, it shows target. Clicking it might fail if locked.
                modifier = Modifier.height(56.dp).weight(1f).padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintAccent, 
                    disabledContainerColor = MintMuted2
                )
            ) {
                Text(btnText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            // Upload
            FloatingActionButton(
                onClick = onUpload,
                containerColor = MintSoft,
                contentColor = MintText,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, "Upload")
            }
        }
        
        // Debug/Danger Zone (Drawer replacement)
        if (true) { // Always show for test? Or put in a menu?
             Spacer(modifier = Modifier.height(8.dp))
             Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                 var showDelConfirm by remember { mutableStateOf(false) }
                 
                 if (showDelConfirm) {
                     AlertDialog(
                         onDismissRequest = { showDelConfirm = false },
                         title = { Text("ลบทั้งหมด & รีเซ็ต?") },
                         text = { Text("ยืนยันการลบข้อมูลทั้งหมดและเริ่มใหม่ การกระทำนี้ไม่สามารถย้อนกลับได้") },
                         confirmButton = {
                             Button(onClick = { onDeleteAll(); showDelConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MintDanger)) { Text("ลบทั้งหมด") }
                         },
                         dismissButton = { TextButton(onClick = { showDelConfirm = false }) { Text("ยกเลิก") } }
                     )
                 }
                 
                 TextButton(onClick = { showDelConfirm = true }) {
                     Text("🗑 Delete All", color = MintDanger, fontSize = 12.sp)
                 }
             }
        }
    }
}

// Helpers
fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
fun fmtDate(ms: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(ms))
fun fmtDuration(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return String.format("%02d:%02d:%02d", h, m, sec)
}
