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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nourtime.app.core.designsystem.component.NourPose
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
    /** Shown under [message] and read only when focused: a countdown that changes every second. */
    detail: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NourStar(Modifier.size(72.dp), pose = NourPose.CLOCK, animated = false)
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
        Column(Modifier.heightIn(min = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Always there, so TalkBack announces each new message (a wrong PIN, the tries left, a wait
            // starting) once, without moving focus off the pad.
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
        PinPad(onDigit = onDigit, onDelete = onDelete, enabled = enabled)
    }
}
