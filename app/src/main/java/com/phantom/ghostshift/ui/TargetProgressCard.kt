package com.phantom.ghostshift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.ghostshift.ui.theme.MintLine
import com.phantom.ghostshift.ui.theme.MintText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TargetProgressCard(targetAt: Long?, progress: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (targetAt == null) {
            Text("Target\nยังไม่ได้ตั้ง", color = MintText, style = MaterialTheme.typography.labelMedium)
            return
        }
        Canvas(Modifier.size(74.dp)) {
            val stroke = Stroke(width = 5.dp.toPx())
            drawArc(MintLine, -90f, 360f, false, style = stroke)
            drawArc(Color(0xFF24B883), -90f, 360f * progress, false, style = stroke)
        }
        Text(formatTime(targetAt), color = MintText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
