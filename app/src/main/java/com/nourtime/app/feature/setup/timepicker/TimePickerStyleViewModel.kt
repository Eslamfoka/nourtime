package com.nourtime.app.feature.setup.timepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.data.ui.TimePickerStyle
import com.nourtime.app.data.ui.UiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The phone's time-picker theme (U1): read at the app root, changed from Settings. */
@HiltViewModel
class TimePickerStyleViewModel @Inject constructor(
    private val prefs: UiPreferences,
) : ViewModel() {
    val style: StateFlow<TimePickerStyle> =
        prefs.timePickerStyle.stateIn(viewModelScope, SharingStarted.Eagerly, TimePickerStyle.DIAL)

    fun select(style: TimePickerStyle) {
        viewModelScope.launch { prefs.setTimePickerStyle(style) }
    }
}

/** Settings card for the theme; used on the child's phone and on the parent's phone. */
@Composable
fun TimePickerStyleSetting(modifier: Modifier = Modifier, viewModel: TimePickerStyleViewModel = hiltViewModel()) {
    val style by viewModel.style.collectAsStateWithLifecycle()
    NourCard(modifier) {
        TimePickerStyleChooser(style, viewModel::select)
    }
}
