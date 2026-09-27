package com.nourtime.app.feature.parent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn

/** The parent's phone (Phase 2). Sign-in and the child list arrive in the next task. */
@Composable
fun ParentRoute() {
    CenteredScrollColumn(Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.mode_parent_title), style = MaterialTheme.typography.headlineMedium)
    }
}
