package com.nourtime.app.core.designsystem.component

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.security.PinRules

private val KeySize = 68.dp

/** Large number pad. Always laid out 1-2-3 left to right, like a phone dialer, even in RTL. */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { d -> PinKey(enabled, onClick = { onDigit(d) }) { KeyLabel(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Spacer(Modifier.size(KeySize))
                PinKey(enabled, onClick = { onDigit('0') }) { KeyLabel('0') }
                val deleteLabel = stringResource(R.string.pin_delete)
                PinKey(enabled, onClick = onDelete, subtle = true, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun KeyLabel(digit: Char) {
    Text(digit.toString(), style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtle: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (subtle) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        modifier = modifier.size(KeySize),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Dots that fill as digits are typed. Each change of [shakeKey] (other than the first) shakes the
 * dots and gives a light "reject" vibration.
 */
@Composable
fun PinDots(
    filled: Int,
    shakeKey: Int,
    modifier: Modifier = Modifier,
    length: Int = PinRules.LENGTH,
) {
    val offset = remember { Animatable(0f) }
    val view = LocalView.current
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        )
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 400
                -18f at 50
                18f at 120
                -12f at 190
                12f at 260
                -5f at 330
            },
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.offset { IntOffset(offset.value.dp.roundToPx(), 0) },
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(length) { i ->
                val isFilled = i < filled
                Box(
                    Modifier
                        .size(20.dp)
                        .background(if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background, CircleShape)
                        .border(2.dp, if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
    }
}
