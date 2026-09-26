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
4. **Phase 2 (after Phase 1.5 and your decisions):** Firebase Auth + Firestore + FCM, plus the `INTERNET`
   permission and Data safety updates. Keep Phase 1 fully offline: remote commands (bonus time,
   settings) should be written into the same repositories the local UI uses (`ParentSettingsRepository`,
   a new `bonus` input to `TimeRules`), so the engine and blocking stay unchanged.
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

**Not reproduced:** Honor's exact trigger. A clean disable/enable and a second `onServiceConnected`
on the same instance both work on the emulator. `NourA11y` logs (connect / unbind / destroy, each
window-list change, and a warning whenever the fallback is used) are there for the next Honor run:
`adb logcat -s NourA11y BlockCoordinator LockOverlay TimerService`.

### Task 1: guided onboarding (no auto-granting)
- Android has no API for an app to grant Accessibility or Device admin to itself, and Play's
  Accessibility policy forbids using Accessibility to click through permission screens. Everything
  stays a manual toggle; the work is guidance.
- Per-brand help text and deep links (`OemAutostart.kt` already maps autostart screens): *Allow
  restricted settings* location, autostart, battery ("App launch" on Honor, "Sleeping apps" on
  Samsung, "Autostart" + "No restrictions" on Xiaomi).
- Bring the onboarding back to the front automatically when the permission is detected (the
  Accessibility service's `onServiceConnected` can do this; for the others, poll on resume).
- A last "Test protection" step: open a limited app for a second and confirm it's detected.

### Task 2: educational content during the lock
- **Allow-list (offline):** new `ParentSettings.allowedDuringLock: Set<String>`. In `BlockPolicy`,
  skip `TIME_UP`/`BEDTIME` blocking when everything on screen is in the allow-list (also for the
  whole-phone mode). Whether these apps use the budget outside lock periods is a separate choice.
- **Mini-browser (needs `INTERNET`):** a `WebView` in the lock overlay. Only `https`, a host
  allow-list checked in `shouldOverrideUrlLoading` *and* `shouldInterceptRequest` for top-level
  navigations, no `addJavascriptInterface`, no file or content access, no downloads, no new windows
  / pop-ups, no external intents, cookies cleared on close. A PiP-able video site (YouTube) would
  escape the allow-list through its related videos, so offer curated entries, not free URLs, unless
  the parent insists. Needs privacy policy and Data safety updates.

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
