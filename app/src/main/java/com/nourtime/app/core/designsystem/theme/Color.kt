package com.nourtime.app.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand palette (brief §12). */
object NourPalette {
    val Gold = Color(0xFFFFC857)
    val GoldLight = Color(0xFFFFE3A3)
    val GoldDeep = Color(0xFFE8A93A)
    val Navy = Color(0xFF1E2A4A)
    val NavyDeep = Color(0xFF141C33)
    val NavySoft = Color(0xFF2B3960)
    val Cream = Color(0xFFFFF8EC)
    val Coral = Color(0xFFFF7A6B)
    val CoralDeep = Color(0xFFC94A3C)
    val Mint = Color(0xFF5CC8A8)
    val MintDeep = Color(0xFF1F7A61)
    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF5D6785)
    val MutedDark = Color(0xFFB4BCD4)
}

internal val LightColors = lightColorScheme(
    primary = NourPalette.Gold,
    onPrimary = NourPalette.Navy,
    primaryContainer = NourPalette.GoldLight,
    onPrimaryContainer = NourPalette.Navy,
    secondary = NourPalette.Navy,
    onSecondary = NourPalette.Cream,
    tertiary = NourPalette.Mint,
    onTertiary = NourPalette.Navy,
    background = NourPalette.Cream,
    onBackground = NourPalette.Navy,
    surface = NourPalette.White,
    onSurface = NourPalette.Navy,
    surfaceVariant = Color(0xFFF6ECD9),
    onSurfaceVariant = NourPalette.Muted,
    surfaceContainerLowest = NourPalette.White,
    surfaceContainerLow = Color(0xFFFFFBF4),
    surfaceContainer = Color(0xFFFBF1E0),
    surfaceContainerHigh = Color(0xFFF6ECD9),
    outline = Color(0xFFCFC3AC),
    outlineVariant = Color(0xFFE9DEC8),
    error = NourPalette.CoralDeep,
    onError = NourPalette.White,
)

internal val DarkColors = darkColorScheme(
    primary = NourPalette.Gold,
    onPrimary = NourPalette.Navy,
    primaryContainer = Color(0xFF5A4A22),
    onPrimaryContainer = NourPalette.GoldLight,
    secondary = NourPalette.Cream,
    onSecondary = NourPalette.Navy,
    tertiary = NourPalette.Mint,
    onTertiary = NourPalette.Navy,
    background = NourPalette.NavyDeep,
    onBackground = NourPalette.Cream,
    surface = NourPalette.Navy,
    onSurface = NourPalette.Cream,
    surfaceVariant = NourPalette.NavySoft,
    onSurfaceVariant = NourPalette.MutedDark,
    surfaceContainerLowest = NourPalette.NavyDeep,
    surfaceContainerLow = Color(0xFF1A2440),
    surfaceContainer = NourPalette.Navy,
    surfaceContainerHigh = Color(0xFF26335A),
    outline = Color(0xFF55618A),
    outlineVariant = Color(0xFF34416A),
    error = NourPalette.Coral,
    onError = NourPalette.Navy,
)

/** Colors outside the Material scheme: success (mint) and danger (coral). */
@Immutable
data class NourExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successText: Color,
    val danger: Color,
    val onDanger: Color,
)

internal val LightExtraColors = NourExtraColors(
    success = NourPalette.Mint,
    onSuccess = NourPalette.Navy,
    successText = NourPalette.MintDeep,
    danger = NourPalette.Coral,
    onDanger = NourPalette.Navy,
)

internal val DarkExtraColors = NourExtraColors(
    success = NourPalette.Mint,
    onSuccess = NourPalette.Navy,
    successText = NourPalette.Mint,
    danger = NourPalette.Coral,
    onDanger = NourPalette.Navy,
)

val LocalNourColors = staticCompositionLocalOf { LightExtraColors }
