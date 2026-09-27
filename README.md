# Nour Time – وقت نور

Android parental-control app: one shared time budget for the apps a parent picks, a lock period when
it runs out, and a friendly "Time's up" screen for the child. Everything runs on the child's phone; a
parent can optionally follow and control it from their own phone (Phase 2, Firebase).

The product brief is in [`nour-time-brief-en.md`](nour-time-brief-en.md) (Arabic: [`nour-time-brief-ar.md`](nour-time-brief-ar.md)).

## Build

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug           # lint (no errors expected)
./gradlew :app:bundleRelease       # minified release bundle for Play: app/build/outputs/bundle/release/
./gradlew :app:assembleRelease     # minified release APK; both need app/google-services.json (Phase 2)
```

### Release signing

Release builds are signed when a key is configured, and unsigned otherwise:

1. Create an upload key once and keep it (and its passwords) safe outside the repository; losing it
   means asking Google to reset the upload key:
   `keytool -genkeypair -keystore ~/keys/nourtime-upload.jks -alias nourtime -keyalg RSA -keysize 2048 -validity 10000`
2. Put `keystore.properties` in the project root (git-ignored):
   ```properties
   storeFile=C:/Users/you/keys/nourtime-upload.jks
   storePassword=...
   keyAlias=nourtime
   keyPassword=...
   ```
   On a build server, set `NOURTIME_KEYSTORE`, `NOURTIME_KEYSTORE_PASSWORD`, `NOURTIME_KEY_ALIAS` and
   `NOURTIME_KEY_PASSWORD` instead.
3. `./gradlew :app:bundleRelease`, then upload the `.aab` in Play Console with **Play App Signing** on
   (Google keeps the app signing key; yours is the upload key). Add the SHA-1/SHA-256 of both keys
   (Play Console → App integrity) to the Firebase Android app for Google sign-in.
4. Raise `versionCode` in `app/build.gradle.kts` for every upload.

Debug builds show a **Detection (debug)** card on Home with what detection sees, and two buttons,
**End budget now** / **End lock now**, to test locking without waiting. They don't exist in release builds.

## Phase 2: parent's phone (Firebase)

On first launch the app asks whose phone it is. A **child's phone** works exactly as before and, once
connected, syncs with Firestore. A **parent's phone** signs in with Google and controls its children's
phones. Details: [`docs/handoff.md` §7](docs/handoff.md#7-phase-2-remote-control).

### Develop against the local emulator (default for debug builds)

Needs Node 18+ and **JDK 21** for firebase-tools (Gradle keeps JDK 17).

```bash
cd firebase && npm install
JAVA_HOME=/path/to/jdk-21 npm run emulators         # auth :9099, firestore :8080
JAVA_HOME=/path/to/jdk-21 npm test                  # Firestore rules tests (own project, safe to run)
adb reverse tcp:8080 tcp:8080 && adb reverse tcp:9099 tcp:9099   # for every phone/emulator
```

Debug builds use the demo config in `app/src/debug/google-services.json` and connect to 127.0.0.1.
On the parent's phone, "Use a test account (emulator)" signs in without a real Google account.

### Connect a real Firebase project (needed for release builds and real phones over the internet)

1. [Firebase console](https://console.firebase.google.com) → **Add project** (Analytics not needed).
2. **Build → Authentication → Sign-in method:** enable **Anonymous** and **Google** (set the support email).
3. **Build → Firestore Database → Create database** in production mode; pick the location closest to your
   users from the list the console offers. It can't be changed later.
4. **Project settings → Your apps → Add app → Android**, package `com.nourtime.app`. Add the **SHA-1
   and SHA-256** of your debug key (`./gradlew signingReport`) and, later, of the release/Play App
   Signing key. Google sign-in fails without them.
5. Download **`google-services.json`** into **`app/`** (not `app/src/debug/`).
6. Deploy the rules: `cd firebase && npx firebase deploy --only firestore:rules --project <your-project-id>`.
7. *Optional, needs billing (Blaze):* turn on clean-up of abandoned pairing codes. The app already deletes a
   code when it is refused, expires or is closed, so this only catches codes left by an app killed mid-pairing: Firestore → **TTL policies** → collection group
   `pairings`, timestamp field `expireAt` (or `gcloud firestore fields ttls update expireAt
   --collection-group=pairings --enable-ttl --project <your-project-id>`).
8. Release builds now build and use it. To run a **debug** build against the real project, delete
   `app/src/debug/google-services.json` and build with `./gradlew assembleDebug -Pnourtime.firebaseEmulator=false`.

The free Spark plan is enough to start (no Cloud Functions are used).

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
| Remote control (Phase 2) | `remote/*`, `feature/remote`, `feature/parent`, `firebase/` | Pure model (pairing code, settings sync, commands, status), child sync run by the timer service, parent screens, Firestore rules + tests. |

Architecture: single `:app` module, Kotlin + Jetpack Compose + Material 3, Hilt, MVVM. Arabic (RTL)
and English. Fonts (Cairo, Nunito) are bundled; licences in `licenses/`.

## Docs

- [`docs/progress.md`](docs/progress.md): what's done against the brief, and what's pending
- [`docs/handoff.md`](docs/handoff.md): architecture, tricky decisions, how to resume
- [`docs/testing-checklist.md`](docs/testing-checklist.md): device and tampering tests (implementation step 8)
- [`docs/play-compliance.md`](docs/play-compliance.md): permission justifications and Play declarations (draft)
- [`docs/privacy-policy.md`](docs/privacy-policy.md): privacy policy (draft)
