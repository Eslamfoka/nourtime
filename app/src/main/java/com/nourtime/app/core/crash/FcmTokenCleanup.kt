package com.nourtime.app.core.crash

/** What happens to FCM on this phone for [FirebaseCollection.pushes]. */
enum class FcmAction {
    /** Pushes on: FCM auto-init on, which makes the token. */
    TURN_ON,

    /** Pushes off, and a token (or auto-init left on) may be here: auto-init off, then the token deleted. */
    TURN_OFF_AND_DELETE,

    /** Pushes off and nothing to undo: FCM isn't touched at all, so it can't contact Firebase. */
    NOTHING,
}

/**
 * Whether FCM must be touched, so a phone that never had a token never calls FCM or Firebase
 * Installations ("nothing leaves this phone"). [FcmTokenFlag] remembers when a token may exist; until
 * this version first runs there is no flag, and the phone may hold one only if an older version (FCM
 * auto-init on everywhere) was installed before.
 */
object FcmTokenCleanup {

    /** [stored] is [FcmTokenFlag]'s value, null before this version first ran. */
    fun tokenMayExist(stored: Boolean?, updatedFromOlderVersion: Boolean): Boolean = stored ?: updatedFromOlderVersion

    fun action(pushes: Boolean, tokenMayExist: Boolean): FcmAction = when {
        pushes -> FcmAction.TURN_ON
        tokenMayExist -> FcmAction.TURN_OFF_AND_DELETE
        else -> FcmAction.NOTHING
    }
}
