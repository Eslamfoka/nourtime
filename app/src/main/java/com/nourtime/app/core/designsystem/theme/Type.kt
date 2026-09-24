package com.nourtime.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nourtime.app.R

private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int) = FontFamily(
    weights.map { weight ->
        Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

/** Cairo covers Arabic and Latin; Nunito is used for English UI. Both are bundled (brief §12). */
val CairoFamily = variableFamily(R.font.cairo_variable)
val NunitoFamily = variableFamily(R.font.nunito_variable)

internal fun nourTypography(family: FontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    return Typography(
        displayLarge = style(52, 60, FontWeight.ExtraBold),
        displayMedium = style(42, 50, FontWeight.ExtraBold),
        displaySmall = style(34, 42, FontWeight.Bold),
        headlineLarge = style(30, 38, FontWeight.Bold),
        headlineMedium = style(26, 34, FontWeight.Bold),
        headlineSmall = style(22, 30, FontWeight.Bold),
        titleLarge = style(20, 28, FontWeight.Bold),
        titleMedium = style(17, 24, FontWeight.SemiBold),
        titleSmall = style(15, 22, FontWeight.SemiBold),
        bodyLarge = style(16, 25, FontWeight.Normal),
        bodyMedium = style(14, 22, FontWeight.Normal),
        bodySmall = style(12, 18, FontWeight.Normal),
        labelLarge = style(16, 22, FontWeight.Bold),
        labelMedium = style(13, 18, FontWeight.SemiBold),
        labelSmall = style(11, 16, FontWeight.SemiBold),
    )
}
