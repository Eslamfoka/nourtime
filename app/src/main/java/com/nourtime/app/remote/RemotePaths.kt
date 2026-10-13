package com.nourtime.app.remote

/** Firestore collection names (Phase 2); the rules in firebase/firestore.rules use the same. */
object RemotePaths {
    const val DEVICES = "devices"
    const val PAIRINGS = "pairings"
    const val USAGE = "usage"
    const val META = "meta"
    const val APPS_DOC = "apps"
    /** The protection alerts, apart from the device document so the push functions run only on alert changes. */
    const val ALERTS_DOC = "alerts"
    const val COMMANDS = "commands"
    const val REQUESTS = "requests"
}
