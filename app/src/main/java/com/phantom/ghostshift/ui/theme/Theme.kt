package com.phantom.ghostshift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1FBF9A),
    secondary = Color(0xFF0E2A23),
    tertiary = Color(0xFF7AA39A),
    background = Color(0xFFF6FFFB),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun GhostShiftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
