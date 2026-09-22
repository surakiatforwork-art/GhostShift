package com.phantom.ghostshift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.ghostshift.ui.theme.MintAccent
import com.phantom.ghostshift.ui.theme.MintCardBg
import com.phantom.ghostshift.ui.theme.MintClayHighlight
import com.phantom.ghostshift.ui.theme.MintClayShadow
import com.phantom.ghostshift.ui.theme.MintDanger
import com.phantom.ghostshift.ui.theme.MintLine
import com.phantom.ghostshift.ui.theme.MintMuted
import com.phantom.ghostshift.ui.theme.MintText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TargetProgressCard(targetAt: Long?, progress: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(9.dp, CircleShape, spotColor = MintClayShadow)
                .clip(CircleShape)
                .background(MintCardBg)
                .border(1.dp, MintClayHighlight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (targetAt == null) {
                Text("-", color = MintText, style = MaterialTheme.typography.labelLarge)
            } else {
                // Keep the canvas inset so the thick stroke is never clipped by its bounds.
                Canvas(Modifier.size(60.dp)) {
                    val stroke = Stroke(width = 8.dp.toPx())
                    drawArc(MintLine, -90f, 360f, false, style = stroke)
                    drawArc(Color(0xFF24B883), -90f, 360f * progress, false, style = stroke)
                }
                Text(formatTime(targetAt), color = MintText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text("Target", color = MintMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Shows time remaining to the next export with one uniform ring color at a time. */
@Composable
fun NextCountdownCard(
    nextTag: String?,
    nextAt: Long?,
    intervalStartedAt: Long?,
    now: Long,
    modifier: Modifier = Modifier
) {
    val duration = nextAt?.let { dueAt -> intervalStartedAt?.let { dueAt - it } } ?: 0L
    val rawRemaining = nextAt?.let { it - now } ?: 0L
    val remaining = rawRemaining.coerceAtLeast(0L)
    val elapsedProgress = if (duration > 0L) ((duration - remaining).toFloat() / duration).coerceIn(0f, 1f) else 0f
    val isDue = nextAt != null && remaining == 0L
    val pulseTransition = rememberInfiniteTransition(label = "dueRingPulse")
    val dueAlpha = if (isDue) {
        pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "dueRingAlpha"
        ).value
    } else {
        1f
    }
    val ringColor = lerp(MintAccent, MintDanger, elapsedProgress).copy(alpha = dueAlpha)

    Column(
        modifier = modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(9.dp, CircleShape, spotColor = MintClayShadow)
                .clip(CircleShape)
                .background(MintCardBg)
                .border(1.dp, MintClayHighlight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(60.dp)) {
                val stroke = Stroke(width = 8.dp.toPx())
                drawArc(MintLine, -90f, 360f, false, style = stroke)
                if (nextAt != null) {
                    drawArc(ringColor, -90f, if (isDue) 360f else 360f * elapsedProgress, false, style = stroke)
                }
            }
            Text(
                when {
                    nextAt == null -> "-"
                    rawRemaining < 0L -> "-${formatDuration(-rawRemaining)}"
                    else -> formatDuration(remaining)
                },
                color = if (isDue) MintDanger else MintText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(
            nextTag?.let { "ถัดไป $it" } ?: "รูปถัดไป",
            color = MintMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000L
    return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
