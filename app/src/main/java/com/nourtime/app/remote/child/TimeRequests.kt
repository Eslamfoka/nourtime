package com.nourtime.app.remote.child

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.remote.RemotePaths
import com.nourtime.app.remote.model.AskPolicy
import com.nourtime.app.remote.model.AskState
import com.nourtime.app.remote.model.TimeRequest
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Ask for more time" on the child's phone (Phase 4c). The request is a document the parent answers;
 * an approval arrives as an ordinary bonus command. Works offline: the request is queued and shows as
 * waiting at once.
 */
@Singleton
class TimeRequests @Inject constructor(
    private val identity: DeviceIdentity,
    // Lazy: the lock screen reads this on phones that never pair.
    private val firestore: Lazy<FirebaseFirestore>,
    private val trustedClock: TrustedClock,
) {
    /** What the "Time's up" screen shows; null when this phone isn't paired (nobody to ask). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<AskState?> = identity.pairedOwner.flatMapLatest { owner ->
        if (owner == null) {
            flowOf(null)
        } else {
            combine(latest(), ticks()) { request, now -> AskPolicy.state(request, now) as AskState? }
                .catch { emit(null) }
        }
    }

    suspend fun ask() {
        // Not awaited: offline, both writes are queued and the local snapshot already shows "waiting".
        val device = deviceRef()
        device.collection(RemotePaths.REQUESTS).add(mapOf("status" to AskPolicy.PENDING, "createdAt" to FieldValue.serverTimestamp()))
        device.update("askingAt", FieldValue.serverTimestamp())
    }

    /** The newest request, as it changes. */
    fun latest(): Flow<TimeRequest?> = callbackFlow {
        val registration = deviceRef().collection(RemotePaths.REQUESTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, error ->
                if (error != null) close(error) else if (snap != null) trySend(snap.documents.firstOrNull()?.let(::toRequest))
            }
        awaitClose { registration.remove() }
    }

    private fun ticks(): Flow<Long> = flow {
        while (true) {
            emit(trustedClock.now().toInstant().toEpochMilli())
            delay(TICK_MS)
        }
    }

    private suspend fun deviceRef() = firestore.get().collection(RemotePaths.DEVICES).document(identity.deviceId())

    private companion object {
        const val TICK_MS = 15_000L
    }
}

fun toRequest(doc: DocumentSnapshot) = TimeRequest(
    id = doc.id,
    status = doc.getString("status").orEmpty(),
    createdAtMs = doc.getTimestamp("createdAt")?.toDate()?.time,
    answeredAtMs = doc.getTimestamp("answeredAt")?.toDate()?.time,
    minutes = doc.getLong("minutes")?.toInt(),
)
