package com.phantom.ghostshift.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phantom.ghostshift.ui.theme.*
import com.phantom.ghostshift.domain.ScheduleSettings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onSoundSelected: (String?) -> Unit,
    themeMode: String = "system",
    onThemeModeChanged: (String) -> Unit = {},
    onTargetMinutesSelected: (Int) -> Unit,
    onFinishTimeSelected: (Long?) -> Unit,
    onTimerEnabledChanged: (Boolean) -> Unit,
    onScheduleSettingsChanged: (ScheduleSettings) -> Unit,
    onResetTimer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currentSoundUri = state.soundUri
    var targetStep by remember { mutableStateOf(0) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var proposed by remember { mutableStateOf(0) }
    var savedTarget by remember { mutableStateOf(0) }
    fun durationText(total: Int) = "${total / 60} ชั่วโมง ${total % 60} นาที"
    fun finishText(total: Int): String {
        val end = 8 * 60 + total
        val time = String.format(Locale.US, "%02d:%02d", (end / 60) % 24, end % 60)
        return time + if (end >= 1440) " (วันถัดไป)" else ""
    }
    fun confirmTarget(extra: Int) {
        savedTarget = proposed + extra
        onTargetMinutesSelected(savedTarget)
        targetStep = 3
    }
    var showResetTimerConfirmation by remember { mutableStateOf(false) }
    var inOutMin by remember(state.scheduleSettings) { mutableStateOf(state.scheduleSettings.inOutMinMinutes.toString()) }
    var inOutMax by remember(state.scheduleSettings) { mutableStateOf(state.scheduleSettings.inOutMaxMinutes?.toString().orEmpty()) }
    var outInMin by remember(state.scheduleSettings) { mutableStateOf(state.scheduleSettings.outInMinMinutes.toString()) }
    var outInMax by remember(state.scheduleSettings) { mutableStateOf(state.scheduleSettings.outInMaxMinutes?.toString().orEmpty()) }

    fun saveTiming(
        autoStart: Boolean = state.scheduleSettings.autoStartOnFirstDownload,
        repeatAlarmSound: Boolean = state.scheduleSettings.repeatAlarmSound
    ) {
        val inMin = inOutMin.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val outMin = outInMin.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val inMax = inOutMax.toIntOrNull()?.coerceAtLeast(inMin)
        val outMax = outInMax.toIntOrNull()?.coerceAtLeast(outMin)
        onScheduleSettingsChanged(
            ScheduleSettings(
                inOutMinMinutes = inMin,
                inOutMaxMinutes = inMax,
                outInMinMinutes = outMin,
                outInMaxMinutes = outMax,
                autoStartOnFirstDownload = autoStart,
                repeatAlarmSound = repeatAlarmSound
            )
        )
    }
    val targetSummary = durationText(state.scheduleSettings.targetMinutes)
    fun openTargetPicker() {
        hours = (state.scheduleSettings.targetMinutes / 60).toString()
        minutes = (state.scheduleSettings.targetMinutes % 60).toString()
        targetStep = 1
    }

    val fixedFinish = state.scheduleSettings.finishOverrideAt
    fun openFinishPicker() {
        val now = System.currentTimeMillis()
        val initial = Calendar.getInstance().apply {
            timeInMillis = fixedFinish?.takeIf { it > now }
                ?: state.timer.targetAt?.takeIf { it > now }
                ?: (now + state.scheduleSettings.targetMinutes * 60_000L)
        }
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                val selected = Calendar.getInstance().apply {
                    set(year, month, day, hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (selected <= System.currentTimeMillis()) {
                    Toast.makeText(context, "เวลาเลิกงานต้องมากกว่าเวลาปัจจุบัน", Toast.LENGTH_SHORT).show()
                } else {
                    onFinishTimeSelected(selected)
                    Toast.makeText(context, "บันทึกเวลาเลิกงานแล้ว", Toast.LENGTH_SHORT).show()
                }
            }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), true).show()
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = now
        }.show()
    }

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
            MintCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ธีมแอป", style = MaterialTheme.typography.titleMedium, color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("เลือกรูปแบบการแสดงผล", style = MaterialTheme.typography.bodySmall, color = MintMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to "ตามอุปกรณ์", "dark" to "มืด", "light" to "สว่าง").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChanged(mode) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MintSoft, selectedLabelColor = MintOkText)
                            )
                        }
                    }
                }
            }
            MintCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ระบบจับเวลาและแจ้งเตือน", style = MaterialTheme.typography.titleMedium, color = MintText)
                        Text("เปิดเพื่อกำหนดระยะเวลาทำงานและรับแจ้งเตือน", style = MaterialTheme.typography.bodySmall, color = MintMuted)
                    }
                    Switch(checked = state.scheduleSettings.timerEnabled, onCheckedChange = onTimerEnabledChanged)
                }
            }
            if (state.scheduleSettings.timerEnabled) {
            // Target Time Section
            MintCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ระยะเวลาทำงาน (Target)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MintText,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        targetSummary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MintText
                    )
                    Text(
                        if (fixedFinish != null) {
                            "ค่าเริ่มต้นสำหรับวันที่ไม่ได้กำหนดเวลาเลิกงานเอง"
                        } else if (state.timer.running) {
                            "Timer กำลังทำงาน: เปลี่ยนค่าแล้วตารางแจ้งเตือนจะอัปเดตทันที"
                        } else {
                            "Timer ยังไม่เริ่ม: ค่านี้จะถูกใช้ตอนกด Start ครั้งถัดไป"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MintMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { openTargetPicker() },
                            colors = ButtonDefaults.buttonColors(containerColor = MintAccent)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ตั้งเวลา Target")
                        }
                        OutlinedButton(onClick = { showResetTimerConfirmation = true }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("รีเซ็ต")
                        }
                    }
                }
            }

            MintCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("เวลาเลิกงาน", style = MaterialTheme.typography.titleMedium, color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(fixedFinish?.let { formatTargetDateTime(it) + if (it <= state.currentTime) " (ผ่านแล้ว)" else "" }
                        ?: state.timer.targetAt?.let { formatTargetDateTime(it) + " (ตาม Target)" }
                        ?: "ยังไม่ได้กำหนดเวลาเลิกงานเอง", style = MaterialTheme.typography.bodyLarge, color = MintText)
                    Text("กำหนดวันและเวลาเลิกงานสำหรับรอบนี้โดยตรง โดยไม่เปลี่ยนระยะเวลา Target เริ่มต้น", style = MaterialTheme.typography.bodySmall, color = MintMuted)
                    Button(onClick = { openFinishPicker() }, colors = ButtonDefaults.buttonColors(containerColor = MintAccent), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ตั้งเวลาเลิกงาน")
                    }
                    if (fixedFinish != null) {
                        OutlinedButton(onClick = { onFinishTimeSelected(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text("กลับไปใช้ระยะเวลา Target")
                        }
                    }
                }
            }

            MintCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ช่วงเวลาระหว่างภาพ", style = MaterialTheme.typography.titleMedium, color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("เว้นช่องเวลาสูงสุดว่างไว้ หากไม่ต้องการจำกัด", style = MaterialTheme.typography.bodySmall, color = MintMuted)
                    TimingFields("IN → OUT", inOutMin, { inOutMin = it.filter(Char::isDigit) }, inOutMax, { inOutMax = it.filter(Char::isDigit) })
                    TimingFields("OUT → IN", outInMin, { outInMin = it.filter(Char::isDigit) }, outInMax, { outInMax = it.filter(Char::isDigit) })
                    Button(onClick = { saveTiming() }, colors = ButtonDefaults.buttonColors(containerColor = MintAccent)) {
                        Text("บันทึกช่วงเวลา")
                    }
                    Divider(color = MintLine)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("เริ่มนับเวลาอัตโนมัติ", color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text("เริ่ม Timer เมื่อกด Download Next ที่ IN-1", style = MaterialTheme.typography.bodySmall, color = MintMuted)
                        }
                        Switch(
                            checked = state.scheduleSettings.autoStartOnFirstDownload,
                            onCheckedChange = { enabled -> saveTiming(enabled) }
                        )
                    }
                    Divider(color = MintLine)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("เล่นเสียงเตือนซ้ำ", color = MintText, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text("ปิดไว้เพื่อเล่นเสียงเพียงครั้งเดียว", style = MaterialTheme.typography.bodySmall, color = MintMuted)
                        }
                        Switch(
                            checked = state.scheduleSettings.repeatAlarmSound,
                            onCheckedChange = { enabled -> saveTiming(repeatAlarmSound = enabled) }
                        )
                    }
                }
            }

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

                    // Overlay alert
                    val canDrawOverlays = Settings.canDrawOverlays(context)
                    PermissionRow(
                        title = "Alarm Popup Overlay",
                        desc = "Show a tappable popup and stop its sound when a photo is due",
                        isGranted = canDrawOverlays,
                        onClick = {
                            if (!canDrawOverlays) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            }
                        }
                    )

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
                    DebugRow("Internal Next At", state.schedule.nextAt?.let { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "-")
                    DebugRow("Alarm Active", "${state.alarmActive}")
                    DebugRow("Scheduled At", if (state.alarmDueAt != null) java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(state.alarmDueAt)) else "-")
                    DebugRow("Scheduled Tag", state.alarmTag ?: "-")
                 }
            }
            }
        }
    }

    if (state.scheduleSettings.timerEnabled && targetStep == 1) {
        val h = hours.toIntOrNull()
        val m = minutes.toIntOrNull()
        val valid = h != null && m != null && h in 0..24 && m in 0..59 && h * 60 + m > 0
        AlertDialog(
            onDismissRequest = { targetStep = 0 },
            title = { Text("กำหนดระยะเวลาทำงาน") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(2) }, label = { Text("ชั่วโมง") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("นาที") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            },
            confirmButton = { Button(enabled = valid, onClick = { proposed = h!! * 60 + m!!; targetStep = 2 }) { Text("ดำเนินการต่อ") } },
            dismissButton = { TextButton(onClick = { targetStep = 0 }) { Text("ยกเลิก") } }
        )
    }
    if (state.scheduleSettings.timerEnabled && targetStep == 2) {
        AlertDialog(
            onDismissRequest = { targetStep = 0 },
            title = { Text("แนะนำให้เผื่อเวลาอีก 10 นาที") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ระยะเวลาที่เลือก: " + durationText(proposed))
                    Text("แนะนำให้เผื่อเวลาสำหรับขั้นตอนนำภาพไปใช้งาน")
                    Text("เมื่อเพิ่มเวลา: " + durationText(proposed + 10))
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { confirmTarget(10) }, modifier = Modifier.fillMaxWidth()) { Text("ยืนยัน เพิ่ม 10 นาที") }
                    OutlinedButton(onClick = { confirmTarget(0) }, modifier = Modifier.fillMaxWidth()) { Text("ยืนยัน ไม่เพิ่มเวลา 10 นาที") }
                    TextButton(onClick = { targetStep = 0 }, modifier = Modifier.fillMaxWidth()) { Text("ยกเลิก") }
                }
            }
        )
    }
    if (state.scheduleSettings.timerEnabled && targetStep == 3) {
        AlertDialog(
            onDismissRequest = { targetStep = 0 },
            title = { Text("ตั้งค่าเวลาทำงานระหว่างวันสำเร็จ") },
            text = { Text(durationText(savedTarget) + "\n\nตัวอย่าง: หากเริ่มงานหรือเริ่มนับเวลา 08:00 น. คุณจะเลิกงานเวลา " + finishText(savedTarget) + " น.") },
            confirmButton = { Button(onClick = { targetStep = 0 }) { Text("เรียบร้อย") } }
        )
    }
    if (state.scheduleSettings.timerEnabled && showResetTimerConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetTimerConfirmation = false },
            title = { Text("รีเซ็ตเวลา?") },
            text = {
                Text("การรีเซ็ตจะหยุดจับเวลา ยกเลิกเวลาเลิกงานที่กำหนดเองและตารางแจ้งเตือน โดยยังเก็บระยะเวลา Target เริ่มต้นไว้")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetTimerConfirmation = false
                        onResetTimer()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MintDanger)
                ) {
                    Text("รีเซ็ต")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTimerConfirmation = false }) {
                    Text("ยกเลิก")
                }
            }
        )
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

@Composable
private fun TimingFields(
    title: String,
    minimum: String,
    onMinimumChanged: (String) -> Unit,
    maximum: String,
    onMaximumChanged: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MintText)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = minimum,
            onValueChange = onMinimumChanged,
            label = { Text("ขั้นต่ำ (นาที)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = maximum,
            onValueChange = onMaximumChanged,
            label = { Text("สูงสุด (นาที)") },
            placeholder = { Text("ไม่จำกัด") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTargetDateTime(ms: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}
