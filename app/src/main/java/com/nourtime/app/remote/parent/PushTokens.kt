package com.nourtime.app.remote.parent

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** FCM token of this phone (Firebase in the app, a fake in tests). */
interface PushTokenSource {
    suspend fun token(): String

    /** Forgets the token, so the next one is new (after sign-out). */
    suspend fun delete()
}

/** Where the parent's tokens are kept: `parents/{uid}/tokens/{token}`. */
interface TokenStore {
    suspend fun put(uid: String, token: String)
    suspend fun remove(uid: String, token: String)
}

/**
 * The parent's phone receives pushes only while a parent is signed in, and only that parent's: the
 * token is stored under the signed-in uid, and removed and replaced on sign-out (a phone may change
 * hands). Failures are logged and never block signing in or out.
 */
class PushTokens(private val source: PushTokenSource, private val store: TokenStore) {

    suspend fun register(uid: String) = quietly("register") { store.put(uid, source.token()) }

    /**
     * Before signing out (the delete needs the parent still signed in). Bounded: offline, Firestore waits
     * for the server forever, and signing out must still happen. This phone's token is replaced even if
     * the server copy couldn't be removed, and pushes for the old account are dropped on arrival anyway
     * ([pushIsFor]).
     */
    suspend fun unregister(uid: String) {
        quietly("unregister") { withTimeoutOrNull(STEP_TIMEOUT_MS) { store.remove(uid, source.token()) } }
        quietly("new token") { withTimeoutOrNull(STEP_TIMEOUT_MS) { source.delete() } }
    }

    suspend fun onNewToken(uid: String?, token: String) {
        if (uid != null) quietly("new token") { store.put(uid, token) }
    }

    private suspend fun quietly(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { Log.w(TAG, "$what failed", e) }
        }
    }

    private companion object {
        const val TAG = "PushTokens"
        const val STEP_TIMEOUT_MS = 5_000L
    }
}

/**
 * FCM's token calls, deprecated in firebase-messaging 25.1.0 for registration through the Firebase
 * installation id. They still work; moving over changes how the push functions address the parent's
 * phones too, so it's its own task (handoff §28).
 */
@Suppress("DEPRECATION")
class FirebasePushTokenSource : PushTokenSource {
    override suspend fun token(): String = FirebaseMessaging.getInstance().token.await()
    override suspend fun delete() {
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}

class FirestoreTokenStore(private val firestore: FirebaseFirestore) : TokenStore {
    private fun doc(uid: String, token: String) = firestore.collection(PARENTS).document(uid).collection(TOKENS).document(token)

    override suspend fun put(uid: String, token: String) {
        doc(uid, token).set(mapOf("createdAt" to FieldValue.serverTimestamp(), "platform" to "android")).await()
    }

    override suspend fun remove(uid: String, token: String) {
        doc(uid, token).delete().await()
    }

    companion object {
        const val PARENTS = "parents"
        const val TOKENS = "tokens"
    }
}
