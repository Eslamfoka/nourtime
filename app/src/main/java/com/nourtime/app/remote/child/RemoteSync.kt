package com.nourtime.app.remote.child

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.data.usage.UsageRepository
import com.nourtime.app.remote.RemotePaths
import com.nourtime.app.remote.model.RemoteSettings
import com.nourtime.app.remote.model.SettingsSync
import com.nourtime.app.remote.model.StatusThrottle
import com.nourtime.app.remote.model.SyncAction
import com.nourtime.app.remote.model.statusMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The child's side of remote control (Phase 2), run by the timer service while this phone is
 * paired: uploads status, today's usage, the app list and local settings changes; applies parent
 * settings and commands through the same repositories and [TimeEngine] the local UI uses. All
 * Firestore writes are queued while offline.
 */
@Singleton
class RemoteSync @Inject constructor(
    private val identity: DeviceIdentity,
    private val firestore: FirebaseFirestore,
    private val store: DataStore<Preferences>,
    private val engine: TimeEngine,
    private val settings: ParentSettingsRepository,
    private val usage: UsageRepository,
    private val apps: InstalledAppsRepository,
    private val trustedClock: TrustedClock,
    private val clock: DeviceClock,
    private val pairing: ChildPairing,
) {
    suspend fun run() {
        identity.pairedOwner.collectLatest { owner ->
            if (owner == null) return@collectLatest
            identity.ensureSignedIn()
            val device = firestore.collection(RemotePaths.DEVICES).document(identity.deviceId())
            // Nothing else runs until the server (not the local cache) confirms this phone is still
            // paired: a parent who removed it while it was offline gets no commands applied and no
            // more uploads.
            val confirmed = CompletableDeferred<Unit>()
            val removed = try {
                coroutineScope {
                    launch { syncSettings(device, owner, confirmed) }
                    launch {
                        confirmed.await()
                        launch { applyCommands(device, owner) }
                        launch { uploadStatus(device) }
                        launch { uploadUsage(device) }
                        launch { uploadApps(device) }
                    }
                }
                false
            } catch (e: RemovedByParent) {
                true
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED || canStillRead(device)) throw e
                // This phone's anonymous account no longer owns the device (it was lost or deleted):
                // it would fail every few seconds while showing "Connected". Start over, unpaired.
                Log.w(TAG, "lost access to the device document; unpairing")
                identity.reset()
                false
            }
            if (removed) {
                // Unpairs and deletes this phone's data from the server (see ChildPairing.disconnect).
                // Clearing the owner cancels this block, so the erase must not be cancelled with it.
                withContext(NonCancellable) { pairing.disconnect() }
            }
        }
    }

    private suspend fun canStillRead(device: DocumentReference): Boolean = try {
        device.get(Source.SERVER).await()
        true
    } catch (e: FirebaseFirestoreException) {
        e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    /** Two-way settings sync, and noticing that the parent removed this phone. */
    @OptIn(FlowPreview::class)
    private suspend fun syncSettings(device: DocumentReference, owner: PairedOwner, confirmed: CompletableDeferred<Unit>) {
        // Local changes only trigger a check; the decision always reads the current settings, since a
        // debounced value can be older than a parent change applied a moment ago (it would be
        // uploaded back and briefly undo the parent's change).
        val localChanges = settings.settings.debounce(LOCAL_SETTINGS_DEBOUNCE_MS)
        combine(localChanges, device.snapshots()) { _, snap -> snap }.collect { snap ->
            val localSettings = RemoteSettings.of(settings.settings.first())
            if (!PairingCheck.stillPaired(owner, snap.exists(), snap.metadata.isFromCache, snap.getString("ownerUid"))) {
                Log.i(TAG, "the parent removed this phone")
                throw RemovedByParent()
            }
            if (snap.exists() && !snap.metadata.isFromCache) confirmed.complete(Unit)
            @Suppress("UNCHECKED_CAST")
            val remoteMap = snap.get("settings") as? Map<String, Any?>
            val prefs = store.data.first()
            val lastRev = prefs[SETTINGS_REV] ?: 0
            val remoteRev = (remoteMap?.get("rev") as? Number)?.toLong() ?: 0
            val remote = RemoteSettings.fromMap(remoteMap)
            when (SettingsSync.decide(localSettings, RemoteSettings.decode(prefs[SETTINGS_SNAPSHOT]), lastRev, remote, remoteRev, remoteMap?.get("by") as? String)) {
                SyncAction.UPLOAD -> uploadSettings(device, localSettings, lastRev)
                SyncAction.APPLY_REMOTE -> {
                    remember(remote!!, remoteRev)
                    settings.replaceWith(remote)
                }
                SyncAction.NOTHING -> Unit
            }
        }
    }

    /**
     * Writes the local settings one revision past the server's, in a transaction, so a parent edit
     * that arrived meanwhile is never overwritten (it's applied on the next snapshot instead). Offline
     * the transaction fails and the next snapshot after reconnecting decides again.
     */
    private suspend fun uploadSettings(device: DocumentReference, local: RemoteSettings, lastRev: Long) {
        val rev = runCatching {
            firestore.runTransaction { tx ->
                val current = tx.get(device)
                @Suppress("UNCHECKED_CAST")
                val map = current.get("settings") as? Map<String, Any?>
                val currentRev = (map?.get("rev") as? Number)?.toLong() ?: 0
                if (!SettingsSync.uploadAllowed(lastRev, currentRev, map?.get("by") as? String)) {
                    null
                } else {
                    SettingsSync.nextRev(lastRev, currentRev).also { tx.update(device, "settings", local.toMap(it, RemoteSettings.BY_CHILD)) }
                }
            }.await()
        }.getOrNull() ?: return
        remember(local, rev)
    }

    private suspend fun remember(synced: RemoteSettings, rev: Long) {
        store.edit {
            it[SETTINGS_SNAPSHOT] = synced.encode()
            it[SETTINGS_REV] = rev
        }
    }

    private suspend fun applyCommands(device: DocumentReference, owner: PairedOwner) {
        // TimeEngine.apply needs a timer state; before the service's first update there is none, and
        // the command would be consumed without effect.
        engine.status.filterNotNull().first()
        device.collection(RemotePaths.COMMANDS).whereEqualTo("appliedAt", null).snapshots().collect { snap ->
            val docs = snap.documents.map { CommandDoc(it.id, it.getTimestamp("createdAt")?.toDate()?.time, it.data.orEmpty()) }
            var applied = store.data.first()[APPLIED_COMMANDS]?.split(",")?.filter { it.isNotEmpty() }.orEmpty()
            val due = CommandQueue.due(owner.uid, trustedClock.now().toInstant().toEpochMilli(), docs, applied.toSet())
            CommandQueue.runEach(
                due,
                remember = { id ->
                    applied = CommandQueue.remember(applied, listOf(id))
                    store.edit { it[APPLIED_COMMANDS] = applied.joinToString(",") }
                },
                apply = { engine.apply(it) },
            )
            for (c in due) {
                Log.i(TAG, "command ${c.id}: ${c.command ?: "ignored"}")
                device.collection(RemotePaths.COMMANDS).document(c.id).update("appliedAt", FieldValue.serverTimestamp())
            }
        }
    }

    private suspend fun uploadStatus(device: DocumentReference) {
        var last: TimerStatus? = null
        var lastAt = 0L
        engine.status.filterNotNull().collect { status ->
            val now = clock.elapsedRealtime()
            if (StatusThrottle.shouldUpload(last, status, now - lastAt)) {
                device.update("status", statusMap(status) + ("updatedAt" to FieldValue.serverTimestamp()))
                last = status
                lastAt = now
            }
        }
    }

    private suspend fun uploadUsage(device: DocumentReference) {
        while (true) {
            val day = trustedClock.now().toLocalDate()
            val rows = usage.observeDay(day).first()
            device.collection(RemotePaths.USAGE).document(day.format(DateTimeFormatter.ISO_LOCAL_DATE)).set(
                mapOf("ms" to rows.associate { it.packageName to it.usedMs }, "updatedAt" to FieldValue.serverTimestamp()),
            )
            delay(USAGE_EVERY_MS)
        }
    }

    /** The launchable apps, so the parent can choose limited and allowed apps remotely. */
    private suspend fun uploadApps(device: DocumentReference) {
        var lastHash: Int? = null
        while (true) {
            val list = apps.launchableApps()
            val hash = list.hashCode()
            if (hash != lastHash) {
                device.collection(RemotePaths.META).document(RemotePaths.APPS_DOC).set(
                    mapOf("apps" to list.map { mapOf("p" to it.packageName, "l" to it.label) }, "updatedAt" to FieldValue.serverTimestamp()),
                )
                lastHash = hash
            }
            delay(APPS_EVERY_MS)
        }
    }

    private companion object {
        const val TAG = "RemoteSync"
        const val LOCAL_SETTINGS_DEBOUNCE_MS = 2_000L
        const val USAGE_EVERY_MS = 5 * 60_000L
        const val APPS_EVERY_MS = 6 * 60 * 60_000L
        val SETTINGS_REV = longPreferencesKey("remote_settings_rev")
        val SETTINGS_SNAPSHOT = stringPreferencesKey("remote_settings_snapshot")
        val APPLIED_COMMANDS = stringPreferencesKey("remote_applied_commands")
    }
}

/**
 * Includes metadata-only changes: when the cached copy already equals the server's (after an app
 * restart, or re-pairing in the same process), the server's confirmation changes only
 * `isFromCache`, and without this that event never arrives, so the sync would wait forever.
 */
/** Ends the sync for this pairing; the phone then unpairs and deletes its server data. */
private class RemovedByParent : Exception()

private fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
        if (error != null) close(error) else if (snap != null) trySend(snap)
    }
    awaitClose { registration.remove() }
}

private fun com.google.firebase.firestore.Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snap, error ->
        if (error != null) close(error) else if (snap != null) trySend(snap)
    }
    awaitClose { registration.remove() }
}
