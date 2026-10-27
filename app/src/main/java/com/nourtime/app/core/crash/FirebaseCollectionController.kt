package com.nourtime.app.core.crash

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.nourtime.app.BuildConfig
import com.nourtime.app.data.mode.AppMode
import com.nourtime.app.data.mode.AppModeRepository
import com.nourtime.app.remote.child.DeviceIdentity
import com.nourtime.app.remote.parent.FirebasePushTokenSource
import com.nourtime.app.remote.parent.ParentAuth
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns Crashlytics and FCM auto-init on and off with [FirebaseCollection], at app start (so phones
 * connected before an update are set too) and on every change of mode, pairing or sign-in. Both are
 * off in the manifest and their on/off setting persists, so they are set both ways on every change.
 * Reports saved while Crashlytics was off (an unpaired phone) are deleted, never sent later. With
 * pushes off, this phone's FCM token is deleted: one made before auto-init was off in the manifest
 * (older versions), or left after leaving parent mode; with no token it is a local no-op.
 */
@Singleton
class FirebaseCollectionController @Inject constructor(
    private val modes: AppModeRepository,
    private val identity: DeviceIdentity,
    // Lazy: a child's phone never starts Firebase Auth just for this.
    private val parentAuth: Lazy<ParentAuth>,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        modes.mode.flatMapLatest { mode ->
            when (mode) {
                AppMode.CHILD -> identity.pairedOwner.map { FirebaseCollection.of(BuildConfig.DEBUG, mode, it != null, false) }
                AppMode.PARENT -> parentAuth.get().user.map { FirebaseCollection.of(BuildConfig.DEBUG, mode, false, it != null) }
                null -> flowOf(FirebaseCollection.NONE)
            }
        }.distinctUntilChanged().collect { apply(it) }
    }

    private suspend fun apply(collection: FirebaseCollection) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (!collection.crashReports) crashlytics.deleteUnsentReports()
        crashlytics.setCrashlyticsCollectionEnabled(collection.crashReports)

        // Off first, so the delete below isn't followed by a new token.
        FirebaseMessaging.getInstance().isAutoInitEnabled = collection.pushes
        if (!collection.pushes) {
            // Bounded: offline, it mustn't hold up the next change.
            try {
                withTimeoutOrNull(TOKEN_DELETE_TIMEOUT_MS) { FirebasePushTokenSource().delete() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "FCM token delete failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "FirebaseCollection"
        const val TOKEN_DELETE_TIMEOUT_MS = 5_000L
    }
}
