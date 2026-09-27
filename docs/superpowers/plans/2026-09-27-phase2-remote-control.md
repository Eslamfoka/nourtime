# Phase 2: Remote Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent controls their child's Nour Time from their own phone: sees status and usage, gives bonus time, locks or unlocks, and changes settings.

**Architecture:** One app with two modes chosen on first launch. The child phone signs in to Firebase anonymously and mirrors its status, usage, installed-app list and settings into a Firestore `devices/{deviceId}` document; it listens for parent settings and `commands`, which it applies through the same repositories and `TimeEngine` the local UI uses, so blocking and timing stay unchanged and keep working offline. The parent phone signs in with Google, pairs by scanning a QR code or typing a 6-digit code that the child phone confirms, and reads and writes the same documents. No server code: the child's always-running foreground service holds the Firestore listeners, so FCM and Cloud Functions aren't needed.

**Tech Stack:** Kotlin, Compose, Hilt, DataStore; Firebase Auth (anonymous + Google), Cloud Firestore (Firebase BoM); Credential Manager + `googleid` for Google sign-in; `play-services-code-scanner` (no camera permission); `zxing:core` to draw the QR; Firebase Emulator Suite + `@firebase/rules-unit-testing` for development and rules tests.

**Spec:** decisions recorded 2026-09-27 (below) + `docs/handoff.md` §5 item 4 + `docs/progress.md` "Phase 2 decisions".

## Decisions (user, 2026-09-27)

| Topic | Decision |
|---|---|
| Parent app | Same app, two modes ("This is my child's phone" / "This is my phone (parent)") |
| Parent sign-in | Google account |
| Pairing | QR code, with a 6-digit code as fallback; valid 10 minutes |
| Remote abilities | See status and usage · give bonus time · lock now / end lock · change settings |
| Bonus during a lock | Ends the lock; the child gets exactly the bonus minutes; a new lock period starts when they're used |

## Global Constraints

- Phase 1 must keep working with no network and without pairing. Firebase is touched only in parent mode or after the child phone is paired.
- minSdk 26, targetSdk 35, Kotlin 2.0.21, AGP 8.7.3, Hilt 2.52 (existing catalog).
- Every new string in `values/strings.xml` and `values-ar/strings.xml`; child-facing copy masculine and feminine.
- The child side never reads screen content (unchanged Accessibility policy).
- Remote actions arrive only from the Google-signed-in owner of the device; the child confirms every pairing on the child phone (behind the parent PIN).
- Debug builds talk to the Firebase Emulator Suite through `adb reverse` (127.0.0.1); release builds need the real `app/google-services.json` from the user's Firebase project.
- Bonus minutes: 1–240 per command. Settings values are clamped by the existing `TimeLimits`.

## Review Focus

1. **Stale or duplicate commands** (the child phone was offline for hours, or a command document is delivered twice): each command applies exactly once, in creation order. Test in Task 4 (`TimerCommandTest`) and Task 8 (`CommandApplierTest`).
2. **Settings edited on both phones while one is offline:** the newest edit wins, and a child-side echo never overwrites a newer parent edit. Test in Task 6 (`SettingsSyncTest`).
3. **Brute-forcing the 6-digit code:** a claimed code does nothing until the child phone confirms, and an expired code can't be claimed. Test in Task 2 (rules tests).
4. **The parent removes the device while the child phone is offline:** when it comes back it drops its pairing and stops uploading. Test in Task 8 (`RemoteSyncStateTest`).
5. **A child resets Nour Time's data or reinstalls:** the old device document stays with the parent as "not seen since …"; nothing on the child side can re-attach to it without a new pairing. Test in Task 2 (rules: a different anonymous uid can't write an existing device).

---

## File Structure

```
firebase/                                  # dev tooling, not shipped
  firebase.json, .firebaserc               # demo-nourtime project, auth + firestore emulators
  firestore.rules                          # security rules (source of truth; deploy with firebase deploy)
  firestore.indexes.json
  package.json                             # firebase-tools, @firebase/rules-unit-testing, vitest
  test/rules.test.ts                       # rules tests (run against the emulator)
app/src/debug/google-services.json         # demo project, emulator only
app/src/debug/res/xml/network_security_config.xml   # cleartext to 127.0.0.1 for the emulators
app/src/main/java/com/nourtime/app/
  data/mode/AppModeRepository.kt           # CHILD | PARENT | null (choose)
  feature/mode/ModeChooserScreen.kt
  core/timer/TimerCommand.kt               # BONUS / LOCK_NOW / END_LOCK (pure) + TimeRules functions
  remote/model/PairingCode.kt              # generate + parse (QR uri or typed)
  remote/model/RemoteSettings.kt           # ParentSettings <-> map; SettingsSync.decide
  remote/model/RemoteCommand.kt            # Firestore map -> TimerCommand, validation
  remote/model/RemoteStatus.kt             # TimerStatus -> map; StatusThrottle
  remote/FirebaseModule.kt                 # Hilt providers, emulator wiring in debug
  remote/child/DeviceIdentity.kt           # stable deviceId + anonymous sign-in
  remote/child/ChildPairing.kt             # create code, listen, confirm, disconnect
  remote/child/RemoteSync.kt               # uploads + listeners, run from TimerService
  remote/child/CommandApplier.kt           # applies commands once, in order
  remote/parent/ParentAuth.kt              # Google sign-in (Credential Manager) + debug test account
  remote/parent/ParentDevices.kt           # list/observe devices, claim code, send commands, write settings
  feature/remote/ChildPairingDialog.kt     # QR + code + confirm (child Settings)
  feature/parent/ParentRoute.kt            # sign-in, device list, add child
  feature/parent/ChildDeviceScreen.kt      # status, actions, usage, settings
```

---

### Task 1: Firebase emulator tooling

**Files:** Create `firebase/firebase.json`, `firebase/.firebaserc`, `firebase/firestore.rules` (deny all), `firebase/firestore.indexes.json`, `firebase/package.json`, `firebase/vitest.config.ts`, `firebase/test/rules.test.ts`, `firebase/.gitignore` (`node_modules/`, `*.log`).

**Interfaces:** Produces `npm test` (in `firebase/`) = `firebase emulators:exec --only firestore,auth "vitest run"`; emulator ports auth 9099, firestore 8080, UI off.

- [ ] Step 1: `firebase.json` with `{"firestore":{"rules":"firestore.rules","indexes":"firestore.indexes.json"},"emulators":{"auth":{"port":9099,"host":"127.0.0.1"},"firestore":{"port":8080,"host":"127.0.0.1"},"ui":{"enabled":false},"singleProjectMode":true}}`; `.firebaserc` `{"projects":{"default":"demo-nourtime"}}`.
- [ ] Step 2: `package.json` devDependencies `firebase-tools`, `@firebase/rules-unit-testing`, `firebase`, `vitest`, `typescript`; script `"test": "firebase emulators:exec --project demo-nourtime --only firestore,auth \"vitest run\""`.
- [ ] Step 3: first test: unauthenticated read of `devices/x` is denied (`assertFails`).
- [ ] Step 4: `npm install && npm test`. If the Firestore emulator refuses Java 17, download Temurin JDK 21 (zip) to `D:\dev-tools\jdk-21` and run with `JAVA_HOME` pointing there (record in `docs/handoff.md`). Expected: 1 passed.
- [ ] Step 5: commit `Firebase emulator tooling for Phase 2`.

### Task 2: Firestore data model and security rules

**Files:** Modify `firebase/firestore.rules`, `firebase/firestore.indexes.json`; test `firebase/test/rules.test.ts`.

**Interfaces (data model, used by every later task):**
```
pairings/{code}            code = 6 digits
  deviceId: string, childUid: string, expiresAt: timestamp,
  claimedBy: string|null, claimedEmail: string|null, claimedName: string|null
devices/{deviceId}
  childUid: string, ownerUid: string|null, ownerEmail: string|null, name: string, createdAt: timestamp,
  status: { phase: "AVAILABLE"|"LOCKED", remainingMs, budgetMs, lockRemainingMs: number,
            protectionDegraded: bool, updatedAt: timestamp },
  settings: { budgetMinutes, lockPeriodHours, lockType, limitedApps: [string], allowedDuringLock: [string],
              bedtimeEnabled: bool, bedtimeStart, bedtimeEnd: number, dailyResetMinute: number|null,
              rev: number, by: "child"|"parent" }
devices/{deviceId}/meta/apps       { apps: [{p: packageName, l: label}], updatedAt }
devices/{deviceId}/usage/{yyyy-MM-dd}   { ms: { packageName: number }, updatedAt }
devices/{deviceId}/commands/{id}   { type: "BONUS"|"LOCK_NOW"|"END_LOCK", minutes?: 1..240,
                                     createdAt: timestamp, by: uid, appliedAt?: timestamp }
```

Rules (write exactly this logic):
- `pairings/{code}`: `get` by any signed-in user; `list` never. `create` if `request.resource.data.childUid == request.auth.uid`, `claimedBy == null`, `expiresAt <= request.time + duration.value(11, 'm')`, and `get(/devices/$(deviceId)).data.childUid == request.auth.uid`. `update` by the parent (claim) if `request.auth.token.firebase.sign_in_provider == 'google.com'`, `resource.data.claimedBy == null`, `request.time < resource.data.expiresAt`, only `claimedBy/claimedEmail/claimedName` change, and `claimedBy == request.auth.uid`. `delete` by `childUid`.
- `devices/{id}`: `read` if uid is `childUid` or `ownerUid`; `list` if `resource.data.ownerUid == request.auth.uid`. `create` if `childUid == auth.uid` and `ownerUid == null`. `update` by the child: anything except `childUid`; by the owner: only `settings` changes, or `ownerUid`/`ownerEmail` set to null (remove). `delete` never.
- `meta`, `usage`: write by the device's child, read by child or owner.
- `commands`: `create` by the owner with a valid type, `minutes` int in 1..240 only for BONUS, `by == auth.uid`, no `appliedAt`; `read` child or owner; `update` by the child only adding `appliedAt`; `delete` never.

- [ ] Step 1: tests (vitest, `initializeTestEnvironment`), one `it` each: child creates its device; another anonymous uid can't write it (Review Focus 5); child creates a pairing for its own device; parent (Google) claims; anonymous user can't claim; expired pairing can't be claimed (`expiresAt` in the past, written with `withSecurityRulesDisabled`) (Review Focus 3); claim can't change `deviceId`; parent can't read the device before `ownerUid` is set; after the child sets `ownerUid`, parent reads it and lists `where ownerUid == uid`; parent writes `settings` but not `status`; parent creates BONUS 30, rejected with minutes 0 and 500 and for a non-owner; child sets `appliedAt`, parent can't; owner removes itself (`ownerUid: null`).
- [ ] Step 2: `npm test` → failures (deny-all rules).
- [ ] Step 3: write the rules.
- [ ] Step 4: `npm test` → all pass.
- [ ] Step 5: commit `Firestore rules for pairing, devices, usage and commands`.

### Task 3: Firebase in the app (dependencies, config, emulator wiring)

**Files:** Modify `gradle/libs.versions.toml`, `build.gradle.kts` (root: google-services plugin `apply false`), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (INTERNET, `networkSecurityConfig` via debug manifest), create `app/src/debug/google-services.json`, `app/src/debug/res/xml/network_security_config.xml`, `app/src/debug/AndroidManifest.xml`, `app/src/main/java/com/nourtime/app/remote/FirebaseModule.kt`, `app/proguard-rules.pro` (keep nothing extra; Firebase ships consumer rules).

**Interfaces:** Produces Hilt singletons `FirebaseAuth`, `FirebaseFirestore`; `BuildConfig.FIREBASE_EMULATOR_HOST: String` (debug `"127.0.0.1"`, release `""`).

- [ ] Step 1: catalog: `firebase-bom = "33.7.0"`, `googleServices = "4.4.2"`, `credentials = "1.3.0"`, `googleid = "1.1.1"`, `codeScanner = "16.1.0"`, `zxing = "3.5.3"`; libraries `firebase-bom`, `firebase-auth`, `firebase-firestore`, `androidx-credentials`, `androidx-credentials-play-services-auth`, `googleid`, `play-services-code-scanner`, `zxing-core`; plugin `google-services`.
- [ ] Step 2: debug `google-services.json` for project `demo-nourtime`, package `com.nourtime.app`, placeholder api key, and an `oauth_client` of type 3 (`default_web_client_id`) with a placeholder id.
- [ ] Step 3: `FirebaseModule`: `provideAuth()` = `Firebase.auth.also { if (host.isNotEmpty()) it.useEmulator(host, 9099) }`; `provideFirestore()` likewise with port 8080 (call `useEmulator` before any use; Hilt singleton guarantees once).
- [ ] Step 4: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL, 121 tests pass.
- [ ] Step 5: commit `Add Firebase (emulator in debug builds)`.

### Task 4: Timer commands (bonus, lock now, end lock)

**Files:** Create `app/src/main/java/com/nourtime/app/core/timer/TimerCommand.kt`; modify `TimeRules.kt`, `TimeEngine.kt`; test `app/src/test/.../core/timer/TimerCommandTest.kt`.

**Interfaces:** Produces
```kotlin
sealed interface TimerCommand {
    data class Bonus(val minutes: Int) : TimerCommand
    data object LockNow : TimerCommand
    data object EndLock : TimerCommand
}
object TimeRules { fun apply(state: TimerState, command: TimerCommand): TimerState }
class TimeEngine { suspend fun apply(command: TimerCommand) }   // saves immediately, republishes status
```

- [ ] Step 1: tests:
```kotlin
@Test fun `bonus while available adds to what is left`() { assertEquals(40 * MIN, TimeRules.apply(available(remaining = 10 * MIN), TimerCommand.Bonus(30)).remainingMs) }
@Test fun `bonus during a lock ends it and gives exactly the bonus`() {
    val s = TimeRules.apply(locked(lockLeft = 3 * HOUR), TimerCommand.Bonus(15))
    assertEquals(TimerPhase.AVAILABLE, s.phase); assertEquals(15 * MIN, s.remainingMs); assertEquals(0, s.lockRemainingMs)
}
@Test fun `when the bonus is used up a full new lock period starts`() {
    val s = TimeRules.advance(TimeRules.apply(locked(lockLeft = HOUR), TimerCommand.Bonus(15)), nowElapsed = 15 * MIN, bootCount = 1, wasInUse = true)
    assertEquals(TimerPhase.LOCKED, s.phase); assertEquals(s.lockMs, s.lockRemainingMs)
}
@Test fun `lock now starts a full lock`() { val s = TimeRules.apply(available(remaining = 20 * MIN), TimerCommand.LockNow); assertEquals(TimerPhase.LOCKED, s.phase); assertEquals(s.lockMs, s.lockRemainingMs) }
@Test fun `lock now while locked changes nothing`() { val l = locked(lockLeft = HOUR); assertEquals(l, TimeRules.apply(l, TimerCommand.LockNow)) }
@Test fun `end lock refills the budget`() { val s = TimeRules.apply(locked(lockLeft = HOUR), TimerCommand.EndLock); assertEquals(TimerPhase.AVAILABLE, s.phase); assertEquals(s.budgetMs, s.remainingMs) }
@Test fun `end lock while available changes nothing`() { val a = available(remaining = MIN); assertEquals(a, TimeRules.apply(a, TimerCommand.EndLock)) }
@Test fun `bonus is capped at 24 hours left`() { assertEquals(24 * HOUR, TimeRules.apply(available(remaining = 23 * HOUR + 50 * MIN), TimerCommand.Bonus(240)).remainingMs) }
```
  (`available`/`locked` helpers build a `TimerState` with budget 60 min, lock 6 h, lastElapsed 0, bootCount 1.)
- [ ] Step 2: run → fails (unresolved `TimerCommand`).
- [ ] Step 3: implement `TimeRules.apply` with `startLock`/`refill`; `TimeEngine.apply` under the mutex: `state = TimeRules.apply(state ?: load() ?: return, command)`, `save`, publish status. Replace `debugSkip` callers with `apply(LockNow)` / `apply(EndLock)` and delete `debugSkip`.
- [ ] Step 4: run → pass; full suite passes.
- [ ] Step 5: commit `Timer commands: bonus, lock now, end lock`.

### Task 5: App mode (child phone or parent phone)

**Files:** Create `data/mode/AppModeRepository.kt`, `feature/mode/ModeChooserScreen.kt`; modify `MainActivity.kt` routing, strings; test `app/src/test/.../data/mode/AppModeRepositoryTest.kt`.

**Interfaces:** `enum class AppMode { CHILD, PARENT }`; `AppModeRepository.mode: Flow<AppMode?>`; `suspend fun setMode(mode: AppMode)`. Existing installs whose onboarding step is past `WELCOME` (or complete) read as `CHILD`.

- [ ] Step 1: tests: fresh store → `null`; after `setMode(PARENT)` → `PARENT`; store with onboarding step `CREATE_PIN` and no mode → `CHILD`.
- [ ] Step 2–4: implement (DataStore key `app_mode`), run tests.
- [ ] Step 5: `ModeChooserScreen`: Nour star, title "Whose phone is this?", two large cards ("My child's phone — Set limits and protection here" / "My phone — Follow and control your child's phone"), EN + AR. `MainActivity`: `null` → chooser, `CHILD` → existing route, `PARENT` → `ParentRoute` (Task 9 stub screen until then). Parent mode never starts `TimerService`.
- [ ] Step 6: emulator check: fresh install shows the chooser; the existing emulator install goes straight to Home.
- [ ] Step 7: commit `First launch: choose child phone or parent phone`.

### Task 6: Remote model (pure logic)

**Files:** Create `remote/model/PairingCode.kt`, `RemoteSettings.kt`, `RemoteCommand.kt`, `RemoteStatus.kt`; tests `app/src/test/.../remote/model/*Test.kt`.

**Interfaces:**
```kotlin
object PairingCode {
    fun generate(random: Random = SecureRandom()): String            // "000000".."999999"
    fun uri(code: String): String                                  // "nourtime://pair?c=123456"
    fun parse(input: String): String?                              // uri, "123 456", "123-456" -> "123456"; else null
}
data class RemoteSettings(budgetMinutes, lockPeriodHours, lockType: LockType, limitedApps: Set<String>, allowedDuringLock: Set<String>,
    bedtime: Bedtime, dailyResetMinute: Int?) {
    companion object { fun of(s: ParentSettings): RemoteSettings; fun fromMap(m: Map<String, Any?>): RemoteSettings? }
    fun toMap(rev: Long, by: String): Map<String, Any?>
}
enum class SyncAction { UPLOAD, APPLY_REMOTE, NOTHING }
object SettingsSync { fun decide(local: RemoteSettings, lastSynced: RemoteSettings?, lastSyncedRev: Long, remote: RemoteSettings?, remoteRev: Long, remoteBy: String?): SyncAction }
fun remoteCommandOf(id: String, m: Map<String, Any?>): Pair<String, TimerCommand>?   // null for invalid/unknown
object StatusThrottle { fun shouldUpload(prev: TimerStatus?, next: TimerStatus, sinceLastMs: Long): Boolean }  // phase/degraded change or >= 60 s
```

- [ ] Step 1: tests — PairingCode: generate is 6 digits for seeds 0..999; parse of uri, spaced, dashed, Arabic-Indic digits (`١٢٣٤٥٦`) → `"123456"`; parse of `"12345"`, `"abcdef"`, other scheme → null. RemoteSettings: `of(s).toMap(...)` → `fromMap` round-trips; `fromMap` clamps budget 0 → `TimeLimits` min; missing keys → null. SettingsSync (Review Focus 2): local changed since last sync and remote unchanged → UPLOAD; remote rev > lastSyncedRev by parent → APPLY_REMOTE even if local also changed; remote rev > last by child (own echo) → NOTHING; nothing changed → NOTHING; no remote yet → UPLOAD. remoteCommandOf: BONUS 30 → Bonus(30); BONUS 0/500/missing → null; LOCK_NOW, END_LOCK; unknown type → null; already has `appliedAt` → null. StatusThrottle: phase change → true; 30 s same phase → false; 60 s → true.
- [ ] Step 2–4: implement, run.
- [ ] Step 5: commit `Remote model: pairing code, settings sync, commands, status`.

### Task 7: Child pairing (identity, code, QR, confirmation)

**Files:** Create `remote/child/DeviceIdentity.kt`, `remote/child/ChildPairing.kt`, `feature/remote/ChildPairingDialog.kt`, `feature/remote/QrCode.kt` (zxing → `ImageBitmap`); modify `feature/home/HomeScreen.kt` (Settings tab section "Parent's phone"), strings.

**Interfaces:**
```kotlin
class DeviceIdentity { suspend fun ensureSignedIn(): String /* uid */; suspend fun deviceId(): String /* UUID in DataStore */;
                       val pairedOwner: Flow<PairedOwner?>; suspend fun setPairedOwner(o: PairedOwner?) }
data class PairedOwner(val uid: String, val email: String?, val name: String?)
sealed interface PairingState { data object Starting; data class Waiting(code: String, expiresAtMs: Long); data class Claimed(code, name, email); data object Paired; data class Failed(reason: Int /*string res*/) ; data object Expired }
class ChildPairing { fun start(scope): StateFlow<PairingState>; suspend fun confirm(); suspend fun reject(); suspend fun disconnect() }
```
- `start`: sign in anonymously, create/merge `devices/{id}` (childUid, name = `Build.MODEL`, ownerUid null on create only), create `pairings/{code}` in a transaction that retries on an existing code (max 5), listen to it. `confirm`: update device `ownerUid/ownerEmail`, delete pairing, `setPairedOwner`. `disconnect`: device `ownerUid = null`, `setPairedOwner(null)`.
- UI: Settings → "Parent's phone": not paired → "Connect a parent's phone" button → dialog with QR (240 dp), the code as `123 456`, countdown, "Waiting for the parent's phone…"; Claimed → "Allow {name} ({email}) to control this phone?" Allow / Don't allow; paired → "Connected to {email}" + "Disconnect" (confirmation dialog). Offline → "Connect to the internet to pair."
- [ ] Steps: implement; manual check on the API 31 emulator against the Firebase emulator (`adb reverse tcp:8080 tcp:8080`, `adb reverse tcp:9099 tcp:9099`): the QR and code appear, and the pairing document exists (Firestore emulator REST `GET http://127.0.0.1:8080/v1/projects/demo-nourtime/databases/(default)/documents/pairings/{code}`); a claim written over REST (as a test Google user) shows the confirmation; Allow sets `ownerUid`.
- [ ] Commit `Child phone: pair with a parent's phone by QR or code`.

### Task 8: Child sync (status, usage, apps, settings, commands)

**Files:** Create `remote/child/RemoteSync.kt`, `remote/child/CommandApplier.kt`; modify `service/timer/TimerService.kt` (launch `resilient { remoteSync.run() }`), `ParentSettingsRepository.kt` (`suspend fun replaceWith(r: RemoteSettings)`), DataStore keys `remote_settings_rev`, `remote_settings_snapshot`, `remote_applied_commands` (last 50 ids); tests `CommandApplierTest.kt`, `RemoteSyncStateTest.kt`.

**Interfaces:** `CommandApplier.applyAll(docs: List<Pair<String, Map<String, Any?>>>, applied: Set<String>): List<String>` (pure ordering + dedupe by id, sorted by `createdAt`; returns ids it applied, calling `engine.apply`). `RemoteSyncState.next(paired: Boolean, ownerUidInDoc: String?)` → `ACTIVE | UNPAIRED_REMOTELY`.

- `run()`: collects `pairedOwner`; while paired: listener on `devices/{id}` (settings → `SettingsSync.decide`; `ownerUid == null` → `setPairedOwner(null)`, stop — Review Focus 4); listener on `commands where appliedAt == null` → `CommandApplier`, then `update(appliedAt = serverTimestamp())`; upload `status` via `StatusThrottle`; upload today's usage every 5 min and on phase change; upload `meta/apps` once per start and when the list changes; upload settings when `SettingsSync` says UPLOAD (debounced 2 s).
- [ ] Step 1: tests: CommandApplier applies in `createdAt` order; a duplicate id is skipped (Review Focus 1); invalid docs are marked applied without effect; RemoteSyncState: owner null → UNPAIRED_REMOTELY.
- [ ] Step 2–4: implement, run.
- [ ] Step 5: manual: with the child paired (Task 7), write a BONUS command over REST → the child's Home ring shows the bonus within seconds and the command gets `appliedAt`; change budget over REST (by parent, rev+1) → child Settings shows it; change it on the child → `settings.by == "child"` and rev+1 in Firestore.
- [ ] Step 6: commit `Child phone syncs status, usage and settings and applies parent commands`.

### Task 9: Parent sign-in, device list, add a child

**Files:** Create `remote/parent/ParentAuth.kt`, `remote/parent/ParentDevices.kt`, `feature/parent/ParentRoute.kt`, `feature/parent/AddChildDialog.kt`; strings.

**Interfaces:** `ParentAuth.user: StateFlow<ParentUser?>`; `suspend fun signInWithGoogle(activity): Result<Unit>` (Credential Manager `GetGoogleIdOption(serverClientId = R.string.default_web_client_id)` → `GoogleAuthProvider.getCredential(idToken)`); debug-only `signInTestAccount(email)` using the emulator's unsigned token `{"sub":…,"email":…,"email_verified":true}`; `signOut()`. `ParentDevices.devices: Flow<List<ChildDevice>>` (`where ownerUid == uid`); `suspend fun claim(code: String): ClaimResult` (NOT_FOUND, EXPIRED, ALREADY_CLAIMED, WAITING_FOR_CHILD, PAIRED — waits up to 2 min for the device to appear in `devices`).

- UI: signed out → Nour star, "Follow your child's phone", Google button (+ "Test account" in debug when the emulator is used). Signed in → list of child phones (name, status line "Locked · back in 2 h" / "35 min left" / "Not seen since …"), "Add a child's phone" → dialog: "Scan the QR code" (`GmsBarcodeScanning.getClient(context).startScan()`) or type the 6-digit code; progress "Confirm on your child's phone"; errors per `ClaimResult`. Account menu: signed-in email, Sign out.
- [ ] Manual (two emulators: API 31 child, API 35 parent): test account sign-in; add child by typing the code; child confirms; the parent list shows the phone.
- [ ] Commit `Parent phone: Google sign-in, child list, add a child by QR or code`.

### Task 10: Parent control screen

**Files:** Create `feature/parent/ChildDeviceScreen.kt`, `feature/parent/ChildDeviceViewModel.kt`; reuse `TimeBudgetEditor`, `LockTypeEditor`, `BedtimeCard`, `AppList`; strings.

**Interfaces:** `ParentDevices.observe(deviceId): Flow<ChildDevice?>`, `usage(deviceId, day): Flow<Map<String, Long>>`, `apps(deviceId): Flow<List<InstalledApp>>`, `send(deviceId, TimerCommand)`, `writeSettings(deviceId, RemoteSettings, currentRev)`, `remove(deviceId)`.

- Screen: status card (ring like child Home, from `status` + age: "Updated 2 min ago"; stale > 15 min shows "Not seen since …"; `protectionDegraded` → warning "Protection needs attention on the child's phone"); actions: bonus chips +15 / +30 / +60 (confirm dialog), "Lock now" (when available), "End lock" (when locked); "Sent — waiting for the child's phone" until `appliedAt`; today's usage per app; settings (budget, lock period, lock type, bedtime, daily reset, limited apps, allowed during lock — the app pickers use `meta/apps`); "Remove this phone" (confirm).
- [ ] Manual on two emulators: bonus while locked unlocks the child with exactly the bonus; lock now covers YouTube on the child; end lock; change the budget on the parent → child Settings updates; change on the child → parent updates; remove → child Settings shows not connected.
- [ ] Commit `Parent phone: status, bonus, lock, usage and settings for each child`.

### Task 11: Docs, privacy and store compliance

**Files:** `docs/privacy-policy.md` (what is uploaded when paired: device model, Nour Time settings, timer status, per-app minutes for limited apps, list of launchable apps' names; parent's Google email/name; stored in Firebase/Google Cloud; deleted by removing the phone or uninstalling), `docs/play-compliance.md` (INTERNET; Data safety: app activity + app info shared with the parent's account, encrypted in transit, deletion on request), `README.md` (running the emulators, `adb reverse`, creating the real Firebase project: enable Anonymous + Google sign-in, add SHA-1/256, download `app/google-services.json`, `firebase deploy --only firestore:rules`), `docs/handoff.md`, `docs/progress.md`, `docs/testing-checklist.md` (Phase 2 section).
- [ ] Commit `Docs: Phase 2 architecture, privacy and setup`.

---

## Self-review notes

- Coverage: status ✓ (T8/T10), usage ✓ (T8/T10), bonus ✓ (T4/T8/T10), lock now / end lock ✓, settings both ways ✓ (T6/T8/T10), QR + code ✓ (T6/T7/T9), Google sign-in ✓ (T9), two modes ✓ (T5), bonus-ends-lock ✓ (T4).
- Real Firebase project creation needs the user (Task 11 README); everything else runs on the emulator suite.
