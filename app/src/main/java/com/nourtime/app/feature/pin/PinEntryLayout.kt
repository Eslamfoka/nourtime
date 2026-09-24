package com.nourtime.app.feature.pin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nourtime.app.core.designsystem.component.NourFace
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.designsystem.component.PinDots
import com.nourtime.app.core.designsystem.component.PinPad

/** Shared layout for creating and entering the parent PIN. */
@Composable
fun PinEntryLayout(
    title: String,
    body: String,
    message: String?,
    filled: Int,
    shakeKey: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NourStar(Modifier.size(72.dp), face = NourFace.CLOCK, animated = false)
        AnimatedContent(title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pinTitle") {
            Text(it, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PinDots(filled = filled, shakeKey = shakeKey, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 36.dp),
        )
        PinPad(onDigit = onDigit, onDelete = onDelete, enabled = enabled)
    }
}
