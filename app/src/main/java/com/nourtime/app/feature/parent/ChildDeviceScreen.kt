package com.nourtime.app.feature.parent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.remote.parent.ParentUser

/** Status, actions and settings for one child's phone (filled in by Phase 2, task 10). */
@Composable
fun ChildDeviceScreen(deviceId: String, user: ParentUser, onBack: () -> Unit) {
    CenteredScrollColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Text(deviceId)
        NourTextButton("Back", onBack)
    }
}
