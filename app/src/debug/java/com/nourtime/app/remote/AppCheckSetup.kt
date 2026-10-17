package com.nourtime.app.remote

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds: nothing against the local emulator (it doesn't check App Check); against the real
 * project, the debug provider, which logs a token (tag DebugAppCheckProvider) to add in the Firebase
 * console under App Check → Manage debug tokens. Release builds use Play Integrity.
 */
object AppCheckSetup {
    @Volatile private var installed = false

    @Synchronized
    fun install() {
        if (installed || FirebaseModule.usesEmulator) return
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        installed = true
    }
}
