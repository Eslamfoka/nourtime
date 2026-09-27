package com.nourtime.app.remote.child

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nourtime.app.remote.RemotePaths
import com.nourtime.app.remote.model.PairingCode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pairing on the child's phone (Phase 2): publish a 6-digit code, then let the parent holding this
 * phone confirm the Google account that claimed it. Only this phone can make a parent the owner.
 */
@Singleton
class ChildPairing @Inject constructor(
    private val identity: DeviceIdentity,
    private val firestore: FirebaseFirestore,
    private val store: DataStore<Preferences>,
) {
    /** Makes sure the device document exists, then stores a fresh unused code. Needs the network. */
    suspend fun createCode(): String {
        val uid = identity.ensureSignedIn()
        val device = deviceRef()
        if (store.data.first()[DEVICE_CREATED] != true) {
            // A new device id is never in use, so this is a create (the rules need ownerUid == null).
            device.set(
                mapOf(
                    "childUid" to uid,
                    "ownerUid" to null,
                    "ownerEmail" to null,
                    "name" to deviceName(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            store.edit { it[DEVICE_CREATED] = true }
        } else {
            // Never set(): it would clear ownerUid on a paired device.
            device.update("name", deviceName()).await()
        }
        repeat(CODE_ATTEMPTS) {
            val code = PairingCode.generate()
            val ref = firestore.collection(RemotePaths.PAIRINGS).document(code)
            val created = firestore.runTransaction { tx ->
                if (tx.get(ref).exists()) {
                    false
                } else {
                    tx.set(
                        ref,
                        mapOf(
                            "deviceId" to device.id,
                            "childUid" to uid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "claimedBy" to null,
                            "claimedEmail" to null,
                            "claimedName" to null,
                        ),
                    )
                    true
                }
            }.await()
            if (created) return code
        }
        error("No free pairing code after $CODE_ATTEMPTS attempts")
    }

    /** The pairing document as it changes; null once it's gone. */
    fun observe(code: String): Flow<DocumentSnapshot?> = callbackFlow {
        val registration = firestore.collection(RemotePaths.PAIRINGS).document(code).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snap?.takeIf { it.exists() })
            }
        }
        awaitClose { registration.remove() }
    }

    /** The parent holding this phone accepted the claim. */
    suspend fun confirm(code: String, owner: PairedOwner) {
        deviceRef().update(mapOf("ownerUid" to owner.uid, "ownerEmail" to owner.email, "ownerName" to owner.name)).await()
        identity.setPairedOwner(owner)
        forget(code)
    }

    /** Refused, expired or closed: remove the code so nobody else can claim it. */
    suspend fun forget(code: String) {
        runCatching { firestore.collection(RemotePaths.PAIRINGS).document(code).delete().await() }
    }

    /** Stops the parent's phone from seeing or controlling this phone. Works offline (queued). */
    suspend fun disconnect() {
        identity.setPairedOwner(null)
        runCatching {
            deviceRef().update(mapOf("ownerUid" to null, "ownerEmail" to null, "ownerName" to null))
        }
    }

    private suspend fun deviceRef() = firestore.collection(RemotePaths.DEVICES).document(identity.deviceId())

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ")

    private companion object {
        const val CODE_ATTEMPTS = 5
        val DEVICE_CREATED = booleanPreferencesKey("remote_device_created")
    }
}
