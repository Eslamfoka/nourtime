package com.nourtime.app.feature.learning

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A content word (a letter, "Peacock", "عنكبوت") on one line, shrunk until it fits the space.
 *
 * Plain Text keeps the theme's fixed line height (25 sp), so a 96 sp word that wrapped drew its
 * second line on top of the first and split Arabic letters apart. Here the word never wraps and the
 * line height follows the font size.
 */
@Composable
fun FitText(
    text: String,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val base = LocalTextStyle.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxWidth = constraints.maxWidth
        val size = remember(text, maxSize, maxWidth, base) {
            fitSize(maxSize.value, minSize.value) { candidate ->
                maxWidth == Constraints.Infinity ||
                    measurer.measure(text, fitStyle(base, candidate.sp, fontWeight), maxLines = 1, softWrap = false).size.width <= maxWidth
            }
        }
        Text(
            text,
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            style = fitStyle(base, size.sp, fontWeight),
        )
    }
}

private fun fitStyle(base: TextStyle, size: TextUnit, weight: FontWeight) = base.copy(
    fontSize = size,
    fontWeight = weight,
    lineHeight = 1.3.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** The biggest size from [max] down to [min] (in 2 sp steps) that [fits]; [min] when none does. */
internal fun fitSize(max: Float, min: Float, fits: (Float) -> Boolean): Float {
    var size = max
    while (size > min) {
        if (fits(size)) return size
        size -= 2f
    }
    return min
}
