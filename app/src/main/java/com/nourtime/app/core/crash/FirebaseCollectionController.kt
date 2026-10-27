package com.nourtime.app.core.crash

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * off in the manifest. Crashlytics' setting persists, so it is set both ways on every change; reports
 * saved while it was off (an unpaired phone) are deleted, never sent later. FCM is touched only when
 * [FcmTokenCleanup] says so: with pushes off, a token that may be here (made by an older version, when
 * auto-init was on everywhere, or left after leaving parent mode) is deleted once; a phone that never
 * had one never calls FCM at all.
 */
@Singleton
class FirebaseCollectionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modes: AppModeRepository,
    private val identity: DeviceIdentity,
    // Lazy: a child's phone never starts Firebase Auth just for this.
    private val parentAuth: Lazy<ParentAuth>,
    private val tokenFlag: FcmTokenFlag,
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

        // A settings error here mustn't stop the crash-report setting following later changes.
        try {
            applyPushes(collection.pushes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM setting not applied", e)
        }
    }

    private suspend fun applyPushes(pushes: Boolean) {
        val stored = tokenFlag.read()
        val mayExist = FcmTokenCleanup.tokenMayExist(stored, updatedFromOlderVersion())
        when (FcmTokenCleanup.action(pushes, mayExist)) {
            FcmAction.TURN_ON -> {
                // Saved first: if Nour Time stops right after, the next start still knows to clean up.
                tokenFlag.set(true)
                FirebaseMessaging.getInstance().isAutoInitEnabled = true
            }
            FcmAction.TURN_OFF_AND_DELETE -> {
                // Off first, so the delete below isn't followed by a new token. The setting persists in
                // FCM's own storage, so it's turned off here even though the manifest says off.
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
                // Kept until the delete works (offline, or a timeout): tried again at the next start.
                tokenFlag.set(!deleteToken())
            }
            // Remembered, so a later update of this install doesn't look like one from an older version.
            FcmAction.NOTHING -> if (stored == null) tokenFlag.set(false)
        }
    }

    /** True once the token is gone (with none, FCM answers at once without contacting anything). */
    private suspend fun deleteToken(): Boolean = try {
        // Bounded: offline, it mustn't hold up the next change.
        withTimeoutOrNull(TOKEN_DELETE_TIMEOUT_MS) { FirebasePushTokenSource(tokenFlag).delete() } != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "FCM token delete failed", e)
        false
    }

    /**
     * Whether an older version was installed before this one: the system keeps the first install time
     * across updates, so the two times differ on any updated phone. Older versions made an FCM token
     * at their first start, before anything was saved in the settings (a phone opened once and left on
     * the first screen has no settings key), so a settings key would miss some; the install times don't,
     * and a settings file restored from its backup can't change them. A fresh install updated before its
     * first start counts too, which costs one delete with no token: answered by FCM without the network.
     */
    private fun updatedFromOlderVersion(): Boolean = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.firstInstallTime != info.lastUpdateTime
    } catch (e: PackageManager.NameNotFoundException) {
        // Can't happen for this app's own package; if it did, cleaning up once is the safe side.
        true
    }

    private companion object {
        const val TAG = "FirebaseCollection"
        const val TOKEN_DELETE_TIMEOUT_MS = 5_000L
    }
}
