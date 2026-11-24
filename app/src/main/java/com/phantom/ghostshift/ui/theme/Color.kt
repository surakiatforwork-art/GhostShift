package com.phantom.ghostshift.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalGhostDark = staticCompositionLocalOf { false }
@Composable @ReadOnlyComposable
private fun shade(light: Long, dark: Long): Color = Color(if (LocalGhostDark.current) dark else light)

val MintBg: Color @Composable @ReadOnlyComposable get() = shade(0xFFF7F7FC, 0xFF14191F)
val MintCardBg: Color @Composable @ReadOnlyComposable get() = shade(0xFFFFFFFF, 0xFF202730)
val MintLine: Color @Composable @ReadOnlyComposable get() = shade(0xFFE7E6F1, 0xFF39434F)
val GradientPrimary = listOf(Color(0xFF1FBF9A), Color(0xFF159677))
val MintText: Color @Composable @ReadOnlyComposable get() = shade(0xFF242438, 0xFFEAF0F7)
val MintMuted: Color @Composable @ReadOnlyComposable get() = shade(0xFF666985, 0xFFACB8C7)
val MintMuted2: Color @Composable @ReadOnlyComposable get() = shade(0xFF788595, 0xFF8794A3)
val MintAccent: Color @Composable @ReadOnlyComposable get() = shade(0xFF7050D9, 0xFFB8A5FF)
val MintSoft: Color @Composable @ReadOnlyComposable get() = shade(0xFFF0EBFC, 0xFF343047)
val MintWarn: Color @Composable @ReadOnlyComposable get() = shade(0xFF995A13, 0xFFFFC17C)
val MintDanger: Color @Composable @ReadOnlyComposable get() = shade(0xFFBB3546, 0xFFFF8997)
val MintShadow: Color @Composable @ReadOnlyComposable get() = shade(0xFF536273, 0xFF000000)
val MintClayShadow: Color @Composable @ReadOnlyComposable get() = shade(0xFF536273, 0xFF000000)
val MintClayHighlight: Color @Composable @ReadOnlyComposable get() = shade(0xCCFFFFFF, 0x667C91A8)

// Badges
val MintOkBg: Color @Composable @ReadOnlyComposable get() = shade(0xFFC9F2E1, 0xFF24473D)
val MintOkText: Color @Composable @ReadOnlyComposable get() = shade(0xFF125B43, 0xFF9DEBCC)
val MintWaitBg: Color @Composable @ReadOnlyComposable get() = shade(0xFFFFE4CC, 0xFF49362B)
val MintWaitText: Color @Composable @ReadOnlyComposable get() = shade(0xFF78440E, 0xFFFFC58F)
val MintErrBg: Color @Composable @ReadOnlyComposable get() = shade(0xFFFFDBDF, 0xFF482C35)
val MintErrText: Color @Composable @ReadOnlyComposable get() = shade(0xFF982B3C, 0xFFFFACB5)
