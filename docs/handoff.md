# Nour Time: technical handoff

Read this to resume work. Status against the brief is in [`progress.md`](progress.md). Build commands
and the file map are in the [`README`](../README.md).

## 1. Architecture at a glance

Single `:app` module · Kotlin · Jetpack Compose + Material 3 · Hilt · MVVM · DataStore + Room ·
minSdk 26, targetSdk 35 · Arabic (RTL) + English · no network access.

```
             ┌────────────────────────── one app process ───────────────────────────┐
 system ──►  │ NourAccessibilityService ──window list──►  ForegroundAppTracker        │
 events      │   (background worker thread)                  │ StateFlow<ForegroundState>
             │                                              ▼                        │
 screen on/  │ TimerService (foreground, specialUse)                                 │
 off, boot ─►│   ├─ runTimer ───────► TimeEngine.update() ──► TimerStatus (StateFlow)│
             │   ├─ usage-stats fallback poll (only while Accessibility is off)     │
             │   ├─ notifications / degraded alert / stats cleanup                  │
             │   └─ BlockCoordinator.run ─► BlockPolicy.decide() ─► LockOverlay      │
             │                              ScreenOffTimer ─► ScreenLocker.lockNow() │
             │ MainActivity (parent UI) reads TimeEngine / tracker / repositories    │
             └───────────────────────────────────────────────────────────────────────┘
```

- **Who starts what:** `TimerService` is started by `MainActivity` (once onboarding is complete),
  by the Accessibility service when it connects, and by `BootReceiver` (boot and app update). All go
  through `TimerService.start()`, which is safe to call repeatedly.
- **Everything is a singleton** (Hilt `SingletonComponent`), so the Accessibility service, the timer
  service and the UI share the same tracker, engine, pass and overlay in one process.
- Each service loop runs inside `resilient { }`: an exception is logged and the loop restarts after
  5 s, instead of crashing the process or silently stopping protection.

## 2. Core pieces

| Piece | File(s) | Responsibility |
|---|---|---|
| Detection | `core/detection/*`, `service/detection/NourAccessibilityService.kt` | Turns window lists (or usage events) into `ForegroundState(foreground, visible, screen, source)`. |
| Timer | `core/timer/TimeRules.kt` (pure), `TimeEngine.kt` (state + persistence) | Budget, lock, refill, daily reset; usage per app. |
| Blocking | `core/blocking/BlockPolicy.kt` (pure), `ParentPass.kt`, `ScreenOffTimer.kt` (pure), `service/blocking/BlockCoordinator.kt`, `LockOverlay.kt` | What to cover, when, and the overlay window. |
| Trusted time | `core/time/TrustedClock.kt` (`TrustedTime` is pure) | Wall time that manual clock changes can't move. |
| Security | `core/security/*`, `data/security/SecurityRepository.kt` | PIN/answer hashing, lockout, PIN creation state machine, parent session. |
| Storage | `data/*`, `di/AppModule.kt` | DataStore `nour_prefs` (settings, hashes, timer state, trusted-time anchor); Room `nour.db` (schedule, daily usage). |
| Child UI | `feature/lock/*` | Templates, "Time's up" screen, parent PIN/answer panels inside the overlay. |
| Parent UI | `feature/onboarding`, `home`, `setup`, `schedule`, `pin` | Onboarding, tabs, settings, unlock. |

Pure logic has unit tests (`app/src/test`, 107+ tests): `TimeRules`, `ForegroundRules`,
`BlockPolicy`, `ScreenOffTimer`, `TrustedTime`, `PinAttemptPolicy`, `PinCreation`, answer
normalization, schedule, templates, and the repositories (DataStore in a temp folder).

## 3. Tricky decisions and why

### Picture-in-picture and split screen
Activity-change events alone miss a YouTube video that keeps playing in PiP after Home. We declare
`canRetrieveWindowContent` + `flagRetrieveInteractiveWindows` and read `getWindows()`. From each
window we read **only** `type` and `root.packageName`; no node text, ever (Play policy, and your
explicit requirement). `ForegroundRules.fromWindows` keeps `TYPE_APPLICATION` windows. The active
one is `foreground`; all of them are `visible`, and a limited app anywhere in `visible` counts and
gets blocked.

### Window reads must be off the main thread
Asking for the root of **our own** window is answered by our own main thread. Doing it on the main
thread deadlocked until timeout and froze the app. `NourAccessibilityService` pushes events into a
conflated channel drained by a single background worker (`Dispatchers.Default.limitedParallelism(1)`).

### Transparent packages
System UI, keyboards, the permission controller and the chooser (`android`) don't change the
foreground app: the previous app stays, so pulling down the shade or typing doesn't pause the
timer. Nour Time's own screens are **not** transparent; they count as leaving the limited app, so
the timer never runs while a parent is in the app.

### Fail-closed
When Accessibility disconnects, `TimerService` polls `UsageStatsManager` every 2 s (1 h lookback on
the first poll, then 15 s). `TimerStatus.protectionDegraded` makes `BlockPolicy` block limited apps
even with budget left, until the parent restores Accessibility. The parent alert waits 15 s, because
right after a reboot the service often starts before Accessibility reconnects.

### elapsedRealtime + boot count (timer, PIN lockout, parent pass)
All durations use `SystemClock.elapsedRealtime()`, which the child can't change. It restarts at
boot, so every persisted state stores the `bootCount` (`Settings.Global.BOOT_COUNT`). On a different
boot, `TimeRules.advance` counts a zero-length gap: powering off never uses budget and never shortens
a lock (the lock is frozen while the phone is off, approved). PIN and answer lockouts restart in full
after a reboot (`PinAttemptPolicy.rebase`).

### Trusted wall clock (bedtime, schedule, daily reset)
These need the time of day. `TrustedTime.now`: with automatic (network) time on, trust the system
clock; otherwise advance from an anchor with `elapsedRealtime`, so manual edits are ignored. After a
reboot, use the system clock but never earlier than the last time seen. A parent PIN unlock of the
app re-trusts the system clock. The daily reset also requires ≥ 20 h of real time since the last
one.

### The overlay
- `LockOverlay` shows a Compose `ComposeView` in its own window with a hand-made
  lifecycle/saved-state owner (Compose outside an Activity).
- The window is `TYPE_ACCESSIBILITY_OVERLAY` when Accessibility is attached. That covers PiP and
  the status bar, and the soft keyboard works in it (verified). Otherwise it's
  `TYPE_APPLICATION_OVERLAY` (fallback).
- It takes audio focus so the limited app's playback pauses.
- It never draws over the keyguard (emergency calls), a phone call (ringing or in-call via
  `AudioManager.mode`, the dialer and in-call packages, the default dialer), or Nour Time itself.
- A failed `addView` is cleaned up and retried at most every 10 s.

### Parent pass and security question
A PIN (plus the security answer during a lock period) grants a 15-minute in-memory `ParentPass`
that ends when the screen turns off. A device pass lifts a whole-phone lock; a full pass also opens
limited apps and Settings. Whether a lock period is running is read by `LockPeriodState` straight
from persisted state. Relying on the coordinator's first tick once let the PIN alone through for
about 1 s after process start.

### Whole-device screen-off
`ScreenOffTimer` arms when a lock period *starts* (never when protection starts mid-period, e.g.
after a reboot). About 8 s later, if the whole-device lock is covering the screen at that moment
(no pass, Nour Time not open, screen on), it calls `DevicePolicyManager.lockNow()`. It fires once
per period, not on every wake.

### Settings protection without reading content
Detecting "App info → Force stop" would need screen text, which is ruled out. So the whole Settings
app, OEM security apps and the package installer are matched by **package name**
(`BlockPolicy.SYSTEM_SETTINGS_PACKAGES`). Force stop also turns Accessibility off, and the
fallback plus fail-closed mode covers that.

### Other
- Foreground service type `specialUse` (Android 14+), subtype text in the manifest.
- `ReplaceFileCorruptionHandler` on DataStore: a corrupt file restarts setup instead of crash-looping.
- Daily usage older than 30 days is pruned twice a day.

## 4. Debugging aids

- Debug builds: Home shows **Detection (debug)** (source, foreground, visible, screen) and
  **End budget now** / **End lock now**.
- Logcat: `adb logcat -s NourA11y BlockCoordinator LockOverlay TimerService ScreenLocker`. Every blocking
  decision change is logged at INFO.
- Grant permissions without the UI (emulator):
  ```
  adb shell settings put secure enabled_accessibility_services com.nourtime.app/com.nourtime.app.service.detection.NourAccessibilityService
  adb shell appops set com.nourtime.app GET_USAGE_STATS allow
  adb shell appops set com.nourtime.app SYSTEM_ALERT_WINDOW allow
  adb shell dpm set-active-admin com.nourtime.app/.service.admin.NourDeviceAdminReceiver
  ```
- Emulator quirks on this machine: `screencap` often returns a stale frame (wait 2–3 s or trust
  `uiautomator dump`); the first tap right after a `uiautomator dump` is sometimes dropped.
- Test data used on the emulators: PIN **4827**, security answer **blue**.
- Git in this folder: `git -c safe.directory=E:/cloud/nourtime …` (never change the global config).

## 5. Next technical steps

0. **Phase 1.5** (§6 below) comes first, starting with the detection-freeze bug.
1. **Run step 8** on Samsung and Xiaomi (Honor started 2026-09-26) with [`testing-checklist.md`](testing-checklist.md). Fix what
   comes up; the likeliest areas are OEM battery killers (autostart, "sleeping apps"), OEM Settings
   package names missing from `SYSTEM_SETTINGS_PACKAGES`, and OEM dialer/in-call package names for
   `PHONE_PACKAGES`.
2. **Audio:** drop recordings into `res/raw/` (e.g. `voice_timeup_m.ogg` / `_f`, `lullaby.ogg`) and
   play them from `LockOverlay.playChime()` by age group, gender and template (sleep → lullaby).
3. **Release:** add a `signingConfigs` block reading a keystore from `local.properties` / env vars;
   `./gradlew :app:bundleRelease`. R8 is already verified (a signed release APK ran on the emulator).
4. **Phase 2 follow-ups** (§7): real Firebase project, real-phone test, account deletion.
5. Optional: Lottie animations (the `NourPose` enum is the seam), a TalkBack/contrast audit,
   Device Owner mode for Safe Mode blocking.

## 6. Phase 1.5 design notes

Scheduled 2026-09-26; status and open decisions are in [`progress.md`](progress.md#phase-15-scheduled-2026-09-26).
Every new string goes into both `values/strings.xml` and `values-ar/strings.xml`. Child copy is
masculine and feminine, and layouts are checked in RTL.

### Task 0: detection freeze after an Accessibility reconnect (P0), fixed on the emulator 2026-09-27
Seen on Honor (Android 12) on 2026-09-26: at 22:56:24 the system logged `removeConnection 7` /
`addConnection 9` for our service. From then on `BlockCoordinator` kept ticking but never produced
a decision: NourTube and Settings stayed uncovered during a lock, and no degraded alert was shown.

**Mechanism:** if the window list can't be read, or an application window's root node comes back
`null`, the old code dropped it; `ForegroundRules.fromWindows` then kept the *previous* apps, so
detection silently froze on whatever was last seen (Nour Time's own screen on Honor, which is never
blocked). Forcing every root to `null` on the emulator reproduced it exactly (YouTube open during a
lock, 5/5 unblocked, no alert).

**Fix:** `ForegroundRules.resolveUnknown` fills unknown owners first from the package in the
window's own accessibility events (event metadata, not content), then, for the active window only,
from usage stats. A failed window read becomes the usage-stats app. With every root forced to
`null`: 10/10 blocked. With normal roots the fallback is never used. 6 new unit tests.

**Likely trigger (found 2026-09-27):** `uiautomator dump` makes Android unbind and rebind every
Accessibility service. On the Honor the reconnect came seconds after such a dump during testing.
Don't use `uiautomator dump` while testing detection; use screenshots and `dumpsys window`.
**Not reproduced:** Honor's exact trigger. A clean disable/enable and a second `onServiceConnected`
on the same instance both work on the emulator. `NourA11y` logs (connect / unbind / destroy, each
window-list change, and a warning whenever the fallback is used) are there for the next Honor run:
`adb logcat -s NourA11y BlockCoordinator LockOverlay TimerService`.

### Task 1: guided onboarding, no auto-granting (done on the emulator 2026-09-27)
- Android has no API for an app to grant Accessibility or Device admin to itself, and Play's
  Accessibility policy forbids using Accessibility to click through permission screens. Everything
  stays a manual toggle; the work is guidance.
- **Per-brand help:** `OemAutostart.brand()` → `OemBrand`; the Autostart step shows brand-specific
  steps (Xiaomi, Oppo/realme, Vivo/iQOO, Honor/Huawei, OnePlus) and now also appears on Samsung as
  "Keep Nour Time awake" (Sleeping apps). The menu paths are written as "usually" and **need
  checking on real phones**. *Allow restricted settings* keeps the generic text until device tests
  say where each brand puts it.
- **Automatic return:** after "Open settings" (and "Open App info" on the Accessibility step),
  `OnboardingViewModel.watchUntilGranted` polls that permission for up to 3 min and brings
  `MainActivity` back (`CLEAR_TOP | SINGLE_TOP`) once it's on. Android allows this background
  start because Nour Time's Accessibility service is bound (first permission step). Verified for
  Accessibility, usage access, overlay, device admin and battery.
- **"Test protection" step** (`OnboardingStep.TEST_PROTECTION`, before Finished): opens the first
  limited app; when the tracker sees a limited app on screen it shows "Nour Time saw …" and brings
  onboarding back. Skippable. The timer service isn't running yet during onboarding, so the test
  doesn't use any budget.
- The Finished screen no longer says the timer "arrives in the next updates" (leftover text).

### Task 2: educational apps during the lock (done on the emulator 2026-09-27)
Decisions (user, 2026-09-27): allow-list only, time in allowed apps is free, applies at bedtime too.
Whole-phone mode is included as well (otherwise the list would do nothing there); easy to drop.
- `ParentSettings.allowedDuringLock` (DataStore `allowed_during_lock`). An app is either limited or
  allowed: `setAppLimited` / `setAppAllowedDuringLock` remove it from the other list. Allowed apps
  are never limited, so they never use the budget.
- `BlockPolicy`: in whole-phone mode during a lock period or bedtime, nothing is covered while
  **everything** on screen is allowed (split screen / PiP with another app keeps the lock). In
  selected-apps mode allowed apps are ordinary unlimited apps. Settings protection still applies.
- The "Time's up" / bedtime screen shows "You can still open:" with the allowed apps' icons and
  names (`AllowedAppsRow`, gendered Arabic copy); tapping one opens it (`LockOverlay.openApp`).
- Parent UI: Settings → "Allowed during the lock" → Choose apps (limited apps aren't offered).
- Verified on Android 12: selected-apps and whole-phone modes, Home re-locks the phone.

### Task 3: calmer Settings / uninstall protection (done on the emulator 2026-09-27)
- The Settings / installer cover (`BlockReason.SYSTEM_SETTINGS`) now opens straight on the PIN pad
  with a "for grown-ups" line (`LockOverlayContent`); Cancel goes back to the child screen and its
  OK button. PIN, plus the security answer during a lock period, grants the full parent pass, and
  Settings then works normally: App info → Uninstall leads to Android's own "Deactivate & uninstall".
- Settings tab → **Uninstall Nour Time** (`feature/home/UninstallSettings.kt`): security answer
  (always, not only during a lock) → `removeActiveAdmin` (waits until Android confirms) → parent
  pass → `ACTION_DELETE`. Needs `REQUEST_DELETE_PACKAGES`. If the parent cancels Android's dialog,
  Home's Permissions card shows Device admin → **Fix**, which re-activates it. All three paths
  (Settings route, cancel + Fix, full uninstall) verified on Android 12.
- The flashing the user saw didn't reproduce on the emulator: both App info and the uninstall
  dialog were covered steadily. Likely Honor-specific; watch `BlockCoordinator` / `LockOverlay`
  open/close lines when re-testing there.

## 7. Phase 2: remote control

Built 2026-09-27 on branch `phase2-remote-control`; plan in
[`superpowers/plans/2026-09-27-phase2-remote-control.md`](superpowers/plans/2026-09-27-phase2-remote-control.md).

### Shape
- **Two modes, one app** (`data/mode/AppModeRepository`): the first launch asks; phones that started
  onboarding before Phase 2 are child phones. Parent phones skip the PIN (Google sign-in protects them)
  and never start protection.
- **No server code.** The child's foreground service (`TimerService`) runs `RemoteSync` while paired, so
  Firestore snapshot listeners replace push notifications. Firestore queues writes offline.
- **Everything remote goes through the Phase 1 paths:** settings via `ParentSettingsRepository.replaceWith`,
  commands via `TimeEngine.apply(TimerCommand)`. Blocking and timing didn't change.

### Data model (Firestore; rules in `firebase/firestore.rules`, tests in `firebase/test/rules.test.ts`)
```
pairings/{6-digit code}  deviceId, childUid, createdAt (server time; valid 10 min), claimedBy/Email/Name
devices/{deviceId}       childUid (anonymous uid), ownerUid/Email/Name (set only by the child's phone),
                         name, createdAt, status{phase, remainingMs, budgetMs, lockRemainingMs,
                         protectionDegraded, updatedAt}, settings{..., rev, by: child|parent}
devices/{id}/meta/apps   apps: [{p, l}]
devices/{id}/usage/{yyyy-MM-dd}  ms: {package: millis}
devices/{id}/commands/{auto}     type BONUS|LOCK_NOW|END_LOCK, minutes 1..240, createdAt, by, appliedAt (null → set once by the child)
```

### Decisions worth knowing
- **Pairing** (`remote/child/ChildPairing`, `remote/parent/ParentDevices.claim`): a claimed code does
  nothing until the child's phone (behind the PIN) taps Allow and writes `ownerUid` together with
  `pairingCode`; the rules accept only the uid that claimed that code for that device, so nobody can
  push a device into a parent's list. A claim must carry the claimer's own verified email (the rules
  check the token), so the "Allow Mom (mom@…)?" question can't be faked. Only Google users (or the
  code's own phone) can read a code. Codes expire from their server `createdAt`; the child's countdown
  runs on its own clock from when it started, so a wrong clock can't expire it early.
- **Settings sync** (`remote/model/SettingsSync`): every write carries `rev` and `by`, and both phones
  write in a Firestore **transaction** (rev = server rev + 1), so two writers never share a revision.
  The parent's edit is applied to the server's current settings; the child only uploads if no newer
  parent revision is on the server (otherwise it applies that). A parent revision newer than (or equal
  to but different from) the last one the child synced wins. Settings edits need the network (the
  transaction fails offline; the child re-decides on the next snapshot).
- **Commands** (`remote/child/CommandQueue`): at most once each, in `createdAt` order: each id is saved
  before its command is applied. Only the current owner's commands take effect; others (a removed
  parent's) are consumed without effect. Nothing but the settings listener runs until a server (not
  cache) snapshot confirms the phone is still paired. That listener includes **metadata changes**: when
  the cached copy equals the server's (app restart, re-pairing in the same process) the confirmation
  only flips `isFromCache`, and without `MetadataChanges.INCLUDE` it never arrives (found 2026-09-27:
  after a restart nothing synced until the document changed).
- **Removal:** the parent clears `ownerUid` (or the server says the device is gone); the child notices
  (`PairingCheck`), unpairs and deletes its own data (below).
- **Account deletion** (2026-09-27): server data exists only while paired. The child's phone deletes
  `usage`, `meta`, `commands`, the device and its anonymous account on *Disconnect*, on removal by the
  parent, and before *Uninstall Nour Time* (`ChildPairing.disconnect`, subcollections first because
  their rules read the device). Offline it only unlinks and shows "couldn't be deleted… Try again". The
  parent's *Delete my account* deletes the commands it sent, unlinks its phones
  (`ParentDevices.forgetParent`) and deletes the Firebase user, re-authenticating if Firebase asks for a
  recent sign-in. Web deletion page: `docs/account-deletion.md`.
- **Device document:** created after a server-side existence check (rules allow reading a missing
  device); `set()` is never used on an existing one because it would clear `ownerUid`.
- **Google sign-in:** Credential Manager + `googleid`, `default_web_client_id` from google-services.json.
  Debug + emulator can sign in with an unsigned test token ("Use a test account").

### Running it
See the README ("Phase 2"). Useful: the emulator's REST API with `Authorization: Bearer owner` bypasses
rules, e.g. `curl -H "Authorization: Bearer owner" "http://127.0.0.1:8080/v1/projects/demo-nourtime/databases/(default)/documents/devices"`.
The rules tests use project `demo-nourtime-test` because they clear the database.
If the emulator has been running for hours, Android clients may get `RESOURCE_EXHAUSTED … too_many_pings`
and stop receiving snapshots; restart it (`npm run emulators`). After a restart the phones' cached
Firebase accounts no longer exist: clear the parent app's data, and on a rooted emulator delete the
child's `shared_prefs/com.google.firebase.auth.api.Store.*.xml` and `databases/firestore.*` (`adb root`
drops the `adb reverse` tunnels; add them again).

### Not done yet
- Publish the account-deletion web page and fill in its contact email (`docs/account-deletion.md`).
- Real-phone test incl. QR scanning (emulators have no camera) and real Google sign-in (needs SHA-1).
- Push notifications to the parent (e.g. "protection needs attention") would need FCM + Cloud Functions
  (Blaze plan); today the parent sees it when opening the app.
- The parent's phone uses its own date for "today's" usage; a parent in another time zone sees the
  child's day shifted.

### Deferred review items (resolved 2026-09-27)
- Pairing codes: the rules accept only 6-digit ids and require an `expireAt` 10 min–2 days ahead, for a
  Firestore **TTL policy** on `pairings.expireAt` (README step 7) that deletes abandoned codes.
- Lost anonymous account: if the child's phone can no longer read its own device (PERMISSION_DENIED,
  checked again with a server read) it starts over, unpaired, with a new device id
  (`DeviceIdentity.reset`); the parent removes the stale entry. Not reproducible on the emulator
  (it doesn't verify accounts), so only covered by reasoning.
- Status uploads: on every change the parent sees, once a minute while the timer counts, otherwise a
  10-minute heartbeat (`StatusThrottle`, below the parent's 15-minute "not seen").
- Commands wait for the timer's first state before applying (`TimeEngine.apply` needs one).
- A budget edit moves the time left by the change and keeps extra time a bonus added above the budget.
- Commands expire after an hour (`CommandQueue.EXPIRES_AFTER_MS`, by the child's trusted clock); the
  parent's phone shows "Not applied: … offline for over an hour" (applied − created > 1 h).
- Firebase starts only once the phone pairs: the timer service waits for a pairing before creating
  `RemoteSync`, and `DeviceIdentity` gets `FirebaseAuth` lazily.
- Sign-in checks the credential type. Lint's `CredentialManagerSignInWithGoogle` still warns
  (false positive: the check is there).

## 8. Phase 4 (2026-09-27)

### 4a. Weekend limits
`data/settings/DayRules.kt`: `WeekendRules` (off by default; days default to Friday+Saturday for Arabic,
Saturday+Sunday otherwise; unset values copy the normal ones). `DayRules.limitsOn(date)` gives the
budget and lock length of a calendar day (TimeEngine applies it every update, so at midnight the time
left moves by the difference, like any budget edit). `DayRules.activeBedtime(now)` treats each night
as noon to noon and uses the weekend bedtime on the night *before* a weekend day (BlockCoordinator,
LockPeriodState). Synced as `settings.weekend` (a map); settings written without it read as off; the
local sync snapshot still decodes its old 9-field form.

### 4b. Usage history
`data/usage/WeekReport.kt` builds the last 7 days (plus the change from the 7 before) from per-day,
per-app minutes: from Room on the child (`UsageRepository.observeRange`) and from `usage/{yyyy-MM-dd}`
on the parent (`ParentDevices.usageRange`, a document-id range query). `WeekCard` shows it on both. The
child re-uploads the finished day after midnight (and yesterday at start) and deletes server usage
older than 14 days (`RemoteSync.uploadUsage`).

### 4c. Asking for more time
On a time-up lock (not bedtime or the Settings cover, which a bonus can't lift) a paired child's
"Time's up" screen shows **Ask for more time** (`TimeRequests.state` → `AskPolicy`). The child creates
`devices/{id}/requests/{auto}` (`status: pending`, server `createdAt`) and sets the device's `askingAt`,
so the parent's list shows "Asking for more time". The parent's child screen shows the pending request
with +15 / +30 / Not now; `ParentDevices.answer` updates the request and (on approval) sends an ordinary
BONUS command in **one batch**, so a request answered twice never sends two bonuses (the rules allow
one answer). The child's `RemoteSync.followRequests` clears `askingAt` whenever the newest request isn't
pending and deletes requests older than a day; `ChildPairing.disconnect` deletes them too.
Timings (`AskPolicy`): a pending request lapses after 30 min; after "Not now" the child waits 10 min;
"Yes! +15 min" shows for up to 2 min (the bonus usually closes the screen first). Works offline: the
request is queued and shows "Waiting…" at once. There is no push notification to the parent (needs FCM +
Cloud Functions); the parent sees it on opening the app.

## 9. Resume here (2026-09-28, real Firebase project)

**State:** branch `phase4` (not merged into `master`): 4a weekend limits, 4b usage history, 4c ask for
more time, all tested on the emulators with the Firebase emulator. Status against the brief, for outside
review: [`brief-with-status.md`](brief-with-status.md).

**Real Firebase project `nourtime-8d4ce`** (Spark plan, no billing):
- Anonymous + Google sign-in enabled; debug-key SHA-1/SHA-256 added
  (SHA-1 `CA:D6:D5:08:12:A6:FF:34:9C:77:73:02:31:BE:FA:1F:DD:66:D9:BA`).
- `app/google-services.json` is the real config (**not committed**, by the owner's choice).
- Firestore rules were **published by hand in the console** from `firebase/firestore.rules`. Whenever the
  rules change, publish them again (console, or `node node_modules/firebase-tools/lib/bin/firebase.js
  deploy --only firestore:rules --project nourtime-8d4ce` from `firebase/` after a login; `npx firebase`
  hangs on this PC and the CLI isn't logged in).
- No TTL policy (needs billing); the app deletes codes itself (README step 7).

**Build against the real project** (debug build, real Firebase instead of the local emulator):
```
mv app/src/debug/google-services.json <somewhere-outside>/   # the demo config would win for debug
./gradlew :app:assembleDebug -Pnourtime.firebaseEmulator=false
mv <somewhere-outside>/google-services.json app/src/debug/   # restore for emulator work
```
Built once on 2026-09-28 00:19 (OK). Rebuild after any code change. Don't run `adb reverse` for 8080/9099
with this build (it ignores them anyway).

**Next steps, in order:**
1. **Emulator vs the real project:** install that APK on `nourdm-api35` (child) and `nourdm-api31`
   (parent). The parent needs a real Google account on the emulator (Settings → Accounts; the owner types
   the password), then *Sign in with Google*. Pair with the 6-digit code; check status, extra time, lock /
   end lock, settings both ways, ask for more time, and the documents in the Firebase console.
2. **Real devices:** child = the owner's **HONOR VNE-N41** (Android 12) over USB (uninstall any old Nour
   Time first; it was removed earlier), parent = the `nourdm-api31` emulator with the 6-digit code (the
   emulator has no camera). Run [`testing-checklist.md`](testing-checklist.md) §7 (remote) and §8 (Phase
   4), plus the Phase 1.5 re-tests on Honor (detection freeze, Settings cover, onboarding brand texts).
   Watch `adb logcat -s NourA11y BlockCoordinator LockOverlay TimerService RemoteSync`. Don't run
   `uiautomator dump` while testing detection; the phone may be in use, so check before sending input.
3. Then ask the owner whether to merge `phase4` into `master`.

