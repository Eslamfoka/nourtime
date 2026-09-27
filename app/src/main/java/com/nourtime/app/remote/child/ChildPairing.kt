package com.nourtime.app.remote.child

import android.os.Build
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.nourtime.app.remote.RemotePaths
import com.nourtime.app.remote.model.PairingCode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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
) {
    /** Makes sure the device document exists, then stores a fresh unused code. Needs the network. */
    suspend fun createCode(): String {
        val uid = identity.ensureSignedIn()
        val device = deviceRef()
        val create = mapOf(
            "childUid" to uid,
            "ownerUid" to null,
            "ownerEmail" to null,
            "name" to deviceName(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        // Never set() an existing device: it would clear ownerUid. The server copy decides.
        if (device.get(Source.SERVER).await().exists()) {
            device.update("name", deviceName()).await()
        } else {
            device.set(create).await()
        }
        repeat(CODE_ATTEMPTS) {
            val code = PairingCode.generate()
            val ref = firestore.collection(RemotePaths.PAIRINGS).document(code)
            // Another phone's code can't be read (the rules say no), which also means "taken".
            val created = runCatching { firestore.runTransaction { tx ->
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
            }.await() }.getOrElse { e -> if (e.isPermissionDenied()) false else throw e }
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
        // The rules only accept an owner who claimed this code for this device.
        deviceRef().update(
            mapOf("ownerUid" to owner.uid, "ownerEmail" to owner.email, "ownerName" to owner.name, "pairingCode" to code),
        ).await()
        identity.setPairedOwner(owner)
        identity.setErasePending(false)
        forget(code)
    }

    /** Refused, expired or closed: remove the code so nobody else can claim it. */
    suspend fun forget(code: String) {
        runCatching { firestore.collection(RemotePaths.PAIRINGS).document(code).delete().await() }
    }

    /**
     * Stops the parent's phone from seeing or controlling this phone and deletes everything this
     * phone stored in Firestore (usage, app list, commands, the device), then its anonymous account.
     * Needs the network: offline it only unlinks (queued), and returns false.
     */
    suspend fun disconnect(): Boolean {
        identity.setPairedOwner(null)
        if (!identity.usedRemote()) {
            identity.setErasePending(false)
            return true
        }
        val device = deviceRef()
        val erased = withTimeoutOrNull(ERASE_TIMEOUT_MS) {
            runCatching {
                if (device.get(Source.SERVER).await().exists()) {
                    // Subcollections first: their rules look the device up.
                    for (name in listOf(RemotePaths.USAGE, RemotePaths.META, RemotePaths.COMMANDS)) {
                        deleteAll(device.collection(name).get(Source.SERVER).await().documents.map { it.reference })
                    }
                    device.delete().await()
                }
                identity.deleteAccount()
            }.isSuccess
        } ?: false
        if (!erased) {
            runCatching { device.update(mapOf("ownerUid" to null, "ownerEmail" to null, "ownerName" to null)) }
        }
        identity.setErasePending(!erased)
        return erased
    }

    private suspend fun deleteAll(refs: List<DocumentReference>) {
        refs.chunked(BATCH_LIMIT).forEach { chunk ->
            firestore.batch().apply { chunk.forEach(::delete) }.commit().await()
        }
    }

    private fun Throwable.isPermissionDenied() =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private suspend fun deviceRef() = firestore.collection(RemotePaths.DEVICES).document(identity.deviceId())

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ")

    private companion object {
        const val CODE_ATTEMPTS = 5
        const val ERASE_TIMEOUT_MS = 20_000L
        const val BATCH_LIMIT = 400
    }
}
