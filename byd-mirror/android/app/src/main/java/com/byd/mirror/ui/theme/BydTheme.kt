package com.byd.mirror.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object BydColors {
    // Phase 2 Design System Tokens
    val Background = Color(0xFF050914)
    val PrimaryBackground = Color(0xFF080F1C)
    val Surface = Color(0xFF101B2D)
    val ElevatedSurface = Color(0xFF15243B)
    val Border = Color(0xFF203754)
    val Primary = Color(0xFF00C8FF)
    val PrimaryBlue = Color(0xFF1677FF)
    val SecondaryCyan = Color(0xFF00E5FF)
    val Success = Color(0xFF00D084)
    val Warning = Color(0xFFFFB547)
    val Error = Color(0xFFFF4545)
    val PrimaryText = Color(0xFFF4F8FF)
    val SecondaryText = Color(0xFF9AAEC7)
    val Disabled = Color(0xFF65748A)

    // Backwards-compatible aliases preserved for any internal references
    val ObsidianBg = Background
    val SurfaceCard = Surface
    val SurfaceElevated = ElevatedSurface
    val EvCyan = Primary
    val EvEmerald = Success
    val WarningAmber = Warning
    val DangerRed = Error
    val TextPrimary = PrimaryText
    val TextSecondary = SecondaryText
    val BorderSubtle = Border
}

object BydGradients {
    val DeepNavyBackdrop = Brush.verticalGradient(
        colors = listOf(BydColors.PrimaryBackground, BydColors.Background)
    )
    val ElectricBlueToCyan = Brush.horizontalGradient(
        colors = listOf(BydColors.PrimaryBlue, BydColors.Primary)
    )
    val CyanAura = Brush.radialGradient(
        colors = listOf(
            BydColors.Primary.copy(alpha = 0.34f),
            BydColors.PrimaryBlue.copy(alpha = 0.14f),
            Color.Transparent
        )
    )
}

private val BydDarkColorScheme = darkColorScheme(
    primary = BydColors.Primary,
    secondary = BydColors.PrimaryBlue,
    tertiary = BydColors.Success,
    background = BydColors.Background,
    surface = BydColors.Surface,
    surfaceVariant = BydColors.ElevatedSurface,
    outline = BydColors.Border,
    onPrimary = Color(0xFF041016),
    onSecondary = BydColors.PrimaryText,
    onBackground = BydColors.PrimaryText,
    onSurface = BydColors.PrimaryText,
    onSurfaceVariant = BydColors.SecondaryText,
    error = BydColors.Error
)

@Composable
fun BydMirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BydDarkColorScheme,
        content = content
    )
}
