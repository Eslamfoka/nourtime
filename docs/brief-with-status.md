# Nour Time – وقت نور: the brief, with implementation status

A copy of [`nour-time-brief-en.md`](../nour-time-brief-en.md), section by section, with the status of every
requirement as of **2026-09-27**. It is written so that someone who hasn't seen the code (a person or
another AI) can check what was built.

**Legend**
- ✅ **Done**: built and tested on the Android emulator (Android 12 / API 31 and Android 15 / API 35).
- 🟡 **Done, but different**: built, with a difference from the brief that is explained (and approved by the owner unless it says otherwise).
- 📱 **Needs a real phone**: works on the emulator, but hasn't been confirmed on a real Samsung or Xiaomi phone.
- ⏳ **Not done yet**.

**Important context**
- "Tested" in this document means tested on **emulators**. Real-phone testing began on a **HONOR VNE-N41 (Android 12)** on 2026-09-26. It found one serious bug, which is fixed on the emulator (see Part 2, 1.5-0) but not yet re-tested on the Honor. Samsung and Xiaomi haven't been tested yet.
- Automated tests: **240 unit tests** (the pure logic: timer, blocking, clock, PIN, sync) and **49 Firestore security-rules tests**, all passing. Android lint shows no errors.
- Code: a single Android module (`app/`), Kotlin + Jetpack Compose. Firebase rules are in `firebase/`. Technical notes are in [`handoff.md`](handoff.md), the earlier status in [`progress.md`](progress.md), and the device test list in [`testing-checklist.md`](testing-checklist.md).
- Git: `master` holds Phases 1, 1.5, 2, 2.5 and 3. Branch **`phase4`** holds Phase 4 (3 commits, **not merged yet**).

---

# Part 1: The original brief, with status

## 1. Overview

| Requirement | Status | Notes |
|---|---|---|
| Name "Nour Time / وقت نور", package `com.nourtime.app` | ✅ | |
| Limits chosen apps; runs automatically in the background | ✅ | A foreground service plus an Accessibility service |
| Timer starts when a chosen app opens; "Time's up" screen when the budget ends | ✅ | |
| Lock the whole device or only the chosen apps (parent's choice) | ✅ | |
| Only the parent's PIN unlocks | ✅ | Plus the security question during a lock period (§3) |
| Ready for Google Play policies from day one | 🟡 | Drafts written (§10); publishing steps remain |

## 2. Time logic

| Requirement | Status | Notes |
|---|---|---|
| One shared budget for all chosen apps, configurable; switching apps continues the countdown | ✅ | |
| Only active use counts: pauses when leaving the chosen apps or when the screen turns off | ✅ | Picture-in-picture and split screen count as use too |
| Lock period when the budget reaches zero (6 h by default, configurable) | ✅ | |
| Full automatic refill when the lock period ends | ✅ | |
| Unused budget carries over; optional daily reset at a chosen time | ✅ | The reset also needs ≥ 20 h since the last one, so changing the clock can't trigger it |
| No warning before the end | ✅ | |
| Tamper-proof: `elapsedRealtime()`, state survives reboots | ✅ | Manual clock changes are ignored. 🟡 While the phone is **powered off**, the lock period does not count down (approved by the owner) |

## 3. Locking and protection

| Requirement | Status | Notes |
|---|---|---|
| Whole device: full-screen cover, PIN only; `lockNow()` turns the screen off | ✅ | The screen turns off about 8 s after a lock period starts |
| Chosen apps only: opening one shows "Time's up", the rest of the phone works | ✅ | |
| 1. Parent PIN, stored hashed | ✅ | Exactly 4 digits (owner's decision); PBKDF2-SHA256 with salt; weak PINs like 1234 rejected |
| 2. Security question: required during a lock period even with the PIN; answer normalized (spaces, case, Arabic hamza/alef) and hashed | ✅ | |
| 3. Device Admin prevents uninstalling | ✅ | 📱 |
| 3. Block Accessibility / App info / Device admin / Force stop screens without the PIN | 🟡 | The **whole Settings app** (plus OEM security apps and the package installer) asks for the PIN. Reason: finding the exact page would mean reading screen text, which Google Play's Accessibility policy and the owner both rule out. Approved. The cover opens straight on the PIN pad (Phase 1.5-3). 📱 |
| 3. Battery optimization exemption | ✅ | |
| 3. Guidance for Autostart on Xiaomi / Oppo / Huawei etc. | ✅ | Per-brand instructions (Phase 1.5-1). 📱 The menu paths must be checked on real phones |
| 3. Restart after `BOOT_COMPLETED` | ✅ | Also after an app update |
| 4. Escalating delay after wrong PINs | ✅ | 4 free attempts, then 30 s up to 1 h. The security answer has the same protection |
| *Extra:* **fail-closed** | ✅ | If Accessibility is turned off, the app falls back to Usage Stats, blocks the chosen apps entirely and alerts the parent |

## 4. "Time's up" screen (templates)

| Requirement | Status | Notes |
|---|---|---|
| Ages 3–6: big character, cheerful colors, short sound **or voice message**, little text | 🟡 | A chime plays; ⏳ **the voice message needs a recording** from the owner (masculine and feminine) |
| Ages 7–9: short message + countdown to the refill | ✅ | Round countdown + 3 off-screen activity cards |
| Ages 10–12: calmer, written message, time shown clearly | ✅ | |
| Time-of-day periods (study, play, meal, sleep), each with its own message and design | ✅ | Messages as written in the brief, in **masculine and feminine** forms (the parent picks the child's gender) |
| Original artwork (vector / Lottie), no copyrighted characters | 🟡 | "Nour" the star is original and **drawn in code** (Compose Canvas), not Lottie files. Approved. Lottie can be added later if an illustrator provides files |
| Easy to add new themes | ✅ | One entry in `TimeUpTemplates.kt` |

## 5. Bedtime

| Requirement | Status | Notes |
|---|---|---|
| On/off toggle; blocks fully in a set window (e.g. 21:00–07:00) even with budget left | ✅ | Follows the lock type (approved). The time of day comes from a clock that manual changes can't move |

## 6. Parent settings (on the child's phone)

| Requirement | Status |
|---|---|
| Choose apps from the installed list, with icons and search | ✅ (reads only launcher apps, no `QUERY_ALL_PACKAGES`) |
| Budget length and lock-period length | ✅ |
| Lock type | ✅ |
| Age group and daily schedule of periods | ✅ |
| Bedtime (optional) | ✅ |
| Daily reset (optional) | ✅ |
| Change PIN and security question | ✅ |
| Simple stats: today's use, total and per app | ✅ (+ a 7-day history, Phase 4) |

## 7. Onboarding

| Requirement | Status | Notes |
|---|---|---|
| Welcome screen | ✅ | |
| Prominent disclosure for every permission (Google Play) | ✅ | Explicit "I agree" |
| Permissions step by step with explanations: Accessibility, Usage Access, Overlay, Device Admin, Notifications, Battery | ✅ | 🟡 **PIN + security question come before the permissions** (owner's decision). Each step returns to the app automatically once the permission is on. |
| Create PIN and security question | ✅ | |
| Choose apps, time, age group | ✅ | Also the lock type and the child's gender. A final **"Test protection"** step was added (Phase 1.5-1) |

## 8. Phase 2: Remote parent control

| Requirement | Status | Notes |
|---|---|---|
| Pairing by code or QR; the same app in "Parent mode" | ✅ | The first launch asks "child's phone or parent's phone". 6-digit code or QR, **confirmed on the child's phone** behind the PIN. 📱 QR scanning needs a real camera |
| Bonus time as a reward, only from the parent's phone | ✅ | +15/+30/+60 min. Extra time during a lock ends it and gives exactly that time. The child's phone can't add time to itself |
| See usage and the current budget; change settings remotely | ✅ | Status, today's usage, 7-day history, every setting, lock now, end the lock, remove the phone |
| Firebase (Auth + Firestore + FCM) | 🟡 | Auth (Google sign-in for parents, anonymous for the child's phone) + Firestore. **No FCM**: the parent sees news when opening the app. Push notifications need Cloud Functions (paid Blaze plan) |
| Phase 1 works fully offline; remote is an add-on | ✅ | Firebase starts only after pairing. Offline changes are queued |
| ⏳ Real Firebase project | ⏳ | Everything was tested against the **Firebase emulator**. The owner must create the project (README steps 1–8) |

## 9. Tech stack

| Item | Status | Notes |
|---|---|---|
| Kotlin + Compose + Material 3, minSdk 26 | ✅ | targetSdk 35 |
| AccessibilityService first, UsageStatsManager as the fallback | ✅ | Reads only each window's **package name**, never screen text |
| Foreground service for the timer | ✅ | `specialUse` type |
| Room + DataStore | ✅ | |
| AlarmManager for refills and bedtime | 🟡 | Not needed: the service checks every second while the screen is on, and refills are calculated from elapsed time |
| DeviceAdminReceiver | ✅ | |
| Hilt, MVVM | ✅ | |
| Arabic (RTL) + English from the start | ✅ | Every string exists in both languages |

## 10. Google Play readiness (Phase 3)

| Requirement | Status | Notes |
|---|---|---|
| Privacy policy | 🟡 | Draft: [`privacy-policy.md`](privacy-policy.md). ⏳ Must be published at a public URL |
| Families + Accessibility API policy; declare the parental-control purpose | 🟡 | Drafts: [`play-compliance.md`](play-compliance.md) (Accessibility declaration, permission reasons, Data safety answers). ⏳ The Accessibility **demo video** isn't recorded yet |
| No collection of the child's personal data in Phase 1 | ✅ | Phase 1 stores everything on the phone. Phase 2 uploads app usage and settings **only while paired** and deletes them on unpairing |
| *Extra:* account deletion (a Play requirement for apps with sign-in) | ✅ | In the app, plus a web page draft [`account-deletion.md`](account-deletion.md). ⏳ Needs publishing and a contact email |
| *Extra:* release signing | ✅ | Reads a keystore from `keystore.properties` or environment variables. ⏳ The owner must create the real upload key |
| *Extra:* store listing text (Arabic + English) | ✅ Draft | [`store-listing.md`](store-listing.md). ⏳ Screenshots, feature graphic |

## 11. Implementation order

| # | Step | Status |
|---|---|---|
| 1 | Onboarding, permissions, PIN | ✅ |
| 2 | App selection and launch detection | ✅ |
| 3 | Time engine | ✅ |
| 4 | Both lock types + security question | ✅ |
| 5 | Uninstall and disable protection | ✅ 📱 |
| 6 | Templates by age and time of day | 🟡 (code-drawn art; voice recording pending) |
| 7 | Bedtime and stats | ✅ |
| 8 | Testing on Samsung and Xiaomi, including tampering (reboot, clock change, Force stop, uninstall) | ⏳ **Only started** (on Honor). Tampering scenarios pass on the emulator; real phones pending |
| 9 | Phase 2: remote control | ✅ on emulators; ⏳ real Firebase project + two real phones |

## 12. Design and UX

| Requirement | Status | Notes |
|---|---|---|
| "Nour" star character in poses (playing, studying, eating, sleeping) | ✅ | Drawn in code |
| Logo: the star with a clock face | ✅ | |
| Colors #FFC857 / #1E2A4A / #FFF8EC / #FF7A6B / #5CC8A8 | 🟡 | Used as given. For WCAG AA contrast, error text uses a darker coral `#B84232` and outlines were darkened |
| Parent dark mode | ✅ | |
| Cairo + Nunito, bundled | ✅ | |
| 16 dp cards, Material Symbols Rounded | ✅ | |
| Bottom bar: Home, Apps, Schedule, Settings | ✅ | |
| Home: gold budget ring, status card (available / in use / locked + countdown), per-app bars | ✅ | |
| Apps: icon, name, switch, search | ✅ | |
| Schedule: coloured timeline, edit by dragging or tapping | ✅ | Drag moves a period; resizing is done in the edit dialog |
| Buttons: gold primary, outline secondary, coral for danger with confirmation, ≥ 48 dp | ✅ | |
| PIN screen: large pad, filling dots, vibration when wrong | ✅ | |
| Child screens: no red, no X, no "forbidden" wording; positive messages | ✅ | |
| Smooth animations (Lottie); sound optional with a toggle | 🟡 | Smooth code-drawn animations; sound toggle ✅ |
| Subtle 🔒 "للأهل" button | ✅ | |
| Time-of-day templates table (backgrounds, poses, messages) | ✅ | ⏳ Optional **lullaby** for sleep needs an audio file |
| Adjustments by age | ✅ | (voice message pending, see §4) |
| Fade + scale into the lock screen | ✅ | |
| Full RTL, mirrored icons | ✅ | |
| WCAG AA contrast | ✅ | Computed for both themes. ⏳ A full TalkBack walk-through on a real phone |
| Friendly empty states | ✅ | |

---

# Part 2: Work beyond the original brief

These were added after the brief, most at the owner's request after the first real-phone test.

### Phase 1.5: fixes and improvements from the Honor test (all ✅ on the emulator, 📱 re-test on Honor)
| # | What | Why |
|---|---|---|
| 1.5-0 | **Detection freeze fix** (P0 bug): after Android reconnected the Accessibility service, detection stayed stuck on the last app, so nothing was blocked. Unknown windows are now resolved from event metadata and usage stats. | Found on the Honor. Reproduced and fixed on the emulator (10/10 blocked) |
| 1.5-1 | **Guided onboarding**: per-brand help (Xiaomi, Oppo/realme, Vivo, Honor/Huawei, OnePlus, Samsung), automatic return to the app after each permission, a final "Test protection" step | Android doesn't let an app grant these permissions to itself, so the steps are guided instead |
| 1.5-2 | **Educational apps during the lock**: the parent picks allowed apps. They stay usable during a lock period, bedtime and the whole-phone lock, don't use the budget, and appear as big buttons on the "Time's up" screen | Owner request |
| 1.5-3 | **Calmer Settings protection + "Uninstall Nour Time"**: the Settings cover opens straight on the PIN pad. The parent can uninstall from inside the app (security answer → device admin removed → Android's uninstall dialog) | Owner request (the cover flashed on the Honor) |

### Phase 2.5 and Phase 3 (✅, merged to `master`)
- **Account deletion** for parents, and deletion of the child's server data on disconnect, removal or uninstall.
- Review fixes: pairing-code expiry (TTL), status heartbeat, remote commands expire after 1 h, recovery from a lost anonymous account, Firebase starts only after pairing.
- **Release signing**, an **accessibility pass** (contrast, TalkBack labels), **store listing drafts**.
- Bug found and fixed: after a restart the child's sync never started (`MetadataChanges.INCLUDE`).

### Phase 4 (✅ on branch `phase4`, **not merged yet**)
| # | Feature | Status |
|---|---|---|
| 4a | **Weekend limits**: a separate budget, lock length and bedtime on chosen days (Friday+Saturday by default in Arabic, Saturday+Sunday in English), on the child's phone and the parent's phone | ✅ emulator |
| 4b | **7-day usage history**: a bar per day, total, daily average, most-used app and the change from the previous week, on the child's Home and the parent's phone (the server keeps 14 days) | ✅ emulator |
| 4c | **"Ask for more time"**: on the time-up screen a paired child taps *Ask*. The parent sees "Asking for more time" and answers +15 / +30 / Not now. An approval arrives as extra time and ends the lock. Not shown at bedtime or on the Settings cover. A request lapses after 30 min; after "Not now" the child waits 10 min to ask again | ✅ emulator end to end (ask, approve, decline, Arabic); 2 bugs found and fixed |

---

# Part 3: Remaining tasks and next steps

### A. Must do before publishing (needs the owner)
1. **Create the real Firebase project** (README "Phase 2" steps 1–8, including the TTL policy on `pairings.expireAt`) and add `google-services.json`.
2. **Test on two real phones**: pairing by QR, real Google sign-in (needs the app's SHA-1 in Firebase), extra time, asking for time.
3. **Device testing (brief step 8)** on **Samsung** and **Xiaomi**, and re-test the **Honor**, with [`testing-checklist.md`](testing-checklist.md): picture-in-picture, battery savers and autostart, reboot during a lock, Force stop, uninstall attempts, whole-phone screen-off, the per-brand onboarding texts.
4. **Publish** the privacy policy and the account-deletion page (fill in the contact email) and put both URLs in Play Console.
5. **Release**: create the upload key, turn on Play App Signing, then screenshots, the feature graphic and the **Accessibility demo video**.
6. **Audio**: record a short voice message for ages 3–6 (masculine and feminine) and a soft lullaby for the sleep screen.

### B. Engineering next steps
1. Decide whether to **merge `phase4` into `master`** (4a, 4b and 4c are tested on the emulator).
2. Fix whatever the real-phone tests find (most likely: OEM Settings / dialer package names, battery killers).
3. A full TalkBack walk-through on a real phone.

### C. Optional, later
- Push notifications to the parent ("protection needs attention", "your child is asking for time"). These need FCM + Cloud Functions (Blaze plan).
- Lottie animations if an illustrator provides files.
- Device Owner mode, only if blocking Safe Mode is required (it needs a factory reset).

### Known limitations (by design, documented)
- **Safe Mode** disables every third-party app; blocking it needs Device Owner.
- The Usage Stats fallback can't see picture-in-picture windows (only Accessibility can).
- The lock period doesn't count down while the phone is **powered off** (approved).
- Changing the phone's **time zone** shifts bedtime and the schedule (the Settings app is PIN-protected, so this needs the parent).
- The parent's phone uses its own date for "today's" usage, so a parent in another time zone sees the child's day shifted.
- Permissions (Accessibility, Device admin, etc.) must be turned on by hand. Android and Google Play don't allow automating this.
