package com.nourtime.app.core.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.nourtime.app.BuildConfig
import com.nourtime.app.data.mode.AppMode
import com.nourtime.app.data.mode.AppModeRepository
import com.nourtime.app.remote.child.DeviceIdentity
import com.nourtime.app.remote.parent.ParentAuth
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns Crashlytics on and off with [CrashReporting]. It is off in the manifest, and its on/off
 * setting persists, so it is set both ways on every change. Reports saved while it was off (an
 * unpaired phone) are deleted, never sent later.
 */
@Singleton
class CrashReportingController @Inject constructor(
    private val modes: AppModeRepository,
    private val identity: DeviceIdentity,
    // Lazy: a child's phone never starts Firebase Auth just for this.
    private val parentAuth: Lazy<ParentAuth>,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        modes.mode.flatMapLatest { mode ->
            when (mode) {
                AppMode.CHILD -> identity.pairedOwner.map { CrashReporting.allowed(BuildConfig.DEBUG, mode, it != null, false) }
                AppMode.PARENT -> parentAuth.get().user.map { CrashReporting.allowed(BuildConfig.DEBUG, mode, false, it != null) }
                null -> flowOf(false)
            }
        }.distinctUntilChanged().collect { on ->
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (!on) crashlytics.deleteUnsentReports()
            crashlytics.setCrashlyticsCollectionEnabled(on)
        }
    }
}
