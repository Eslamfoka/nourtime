# Nour Time: technical handoff

Read this to resume work. Status against the brief is in [`progress.md`](progress.md). Build commands
and the file map are in the [`README`](../README.md).

> **Resume here (2026-09-29 evening):** go to [§11](#11-evening-of-2026-09-29-overnight-2-request-all-on-master).
> Everything is on `master`; the APK for the owner is `dist/NourTime-2026-09-29-debug.apk`. First step:
> ask the owner for the Honor results of the §11 test list.

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

## 9. Resume here (updated 2026-09-28 ~02:10)

**State:** branch `phase4` (not merged into `master`): 4a weekend limits, 4b usage history, 4c ask for
more time, tested on the emulators with the Firebase emulator. Status against the brief for outside
review: [`brief-with-status.md`](brief-with-status.md). Uncommitted at the time of writing: nothing
except `app/google-services.json` (real config, deliberately not committed).

**Real Firebase project `nourtime-8d4ce`** (Spark plan, no billing): Anonymous + Google sign-in on,
debug-key SHA-1/SHA-256 added (SHA-1 `CA:D6:D5:08:12:A6:FF:34:9C:77:73:02:31:BE:FA:1F:DD:66:D9:BA`),
rules **published by hand in the console** from `firebase/firestore.rules` (republish whenever they
change; the CLI isn't logged in and `npx firebase` hangs here). No TTL policy (needs billing).

**Build against the real project** (debug build, real Firebase):
```
mv app/src/debug/google-services.json <outside>/      # the demo config would win for debug
./gradlew :app:assembleDebug -Pnourtime.firebaseEmulator=false
mv <outside>/google-services.json app/src/debug/      # restore for emulator work
```

### Devices right now
- **Child = the owner's HONOR VNE-N41 (Android 12), serial `AAYSNU2712209663`.** Nour Time (real-project
  debug build) installed and fully set up on 2026-09-28: PIN **4827**, answer **blue**, girl, ages 3–6,
  1 h budget, 6 h lock, the three NourTube apps limited, all permissions on, App launch = manual (3
  switches on). **Protection is live on the owner's phone** (Settings is covered by the PIN). Not paired
  yet.
- **Parent = emulator `nourdm-api35` (emulator-5554)**, real-project build installed, parent mode chosen,
  data cleared. `nourdm-api31` **can't** be the parent: its Google Play services (225014047) are older
  than the sign-in library needs (230815045) and the image has no Play Store.
- The API 35 emulator has **no Google account**. The owner has a dedicated test account for it (the owner
  gives the password when resuming; it's not stored in the repo).

### Onboarding on Honor: results
Every permission step returned to Nour Time by itself (Accessibility, Usage access, Overlay, Device
admin, Battery). Honor's Autostart text matches ("App launch" → Manage manually → 3 switches). "Test
protection" saw NourTube in ~0.3 s and came back.

### Issues found on Honor (to fix)
1. **Play-policy text (important):** the disclosure (onboarding step 1: "Everything stays on this device.
   Nour Time doesn't collect, upload or share any personal data"), the Accessibility step ("Nothing
   leaves this device") and the Accessibility service description in Settings ("No data leaves this
   device") are wrong once a parent's phone is connected (usage and the app list are uploaded). Reword:
   nothing leaves the phone unless a parent's phone is connected, and then only usage times, the app list
   and settings. The welcome screen's "Everything stays on this device" too. Check
   `docs/play-compliance.md` and the privacy policy for the same claim.
2. The Accessibility hint says "open Installed apps (or Downloaded apps)"; on Honor, Nour Time is listed
   directly on the Accessibility page (scroll down). Make the Honor text brand-specific.
3. Usage access opens Nour Time's own page on Honor (good); **Display over other apps opens the full
   list** (Honor ignores the package), so the parent must search. Add that to the hint.
4. On the 720p Honor screen, the app-search list during onboarding shrinks to a thin strip while the
   keyboard is open (pinned header and footer).
5. **Parent sign-in** said "Couldn't sign in. Check the internet connection" when the phone had no Google
   account (Credential Manager error 28433 = no credentials). **Fixed (uncommitted until the build ends,
   see below):** `ParentAuth.googleCredential` falls back to `GetSignInWithGoogleOption` (Google's own
   screen, which can add an account), and `ParentViewModel.signIn` logs the failure (`ParentSignIn`). The
   error text should also distinguish "no Google account / cancelled" from "offline" (to do).

### Next steps when resuming (Phase 2 on real devices)
1. Install the latest real-project APK on emulator-5554 (rebuild with the commands above if needed),
   add the test Google account, and sign in as the parent.
2. Honor: Nour Time → Settings → Parent's phone → Connect a parent's phone (PIN 4827) → 6-digit code →
   type it on the emulator → Allow on the Honor.
3. Checklist [`testing-checklist.md`](testing-checklist.md) §7 and §8 against the real project: status
   and "Updated" time, +15/+30/+1 h, lock now / end the lock, settings both ways (budget, limited apps,
   weekend), usage + 7-day card, ask for more time (approve / not now), Honor offline → command applied
   once online, Accessibility off → "Protection needs attention", disconnect / remove → data gone in the
   Firebase console.
4. Phase 1.5 re-tests on Honor: detection after Accessibility reconnect (no `uiautomator dump`), the
   Settings cover (PIN pad, no flashing), in-app uninstall.
5. Fix issues 1–5 above, then ask the owner about merging `phase4`.
Watch: `adb -s AAYSNU2712209663 logcat -s NourA11y BlockCoordinator LockOverlay TimerService RemoteSync`.

### 2026-09-28 afternoon: Phase 2 tested against the real project (emulators)
The Honor no longer had Nour Time installed; the owner chose to test on emulators instead
(child = `nourdm-api31` emulator-5556, parent = `nourdm-api35` emulator-5554 signed in with the test
Google account). Checklist §7 against `nourtime-8d4ce`, all passing: Google sign-in; typed-code pairing;
wrong code and "Don't allow" messages; Lock now; ask for more time → +15 (lock ends with 15 min) and
Not now; End the lock (full budget); settings both ways (lock period parent→child, budget child→parent);
child offline → +15 applied once ~5 s after reconnecting; Accessibility off → "Protection needs
attention" and back within 5 s; usage (Chrome 1 min) on the parent; Remove this phone; child Disconnect;
Arabic on both phones; weekend section on the parent.
Not done: QR scan with a real camera, expired code (10 min), restart while paired, Firestore
console check that data is gone after remove/disconnect, Honor re-tests (§9 steps 4).

Fixed (678a3d3): **Arabic pairing code showed its two groups swapped** ("413 255" drawn as "255 413"),
so pairing failed for anyone typing the Arabic screen; security question direction; Arabic digits.
Earlier today: privacy wording (a74dd74), privacy policy + Play notes mention ask-for-time (df45c98).

Open findings (not fixed, need the owner's call):
- ~~Lowering the budget right after an approved bonus took the bonus away~~ **fixed 6677bfd**
  (`TimerState.bonusMs`; verified on the emulators: lock → +15 → budget 45→30 keeps 15 min).
  `phase4` fast-forward merged into `master` at 6677bfd (2026-09-28).
- ~~The parent showed "Now tap Allow" while its claim was only queued offline~~ **fixed**: the claim is a
  transaction (fails offline instead of queueing), `ClaimFlow` shows "Connecting…" until the server has
  it and gives up after 20 s as offline. Emulator: offline → "Connecting…" → "No internet", and the
  child never got the request after the parent came back online; online → "tap Allow" in <1 s → paired.
- Unverified: once, right after a child Disconnect, the parent's list still showed the phone (the parent
  app had just been reinstalled and then went offline, so possibly its cache). Re-check the Disconnect
  case with the parent online.
- Weekend default days follow the phone's current language at read time (changing the language
  changes an unset default); only matters if the language changes after pairing.
Emulator notes: see memory — DNS fix via iptables, never `emu kill` (snapshot restore), Nouri disabled.

### 2026-09-28 evening: real Honor as the parent
Owner's Honor = **parent** (real camera), child = `nourdm-api31` emulator. Passing: **QR scan with the
real camera** → "Now tap Allow" → Allow → paired; +15 while locked; Lock now; ask for more time →
+30 from the Honor (~1 s); Lock now + End the lock (full 30 min budget); **restart while paired**
(normal `adb reboot` of the child: Nour Time + Accessibility came back by themselves, "Updated just
now" on the Honor, +15 applied → 44:53 left of 30 min). Child Disconnect with the parent online
removed the phone from the parent's list at once (the earlier stale entry was the offline cache).
After a child reboot the emulator DNS fix must be re-applied (`adb root` + iptables).
**Firestore console check passed** (owner, 2026-09-28): after child Disconnect the `devices/{id}` document
and all subcollections were gone, the anonymous user was deleted, the Honor showed "No phones yet".
Still open: expired code (10 min), Honor as the
**child** (Phase 1.5 re-tests and Honor hint issues 2–4).

## 10. Morning of 2026-09-29 (overnight work, owner asleep)

**APK to install:** `dist/NourTime-2026-09-29-debug.apk` (real Firebase project, debug-signed like the
builds on the Honor, so it installs over them and keeps the data: `adb install -r dist/NourTime-2026-09-29-debug.apk`,
or copy it to the phone and open it). Everything below is on `master`; 272 unit tests pass, lint has no
new warnings (same 61 older ones: library versions, battery-optimization permission, etc.).

### Commits tonight
- `f8e4cf2` **Protection after screen off/on** (found on the Honor, fixed for every phone): the screen
  receiver is exported (Honor's System UI sends USER_PRESENT from its own uid; a not-exported receiver
  dropped it, so after the first unlock nothing was blocked or counted), and the parent pass now ends
  straight from SCREEN_OFF (`ParentPass.onScreenOff`) instead of when the blocker happens to look.
  Verified on the emulator with a fast off/on: the blocker never saw the screen off, the pass still
  ended, Chrome was blocked. Block decisions now log what they were based on (screen, lock, pass).
  **Still to confirm on the Honor** (the last run was cut short by USB): open Nour Time with the PIN or
  use "For parents" on the Time's up screen, screen off/on, open the limited app → Time's up.
- `3d0d169` **Parent sign-in messages**: cancelled → no message; no Google account → "add one in
  Settings → Accounts"; offline → the old text; anything else → "try again". Verified cancel on API 35.
- `ea3a866` **U1 playful time pickers** (owner's idea, merged to master as asked): wherever a time is
  set (onboarding, Settings, weekend limits, the parent's screen) the slider is replaced by the theme
  chosen in Settings → "Time picker style": **Dial** (drag around the ring), **Coins** (drag or tap
  5/15/60-minute coins into the jar, tap a jar coin to take it out), **Liquid** (drag the handle on the
  surface), **Surprise me** (a different one each time). Preset chips stay for quick picks. Only touches
  on the ring/handle move a value, so scrolling Settings can't change the budget by accident (that
  happened in the first version on the emulator and was fixed). TalkBack sees each picker as a slider.
  Emulator-tested in Arabic: dial 5→65 min, coin drag 65→80, coin tap 80→75, liquid 75→175, and
  swipes starting on the dial centre / bottle bottom scroll the page without changing the value.

- `6b097ab` Home logs which permission reads as off when "Some permissions are off" shows (seen once
  on the Honor right after the system restarted the app; not reproduced, so no guessed fix).
- `514068a` **Expired pairing code** (checklist §7, now tested): the child said "Something went wrong"
  after 10 minutes because the watcher's catch-all swallowed its own cancellation. Now "The code has
  expired" + "New code" (makes a fresh code); Cancel shows no error. The parent typing the old code gets
  "No phone is showing this code… get a new code" (the child deletes expired codes).
- `262cd4d` **Time pickers use the latest callbacks** (found in a review pass, reproduced first): a
  second drag on the weekend budget saved a stale copy of the weekend settings and reverted the weekend
  lock period (8 h → 4 h). Fixed with stable forwarders to the latest onCommit/onPreview; the same run
  now keeps 8 h.
- Checked, no change needed: weekend days are saved the moment weekends are turned on, so the
  language-based default is only a suggestion while they're off (the earlier mismatch came from
  changing the phone's language after pairing). A claim timeout counts as offline even when the
  claim's own catch-all swallows the cancellation (test added).

### Owner's rules recorded
- Every fix must work on all Android brands; brand-specific only as runtime-chosen text/behaviour with a
  generic fallback (tonight's fixes are standard Android; the Honor setup hints are chosen by brand).

### To test first on the Honor
1. Install the APK above. Settings → Time picker style → try Dial, Coins, Liquid, Surprise me.
2. The pass re-test from the top of this section.
3. The Honor currently has a lock running on Chrome (debug box → "End lock now" ends it).

### Still open
- Phase 2 checklist §7 is complete except the Honor pass re-test above.
- Phase 1.5 "educational content during lock" still needs the owner's decisions (see §6).
- The earlier "Some permissions are off" flash on Home right after Nour Time was restarted by the
  Honor (it cleared by itself) — not reproduced since.

### 2026-09-29 morning: confirmed on the Honor
Nour Time had been uninstalled from the Honor overnight; the morning APK went in as a fresh install and
the owner set it up again (child, Chrome limited, 1 h). The new dial showed at the "How much time?" step.
**Pass re-test passed:** End budget → Time's up → For parents + PIN/answer (Chrome opened) → screen
off/on → Chrome: `05:00:51 decision=TIME_UP … usable=true timeUp=true pass=false/false`, 10 ms after the
screen-on refresh. Both f8e4cf2 fixes (unlock broadcast, pass ends at SCREEN_OFF) are confirmed on the
real phone.

## 11. Evening of 2026-09-29 (overnight-2 request, all on master)

The morning APK `dist/NourTime-2026-09-29-debug.apk` was rebuilt after `e6a3f23` (real project
`nourtime-8d4ce`) and contains everything below. All unit tests pass.

| Commit | What |
|---|---|
| `06e0163` | **Forgot PIN.** "Forgot the PIN?" under the PIN pad (app and Time's up overlay). The security question → a new PIN twice → done. From the overlay it opens the app straight on the recovery screen. |
| `1aa5f8e` | **Single dashboard.** No bottom bar: ring + status, quick actions (**Lock now** / **End the lock**, with a confirmation), three tiles (Apps, Schedule, Settings), today/week cards. **Permissions** moved into Settings (red warning card on the dashboard if one is missing). Back goes Permissions → Settings → dashboard. The PIN session now also ends when the screen turns off. |
| `e6a3f23` | **Language switcher** (Settings in child mode, and the parent home): Phone language / العربية / English. Android 13+ uses the system per-app language; older phones store it and apply it to the app, the lock overlay and notifications. The layout direction follows the chosen language. |

Tested on the emulators: Forgot PIN from app and overlay (api31); dashboard actions, tiles, Back
(api31); English on api31 (app, overlay, notification, LTR) and back to Arabic; English/Arabic on api35
parent (`cmd locale get-app-locales` shows `[en]`/`[ar]`). The api31 child is in a running lock (Lock now
test); api35 is back to Arabic.

### To test on the Honor (Android 12, the "older phone" language path)
1. Install the APK over the current one (setup and PIN are kept).
2. Dashboard: tap **Lock now** → Chrome shows Time's up; tap **End the lock** → Chrome opens.
3. Settings tile → Permissions (all green) → Back → Back.
4. Settings → Language → English: app, notification and Time's up screen should be English and left-to-right. Switch back to Phone language.
5. Time's up screen → For parents → **Forgot the PIN?** → answer → new PIN twice → the new PIN works.
6. Unlock the app, lock the screen, unlock the phone, open Nour Time: it must ask for the PIN again.

### Still open (after §11)
- Owner's Honor test of §11 (dashboard, quick actions, Permissions in Settings, language, Forgot PIN,
  PIN after screen off) and of the U1 time-picker styles (the owner tried them; "Surprise me" picks a
  different style each time, as designed).
- Phase 2: expired pairing code on a real phone, and the Honor as the child while paired.
- Phase 1.5 "educational content during lock" still needs the owner's decisions (§6).
- Device tests on Samsung and Xiaomi; release items (upload key, screenshots, account-deletion page).
- Emulator state: api31 (5556, child, PIN 4827 / blue) has a lock running from the Lock now test;
  api35 (5554, parent, eslamy319 signed in) is back to Arabic. After an emulator reboot, re-apply the
  DNS iptables fix (see memory / §9). Never `emu kill`.

## 12. Learning Hub (2026-09-30, overnight)

The owner asked for "Phase 3: Gamification & Education": a Learning Hub on the Time's up screen whose
games earn screen time. Plan and architecture: [`progress.md` → Learning Hub](progress.md#learning-hub-gamification--education-planned-and-built-2026-09-30).
Everything is on branch **`learning-hub`** (not merged into master).

### What's built
- **Time's up screen:** a coral **Play & earn time** button (time-up locks only, and only if the parent
  allows it; never at bedtime or on protected Settings screens).
- **Hub menu:** four games, the minute bank, **Use my minutes**.
- **Smart Math** (12 levels): +, −, ×, ÷ and < = >; dots to count on level 1; tap a number to hear it;
  123 / ١٢٣ toggle (Arabic defaults to ١٢٣). With ١٢٣ equations are written right to left like
  Arabic schoolbooks, and < / > are mirrored so the sign still opens toward the bigger number (owner's
  decision 2026-09-30); with 123 they are left to right.
- **Letters & Words** (7 levels, Arabic or English, chosen in the game): letter → picture, letter →
  word then picture (the owner's "A, Apple" flow), word → picture, color name ↔ color. Read aloud with
  the phone's text-to-speech (letter name + word: "ألف، أرنب").
- **Number Connect** (8 drawings): drag dot to dot over a faded outline, lines then curves; each dot
  says its number.
- **Coloring Match** (6 pictures): pick a color, tap a region; the reference is the same drawing in
  color; extra (wrong) colors from level 3.
- **Tutorials:** the first time each game opens, a hand shows what to do (not scored).
- **Stars:** multiple choice by first-try answers (3 ≥ 90 %, 2 ≥ 70 %); drawing games by mistakes
  (≤ 1 → 3, ≤ 3 → 2). **Only 2+ stars earn minutes.**
- **Rewards (decisions for the owner to confirm):**
  1. Earned minutes are a **break inside the lock** (`TimeRules.reward`, not `TimerCommand.Bonus`):
     the lock keeps counting down meanwhile and **resumes with what's left**; a parent's bonus instead
     ends the lock and a full new one follows, which would punish a child who earns 5 minutes.
  2. Minutes are **banked**; the child taps **Use my minutes** when ready.
  3. Defaults: on, **5 min per won level, at most 15 min a day** (parent: Settings → Learning Hub,
     with a **Try the games** preview that changes nothing).
- **Survives Accessibility reconnects:** the hub lives in `LockOverlay`, not the window (Honor
  reconnects the service by itself; before this fix the hub would have closed mid-game).
- Whole-phone lock: the automatic screen-off waits while the child is in the hub.

### Tested (API 31 emulator, child mode, Arabic, girl 3–6, real time-up lock over Chrome)
- Math level 1 with the tutorial (wrong tap shakes, right tap advances), 3 stars, **+5 min**;
  **Use my minutes** opened Chrome; after exactly 5 min of use the lock **came back with ~4.5 h left
  (not a fresh 6 h)**; timer state checked in DataStore (`timer_lock_pending`).
- Letters in Arabic (ذ → 🌽, Arabic voice synthesised) and English (C → 🐱, English voice).
- Number Connect: triangle (tutorial), house (with a wrong drag), fish (curves); **daily max
  reached** message after 15 min.
- Coloring: all 6 pictures completed (a wrong fill in the tutorial, palettes of 3–6 colors).
- Parent preview from Settings: all levels open, "no minutes are earned here".
- System Back steps back through the hub, then to the Time's up screen.
- Unit tests: timer reward rules (12), game engine (16), drawing games (12), repository (7).

- App in **English**: Time's up button, hub menu (daily-max message), Math level 1 left to right with
  Western digits; switched back to Arabic afterwards.

Not tested: whole-phone lock mode with the hub (the screen-off delay) was checked in code only.

APK (real Firebase project, debug): `dist/NourTime-2026-09-30-learning-hub-debug.apk`.

### Please test on the Honor
1. Install the APK over the current one. Settings → **Learning Hub**: switch on, 5 min, 15 min.
   Tap **Try the games** and play one level of each game.
2. Check that the **Arabic voice** speaks (Letters level 1). If it's silent, Honor's TTS engine may lack
   Arabic: install "Speech Services by Google" and pick it in Settings → Accessibility →
   Text-to-speech. Tell me what the phone has; the games stay usable silently.
3. **Lock now** from the dashboard, open a limited app → **Play & earn time** → win a Math level →
   **Use my minutes** → the app opens; after the minutes, the lock returns with the rest of the lock
   time (Home shows it).

### Open questions for the owner
- Confirm the three reward decisions above (break vs. bonus, bank, defaults 5 / 15).
- Pictures are emoji and code-drawn shapes: fine for now, or should we plan illustrations?
- Syncing the Learning Hub settings and "minutes earned today" to the parent's phone isn't built yet.

### Known limitations
- Number Connect and Coloring are touch-only (no TalkBack alternative yet); Math and Letters are
  fully accessible (every choice has a description).
- Levels are fixed content in Kotlin; no videos yet (the owner's "later").

## 13. Content as data (2026-09-30, evening)

On `learning-hub`. All Learning Hub content moved from Kotlin into JSON packs under
`app/src/main/assets/learning/` (guide: [`content-packs.md`](content-packs.md)); the nine new games
are on the roadmap in `progress.md`. Also: math with ١٢٣ is written right to left (owner's decision).

- Nothing changed for the child except: **stars earned before this change are reset** (progress is
  now saved per level id under new keys; the old per-position progress was never released).
- Tests: 341 unit tests, including every shipped pack checked item by item and a 10,000-word pack
  parsed and checked in 72 ms (JVM).
- Verified on the API 31 emulator with a **minified release build** (signed with the debug key):
  lock screen → hub; Math levels from JSON with right-to-left equations and mirrored < >;
  Letters (7 levels), Number Connect (cat with curves), Coloring (car with 2 extra colors).
- Bug found and fixed: a level whose pack had too little content (e.g. a missing language) looped
  forever while making questions; it now returns no questions and the game stays on its level screen.

## 14. Resume here (2026-09-30, end of day)

**Branch `learning-hub`** (not merged into master; master is unchanged since §11). Commits of the day:
reward rule (earned minutes pause the lock), the four games, UI and TTS, Number Connect and Coloring,
right-to-left math with ١٢٣, content moved to JSON packs, docs. 341 unit tests pass; the minified
release build runs on the API 31 emulator.

**Owner's decisions today**
- Earned minutes are a break inside the lock (`TimeRules.reward`); banked; defaults 5 min per level,
  15 min a day (owner hasn't objected; still listed in §12 to confirm).
- Anything Arabic is strictly right to left (math with ١٢٣ done).
- Emoji and code-drawn shapes are placeholders: real illustrations are planned.
- The app is heading for **Google Play**: content must scale to thousands of items in several
  languages. Content is now data (`app/src/main/assets/learning/`, guide `content-packs.md`).
- Nine new game ideas are on the roadmap (`progress.md`), **not started**.

**Next step (owner):** parent app integration: the parent's phone shows and changes the Learning Hub
settings (on/off, minutes per level, daily max) and sees the child's minutes (earned today, bank).
Ask before starting; the owner said "next session".

**Open items**
- Owner's Honor test of the Learning Hub (§12 list), including whether the Arabic TTS voice speaks.
- Merge `learning-hub` into master when the owner agrees.
- GitHub: `origin` = https://github.com/Eslamfoka/nourtime (master and learning-hub pushed 2026-09-30). The repository is **public** for now; the owner will make it private.
- Emulators: run only **one** at a time (16 GB PC; two emulators were killed for memory). The API 31
  emulator (child, PIN 4827 / answer blue) has the release build installed; after a restart it comes
  back from its old snapshot, so reinstall the APK (`./gradlew :app:assembleDebug
  -Pnourtime.firebaseEmulator=false` with `app/src/debug/google-services.json` moved aside, see §9).


## 15. Night of 2026-09-30 → 10-01 (owner asleep): more content, then new games

**Morning summary (read this first).** Everything is on `learning-hub` (not merged, not pushed),
one commit per task. 382 unit tests pass, lint clean.

| Task | Result | Commit |
|---|---|---|
| Smart Math | 12 → 45 levels, missing numbers, up to 1000 and ×12 | `05e82e3` |
| Letters & Words | 7 → 27 levels, 58 → 176 words, picture → word reading | `3e57900` |
| Number Connect | 8 → 33 drawings (up to 16 dots) | `183a42f` |
| Coloring Match | 6 → 26 pictures, two-row palette, canvas fits | `b6e6db6` |
| G1 Listen & Find | new, 20 levels, + game registry | `a926596` |
| G2 What Comes Next? | new, 23 levels | `f2f5d33` |
| G3 Tell the Time | new, 17 levels | `a2c2a7c` |
| G4 Letter Tracing | new, 28 Arabic + 26 English letters | `b8f7ee4` |
| G5 Memory Match | new, 20 levels | `a09b33a` |
| G6 Word Builder | new, 11 levels | `0b5de2e` |
| G7 Sorting | new, 13 levels (+ RTL drag fix for G6) | `3a765f7` |
| G8 Little Shop | new, 10 levels | `ff4a901` |
| G9 surahs & du'as | **not started**: needs your decision on sources and licensing | — |

**APK to install:** `dist/NourTime-2026-10-01-learning-hub-debug.apk` (real Firebase project, debug-signed
like before, installs over the Honor build: `adb install -r dist/NourTime-2026-10-01-learning-hub-debug.apk`).
Open Settings → "جرّب الألعاب" to try all 12 games with every level open (the preview saves nothing).

**Please check on the Honor:** (1) the voice actually speaks in Listen & Find (the emulator can't be
heard); (2) Letter Tracing shapes (simplified school forms, authored by me) and how tracing feels on
a real finger; (3) a quick flick in Number Connect; (4) Memory Match: a mismatch turns back after
1.2 s (only unit-tested; every turn on the emulator happened to match); (5) the menu with 12 tiles.

**Decisions for you:** the coins in Little Shop have no currency (values only); Letter Tracing shapes
may need a calligrapher's look.

**Release check:** a minified release build (R8, signed with the local debug key through the
`NOURTIME_KEYSTORE*` variables) was installed on the API 31 emulator at the end of the night: the hub
opened, every pack loaded (no `LearningContent` warnings, no crash in logcat) and Little Shop's 10
levels were listed. (The emulator was later shut down for low memory; it restarts from its old
snapshot, so reinstall an APK before testing on it.)

Owner's plan for the night: (1) many more levels for each of the four games, easy → hard, one game at a
time; (2) then build the roadmap games in order (Phase A first), each with its own engine, levels,
test, commit and docs. Everything is on **`learning-hub`** (not merged, not pushed). G9 (surahs and
du'as) is skipped: it needs the owner's decision on sources and licensing.

### 15.1 Smart Math: 12 → 45 levels
- Levels go from "1 + 2 with dots" (2 choices) through within 5, 7, 10, 20 (with and without crossing
  ten), 50, 100 and 1000, times tables in steps (×2, ×2–5, ×3–4, ×6–7, ×8–9, up to ×12), division,
  **missing numbers** (`7 + ? = 12`, `15 − ? = 9`, `4 × ? = 28`) and a 10-question **champion** level
  with every operation. Old level ids are kept, so no stars are lost.
- Engine: `min` (smallest number added or taken away, so "within 20" is no longer 1 + 1), ops
  `missing_add`, `missing_sub`, `missing_mul`; from 20 up, some wrong answers are 10 away so the last
  digit doesn't give the answer away; long equations get a smaller font so `999 + 999 = ?` fits.
- Tested: unit tests (every level × 40 seeds: one right answer, in range, `min` respected); API 31
  emulator in the parent preview: 45 levels listed, level 43 `496 + ؟ = 886` right to left with ١٢٣,
  fits the width, right answer accepted.

### 15.2 Letters & Words: 7 → 27 levels, 58 → 176 words
- 118 new words in Arabic and English (animals, food, vehicles, body, nature, objects, and new
  **clothes** and **toys** categories, plus people and places), picked from emoji that Android 9+ draws
  (the three that need Android 10–11 were left out).
- New task **picture → word** (reading: see the picture, pick its written word; nothing is read aloud),
  used by the `read-…` levels.
- Levels: listen-and-find with 2 pictures by category for the youngest, then 3 and 4 choices,
  letters, colors, reading, and a 10-question **champion** level. 7–9 year olds now start at
  "all letters", 10–12 at "letter → word → picture" (unchanged). Old level ids kept.
- Two Arabic instructions were masculine imperatives ("اختر …"); now neutral questions
  ("أين الصورة المناسبة؟", "أين هذا اللون؟") so they fit boys and girls.
- Tested: unit tests (every level × 30 seeds × both languages: one right answer, enough words per
  category); emulator preview: 27 levels, level 26 picture → word (👵 → Grandma) accepted.

### 15.3 Number Connect: 8 → 33 drawings
- 25 new drawings, ordered by dot count: square, diamond, balloon, envelope, snake (open wave), bell,
  ice cream, umbrella, lightning, tent, cup, cloud, flower, arrow, crown, mountains, tree, rocket,
  butterfly, snail (open spiral), car, mosque, pine, castle and a 16-point sun. Curves are used for
  round parts (balloon, bell, cloud, petals, wheels, dome, door arch).
- Every drawing was generated and checked by a script (inside the canvas, dots ≥ 0.13 apart) and
  eyeballed on a contact sheet before going into the pack. New test: each drawing is never more than
  one dot easier than the one before, and the last has ≥ 15 dots. Start levels: 7–9 at the
  lightning, 10–12 at the arrow.
- While testing, a few injected emulator swipes didn't draw a line; the same fast swipe worked when
  repeated, so the cause looks like emulator input, **not confirmed as an app bug**. As a precaution the
  circle a drag may start in is now 1.5 × the touch radius (the drag starts after the touch slop, a
  little way from the dot). Worth watching on the Honor: does a quick flick ever fail to draw?
- Tested: emulator preview, level 32 (castle, 16 dots with a curved door) drawn to the end, filled and
  celebrated ("رائعة! 🏰").

### 15.4 Coloring Match: 6 → 26 pictures
- 20 new pictures in a 100 × 100 viewBox, built from boxes, ovals and polygons by a script and checked
  on a rendered contact sheet: balloon, ball, lollipop, mushroom, sun (8 rays), ice cream, cat,
  butterfly, watermelon, traffic light, bee, snowman, sailboat, bus, owl, rocket, **rainbow** (6 bands),
  castle, train, mosque. Difficulty grows from 2 colors / 2 regions to 6–7 colors with 2 wrong colors
  in the palette. Small parts (eyes, seeds, hubcaps) are at least ~8 % of the canvas so a child's
  finger can hit them. Start levels: 7–9 at the house, 10–12 at the butterfly.
- UI fixes found on the emulator:
  - The palette was one row; with 8–9 colors the swatches shrank to ~30 dp. It now wraps into two even
    rows of 52 dp swatches when there are more than 5.
  - The square canvas took the full width even when less height was left, so it slid under the palette
    and header (seen with the two-row palette). The drawing area is now the largest square that fits
    in both directions (Coloring and Number Connect).
- Tested: unit tests (every region tappable, palettes hold the picture colors, later pictures use
  more colors); emulator preview: level 23 rainbow colored band by band, finished and celebrated.

### 15.5 New game G1: Listen & Find (Phase A)
- **Game registry first** (the roadmap's first step): `GameRegistry.kt` now holds each game's tile,
  level-screen option (numerals / words language / none), pack and how a level starts. The menu,
  level picker, question screen and controller read it instead of `when (game)` blocks. A test checks
  every `GameId` is registered exactly once. The menu keeps tile size with an odd number of games.
- **Engine:** `ListenGame` makes choice questions whose prompt is a new `Card.Sound` (a big speaker
  button). It's read aloud when the question appears and on tap; tasks `listen_to_picture`,
  `listen_to_color`, `listen_to_letter`, `listen_to_number`. Without a TTS voice for the language the
  word/number is shown written, so the level stays playable. Single-letter choices are drawn large.
- **Content:** `listen/levels.json`, 20 levels: 2 pictures of animals/food, colors, numbers to 5 for
  the youngest; then 3 and 4 choices, letters, numbers to 20 and 100, and a mixed champion level.
  7–9 start at the letters, 10–12 at numbers to 20. Uses the same words as Letters & Words, in the
  language picked on its level screen.
- Arabic title/hint are gender-neutral: "الاستماع والاختيار" / "نسمع الكلمة ونجد صورتها".
- Tested: 351 unit tests pass (every listen level × 30 seeds × both languages: the spoken thing is the
  one right choice); emulator preview in Arabic: 5 tiles, level 1 played to the stars screen, level 15
  (letters, 4 choices) shown. **Not verified:** that the emulator actually played sound (no audio
  check here); please listen on the Honor.

### 15.6 New game G2: What Comes Next? (Phase A)
- **Engine:** `PatternGame` makes choice questions from a pattern level: repeating units (`ab`, `aab`,
  `abb`, `abc`, `aabb`, `abbc`, `abcd`) of colors, plain shapes or pictures, and number sequences
  (`step` with any steps, negative counts down, or `double`). Wrong choices are the pattern's own
  other items first. Shapes are silent (a voice would read the symbol's name); white never appears
  as a color. A new task `PATTERN` ("What comes next?" / "ماذا يأتي بعد ذلك؟").
- **UI:** a compact prompt row for patterns (40 dp items, 6 dp gaps) so 7 items fit a 360 dp phone.
  Number patterns follow the numerals like math (right to left with ١٢٣). Bug found on the emulator
  and fixed: with the tight gaps "١ ٢ ٤ ٨ ١٦" read as one long number; each number now sits on its
  own chip.
- **Content:** `patterns/levels.json`, 23 levels: red/green `ab` with 2 choices, shapes and pictures,
  counting to 6 → `aab/abb` → `abc` → counting down → twos → `aabb` → fives and tens → `abcd` →
  steps of 3–4 → doubling → a 10-question champion (steps of 9, 11, −6, ±25 and doubling up to 200).
  7–9 start at counting down, 10–12 at fives and tens. Menu title "ماذا بعد؟", hint "نكمل النمط".
- Tested: unit tests (every level × 40 seeds × both languages: exactly the answer continues the
  pattern and no wrong choice fits any rule of the level; loader rejects bad kinds, rules, zero steps,
  too-small max); emulator preview in Arabic: level 1 (colors, RTL order, hand on the right answer),
  level 23 (doubling with chips).
- Test tooling note (for whoever drives the emulator next): `uiautomator dump` sometimes fails while
  something animates and leaves the old file behind, and on the scrolled parent home its positions
  lagged behind the screen. The helper now deletes the old dump and retries; the parent home is
  scrolled to the top before tapping. One wrong PIN attempt was typed by the old script (lock ended
  → the security-question taps landed on the PIN pad); the right PIN was entered right after.

### 15.7 New game G3: Tell the Time (Phase A)
- **Engine:** `ClockGame` with two new cards, `Card.Clock` (analog clock) and `Card.Time` ("h:mm"),
  and tasks `clock_to_time` / `time_to_clock`. Times are generated per level precision (hour, half,
  quarter, five, minute), different within a level. Wrong answers mix classic mistakes (the long hand
  read as the hour: 3:30 → 6:15; the next/previous hour; a step off) with random times.
- **Clock face** (`ClockFace.kt`): drawn in Compose, numbers in the child's numerals, minute ticks,
  never mirrored (clocks turn clockwise in Arabic too). Found on the emulator: the long hand hid the
  numbers it passed; shortening the hands then made minute-exact levels unreadable. Final design: the
  minute hand reaches the ticks and each number sits on a small white disc drawn above the hands.
- **Content:** `clock/levels.json`, 17 levels: o'clock with 2 choices → halves → quarters → 5 minutes
  → any minute, each first as "read", then "show", then mixed with 4 choices; a 10-question
  champion. 7–9 start at halves, 10–12 at quarters. Menu "ما الساعة؟" / "نقرأ الساعة".
- Tested: unit tests (every level × 40 seeds: one right time, distinct valid times, the hand mix-up
  is among the wrong answers, precision never gets easier level to level); emulator preview: level 6
  (read 2:30 with 4 time choices, then "show 4:00" with 4 clocks), level 17 (minute precision).

### 15.8 New game G4: Letter Tracing (Phase B)
- **Engine:** `Tracing.kt`: a letter is strokes (lines resampled every 0.02 of the canvas, or dots to
  tap). `TraceRound` advances to the path point **nearest** the finger within a short look-ahead
  window, so a stroke must be traced from its start, in its direction, without jumping ahead;
  straying beyond 1.7 × the tolerance is a mistake and ends the drag; lifting the finger keeps the
  progress. Stars from mistakes like the other drawing games; wider tolerance for ages 3–6.
- Found by the new test that traces every shipped letter: with "furthest point within tolerance",
  ج (its top bar turns sharply back on itself) skipped the corner and counted 5 mistakes. Now the
  nearest point wins, the earlier one on a tie.
- **Screen:** the letter as a thick faded guide, traced parts in the game color, a pulsing green start
  dot with an arrow for the direction, stroke numbers at each start, a tutorial hand the first time.
  The gesture uses the finger's **down** position (a quick finger crosses the touch slop far from where
  it went down). Messages are gendered in Arabic (اكتب/اكتبي، ابقَ/ابقَيْ على الخط …).
- **Content:** `tracing/ar.json` (28 letters: ا ب ت ث د ذ ر ز ن ل و ي ف ق ك م ه ج ح خ ع غ س ش ص ض ط ظ,
  simple shapes first, families together, dots after the body) and `tracing/en.json` (26 capitals:
  L I T H E F V X N Z K Y A M W, then the curved C O U J D P B R G Q S). Strokes were authored in a
  script and checked over the real font glyph on a contact sheet. Letter shapes are simplified
  school forms, **worth a look by the owner** (or a calligrapher) before release.
- Also: a game's level pack can now depend on the words language (`HubContent.levels(game, language)`),
  since tracing has one pack per language.

### 15.9 New game G5: Memory Match (Phase B)
- **Engine:** `Memory.kt`: `MemoryGame.deal` puts out the level's pairs from the words language pack
  (same picture, word ↔ picture, letter ↔ picture, number ↔ a new `Card.Dots`, color ↔ name), never
  two cards that show the same thing. `MemoryRound`: turn two, a pair stays, a mismatch waits and
  the controller turns it back after 1.2 s; moves → stars (3 within 1.5 moves a pair, 2 within 2.5).
- **Screen:** a grid (rows of 2/3/4) of cards that flip in 3D, sized to fit; turned cards say their
  word, letter name, number or color. Moves counter. Arabic messages gendered.
- **Content:** `memory/levels.json`, 20 levels: 2 pairs of animals → same pictures up to 8 pairs →
  number ↔ dots, colors, words, letters with 3–8 pairs → mixed tables → an 8-pair champion. 7–9 start
  at words, 10–12 at letters.
- Tested: unit tests (every level × 30 seeds × both languages: each pair belongs together and no card
  could match another pair; turn/mismatch/hide/match/done; stars; loader errors); emulator preview:
  level 16 (mixed, 8 cards, 4 × 2): flips, spoken faces, fox ↔ ث, red ↔ أحمر, zebra ↔ حمار وحشي,
  3 stars. Every turn there happened to match, so the mismatch turning back was **only checked by
  the unit tests**, not seen on screen.

### 15.10 New game G6: Word Builder (Phase C)
- **Engine:** `WordBuilder.kt`: a level deals N words of the right length/categories from the words
  language pack (never words with a space); each word's tiles are its letters plus wrong ones.
  `WordRound`: the next letter in reading order goes in, anything else bounces and is a mistake.
  The word is shown as one text as it grows, so **Arabic letters join as they're placed**.
- Fairness fix found on the emulator: غزال got a wrong tile "أ" next to its "ا". Wrong tiles now
  skip look-alikes of the word's letters (أ إ آ ٱ ↔ ا, ة ↔ ه, ى ↔ ي, upper ↔ lower case).
- **Screen:** the picture (tap = hear the word), the faded word as a hint on the first levels, the
  word so far, one dot per letter, and the tiles: tap one, or drag it up onto the word (the tile
  engine G7/G8 can reuse). Wrong tiles shake with "ليس هذا. أي حرف يأتي بعده؟". A finished word is
  read out and shown for 1.8 s.
- **Content:** `words/levels.json`, 11 levels: 2–3 letters with the word shown faded → animals and
  food → 3–4 letters with a wrong tile → 5, 6 and 6–8 letters with 2–3 wrong tiles → a 6-word
  champion with 4 wrong tiles. 7–9 start at "short", 10–12 at "five".
- Tested: unit tests (every level × 20 seeds × both languages: enough words, each word spelled to the
  end without mistakes, wrong tiles never look-alikes; wrong tile/next word/ignored old tiles; loader);
  emulator preview in Arabic, level 6: فراشة spelled with taps and one drag (ف → فر → … → فراشة in
  green), then غزال with a wrong tile (shake + message).

### 15.11 New game G7: Sorting (Phase C)
- **Engine:** `Sorting.kt`: a level has 2–3 groups, each a word category (pictures) or even/odd
  (numbers 1..max), with a sign and a name per language from the pack. `SortGame.deal` spreads the
  things evenly and shuffles; `SortRound`: the right group counts the thing, a wrong one sends it back
  as a mistake.
- **Screen:** one thing in the middle, the groups as big boxes below with their count; drag the thing
  onto a box (hit-tested against the boxes' positions on screen) or tap a box. The group's name is
  read when a thing goes in.
- **Bug found on the emulator and fixed (also affected G6's tiles):** in Arabic the dragged card moved
  mirrored to the finger and landed in the other box. `Modifier.offset {}` is mirrored in RTL
  layouts; dragged things now use `absoluteOffset`. Re-tested: bee dropped on "طعام" → "ليست هذه
  المجموعة"; dropped on "حيوانات" → counted.
- **Content:** `sorting/levels.json`, 13 levels: animals/food (4 things) → vehicles, clothes, nature,
  things → even/odd to 10, 20, 100, 1000 → three groups → a 12-thing champion. 7–9 start at even/odd
  to 10, 10–12 at even/odd to 20.
- Tested: unit tests (every level × 20 seeds × both languages: every thing in its right group, each
  group used, nothing twice, labels present; wrong drop; loader); emulator preview (above).

### 15.12 New game G8: Little Shop (Phase C)
- **Engine:** `Shop.kt`: a level has a coin set (must include 1), a price range and modes `pay` /
  `change` (the price was paid with a note; give the change). `ShopRound`: coins go on the counter;
  exactly the amount finishes the thing, going over is a mistake and the coins come back; a coin on
  the counter can be taken back freely. Things to buy come from food/toys/objects/clothes words.
- **Screen:** the thing with a price tag, the counter with its total, and the purse (silver coins below
  10, gold from 10, bigger for bigger values), coin values spoken in the app language.
- **Coins have no currency sign** (values only), so the game fits any country. Worth a decision by
  the owner: show a currency (e.g. from the phone's locale) or real coin pictures later.
- **Content:** `shop/levels.json`, 10 levels: pay up to 5 with ones → up to 10 with 1/2/5 → 20 →
  change from 10 → pay up to 50 → change from 20 → pay up to 100 → change from 50 → mixed to 100 →
  a champion with change from 200. 7–9 start at "pay up to 10", 10–12 at "change from 10".
- Tested: unit tests (every level × 20 seeds × both languages: every amount payable with the fewest
  coins and no mistakes; going over, taking back, coins not in the purse; loader); emulator preview in
  Arabic, level 4: balloon 4 paid with 10 → 5+5 went over ("أكثر من اللازم! عادت القطع…"), 5+1 = 6
  accepted.

### 15.13 G9 (short surahs and du'as): not started
Needs the owner's decision on sources and licensing of the recitations and texts (roadmap Phase D).

### 15.14 Optimized release APK for the Honor (2026-10-01 morning)
- Owner's decisions: G9 on hold (audio sources/licensing later); Little Shop money is the **Kuwaiti
  dinar** (prices and totals show "٤ د.ك" / "4 KD", coins stay plain numbered coins); the GitHub repo is
  private; **no merge** until the owner has tested the release APK on the Honor (Listen & Find audio,
  tracing shapes, Memory Match cards), then merge and start the Parent App integration.
- Size: R8 (optimize) and resource shrinking were already on. Added: only English and Arabic
  resources (`resourceConfigurations`, the libraries' ~80 other translations dropped) and excluded
  debug/build metadata (`DebugProbesKt.bin`, `kotlin-tooling-metadata.json`, `META-INF/*.version`).
  Release APK 4.1 MB → **3.58 MB**. Native libraries were already stripped (59 KB, all ABIs).
- **APK:** `dist/NourTime-2026-10-01-release.apk`, real Firebase project, signed with the same debug
  key as the Honor builds (SHA-1 CA:D6:D5:…), so `adb install -r` goes over the debug build, keeps the
  data, and Google sign-in still matches. Release builds have no "End budget / End lock" debug buttons.
- Not re-run after these last changes: the unit tests and an emulator run (the emulator was stopped
  for low memory). The build itself succeeded and the APK lists both locales.

### 15.15 Resume here (2026-10-01 morning)
- **Installed on the owner's Honor** (AAYSNU2712209663, 05:51): `dist/NourTime-2026-10-01-release.apk`,
  the optimized release build (not debuggable), over the previous build with data kept. The app was
  not opened by me.
- **Waiting on the owner:** his Honor test of the release build: Listen & Find audio, Letter Tracing
  shapes and feel, Memory Match mismatch turning back, Little Shop د.ك labels, overall size/speed.
- **Next, once he's happy:** merge `learning-hub` into master, then the **Parent App integration**
  (parent's phone shows/changes the Learning Hub settings and sees the child's minutes).
- On hold: G9 (surahs & du'as) until the owner sorts audio sources and licensing.
- Branch `learning-hub` is committed locally, not pushed (repo is private now). The API 31 emulator is
  off (stopped for low memory); it restarts from an old snapshot, so reinstall an APK before using it.

## 16. Night of 2026-10-02: text fix, content audit, recorded voices (owner asleep)

Branch `learning-hub` (**not merged**; owner's rule: never auto-merge this rewrite). Owner's decisions
(2026-10-02): edge-tts Microsoft neural voices now, a paid Azure key later if Play objects; Arabic is
**Fusha with tashkeel** (no dialect); the phone's TTS is **only a fallback** for a missing file; stay
on `learning-hub`, push, optimized release APK for the morning.

### 16.1 Task 1: words breaking and overlapping on the cards (owner's screenshots)
- Cause: the prompt word was 96 sp with no width limit, so long words wrapped, and the theme's
  `bodyLarge` line height is a fixed **25 sp**, so the wrapped line was drawn on top of the first
  ("Peaco/ck"; عنكبوت and أفوكادو with letters split and overlapping).
- Fix: `FitText` (feature/learning/FitText.kt) keeps a content word on **one line**, shrinks it
  (2 sp steps, measured with the real font) until it fits, and ties the line height to the font size.
  Used on the prompt card, choice cards, Memory cards, Word Builder (word and hint) and the
  spoken-word fallback text. Tested on the API 31 emulator: سلطعون and Spider fill the card on one line.

### 16.2 Task 2: content audit (every word, letter, color, label; Arabic and English)
Fixed (ids unchanged, so saved stars stay):
| Was | Now | Why |
|---|---|---|
| 🦅 Falcon / صقر | Eagle / نسر | the emoji is an eagle; ص now uses صابون |
| رز | أرز | رز is colloquial; ر now uses رضيع (أرز starts with أ) |
| ✏️ قلم | قلم رصاص | قلم alone is a pen |
| 🦌 غزال | أيل | غزال is a gazelle |
| 💡 Lamp / مصباح | Light bulb / مصباح كهربائي | the emoji is a bulb |
| دبدوب | دمية دب | colloquial |
| English alphabet: 25 letters (no X) | X = Xylophone (new concept, own illustration `images/xylophone.webp`) | missing letter |
| Sorting ناس, جسم | أشخاص, الجسم | Fusha / label form |
| اربح مرحلة لتكسب دقائق | أنهِ مرحلة لتكسب دقائق (and feminine) | "finish", not "win" a level |
Checked and kept: all 28 Arabic letter names, the other 170 words, 11 colors in both languages, all UI
strings of the 12 games (masculine/feminine), math/shop/pattern numbers (all generated, test-checked).
Kept on purpose (tell me if you want them changed): آيس كريم (common in MSA), بسكويت, Yacht for Y.

### 16.3 Task 3: recorded human-sounding voices instead of the phone's TTS
- **884 clips** (498 Arabic, 386 English), **4.6 MB**, in `app/src/main/assets/audio/<ar|en>/<sha1-16>.mp3`:
  every word, letter name, "letter، word" phrase (ألف، أرنب / A, Apple), Word Builder letter tile,
  color, sorting label, and numbers. 0–100 are whole clips; 101–1000 play **two clips with no gap**
  (`MediaPlayer.setNextMediaPlayer`): "three hundred" + "forty-five", ثلاثمئة + وخمسة وأربعون.
- Voices: **ar-SA-ZariyahNeural** (Fusha; no Arabic child voices exist) with full tashkeel in pause
  form from `tools/audio/ar_tashkeel.json` (e.g. ذُرَة "corn", never ذَرَّة "atom"; numbers with correct
  case endings: وَاحِدٌ وَعِشْرُون, اثْنَا عَشَر, ثَمَانِمِئَة), and **en-US-AnaNeural**, a child's voice
  (owner's pick from the voice samples, 2026-10-02; was en-GB-MaisieNeural). Clips are trimmed, loudness-matched, mono 40 kbps MP3
  (MP3 because it decodes on every Android phone, per the all-brands rule).
- `SpeechCatalog` (core) lists every clip from the packs; **`SpeechCatalogTest` fails if any clip has no
  file** (passing now) and writes the lists that `tools/audio/generate_audio.py` reads. The script
  refuses to record Arabic without tashkeel. How to add content: `docs/content-packs.md` →
  "Voice recordings".
- `Speaker.kt`: `RecordedSpeaker` plays the clips; the phone's TTS (`TtsSpeaker`) is started only if a
  text has no clip (logs `no recording for …`). Verified on the emulator (debug and minified release):
  Letters (Arabic + English), Math 335 (two chained clips, one continuous audio track), Word Builder
  tiles; no fallback, no errors.
- `tools/audio/verify_audio.py` runs speech recognition (faster-whisper) over every clip and lists the
  ones it hears differently (see 16.4).
- Settings hint text updated: "recorded voices; the phone's voice only for a word with no recording".
- APK size: 3.58 MB → **8.55 MB** (the recordings).

### 16.4 Voice check with speech recognition, and one open question for the owner
- `verify_audio.py` (Whisper "small") over the clips: **Arabic: 495 of 498 heard as written.** The 3
  flagged: ي / ياء heard as "yeah" (that is how ياء sounds, fine) and **وشاح** heard as "وشفل":
  please listen to it (Letters & Words, clothes level).
- **English (en-GB-MaisieNeural, the child voice): Whisper misheard many short words** (Dog → "Talk",
  Bed → "That", Book → "Duck"). I checked that it isn't my processing: the **raw** Maisie audio is
  misheard the same way. Whisper is known to be weak on children's voices, but I can't listen myself,
  so **please listen to a few English words** in the app. If Maisie isn't clear enough, switch to
  en-US-AnaNeural (the other child voice) or an adult voice: change `VOICES["en"]` in
  `tools/audio/generate_audio.py`, run it with `--force`, rebuild.
- `tools/audio/voice_samples.py` records the same words in 4 English and 4 Arabic voices into
  `dist/voice-samples/` for picking by ear. **It didn't run tonight**: the Microsoft voice service
  (speech.platform.bing.com) and GitHub kept timing out from this PC after ~16:30. Run it when online.
- **APK for the Honor:** `dist/NourTime-2026-10-02-release.apk` (8.55 MB, minified, signed with the
  same debug key CA:D6:D5:…, so `adb install -r` keeps the data). Not installed on the Honor (owner's
  phone, he's asleep). Tested on the API 31 emulator (it stopped at the 2-hour background limit; it restarts from its old snapshot, so reinstall the APK before using it).
- Unit tests: **391 passing** (FitTextTest, SpeechCatalogTest including "every clip has its recording").
- Pushed: `learning-hub` on GitHub. **Not merged** into master (owner's rule for this night).

### 16.5 Resume here (2026-10-02)
1. Owner installs `dist/NourTime-2026-10-02-release.apk` on the Honor and checks: long words on cards
   (Letters & Words, Memory, Word Builder), the Arabic voice (Fusha, tashkeel), the English child
   voice (16.4), وشاح, big numbers in Math (e.g. 335 = "ثلاثمئة وخمسة وثلاثون").
2. If he wants another voice: 16.4. If he's happy: merge `learning-hub` → master, then the Parent App
   integration (as planned in 15.15).

### 16.6 English voice switched to Ana (2026-10-02, owner's pick)
After listening to `dist/voice-samples/`, the owner chose **en-US-AnaNeural** for English and kept
**ar-SA-ZariyahNeural** for Arabic. All 386 English clips re-recorded (Arabic unchanged), 391 tests pass,
`dist/NourTime-2026-10-02-release.apk` rebuilt with them.

## 17. Voices: from synthetic to real human recordings (2026-10-02 evening → 2026-10-03)

Branch `learning-hub` (pushed, **not merged**). Owner's verdict after testing on the Honor: UI fixes great;
English Ana "barely acceptable"; **Arabic edge-tts (Zariyah) unacceptable, robotic**. Directive: real
human recordings first; no Google Translate TTS; a premium voice only for what no human recording covers.

### 17.1 What's in the app now
- **Human recordings** from Wikimedia Commons / Lingua Libre (Fusha `(ara)` only, no dialect, no NC/ND
  licences), each checked with speech recognition (`tools/audio/fetch_human_audio.py`):
  **Arabic 123 / 470 clips** (80/175 words, 7/11 colors, 23/209 numbers, 4/28 letter names, 6/35 tiles,
  3/12 labels; ~10 different volunteers), **English 343 / 360** (95 %).
- The rest are still the old synthetic voices as **placeholders** (flagged in `tools/audio/manifest.json`).
  باء (+ the ب tile) is a Gemini "Kore" clip (test of the import path).
- Letter phrases ("ألف، أرنب") now play the name's clip then the word's (`SpeechCatalog.clipsFor`); 830 clips.
- **Settings → Voice credits** (`assets/audio/CREDITS.txt`): attribution required by CC BY / BY-SA.
- APK: `dist/NourTime-2026-10-03-human-voices-release.apk` (392 tests pass). **Not yet installed on the
  Honor; the Honor has `dist/NourTime-2026-10-02-release.apk` (Ana/Zariyah).**

### 17.2 Decisions and limits (said to the owner)
- **Forvo not used** (terms forbid scraping/re-use). **No Google-login automation and no use of AI Studio's
  internal web endpoints with copied cookies** (bypasses Google's limits/ToS; account risk) — declined.
- Gemini API key (owner's) works with `gemini-2.5-flash-preview-tts`, but the project is on the **free
  tier: 10 requests/day, 3/min** (the second key, said to be paid, still reports `FreeTier`). Gemini also
  returns no audio for very short text and for the "وَ + number" parts. Keys live in the git-ignored
  `tools/audio/keys.env`; **both keys were pasted in chat → owner should rotate them**.
- Commons has a one-speaker Arabic alphabet set (42 files, CC BY-SA 4.0): copied to
  `dist/voice-samples/letters-review-commons/` for the owner to identify by ear (labels unreliable,
  recognition can't tell letter names apart). Waiting on the owner.

### 17.3 Current plan: Manus records in AI Studio, I slice and import
- The owner runs **Manus** (his own browser agent) on his logged-in AI Studio app
  (`https://aistudio.google.com/apps/drive/1AI3eea2N05aZkz8vLd9ScNbtiY2eSDwC`), voice **Kore**.
- **Batches**: long recordings of ~25 lines, grouped by category (letters, each word category, colors +
  labels, numbers in order, "و + number" parts). Each line ends with "." and an empty line follows it
  (longest pause). `tools/audio/make_batches.py` (`--test-numbers` for the test batch).
- **Test first (in progress):** `manus_batches/test_numbers_01_25.txt` + `MANUS_PROMPT_TEST.md` (numbers
  1–25). Owner puts the result in `tools/audio/incoming-batches/test_numbers_01_25.wav`.
- Then: `python tools/audio/slice_batches.py` (cuts at pauses, searching the pause length that gives
  exactly one piece per line; checks each piece with Whisper; unsure pieces → `incoming-review/`), then
  `python tools/audio/import_audio.py --replace-human` (for the numbers, so 1–25 share one voice; ask the
  owner) → tests → release APK → install on the Honor.
- If the test is good: `python tools/audio/make_batches.py` for the full grouped set (it overwrites the
  test batch files), update `MANUS_PROMPT.md` file list, owner runs Manus, repeat slice/import.
- Fallbacks: `missing_arabic_audio.csv` (323 unique texts, one file each) + `MANUS_PROMPT.md` (per-clip
  version); `generate_audio.py` uses Gemini or ElevenLabs automatically if a paid key is in keys.env.

### 17.4 Resume here (2026-10-03)
1. Ask the owner for the Manus test file (numbers 1–25) → slice, check, import, APK, Honor.
2. Ask: keep volunteer recordings for 1–5, 8, 10–19, or replace them all for one voice?
3. Ask: results of `letters-review-commons` (which files say which letter name).
4. Then the full batch run. Still open from before: وشاح check, merge only after the owner approves.
