# Google Play compliance notes (draft for Phase 3)

Nour Time is a parental-control app. Everything stays on the device; nothing is collected or sent
anywhere in Phase 1. These notes collect what the Play Console declarations will need.

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
| `<queries>` for launcher apps | Lists apps with a launcher icon for the app picker. **No `QUERY_ALL_PACKAGES`.** | — |

## Accessibility API declaration (Play Console text draft)

> Nour Time is a parental control app that limits a child's time on apps chosen by the parent. It uses
> the AccessibilityService API only to detect which apps are visible on the screen (including
> picture-in-picture and split-screen windows), so that the time limit counts only while a
> parent-selected app is in use, and to show a full-screen "Time's up" screen over those apps when the
> time runs out. For each visible window the app reads only the package name of the app that owns it.
> It does not read, collect, store or share any text or content shown on the screen, or anything the
> user types. All data stays on the device.

The prominent disclosure is shown before any permission is requested and requires an explicit
"I agree" tap. It's in onboarding step 1 (`DisclosureStep`) and each permission step repeats the
reason.

## Families policy notes (to review before publishing)

- Target audience: parents (the app is installed and configured by a parent). The child only sees the
  "Time's up" screens. Decide in Play Console whether the app is listed for "Parents" rather than as a
  children's app; parental-control apps are usually not "Designed for Families".
- No ads, no analytics SDKs, no network access in Phase 1 (the app doesn't even request `INTERNET`).
- No account creation; no personal data of the child is collected.
- Phase 2 (Firebase) will require updating the Data safety form and the privacy policy.

## Data safety form (Phase 1)

- Data collected: **none** (nothing is transmitted off the device).
- Data shared: **none**.
- On-device data: selected app list, usage minutes per app per day (kept for recent days), settings,
  hashed PIN and hashed security answer. Backups and device transfer are disabled
  (`allowBackup=false`, `data_extraction_rules.xml`).

## Open items before publishing

- Release signing (keystore) and Play App Signing.
- Final privacy policy URL (see `privacy-policy.md`).
- Store listing, screenshots, and the Accessibility demo video that Play asks for.
