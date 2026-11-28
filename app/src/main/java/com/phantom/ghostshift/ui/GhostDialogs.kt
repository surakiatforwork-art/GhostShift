package com.phantom.ghostshift.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phantom.ghostshift.ui.theme.*

enum class DialogKind { General, Download, Delete, Time, Success, Warning, Edit, Update, Loading }

@Composable
private fun DialogKind.appearance(): Triple<ImageVector, Color, Color> = when(this) {
    DialogKind.Download, DialogKind.Update -> Triple(Icons.Default.Download, MintOkBg, MintOkText)
    DialogKind.Delete -> Triple(Icons.Default.DeleteOutline, MintErrBg, MintErrText)
    DialogKind.Time -> Triple(Icons.Default.Schedule, MintSoft, MintAccent)
    DialogKind.Success -> Triple(Icons.Default.Check, MintOkBg, MintOkText)
    DialogKind.Warning -> Triple(Icons.Default.WarningAmber, MintWaitBg, MintWaitText)
    DialogKind.Edit -> Triple(Icons.Default.Edit, MintSoft, MintAccent)
    DialogKind.Loading -> Triple(Icons.Default.Download, MintOkBg, MintOkText)
    DialogKind.General -> Triple(Icons.Default.Info, MintSoft, MintAccent)
}

@Composable
private fun DialogSurface(containerColor: Color = MintCardBg, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
        shape = RoundedCornerShape(26.dp), color = containerColor, contentColor = MintText,
        border = BorderStroke(1.dp, MintLine), shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun DialogIcon(kind: DialogKind) {
    val (icon, bg, fg) = kind.appearance()
    Box(Modifier.size(52.dp).background(bg, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun GhostDialog(
    onDismissRequest: () -> Unit,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = MintCardBg,
    kind: DialogKind = DialogKind.General,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        DialogSurface(containerColor) {
            DialogIcon(kind)
            if (title != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)) { title() }
            }
            if (text != null) Box(Modifier.fillMaxWidth().weight(1f, fill = false)
                .verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides MintMuted) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)) { text() }
                }
            }
            if (confirmButton != null || dismissButton != null) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 236.dp || LocalDensity.current.fontScale > 1.3f
                    if (confirmButton != null && dismissButton != null && !stacked) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { dismissButton() }
                            Box(Modifier.weight(1f)) { confirmButton() }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (confirmButton != null) Box(Modifier.fillMaxWidth()) { confirmButton() }
                            if (dismissButton != null) Box(Modifier.fillMaxWidth()) { dismissButton() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GhostDialogButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    tone: UtilityTone = UtilityTone.Mint, enabled: Boolean = true, content: @Composable () -> Unit) {
    val dark = LocalGhostDark.current
    val (fill, fg) = when(tone) {
        UtilityTone.Neutral -> MintBg to MintText
        UtilityTone.Coral -> MintErrBg to MintErrText
        UtilityTone.Mint, UtilityTone.Aqua -> (if (dark) Color(0xFF245447) else Color(0xFFC3F2E4)) to MintOkText
        UtilityTone.Sky -> (if (dark) Color(0xFF293F5D) else Color(0xFFDDEEFF)) to (if(dark) Color(0xFFB9DBFF) else Color(0xFF2364BE))
        UtilityTone.Lavender, UtilityTone.Primary -> MintSoft to MintAccent
        UtilityTone.Peach -> MintWaitBg to MintWaitText
    }
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = if(tone == UtilityTone.Neutral) BorderStroke(1.dp, MintLine) else null,
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = fg),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)) { content() }
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
        DialogSurface {
            Box(Modifier.size(52.dp).background(if (error) MintErrBg else MintOkBg, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center) {
                Crossfade(progress.stage, animationSpec = tween(140), label = "exportState") { stage ->
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        when(stage) {
                            ExportStage.Loading -> CircularProgressIndicator(Modifier.size(30.dp), color = MintOkText, strokeWidth = 3.dp)
                            ExportStage.Success -> Icon(Icons.Default.Check, "สำเร็จ", tint = MintOkText, modifier = Modifier.size(32.dp))
                            ExportStage.Error -> Icon(Icons.Default.ErrorOutline, "ผิดพลาด", tint = MintErrText, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            Text(when (progress.stage) {
                ExportStage.Loading -> "กำลังดาวน์โหลด"
                ExportStage.Success -> "ดาวน์โหลดแล้ว"
                ExportStage.Error -> "ดาวน์โหลดไม่สำเร็จ"
            }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(progress.tag, color = if (error) MintErrText else MintOkText,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                progress.remark?.takeIf { it.isNotBlank() }?.let { Text(it, color = MintMuted, textAlign = TextAlign.Center) }
                Text(when (progress.stage) {
                    ExportStage.Loading -> "กำลังดาวน์โหลดภาพ"
                    ExportStage.Success -> "ดาวน์โหลดแล้ว"
                    ExportStage.Error -> "ตรวจสอบไฟล์แล้วลองอีกครั้ง"
                }, style = MaterialTheme.typography.bodySmall, color = MintMuted, textAlign = TextAlign.Center)
            }
            if (error) GhostDialogButton(onClick = dismissError, tone = UtilityTone.Coral) { Text("รับทราบ") }
        }
    }
}

@Composable
fun SettingsGroupLabel(text: String) {
    Text(text, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        color = MintMuted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}
