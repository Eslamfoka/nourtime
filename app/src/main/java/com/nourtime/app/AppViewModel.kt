package com.nourtime.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nourtime.app.core.security.ParentSession
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.data.security.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class AppDestination { LOADING, ONBOARDING, UNLOCK, HOME }

/** Top-level routing: nothing opens without the parent PIN once one exists. */
@HiltViewModel
class AppViewModel @Inject constructor(
    security: SecurityRepository,
    onboarding: OnboardingRepository,
    private val session: ParentSession,
    private val clock: DeviceClock,
) : ViewModel() {

    val destination: StateFlow<AppDestination> = combine(
        security.hasPin,
        onboarding.isComplete,
        session.isUnlocked,
    ) { hasPin, complete, unlocked ->
        when {
            !hasPin -> AppDestination.ONBOARDING
            !unlocked -> AppDestination.UNLOCK
            !complete -> AppDestination.ONBOARDING
            else -> AppDestination.HOME
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppDestination.LOADING)

    fun onBackground() = session.onBackground(clock.elapsedRealtime())

    fun onForeground() = session.onForeground(clock.elapsedRealtime())
}
