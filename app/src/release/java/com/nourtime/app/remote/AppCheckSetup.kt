package com.nourtime.app.remote

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Firebase App Check: Firestore and Auth only answer this app as installed from Google Play (Play
 * Integrity), once enforcement is turned on in the Firebase console. Stops scripts from creating
 * accounts and pairing codes in bulk (2026-10-17 audit). Installed before Auth or Firestore are used.
 */
object AppCheckSetup {
    @Volatile private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        installed = true
    }
}
