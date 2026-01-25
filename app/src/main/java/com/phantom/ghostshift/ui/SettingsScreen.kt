package com.phantom.ghostshift.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phantom.ghostshift.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onSoundSelected: (String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currentSoundUri = state.soundUri

    // Sound Picker
    val soundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onSoundSelected(uri?.toString())
        }
    }

    Scaffold(
        containerColor = MintBg,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MintBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sound Section
            MintCard {
                Column {
                    Text("Alarm Sound", style = MaterialTheme.typography.titleMedium, color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    
                    val soundName = if (currentSoundUri == null) "Default System Alarm" else "Custom Tone Selected"
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                             val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                 putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
                                 putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone")
                                 if (currentSoundUri != null) {
                                     putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentSoundUri))
                                 }
                             }
                             soundLauncher.launch(intent)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MintText)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(soundName, style = MaterialTheme.typography.bodyLarge, color = MintText)
                            Text("Tap to change", style = MaterialTheme.typography.bodySmall, color = MintMuted)
                        }
                        if (currentSoundUri != null) {
                             IconButton(onClick = { onSoundSelected(null) }) {
                                 Icon(Icons.Default.Check, contentDescription = "Reset", tint = MintAccent)
                             }
                        }
                    }
                }
            }

            // Permissions Section
            MintCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Permissions & Reliability", style = MaterialTheme.typography.titleMedium, color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    
                    // Exact Alarm
                    if (Build.VERSION.SDK_INT >= 31) { // S
                        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
                        val canSchedule = alarmManager?.canScheduleExactAlarms() == true
                        PermissionRow(
                            title = "Exact Alarm Permission",
                            desc = "Required for precise timing",
                            isGranted = canSchedule,
                            onClick = {
                                if (!canSchedule) {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }

                    // Notifications
                    if (Build.VERSION.SDK_INT >= 33) {
                         val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                         PermissionRow(
                             title = "Notifications",
                             desc = "Required to see alarms",
                             isGranted = isGranted,
                             onClick = {
                                 val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                     putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                 }
                                 context.startActivity(intent)
                             }
                         )
                    }

                    // Battery
                    PermissionRow(
                        title = "Battery Optimization",
                        desc = "Disable for reliable background alarms",
                        isGranted = false, // Hard to check programmatically perfectly, just show as action
                        onClick = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        },
                        forceAction = true
                    )
                }
            }
            
            // Debug Info Section (Strict Parity Requirement)
            MintCard {
                 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Debug Status", style = MaterialTheme.typography.titleMedium, color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    DebugRow("Timer Running", "${state.timer.running}")
                    DebugRow("Gate Open", "${state.gateOpen} (Need >0 Export)")
                    DebugRow("Next Slot", state.schedule.nextTag ?: "-")
                    DebugRow("Internal Next At", if (state.schedule.nextAt != null) java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(state.schedule.nextAt!!)) else "-")
                    DebugRow("Alarm Active", "${state.alarmActive}")
                    DebugRow("Scheduled At", if (state.alarmDueAt != null) java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(state.alarmDueAt)) else "-")
                    DebugRow("Scheduled Tag", state.alarmTag ?: "-")
                 }
            }
        }
    }
}

@Composable
fun DebugRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MintMuted)
        Text(
            value, 
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), 
            color = MintText
        )
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, isGranted: Boolean, onClick: () -> Unit, forceAction: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = if (isGranted && !forceAction) MintAccent else MintMuted)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MintMuted)
        }
        if (isGranted && !forceAction) {
            Text("GRANTED", style = MaterialTheme.typography.labelSmall, color = MintAccent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        } else {
            TextButton(onClick = onClick) { Text("OPEN", color = MintAccent) }
        }
    }
}
