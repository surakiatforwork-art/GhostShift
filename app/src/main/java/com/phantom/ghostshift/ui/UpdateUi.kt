package com.phantom.ghostshift.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.phantom.ghostshift.system.AppUpdater
import com.phantom.ghostshift.system.AppRelease
import com.phantom.ghostshift.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import java.io.File

class UpdateState(private val context: Context, private val scope: CoroutineScope) {
    val version = AppUpdater.installedVersion(context)
    var release by mutableStateOf<AppRelease?>(null); private set
    var checking by mutableStateOf(false); private set
    var downloading by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var status by mutableStateOf(""); private set
    var prompt by mutableStateOf(false)
    var ready by mutableStateOf<File?>(null); private set
    private var offered: String? = null

    fun check(automatic: Boolean = false) {
        if (checking || downloading) return
        checking = true
        if (!automatic) status = "กำลังตรวจสอบ..."
        scope.launch {
            try {
                val found = AppUpdater.check(context)
                release = found
                status = if (found == null) "เป็นเวอร์ชันล่าสุดแล้ว" else "มีเวอร์ชันใหม่ v" + found.version
                if (automatic && found != null && offered != found.version) {
                    offered = found.version
                    prompt = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = "ตรวจสอบไม่สำเร็จ: " + (e.message ?: "โปรดลองใหม่") }
            finally { checking = false }
        }
    }
    fun download() {
        val selected = release ?: return
        if (downloading || checking) return
        prompt = false; downloading = true; progress = 0f; ready = null
        status = "กำลังดาวน์โหลด"
        scope.launch {
            try {
                // Marshal progress changes to the main dispatcher.
                val apk = AppUpdater.download(context, selected) { value -> progress = value }
                ready = apk
                status = "ดาวน์โหลดและตรวจสอบ APK สำเร็จ"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = "อัปเดตไม่สำเร็จ: " + (e.message ?: "โปรดลองใหม่") }
            finally { downloading = false }
        }
    }
    fun installationRequested() { ready = null; status = "ยืนยันการติดตั้งในหน้าต่าง Android" }
    fun installationFailed(message: String) { status = message }
}

val LocalUpdateState = compositionLocalOf<UpdateState?> { null }

@Composable
fun UpdateHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(context, scope) { UpdateState(context, scope) }
    var awaitingPermission by remember { mutableStateOf(false) }
    fun launchInstaller() {
        val apk = state.ready ?: return
        try { AppUpdater.install(context, apk); state.installationRequested() }
        catch (e: Exception) { state.installationFailed("เปิดตัวติดตั้งไม่สำเร็จ") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        awaitingPermission = false
        if (context.packageManager.canRequestPackageInstalls()) launchInstaller()
        else state.installationFailed("ยังไม่ได้อนุญาตติดตั้งแอปที่ไม่รู้จัก")
    }
    LaunchedEffect(state) { state.check(automatic = true) }
    CompositionLocalProvider(LocalUpdateState provides state) {
        content()
        if (state.prompt) GhostDialog(
            onDismissRequest = { state.prompt = false },
            kind = DialogKind.Update,
            title = { Text("มีอัปเดต GhostShift") },
            text = { Text("เวอร์ชันใหม่ v" + state.release?.version + " พร้อมดาวน์โหลด คุณสามารถอัปเดตภายหลังได้ที่ Settings") },
            confirmButton = { GhostDialogButton(onClick = { state.download() }) { Text("อัปเดต") } },
            dismissButton = { GhostDialogButton(tone = UtilityTone.Neutral, onClick = { state.prompt = false }) { Text("ภายหลัง") } }
        )
        if (state.downloading) GhostDialog(
            onDismissRequest = {},
            kind = DialogKind.Loading,
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("กำลังดาวน์โหลดอัปเดต") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = state.progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                Text("${(state.progress * 100).toInt()}%")
            } }
        )
        if (state.ready != null && !awaitingPermission) GhostDialog(
            onDismissRequest = { state.installationRequested() },
            kind = DialogKind.Success,
            title = { Text("พร้อมติดตั้ง") },
            text = { Text("Android จะขอให้ยืนยันการติดตั้ง หากยังไม่ได้ให้สิทธิ์ติดตั้งแอปที่ไม่รู้จัก ระบบจะเปิดหน้าตั้งค่าสิทธิ์ก่อน") },
            confirmButton = { GhostDialogButton(onClick = {
                if (context.packageManager.canRequestPackageInstalls()) launchInstaller()
                else {
                    awaitingPermission = true
                    try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))) }
                    catch (e: Exception) { awaitingPermission = false; state.installationFailed("เปิดหน้าสิทธิ์ไม่ได้") }
                }
            }) { Text("ติดตั้ง") } },
            dismissButton = { GhostDialogButton(tone = UtilityTone.Neutral, onClick = { state.installationRequested() }) { Text("ภายหลัง") } }
        )
    }
}

@Composable
fun UpdateSettingsCard() {
    val state = LocalUpdateState.current ?: return
    MintCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("อัปเดตแอป", style = MaterialTheme.typography.titleMedium, color = MintText, fontWeight = FontWeight.Bold)
            Text("GhostShift v" + state.version, color = MintText)
            if (state.status.isNotBlank()) Text(state.status, style = MaterialTheme.typography.bodySmall, color = MintMuted)
            OutlinedButton(onClick = { state.check() }, enabled = !state.checking && !state.downloading, modifier = Modifier.fillMaxWidth()) { Text("ตรวจสอบการอัปเดต") }
            if (state.release != null) Button(onClick = { state.download() }, enabled = !state.checking && !state.downloading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MintAccent)) { Text("อัปเดตเป็น v" + state.release?.version) }
        }
    }
}
