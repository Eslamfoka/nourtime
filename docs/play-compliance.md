# Google Play compliance notes (draft for Phase 3)

Nour Time is a parental-control app. Without a connected parent's phone everything stays on the
device; Phase 2 adds optional remote control through Firebase (see "Data safety form (Phase 2)"). These
notes collect what the Play Console declarations will need.

## Sensitive permissions and APIs

| Permission / API | Why Nour Time needs it | Where it's disclosed |
|---|---|---|
| **AccessibilityService** (`isAccessibilityTool` not set) | Four uses, all declared: (1) knows which apps are on screen, including picture-in-picture and split screen, so the time budget only runs while a parent-selected app is in use, and shows the "Time's up" screen over it; (2) covers the phone's Settings, the package installer and OEM security apps with the PIN screen (`BlockPolicy.SYSTEM_SETTINGS_PACKAGES`), and the Play Store while device admin is off (`APP_STORE_PACKAGES`), so the child can't disable or uninstall Nour Time; (3) performs `GLOBAL_ACTION_HOME` when the child leaves a blocked app (`LockOverlay.goHome`); (4) receives notification events and, for the parent-limited apps only, takes the media session token from their media notifications (`MediaWatcher`), to pause their background audio during a lock, bedtime or weakened protection and count their playback while the screen is on; other apps' notifications are ignored and no notification text is read. From each window it reads **only the owner package name and window type**; it never reads text, view content or what the child types. | Prominent disclosure screen, the Accessibility step, and the service description in system Settings (all four uses, EN + AR; the fourth since 2026-10-17). |
| `canRetrieveWindowContent="true"` + `flagRetrieveInteractiveWindows` | Required by Android to list visible windows and read the owning package (`AccessibilityWindowInfo.getRoot().getPackageName()`). No node text is read anywhere in the code. | Same as above. |
| `PACKAGE_USAGE_STATS` (Usage access) | Fallback foreground-app detection while Accessibility is off (fail-closed protection), and the per-app stats. | Disclosure + Usage step. |
| `SYSTEM_ALERT_WINDOW` | Shows the "Time's up" screen over limited apps when the accessibility overlay isn't available. | Disclosure + Overlay step. |
| **Device admin** (`force-lock` policy) | Locks the screen in whole-phone mode. While it's active Android won't uninstall Nour Time; it can only be deactivated in Settings, which Nour Time covers with the PIN screen (Accessibility use 2). | Disclosure + Device admin step + system activation screen. |
| `POST_NOTIFICATIONS` | Required foreground-service notification ("Nour Time is on"), which also keeps the monitoring visible as the stalkerware policy requires; parent alerts when protection is weakened. | Notifications step. **Required** since 2026-10-12 (no skip). |
| ~~`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`~~ | **Removed 2026-10-12** (Play restricts it to other kinds of apps). The battery step now opens the system battery-optimization list, where the parent picks Nour Time; no permission needed. | Battery step. |
| `com.google.android.gms.isMonitoringTool` = `child_monitoring` (manifest meta-data) | Declares Nour Time as a child-monitoring app under Play's stalkerware policy (the child's phone shares usage with the parent once paired). The launcher icon is never hidden and the notification is always on. | Manifest; Play Console declaration. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | The timer runs as a foreground service with type `specialUse`; subtype declared in the manifest: *"Parental control: tracks the child's active screen time on parent-selected apps and enforces the time budget and lock period."* | Status notification. |
| `RECEIVE_BOOT_COMPLETED` | Restarts protection after a reboot. | — |
| `REQUEST_DELETE_PACKAGES` | Settings → *Uninstall Nour Time* opens the system uninstall dialog for Nour Time itself (after the parent's security answer). | Settings screen. |
| `ACTION_PACKAGE_ADDED` (runtime receiver, no permission) | Limits newly installed apps by default ("Limit new apps", the parent can turn it off), so a newly installed game or browser isn't unlimited. | Settings toggle; "New app limited" notification. |
| `Settings.Global.BOOT_COUNT` / `ADB_ENABLED`, `ACTION_USER_BACKGROUND` (no permission) | Protection alerts: a restart without Nour Time (safe mode), a switch to another user, USB debugging on, Nour Time's notifications off, a PIN reset through the security question (and when the new PIN starts working). Shown on the child's phone and, when connected, on the parent's phone. | Notification; privacy policy. |
| `Settings.Global` `user_switcher_enabled` (hidden key, read only, no permission) | Whether the phone's "Multiple users" switch is on, so setup and the Permissions screen can recommend turning off guest and other users (Nour Time can't limit apps there). Stays on the phone, never uploaded. | Setup step "Guest and other users"; Permissions screen. |
| `INTERNET` | Phase 2 remote control (Firebase Auth + Firestore): only used on a parent's phone, or on a child's phone after the parent connected it. | Privacy policy; the pairing screens. |
| `<queries>` for launcher apps | Lists apps with a launcher icon for the app picker. **No `QUERY_ALL_PACKAGES`.** | — |
| `<queries>` for web links (`ACTION_VIEW` http/https, `BROWSABLE`) | Marks the installed browsers in the app picker and suggests limiting them (the parent chooses). Local only. | — |

## Accessibility API declaration (Play Console text draft)

> Nour Time is a parental control app that limits a child's time on apps chosen by the parent. It uses
> the AccessibilityService API for four purposes: (1) to detect which apps are visible on the screen
> (including picture-in-picture and split-screen windows), so that the time limit counts only while a
> parent-selected app is in use, and to show a full-screen "Time's up" screen over those apps when the
> time runs out; (2) to protect the parental controls from being disabled by the child: when the
> phone's Settings app, the package installer or the phone maker's security app is opened (and the
> Play Store while Nour Time's device admin is off, since it can uninstall apps itself), Nour Time
> shows its parent-PIN screen over it, so only the parent can turn off Nour Time's permissions or
> uninstall it; (3) to return to the home screen (global action Home) when the child dismisses the
> "Time's up" screen; (4) to stop the parental limit being bypassed with background audio: it receives
> notification events and, only for the apps the parent limited, takes the media session token from their
> media notifications, so it can pause their music or video during a lock period (also when played again
> from the notification shade) and count that playback toward the time limit while the screen is on.
> Other apps' notifications are ignored, and no notification text is read. For each visible window the
> app reads only the package name of the app that owns
> it. It does not read, collect, store or share any text or content shown on the screen, or anything
> the user types. What it learns from the Accessibility service stays on the device unless the parent
> connects their own phone. Then the phone's make and model, the minutes used today on each limited app
> (measured with this detection), the time left, the list of installed apps, Nour Time's settings, the
> Learning Hub minutes, requests for more time and protection alerts are sent to Google Cloud Firestore,
> where only that parent's own account can read them, and crash reports, if Nour Time crashes, are sent
> to Firebase Crashlytics so the developer can fix them.

The prominent disclosure is shown before any permission is requested and requires an explicit
"I agree" tap. It's in onboarding step 1 (`DisclosureStep`) and each permission step repeats the
reason.

## Families policy notes (to review before publishing)

- **Target audience (recommended in the 2026-10-12 review): mixed**, so declare under 13 *and* 18+.
  A parent installs and configures the app, but the child sees the "Time's up" screens and plays the
  12 Learning Hub games (ages 3-12), so Play would treat it as appealing to children anyway; an
  "adults only" declaration would itself be a policy violation. With a mixed audience the full
  Families policy applies: no ads, no advertising ID, only Families-approved SDKs, data from children
  only with parental consent. Checked: no ads, no ad ID in the merged release manifest, no
  analytics SDK. Firebase Crashlytics is included but off until a phone is connected (paired child's
  phone or signed-in parent's phone), so it only runs with the parent's consent; confirm it is on
  Google's Families self-certified SDK list (see Data safety below). The same check applies to
  `firebase-messaging` (Firebase Cloud Messaging, used only on the parent's phone).
- Network access only for the optional parent's-phone connection.
- Parents sign in with Google on their own phone; the child's phone uses an anonymous account. The
  child's name, age, photos and location are never collected. Per-app usage of the child's phone
  **does** count as the child's personal data under COPPA / GDPR-K; it is shared only after the parent
  pairs the phones (parental consent, confirmed on the child's phone behind the PIN).
- **Account deletion (required by Play for apps with sign-in):** in-app on the parent's phone
  (*Delete my account*, built 2026-09-27); the child's phone deletes its own data on disconnect, removal
  or uninstall. Play Console also needs a **web URL** for deletion requests: publish
  [`account-deletion.md`](account-deletion.md) (e.g. GitHub Pages) and fill in its contact email.

## Data safety form (submit this one form: Play takes one form for the whole app)

On-device only (not declared): settings, hashed PIN and security answer, per-day usage, Learning Hub
progress, and the playback state of limited apps' media sessions (from their media notifications, used
to pause them during a lock and count their audio; never leaves the phone). Backups and device transfer are disabled (`allowBackup=false`, `data_extraction_rules.xml`).

- Data collected, and shared only with the connected parent's own account, when a parent's phone is
  connected (stored in Google Cloud Firestore, encrypted in transit):
  - **App activity → App interactions:** per-app minutes of limited apps, timer status, "ask for more
    time" requests (time asked and the parent's answer), Learning Hub minutes (bank, minutes earned
    today, a paused lock waiting after a learning break), and Nour Time's settings, uploaded whenever
    they change on either phone so the parent can see and change them remotely (time budget, lock,
    bedtime, limited/allowed apps, Learning Hub and voice settings).
  - **App activity → Installed apps:** list of launchable apps (names, package names).
  - **App info and performance → Other app performance data:** protection alerts (time of a restart
    without Nour Time or of a switch to another user, whether USB debugging is on, whether Nour Time's
    notifications are off, time of a PIN reset through the security question and when the new PIN
    starts working).
  - **App info and performance → Crash logs and Diagnostics:** Firebase Crashlytics crash reports
    (stack trace, Android version, phone model, a Crashlytics installation id). Collected only on a
    paired child's phone or a signed-in parent's phone (`CrashReporting.allowed`, applied by
    `FirebaseCollectionController`); off in the manifest
    (`firebase_crashlytics_collection_enabled=false`), and reports saved while off are deleted.
    Purpose: app functionality / crash fixing. Shared with no one else. Not optional for a connected
    phone (declare as collected, required).
  - **Device or other IDs:** a random device id generated by Nour Time; phone make/model; on the
    **parent's** phone only, the Firebase Cloud Messaging token while signed in (app functionality:
    notifications to the parent; not shared; deleted on sign-out and account deletion). FCM auto-init
    is off in the manifest (`firebase_messaging_auto_init_enabled=false`) and turned on only while a
    parent is signed in (`FirebaseCollection.pushes`), so an unconnected or child's phone creates no token.
    A token made by an older version is deleted once after the update; on a phone that never had one,
    Nour Time makes no FCM call at all (`FcmTokenCleanup`), and FCM's own start-up contacts nothing
    while auto-init is off.
  - **Personal info → Name, Email:** the parent's Google account name and email. Stored on the parent's
    phone, with the connection in Firestore (`pairings`, `devices`) and on the child's phone, which shows
    who is connecting (2026-10-17 audit, M5: earlier drafts said "parent's phone only").
- Purpose: app functionality (parental control). Not used for ads or analytics; not sold.
- SDKs to check before submitting: `play-services-code-scanner` (QR scan, parent's phone) and
  `firebase-crashlytics`. Read Google's data-disclosure pages for both, declare anything they list, and
  check that both are on Google's Families self-certified SDK list (mixed audience).
- Optional for the user: yes (only after connecting a parent's phone).
- Deletion: in-app (parent's *Delete my account*; child's phone on disconnect/uninstall) and by request
  through the web page ([`account-deletion.md`](account-deletion.md)). Data is also deleted when a phone
  is disconnected.

## Open items before publishing

- Play Console declarations: Accessibility (text above + demo video), foreground service `specialUse`
  (description + video), target audience (mixed), Data safety (above), content rating.
- App access for reviewers: test PIN and security answer, a test Google account for the parent's
  phone, and a short video of pairing two phones (hard for a reviewer to do).
- New personal developer accounts: a closed test with at least 12 testers for 14 days before production.
- Release signing (keystore) and Play App Signing.
- Final privacy policy URL (see `privacy-policy.md`).
- Store listing text is drafted in [`store-listing.md`](store-listing.md); still to make: screenshots, feature graphic and the Accessibility demo video that Play asks for.
- Real Firebase project (see README), Firestore rules deployed, and the account-deletion web page
  published (URL in Play Console → Data safety).
