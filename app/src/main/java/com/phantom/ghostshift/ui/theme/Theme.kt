package com.phantom.ghostshift.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val MintColorScheme = lightColorScheme(
    primary = MintAccent,
    onPrimary = MintText, // Dark text on bright mint? Or white? HTML uses #062a22 (Dark) for primary btn text
    secondary = MintMuted,
    background = MintBg,
    surface = MintCardBg,
    onBackground = MintText,
    onSurface = MintText,
    error = MintDanger,
    surfaceVariant = MintSoft,
)

private val GhostShiftShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

@Composable
fun GhostShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Ignoring dark theme for now to force Mint Light as requested? User implies "Mint Theme".
    content: @Composable () -> Unit
) {
    val colorScheme = MintColorScheme // Force Light/Mint for now per request

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MintBg.toArgb() // Match bg
            window.navigationBarColor = MintBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GhostShiftShapes,
        content = content
    )
}
