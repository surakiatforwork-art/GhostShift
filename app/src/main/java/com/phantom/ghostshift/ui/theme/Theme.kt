package com.phantom.ghostshift.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val GhostShiftShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
)

@Composable
fun GhostShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalGhostDark provides darkTheme) {
        val scheme = if (darkTheme) darkColorScheme(
            primary = MintAccent, onPrimary = Color(0xFF10372C), background = MintBg,
            surface = MintCardBg, onBackground = MintText, onSurface = MintText,
            surfaceVariant = MintSoft, onSurfaceVariant = MintMuted, error = MintDanger,
            outline = MintLine
        ) else lightColorScheme(
            primary = MintAccent, onPrimary = Color.White, background = MintBg,
            surface = MintCardBg, onBackground = MintText, onSurface = MintText,
            surfaceVariant = MintSoft, onSurfaceVariant = MintMuted, error = MintDanger,
            outline = MintLine
        )
        val view = LocalView.current
        val bg = MintBg.toArgb()
        if (!view.isInEditMode) SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = bg
            window.navigationBarColor = bg
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
        MaterialTheme(colorScheme = scheme, typography = Typography, shapes = GhostShiftShapes, content = content)
    }
}
