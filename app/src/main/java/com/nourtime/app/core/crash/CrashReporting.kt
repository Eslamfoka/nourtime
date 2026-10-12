package com.nourtime.app.core.crash

import com.nourtime.app.data.mode.AppMode

/**
 * Crash reports (Firebase Crashlytics) only from phones already connected with the parent's consent:
 * a paired child's phone, or a signed-in parent's phone. Owner's decision (2026-10-12), so "without a
 * connected parent's phone, nothing leaves your child's phone" stays true. Never from debug builds.
 */
object CrashReporting {
    fun allowed(debug: Boolean, mode: AppMode?, childPaired: Boolean, parentSignedIn: Boolean): Boolean =
        !debug && when (mode) {
            AppMode.CHILD -> childPaired
            AppMode.PARENT -> parentSignedIn
            null -> false
        }
}
