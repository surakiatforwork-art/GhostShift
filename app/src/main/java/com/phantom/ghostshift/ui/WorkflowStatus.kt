package com.phantom.ghostshift.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.ghostshift.R
import com.phantom.ghostshift.ui.theme.*

@Composable
internal fun WorkflowStatus(state: MainUiState) {
    val next = state.pendingPhotos.firstOrNull()
    val due = state.scheduleSettings.timerEnabled &&
        next?.tag == state.schedule.nextTag &&
        state.schedule.nextAt?.let { it <= state.currentTime } == true
    val ready = next != null && next.filePath.isNotBlank()
    val label = when {
        state.isExportingNext -> "กำลังดาวน์โหลด"
        next == null && state.downloadedPhotos.isNotEmpty() -> "ครบทุกภาพ"
        next == null -> "พร้อมเริ่มงาน"
        !ready -> "รอเพิ่มภาพ"
        due -> "ถึงเวลาแล้ว"
        else -> "ถัดไป"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MintBadge(label, if (due) "err" else if (ready || next == null) "ok" else "wait")
        Text(next?.let { listOfNotNull(it.tag, it.remark?.takeIf(String::isNotBlank)).joinToString(" · ") }
            ?: if (state.downloadedPhotos.isEmpty()) "เพิ่มภาพเพื่อเริ่มงาน" else "ไม่มีภาพรอดาวน์โหลด",
            modifier = Modifier.weight(1f), color = MintText, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun QuietGhostState(completed: Boolean, onAdd: () -> Unit) {
    MintCard(contentPadding = 18.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostMark(Modifier.size(72.dp))
            Text(if (completed) "ดาวน์โหลดครบแล้ว" else "ยังไม่มีภาพ",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintText)
            Text(if (completed) "เพิ่มคู่ภาพเพื่อเริ่มงานต่อได้" else "ถ่ายภาพ เลือกจากแกลเลอรี่ หรือเพิ่มคู่ภาพล่วงหน้า",
                style = MaterialTheme.typography.bodySmall, color = MintMuted)
            ClayActionButton("เพิ่มคู่ภาพ", onAdd, modifier = Modifier.height(44.dp).widthIn(min = 140.dp),
                tone = UtilityTone.Aqua)
        }
    }
}
