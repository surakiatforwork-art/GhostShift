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
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .shadow(2.dp, shape, spotColor = MintShadow)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, MintLine.copy(alpha = 0.8f), shape)
            .padding(contentPadding),
        content = content
    )
}

enum class UtilityTone { Neutral, Mint, Sky, Lavender, Peach, Coral, Aqua }

@Composable
private fun toneColors(tone: UtilityTone): Pair<Color, Color> {
    val dark = LocalGhostDark.current
    return when (tone) {
        UtilityTone.Mint -> if (dark) Color(0xFF245447) to Color(0xFFB1F8DB) else Color(0xFF9EE9CB) to Color(0xFF104B37)
        UtilityTone.Sky -> if (dark) Color(0xFF293F5D) to Color(0xFFB9DBFF) else Color(0xFFBBDFFF) to Color(0xFF174A83)
        UtilityTone.Lavender -> if (dark) Color(0xFF423658) to Color(0xFFE0CFFF) else Color(0xFFE3D7FF) to Color(0xFF583A91)
        UtilityTone.Peach -> if (dark) Color(0xFF533C29) to Color(0xFFFFC68E) else Color(0xFFFFD6AD) to Color(0xFF81470E)
        UtilityTone.Coral -> MintErrBg to MintErrText
        UtilityTone.Aqua -> if (dark) Color(0xFF214E50) to Color(0xFFA8F5EE) else Color(0xFF9BECE4) to Color(0xFF12595B)
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
    val scale by animateFloatAsState(if (pressed && enabled) .97f else 1f, tween(100), label = "glassPress")
    val shape = RoundedCornerShape(9.dp)
    val fill = if (enabled) base else lerp(base, MintBg, .75f)
    val fg = if (enabled) ink else MintMuted2
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .32f else .96f), MintLine, Color.Black.copy(alpha = if (dark) .45f else .16f)))
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(if (enabled && !pressed) 3.dp else 0.dp, shape)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(fill, Color.White, if (dark) .10f else .28f), fill, lerp(fill, Color.Black, .06f))))
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
