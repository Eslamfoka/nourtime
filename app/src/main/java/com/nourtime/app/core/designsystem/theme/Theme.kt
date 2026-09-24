package com.nourtime.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat

private val NourShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Parent UI theme: light or dark, with Cairo for Arabic and Nunito for English. */
@Composable
fun NourTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    val isArabic = locale?.language == "ar"
    val typography = remember(isArabic) { nourTypography(if (isArabic) CairoFamily else NunitoFamily) }

    CompositionLocalProvider(LocalNourColors provides if (darkTheme) DarkExtraColors else LightExtraColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            shapes = NourShapes,
            content = content,
        )
    }
}

object NourTheme {
    val colors: NourExtraColors
        @Composable @ReadOnlyComposable get() = LocalNourColors.current
}
