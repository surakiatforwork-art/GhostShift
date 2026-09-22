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
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .shadow(12.dp, shape, spotColor = MintShadow)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, MintLine.copy(alpha = 0.8f), shape)
            .padding(contentPadding),
        content = content
    )
}

/** Clay treatment is deliberately reserved for elements the user can operate. */
@Composable
fun ClayIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MintText,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.94f else 1f, tween(110), label = "clayIconPress")
    Surface(
        color = MintCardBg,
        contentColor = tint,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 7.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MintClayHighlight),
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
        ) {
            Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else MintMuted2)
        }
    }
}

@Composable
fun ClayActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, tween(110), label = "clayActionPress")
    val background = if (danger) MintErrBg else MintAccent
    val foreground = if (danger) MintDanger else Color.White
    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = if (enabled) 8.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) Color(0xFFFFD1D1) else MintClayHighlight),
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(it, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.Bold)
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
        "ok" -> Triple(MintSoft, Color(0xFF0B5F4A), Color(0xFFBFEEE0))
        "wait" -> Triple(Color(0xFFFFF7E6), Color(0xFF7A4E00), Color(0xFFFFE2AD)) // Warn colors
        "err" -> Triple(Color(0xFFFFF1F1), Color(0xFF7C1313), Color(0xFFFFD1D1))
        else -> Triple(Color(0xFFFBFFFD), MintMuted, MintLine)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
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
    primary: Boolean = false,
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
