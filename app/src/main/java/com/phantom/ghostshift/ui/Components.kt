package com.phantom.ghostshift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    Column(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = MintShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(1.dp, MintLine, RoundedCornerShape(18.dp))
            .padding(contentPadding),
        content = content
    )
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
    val bg = if (primary) {
        Brush.verticalGradient(GradientPrimary)
    } else if (danger) {
        androidx.compose.ui.graphics.SolidColor(Color(0xFFFFF1F1))
    } else {
        androidx.compose.ui.graphics.SolidColor(MintCardBg)
    }
    
    val border = if (primary) null else if (danger) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD1D1)) else androidx.compose.foundation.BorderStroke(1.dp, MintLine)
    val contentColor = if (primary) Color(0xFF062A22) else if (danger) Color(0xFF7C1313) else MintText

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, 
            contentColor = contentColor,
            disabledContainerColor = Color.LightGray
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.then(if (primary) Modifier.background(bg) else Modifier),
            contentAlignment = Alignment.Center
        ) {
             Text(text = text, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}
