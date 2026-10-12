package com.nourtime.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nourtime.app.core.security.ParentSession
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.mode.AppMode
import com.nourtime.app.data.mode.AppModeRepository
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.data.security.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppDestination {
    LOADING, MODE_CHOICE, PARENT, ONBOARDING, UNLOCK, HOME;

    companion object {
        /** Parent phones skip the child PIN: they are protected by the Google sign-in instead. */
        fun of(mode: AppMode?, hasPin: Boolean, complete: Boolean, unlocked: Boolean): AppDestination = when {
            mode == null -> MODE_CHOICE
            mode == AppMode.PARENT -> PARENT
            !hasPin -> ONBOARDING
            !unlocked -> UNLOCK
            !complete -> ONBOARDING
            else -> HOME
        }
    }
}

/** Top-level routing: nothing opens without the parent PIN once one exists. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val security: SecurityRepository,
    onboarding: OnboardingRepository,
    private val modes: AppModeRepository,
    private val session: ParentSession,
    private val clock: DeviceClock,
) : ViewModel() {

    val destination: StateFlow<AppDestination> = combine(
        modes.mode,
        security.hasPin,
        onboarding.isComplete,
        session.isUnlocked,
    ) { mode, hasPin, complete, unlocked ->
        AppDestination.of(mode, hasPin, complete, unlocked)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppDestination.LOADING)

    init {
        viewModelScope.launch { onboarding.isComplete.collect { session.setInSetup(!it) } }
    }

    fun chooseMode(mode: AppMode) {
        viewModelScope.launch { modes.setMode(mode) }
    }

    /** Undoes a wrong tap on the mode choice. Never once a PIN exists: the child could use it to start over. */
    fun backToModeChoice() {
        viewModelScope.launch { if (!security.hasPin.first()) modes.clearMode() }
    }

    fun onBackground() = session.onBackground(clock.elapsedRealtime())

    fun onForeground() = session.onForeground(clock.elapsedRealtime())
}
