package com.byd.mirror.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BydColors {
    val ObsidianBg = Color(0xFF080B10)
    val SurfaceCard = Color(0xFF111827)
    val SurfaceElevated = Color(0xFF1A2332)
    val EvCyan = Color(0xFF00D4FF)
    val EvEmerald = Color(0xFF10B981)
    val WarningAmber = Color(0xFFF59E0B)
    val DangerRed = Color(0xFFEF4444)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val BorderSubtle = Color(0xFF1E293B)
}

private val BydDarkColorScheme = darkColorScheme(
    primary = BydColors.EvCyan,
    secondary = BydColors.EvEmerald,
    background = BydColors.ObsidianBg,
    surface = BydColors.SurfaceCard,
    onPrimary = Color(0xFF041016),
    onBackground = BydColors.TextPrimary,
    onSurface = BydColors.TextPrimary,
    error = BydColors.DangerRed
)

@Composable
fun BydMirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BydDarkColorScheme,
        content = content
    )
}
