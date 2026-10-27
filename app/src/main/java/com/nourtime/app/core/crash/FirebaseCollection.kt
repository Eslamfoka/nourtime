package com.nourtime.app.core.crash

import com.nourtime.app.data.mode.AppMode

/**
 * What Firebase may do on its own on this phone, from whether it is connected; both are off in the
 * manifest. Crash reports: [CrashReporting]. Pushes (FCM auto-init, which creates this phone's FCM
 * token and Firebase installation id): only on a signed-in parent's phone, the one phone that receives
 * pushes. A child's phone never registers a token, so even paired it doesn't create one. Debug builds
 * get pushes too, or they couldn't be tested.
 */
data class FirebaseCollection(val crashReports: Boolean, val pushes: Boolean) {
    companion object {
        val NONE = FirebaseCollection(crashReports = false, pushes = false)

        fun of(debug: Boolean, mode: AppMode?, childPaired: Boolean, parentSignedIn: Boolean) = FirebaseCollection(
            crashReports = CrashReporting.allowed(debug, mode, childPaired, parentSignedIn),
            pushes = mode == AppMode.PARENT && parentSignedIn,
        )
    }
}
