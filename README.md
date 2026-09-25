# Nour Time – وقت نور

Android parental-control app: one shared time budget for the apps a parent picks, a lock period when
it runs out, and a friendly "Time's up" screen for the child. Everything runs and stays on the device.

The product brief is in [`nour-time-brief-en.md`](nour-time-brief-en.md) (Arabic: [`nour-time-brief-ar.md`](nour-time-brief-ar.md)).

## Build

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug           # lint (no errors expected)
./gradlew :app:assembleRelease     # minified release APK (unsigned; signing not configured yet)
```

Debug builds show a **Detection (debug)** card on Home with what detection sees, and two buttons,
**End budget now** / **End lock now**, to test locking without waiting. They don't exist in release builds.

## How it works

| Piece | Where | What it does |
|---|---|---|
| Foreground detection | `core/detection`, `service/detection` | Accessibility window list (owner package + window type only), including picture-in-picture and split screen. Usage-stats polling fallback while Accessibility is off. Screen on/off/keyguard tracking. |
| Time engine | `core/timer` | Shared budget, only active use counts, lock period, full refill, optional daily reset. Uses `elapsedRealtime` + boot count, so clock changes and reboots can't shorten a lock. State saved every 5 s and on every transition. |
| Trusted clock | `core/time/TrustedClock.kt` | Wall time for bedtime, schedule and daily reset: network time when "automatic time" is on, otherwise anchored to `elapsedRealtime` so manual clock edits are ignored. |
| Blocking | `core/blocking`, `service/blocking` | `BlockPolicy` (pure, unit-tested) decides what to cover; `BlockCoordinator` evaluates every second and on every change; `LockOverlay` shows the child screen as an accessibility overlay (or a "display over other apps" window in fallback). |
| Parent checks | `feature/pin` | PIN with escalating lockout; security question during lock periods; short `ParentPass` after success (ends on screen off). |
| Child screens | `feature/lock` | "Time's up" templates by period (play/study/meal/sleep/default) and age group (3–6, 7–9, 10–12), masculine/feminine Arabic. |
| Parent UI | `feature/*` | Onboarding, Home (ring, status, today's stats, permissions), Apps, Schedule, Settings. |
| Storage | `data/*` | DataStore (settings, PIN/answer hashes, timer state), Room (schedule periods, daily usage). |

Architecture: single `:app` module, Kotlin + Jetpack Compose + Material 3, Hilt, MVVM. Arabic (RTL)
and English. Fonts (Cairo, Nunito) are bundled; licences in `licenses/`.

## Docs

- [`docs/progress.md`](docs/progress.md): what's done against the brief, and what's pending
- [`docs/handoff.md`](docs/handoff.md): architecture, tricky decisions, how to resume
- [`docs/testing-checklist.md`](docs/testing-checklist.md): device and tampering tests (implementation step 8)
- [`docs/play-compliance.md`](docs/play-compliance.md): permission justifications and Play declarations (draft)
- [`docs/privacy-policy.md`](docs/privacy-policy.md): privacy policy (draft)
