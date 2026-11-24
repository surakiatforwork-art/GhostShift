package com.phantom.ghostshift.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phantom.ghostshift.ui.theme.*

@Composable
fun GhostDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = MintCardBg
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val shape = RoundedCornerShape(14.dp)
        Surface(shape = shape, color = containerColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, MintClayHighlight),
            shadowElevation = 5.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (title != null) ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) { title() }
                if (text != null) Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalContentColor provides MintText) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                if (dismissButton == null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { confirmButton() }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { dismissButton() }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { confirmButton() }
                }
            }
        }
    }
}

@Composable
fun GhostDialogButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    tone: UtilityTone = UtilityTone.Mint, enabled: Boolean = true, content: @Composable () -> Unit) {
    // Reuse the same thin glass surface as the main screen.
    GlassControl(modifier.heightIn(min = 48.dp), tone, enabled, onClick) { fg ->
        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)) { content() }
            }
        }
    }
}

@Composable
fun ExportProgressDialog(progress: ExportProgress, dismissError: () -> Unit) {
    val error = progress.stage == ExportStage.Error
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(progress.stage) {
        if (progress.stage == ExportStage.Success) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Dialog(onDismissRequest = { if (error) dismissError() },
        properties = DialogProperties(dismissOnBackPress = error, dismissOnClickOutside = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
            color = MintCardBg, shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MintClayHighlight), shadowElevation = 5.dp
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(64.dp).background(if (error) MintErrBg else MintOkBg, RoundedCornerShape(12.dp))
                    .border(1.dp, MintClayHighlight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Crossfade(progress.stage, animationSpec = tween(140), label = "exportState") { stage ->
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            when(stage) {
                                ExportStage.Loading -> CircularProgressIndicator(Modifier.size(32.dp), color = MintAccent, strokeWidth = 3.dp)
                                ExportStage.Success -> Icon(Icons.Default.Check, "สำเร็จ", tint = MintOkText, modifier = Modifier.size(38.dp))
                                ExportStage.Error -> Icon(Icons.Default.ErrorOutline, "ผิดพลาด", tint = MintErrText, modifier = Modifier.size(38.dp))
                            }
                        }
                    }
                }
                Text(when (progress.stage) {
                    ExportStage.Loading -> "กำลังดาวน์โหลด"
                    ExportStage.Success -> "ดาวน์โหลดแล้ว"
                    ExportStage.Error -> "ดาวน์โหลดไม่สำเร็จ"
                }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(progress.tag, color = if (error) MintErrText else MintAccent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                progress.remark?.takeIf { it.isNotBlank() }?.let { Text(it, color = MintMuted, textAlign = TextAlign.Center) }
                Text(when (progress.stage) {
                    ExportStage.Loading -> "กำลังดาวน์โหลดภาพ"
                    ExportStage.Success -> "ดาวน์โหลดแล้ว"
                    ExportStage.Error -> "ตรวจสอบไฟล์แล้วลองอีกครั้ง"
                }, style = MaterialTheme.typography.bodySmall, color = MintMuted, textAlign = TextAlign.Center)
                if (error) GhostDialogButton(onClick = dismissError, modifier = Modifier.fillMaxWidth(), tone = UtilityTone.Coral) { Text("รับทราบ") }
            }
        }
    }
}

@Composable
fun SettingsGroupLabel(text: String) {
    Text(text, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        color = MintMuted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}
