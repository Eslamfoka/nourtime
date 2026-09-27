package com.nourtime.app.remote.child

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.FirebaseAuth
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
    private val auth: FirebaseAuth,
) {
    val pairedOwner: Flow<PairedOwner?> = store.data.map { prefs ->
        prefs[OWNER_UID]?.let { PairedOwner(it, prefs[OWNER_EMAIL], prefs[OWNER_NAME]) }
    }.distinctUntilChanged()

    /** Signs in anonymously if needed and returns the account id. */
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid ?: error("Anonymous sign-in returned no user")

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
    }
}
