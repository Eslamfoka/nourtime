package com.nourtime.app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nourtime.app.R
import kotlinx.coroutines.delay

/**
 * The app's spinner in the middle of [modifier], read as "Loading…". It appears only after
 * [SHOW_AFTER_MS]: most loads (a settings read) finish sooner, and a spinner that flashes for a
 * moment looks like a glitch.
 */
@Composable
fun CenteredProgress(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHOW_AFTER_MS)
        shown = true
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (shown) {
            val label = stringResource(R.string.device_loading)
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

private const val SHOW_AFTER_MS = 300L
