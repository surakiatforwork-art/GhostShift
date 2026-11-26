package com.phantom.ghostshift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.phantom.ghostshift.ui.theme.*

@Composable
fun MintCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MintCardBg,
    contentPadding: Dp = 14.dp,
    accentBorder: Color? = null,
    elevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = MintShadow, spotColor = MintShadow)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, accentBorder ?: MintLine.copy(alpha = 0.8f), shape)
            .padding(contentPadding),
        content = content
    )
}

enum class UtilityTone { Primary, Neutral, Mint, Sky, Lavender, Peach, Coral, Aqua }

@Composable
private fun toneColors(tone: UtilityTone): Pair<Color, Color> {
    val dark = LocalGhostDark.current
    return when (tone) {
        UtilityTone.Primary -> Color(0xFF7650E6) to Color.White
        UtilityTone.Mint -> if (dark) Color(0xFF245447) to Color(0xFFB1F8DB) else Color(0xFFE0F7EF) to Color(0xFF087C61)
        UtilityTone.Sky -> if (dark) Color(0xFF293F5D) to Color(0xFFB9DBFF) else Color(0xFFDDEEFF) to Color(0xFF2364BE)
        UtilityTone.Lavender -> if (dark) Color(0xFF423658) to Color(0xFFE0CFFF) else Color(0xFFECE1FF) to Color(0xFF6942C5)
        UtilityTone.Peach -> if (dark) Color(0xFF533C29) to Color(0xFFFFC68E) else Color(0xFFFFE8D5) to Color(0xFF81470E)
        UtilityTone.Coral -> MintErrBg to MintErrText
        UtilityTone.Aqua -> if (dark) Color(0xFF214E50) to Color(0xFFA8F5EE) else Color(0xFFD8F7F3) to Color(0xFF12595B)
        UtilityTone.Neutral -> MintCardBg to MintText
    }
}

@Composable
internal fun GlassControl(
    modifier: Modifier, tone: UtilityTone, enabled: Boolean, onClick: () -> Unit,
    content: @Composable BoxScope.(Color) -> Unit
) {
    val (base, ink) = toneColors(tone)
    val dark = LocalGhostDark.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) .97f else 1f, tween(if (pressed) 75 else 125), label = "glassPress")
    val shape = RoundedCornerShape(16.dp)
    val fill = if (enabled) base else lerp(base, MintBg, .75f)
    val fg = if (enabled) ink else MintMuted2
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .20f else .95f), MintLine.copy(alpha = .65f)))
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(if (enabled && !pressed) 2.dp else 0.dp, shape)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(fill, Color.White, if (dark) .06f else .12f), fill, lerp(fill, Color.Black, .02f))))
        .border(1.dp, edge, shape)
        .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content(fg) }
}

@Composable
fun ClayIconButton(
    onClick: () -> Unit, icon: ImageVector, contentDescription: String,
    modifier: Modifier = Modifier, tint: Color = Color.Unspecified, enabled: Boolean = true,
    tone: UtilityTone = UtilityTone.Neutral
) {
    GlassControl(modifier, tone, enabled, onClick) { fg ->
        Icon(icon, contentDescription, tint = if (enabled && tint != Color.Unspecified) tint else fg)
    }
}

@Composable
fun ClayActionButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true, danger: Boolean = false,
    emphasized: Boolean = true, tone: UtilityTone = if (danger) UtilityTone.Coral else if (emphasized) UtilityTone.Mint else UtilityTone.Neutral
) {
    GlassControl(modifier, tone, enabled, onClick) { fg ->
        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(5.dp)) }
            Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun MintBadge(
    text: String,
    type: String = "ok", // ok, wait, err
    modifier: Modifier = Modifier
) {
    val (bg, contentColor, border) = when (type) {
        "downloaded" -> Triple(
            if (LocalGhostDark.current) Color(0xFF3F444C) else Color(0xFFE2E3E6),
            if (LocalGhostDark.current) Color(0xFFE2E3E6) else Color(0xFF454950), MintLine)
        "ok" -> Triple(MintOkBg, MintOkText, MintLine)
        "wait" -> Triple(MintWaitBg, MintWaitText, MintLine) // Warn colors
        "err" -> Triple(MintErrBg, MintErrText, MintLine)
        else -> Triple(MintCardBg, MintMuted, MintLine)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun MintButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    ClayActionButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        danger = danger,
        modifier = modifier.heightIn(min = 44.dp)
    )
}
