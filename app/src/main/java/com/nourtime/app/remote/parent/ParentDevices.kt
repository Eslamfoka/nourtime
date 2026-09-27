package com.nourtime.app.remote.parent

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.nourtime.app.core.timer.TimerCommand
import com.nourtime.app.data.apps.InstalledApp
import com.nourtime.app.data.usage.UsageEntry
import com.nourtime.app.remote.RemotePaths
import com.nourtime.app.remote.child.CommandQueue
import com.nourtime.app.remote.model.RemoteSettings
import com.nourtime.app.remote.model.RemoteStatus
import com.nourtime.app.remote.model.commandMap
import com.nourtime.app.remote.model.remoteCommandOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** A child's phone as the parent sees it. */
data class ChildDevice(
    val id: String,
    val name: String,
    val status: RemoteStatus?,
    val settings: RemoteSettings?,
    val settingsRev: Long,
)

/**
 * A command the parent sent, and whether the child's phone has applied it yet; [expired] when it
 * arrived too late to take effect (the phone was off or offline, see CommandQueue.EXPIRES_AFTER_MS).
 */
data class SentCommand(val id: String, val command: TimerCommand?, val applied: Boolean, val expired: Boolean = false)

/** The parent's side of Firestore (Phase 2). Every call needs a signed-in [ParentUser]. */
@Singleton
class ParentDevices @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val devices get() = firestore.collection(RemotePaths.DEVICES)

    fun devices(uid: String): Flow<List<ChildDevice>> = callbackFlow {
        val registration = devices.whereEqualTo("ownerUid", uid).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
            } else if (snap != null) {
                trySend(snap.documents.map(::toDevice).sortedBy { it.name })
            }
        }
        awaitClose { registration.remove() }
    }

    fun device(id: String): Flow<ChildDevice?> = callbackFlow {
        val registration = devices.document(id).addSnapshotListener { snap, error ->
            if (error != null) close(error) else trySend(snap?.takeIf { it.exists() }?.let(::toDevice))
        }
        awaitClose { registration.remove() }
    }

    /**
     * Claims [code] and waits (up to [WAIT_FOR_CHILD_MS]) for the child's phone to confirm. The
     * rules check the expiry with server time; the local check only gives a clearer message.
     */
    suspend fun claim(code: String, user: ParentUser): ClaimResult {
        val ref = firestore.collection(RemotePaths.PAIRINGS).document(code)
        val snap = try {
            ref.get().await()
        } catch (e: Exception) {
            return e.toClaimResult()
        }
        val deviceId = snap.getString("deviceId")
        val precheck = ClaimCheck.before(
            exists = snap.exists(),
            createdAtMs = snap.getTimestamp("createdAt")?.toDate()?.time,
            claimedBy = snap.getString("claimedBy"),
            myUid = user.uid,
            nowMs = System.currentTimeMillis(),
        )
        if (precheck != null && precheck != ClaimResult.WAITING_FOR_CHILD) return precheck
        if (precheck == null) {
            try {
                ref.update(mapOf("claimedBy" to user.uid, "claimedEmail" to user.email, "claimedName" to user.name)).await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return e.toClaimResult()
                // Claimed by someone else a moment ago, or expired by server time.
                val again = runCatching { ref.get().await() }.getOrNull()
                val other = again?.getString("claimedBy")
                return if (other != null && other != user.uid) ClaimResult.ALREADY_CLAIMED else ClaimResult.EXPIRED
            } catch (e: Exception) {
                return e.toClaimResult()
            }
        }
        val pairingGone = callbackFlow {
            val registration = ref.addSnapshotListener { s, _ -> trySend(s?.exists() == false) }
            awaitClose { registration.remove() }
        }
        val outcome = withTimeoutOrNull(WAIT_FOR_CHILD_MS) {
            combine(devices(user.uid), pairingGone) { list, gone ->
                when {
                    list.any { it.id == deviceId } -> ClaimResult.PAIRED
                    gone -> ClaimResult.REFUSED
                    else -> null
                }
            }.first { it != null }
        }
        return outcome ?: ClaimResult.WAITING_FOR_CHILD
    }

    suspend fun send(deviceId: String, command: TimerCommand, uid: String) {
        devices.document(deviceId).collection(RemotePaths.COMMANDS).add(
            commandMap(command) + mapOf("createdAt" to FieldValue.serverTimestamp(), "by" to uid, "appliedAt" to null),
        ).await()
    }

    /** The latest commands, newest first, so the screen can show "waiting for the child's phone". */
    fun recentCommands(deviceId: String): Flow<List<SentCommand>> = callbackFlow {
        val registration = devices.document(deviceId).collection(RemotePaths.COMMANDS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(RECENT_COMMANDS.toLong())
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                } else if (snap != null) {
                    trySend(
                        snap.documents.map {
                            val created = it.getTimestamp("createdAt")?.toDate()?.time
                            val applied = it.getTimestamp("appliedAt")?.toDate()?.time
                            SentCommand(
                                it.id,
                                remoteCommandOf(it.data.orEmpty()),
                                applied = applied != null,
                                expired = created != null && applied != null && CommandQueue.expired(created, applied),
                            )
                        },
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Writes the settings one revision past the server's current one, in a transaction, so two
     * writers never produce the same revision. [change] is applied to the server's settings, so an
     * edit made meanwhile on the child's phone isn't lost. Needs the network.
     */
    suspend fun writeSettings(deviceId: String, change: (RemoteSettings) -> RemoteSettings) {
        val ref = devices.document(deviceId)
        firestore.runTransaction { tx ->
            @Suppress("UNCHECKED_CAST")
            val map = tx.get(ref).get("settings") as? Map<String, Any?>
            val current = RemoteSettings.fromMap(map) ?: return@runTransaction
            val rev = (map?.get("rev") as? Number)?.toLong() ?: 0
            tx.update(ref, "settings", change(current).toMap(rev + 1, RemoteSettings.BY_PARENT))
        }.await()
    }

    fun usage(deviceId: String, day: LocalDate): Flow<Map<String, Long>> = callbackFlow {
        val registration = devices.document(deviceId).collection(RemotePaths.USAGE)
            .document(day.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val ms = snap?.get("ms") as? Map<String, Any?>
                    trySend(ms.orEmpty().mapNotNull { (k, v) -> (v as? Number)?.toLong()?.let { k to it } }.toMap())
                }
            }
        awaitClose { registration.remove() }
    }

    /** Each day's per-app minutes from [from] to [to] (Phase 4b); usage documents are named by ISO date. */
    fun usageRange(deviceId: String, from: LocalDate, to: LocalDate): Flow<List<UsageEntry>> = callbackFlow {
        val registration = devices.document(deviceId).collection(RemotePaths.USAGE)
            .orderBy(FieldPath.documentId())
            .startAt(from.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .endAt(to.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                } else if (snap != null) {
                    trySend(
                        snap.documents.flatMap { doc ->
                            val date = runCatching { LocalDate.parse(doc.id) }.getOrNull() ?: return@flatMap emptyList()
                            @Suppress("UNCHECKED_CAST")
                            val ms = doc.get("ms") as? Map<String, Any?>
                            ms.orEmpty().mapNotNull { (app, v) -> (v as? Number)?.toLong()?.let { UsageEntry(date, app, it) } }
                        },
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    fun apps(deviceId: String): Flow<List<InstalledApp>> = callbackFlow {
        val registration = devices.document(deviceId).collection(RemotePaths.META).document(RemotePaths.APPS_DOC)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                } else {
                    val list = (snap?.get("apps") as? List<*>).orEmpty().mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        val p = m["p"] as? String ?: return@mapNotNull null
                        InstalledApp(p, m["l"] as? String ?: p)
                    }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Stops following this phone; the child's phone notices and shows it isn't connected. */
    suspend fun remove(deviceId: String) {
        devices.document(deviceId).update(mapOf("ownerUid" to null, "ownerEmail" to null, "ownerName" to null)).await()
    }

    /**
     * Account deletion: deletes the commands this parent sent and unlinks every phone it controls
     * (the phones keep working with their current settings). Needs the network; safe to repeat.
     */
    suspend fun forgetParent(uid: String) {
        val owned = devices.whereEqualTo("ownerUid", uid).get(Source.SERVER).await().documents
        for (device in owned) {
            val sent = device.reference.collection(RemotePaths.COMMANDS).whereEqualTo("by", uid).get(Source.SERVER).await().documents
            sent.chunked(BATCH_LIMIT).forEach { chunk ->
                firestore.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
            }
            device.reference.update(mapOf("ownerUid" to null, "ownerEmail" to null, "ownerName" to null)).await()
        }
    }

    private fun toDevice(doc: DocumentSnapshot): ChildDevice {
        @Suppress("UNCHECKED_CAST")
        val settingsMap = doc.get("settings") as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val statusMap = doc.get("status") as? Map<String, Any?>
        val statusTime = (statusMap?.get("updatedAt") as? Timestamp)?.toDate()?.time
        return ChildDevice(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            status = RemoteStatus.fromMap(statusMap, statusTime),
            settings = RemoteSettings.fromMap(settingsMap),
            settingsRev = (settingsMap?.get("rev") as? Number)?.toLong() ?: 0,
        )
    }

    private fun Exception.toClaimResult(): ClaimResult =
        if (this is FirebaseNetworkException || (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE)) {
            ClaimResult.OFFLINE
        } else {
            ClaimResult.FAILED
        }

    private companion object {
        const val WAIT_FOR_CHILD_MS = 3 * 60_000L
        const val RECENT_COMMANDS = 5
        const val BATCH_LIMIT = 400
    }
}
