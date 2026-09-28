# Google Play compliance notes (draft for Phase 3)

Nour Time is a parental-control app. Without a connected parent's phone everything stays on the
device; Phase 2 adds optional remote control through Firebase (see "Data safety form (Phase 2)"). These
notes collect what the Play Console declarations will need.

## Sensitive permissions and APIs

| Permission / API | Why Nour Time needs it | Where it's disclosed |
|---|---|---|
| **AccessibilityService** (`isAccessibilityTool` not set) | Knows which apps are on screen, including picture-in-picture and split screen, so the time budget only runs while a parent-selected app is in use, and shows the "Time's up" screen over it. From each window it reads **only the owner package name and window type**; it never reads text, view content or what the child types. | Prominent disclosure screen, the Accessibility step, and the service description in system Settings. |
| `canRetrieveWindowContent="true"` + `flagRetrieveInteractiveWindows` | Required by Android to list visible windows and read the owning package (`AccessibilityWindowInfo.getRoot().getPackageName()`). No node text is read anywhere in the code. | Same as above. |
| `PACKAGE_USAGE_STATS` (Usage access) | Fallback foreground-app detection while Accessibility is off (fail-closed protection), and the per-app stats. | Disclosure + Usage step. |
| `SYSTEM_ALERT_WINDOW` | Shows the "Time's up" screen over limited apps when the accessibility overlay isn't available. | Disclosure + Overlay step. |
| **Device admin** (`force-lock` policy) | Prevents uninstalling Nour Time without the parent; lock-screen capability. | Disclosure + Device admin step + system activation screen. |
| `POST_NOTIFICATIONS` | Required foreground-service notification; parent alerts when protection is weakened. | Notifications step. Optional (can be skipped). |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keeps the timer accurate in the background. Parental control is an accepted use case, but Play may ask for justification. | Battery step. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | The timer runs as a foreground service with type `specialUse`; subtype declared in the manifest: *"Parental control: tracks the child's active screen time on parent-selected apps and enforces the time budget and lock period."* | Status notification. |
| `RECEIVE_BOOT_COMPLETED` | Restarts protection after a reboot. | — |
| `REQUEST_DELETE_PACKAGES` | Settings → *Uninstall Nour Time* opens the system uninstall dialog for Nour Time itself (after the parent's security answer). | Settings screen. |
| `INTERNET` | Phase 2 remote control (Firebase Auth + Firestore): only used on a parent's phone, or on a child's phone after the parent connected it. | Privacy policy; the pairing screens. |
| `<queries>` for launcher apps | Lists apps with a launcher icon for the app picker. **No `QUERY_ALL_PACKAGES`.** | — |

## Accessibility API declaration (Play Console text draft)

> Nour Time is a parental control app that limits a child's time on apps chosen by the parent. It uses
> the AccessibilityService API only to detect which apps are visible on the screen (including
> picture-in-picture and split-screen windows), so that the time limit counts only while a
> parent-selected app is in use, and to show a full-screen "Time's up" screen over those apps when the
> time runs out. For each visible window the app reads only the package name of the app that owns it.
> It does not read, collect, store or share any text or content shown on the screen, or anything the
> user types. Nothing it learns from the Accessibility service leaves the device; only if the parent
> connects their own phone are the time left and per-app minutes shared with the parent's account.

The prominent disclosure is shown before any permission is requested and requires an explicit
"I agree" tap. It's in onboarding step 1 (`DisclosureStep`) and each permission step repeats the
reason.

## Families policy notes (to review before publishing)

- Target audience: parents (the app is installed and configured by a parent). The child only sees the
  "Time's up" screens. Decide in Play Console whether the app is listed for "Parents" rather than as a
  children's app; parental-control apps are usually not "Designed for Families".
- No ads, no analytics SDKs. Network access only for the optional parent's-phone connection.
- Parents sign in with Google on their own phone; the child's phone uses an anonymous account. No
  personal data of the child (name, age, photos, location) is collected.
- **Account deletion (required by Play for apps with sign-in):** in-app on the parent's phone
  (*Delete my account*, built 2026-09-27); the child's phone deletes its own data on disconnect, removal
  or uninstall. Play Console also needs a **web URL** for deletion requests: publish
  [`account-deletion.md`](account-deletion.md) (e.g. GitHub Pages) and fill in its contact email.

## Data safety form (Phase 1)

- Data collected: **none** (nothing is transmitted off the device).
- Data shared: **none**.
- On-device data: selected app list, usage minutes per app per day (kept for recent days), settings,
  hashed PIN and hashed security answer. Backups and device transfer are disabled
  (`allowBackup=false`, `data_extraction_rules.xml`).

## Data safety form (Phase 2, when a parent's phone is connected)

- Data collected and shared with the connected parent's account (stored in Google Cloud Firestore,
  encrypted in transit):
  - **App activity → App interactions / Other:** per-app minutes of limited apps, timer status,
    "ask for more time" requests (time asked and the parent's answer).
  - **App info and performance → Other:** list of launchable apps (names, package names).
  - **Device or other IDs:** a random device id generated by Nour Time; phone make/model.
  - **Personal info → Name, Email:** the parent's Google account (parent's phone only).
- Purpose: app functionality (parental control). Not used for ads or analytics; not sold.
- Optional for the user: yes (only after connecting a parent's phone).
- Deletion: in-app (parent's *Delete my account*; child's phone on disconnect/uninstall) and by request
  through the web page ([`account-deletion.md`](account-deletion.md)). Data is also deleted when a phone
  is disconnected.

## Open items before publishing

- Release signing (keystore) and Play App Signing.
- Final privacy policy URL (see `privacy-policy.md`).
- Store listing text is drafted in [`store-listing.md`](store-listing.md); still to make: screenshots, feature graphic and the Accessibility demo video that Play asks for.
- Real Firebase project (see README), Firestore rules deployed, and the account-deletion web page
  published (URL in Play Console → Data safety).
