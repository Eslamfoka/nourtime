package com.nourtime.app.remote.child

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The parent account that controls this child's phone. */
data class PairedOwner(val uid: String, val email: String?, val name: String?)

/**
 * Who this child's phone is to Firebase (Phase 2): an anonymous account and a stable device id,
 * plus the parent it's paired with. Nothing here runs until the parent starts pairing.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    private val store: DataStore<Preferences>,
    // Lazy: reading the pairing must not start Firebase on phones that never pair.
    private val authLazy: Lazy<FirebaseAuth>,
) {
    private val auth: FirebaseAuth get() = authLazy.get()

    val pairedOwner: Flow<PairedOwner?> = store.data.map { prefs ->
        prefs[OWNER_UID]?.let { PairedOwner(it, prefs[OWNER_EMAIL], prefs[OWNER_NAME]) }
    }.distinctUntilChanged()

    /** True while this phone's data is still in Firestore after a disconnect that couldn't finish. */
    val erasePending: Flow<Boolean> = store.data.map { it[ERASE_PENDING] == true }.distinctUntilChanged()

    suspend fun setErasePending(pending: Boolean) {
        store.edit { if (pending) it[ERASE_PENDING] = true else it.remove(ERASE_PENDING) }
    }

    /** Signs in anonymously if needed and returns the account id. */
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid ?: error("Anonymous sign-in returned no user")

    /** True once this phone has started pairing at least once, so it may have data in Firestore. */
    suspend fun usedRemote(): Boolean = store.data.first()[DEVICE_ID] != null && auth.currentUser != null

    /** Account deletion: removes this phone's anonymous account (a new one is made if it pairs again). */
    suspend fun deleteAccount() {
        auth.currentUser?.takeIf { it.isAnonymous }?.delete()?.await()
    }

    /**
     * Starts over after this phone lost access to its device document (its anonymous account is
     * gone): a new device id, not paired, and signed out so the next pairing makes a new account.
     */
    suspend fun reset() {
        auth.signOut()
        store.edit { prefs ->
            prefs.remove(DEVICE_ID)
            prefs.remove(OWNER_UID)
            prefs.remove(OWNER_EMAIL)
            prefs.remove(OWNER_NAME)
            prefs.remove(ERASE_PENDING)
        }
    }

    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        var id = ""
        store.edit { prefs ->
            id = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return id
    }

    suspend fun setPairedOwner(owner: PairedOwner?) {
        store.edit { prefs ->
            if (owner == null) {
                prefs.remove(OWNER_UID)
                prefs.remove(OWNER_EMAIL)
                prefs.remove(OWNER_NAME)
            } else {
                prefs[OWNER_UID] = owner.uid
                owner.email?.let { prefs[OWNER_EMAIL] = it } ?: prefs.remove(OWNER_EMAIL)
                owner.name?.let { prefs[OWNER_NAME] = it } ?: prefs.remove(OWNER_NAME)
            }
        }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("remote_device_id")
        val OWNER_UID = stringPreferencesKey("remote_owner_uid")
        val OWNER_EMAIL = stringPreferencesKey("remote_owner_email")
        val OWNER_NAME = stringPreferencesKey("remote_owner_name")
        val ERASE_PENDING = booleanPreferencesKey("remote_erase_pending")
    }
}
