# Nour Time: progress against the brief

Status as of 2026-09-26. Legend: ✅ done · 🟡 done with a noted difference · ⏳ pending.

Phase 1 is feature-complete on the emulator (Android 12 and 15). What's left is testing on real
phones, recorded audio, release plumbing, Phase 1.5 (below) and Phase 2.

## Implementation order (brief §11)

| # | Step | Status | Notes |
|---|---|---|---|
| 1 | Onboarding, permissions, PIN | ✅ | PIN and security question come **before** permissions (your decision). |
| 2 | App selection and launch detection | ✅ | Accessibility window list (PiP / split screen) + usage-stats fallback. |
| 3 | Time engine | ✅ | Shared budget, active use only, lock period, full refill, optional daily reset. |
| 4 | Lock screens + security question | ✅ | Selected apps or whole phone; screen turns off in whole-phone mode. |
| 5 | Uninstall and disable protection | ✅ | Device admin, Settings screens covered, boot restart, fail-closed. |
| 6 | Templates by age and time of day | 🟡 | Done; animations are drawn in code instead of Lottie, and ages 3–6 get a chime, not a voice message (needs a recording). |
| 7 | Bedtime and stats | ✅ | |
| 8 | Testing on Samsung and Xiaomi, tampering scenarios | 🟡 | Started 2026-09-26 on a **HONOR VNE-N41** (Magic UI 6.1, Android 12): blocking worked, then stopped after Honor reconnected the Accessibility service (see Phase 1.5, task 0). Samsung and Xiaomi still to do. Checklist: [`testing-checklist.md`](testing-checklist.md). |
| 9 | Phase 1.5: fixes and UX requests from device testing | ⏳ | Scheduled 2026-09-26, see [Phase 1.5](#phase-15-scheduled-2026-09-26). |
| 10 | Phase 2: remote control | ⏳ | Not started; needs your decisions (see below). |

## Brief sections in detail

### §2 Time logic
| Requirement | Status |
|---|---|
| One shared, configurable budget; switching apps continues the countdown | ✅ |
| Only active use counts (limited app on screen, screen on and unlocked) | ✅ PiP and split screen count too |
| Lock period when the budget hits zero (default 6 h, configurable) | ✅ |
| Full automatic refill when the lock ends | ✅ |
| Unused budget carries over; optional daily reset at a chosen time | ✅ Reset needs ≥ 20 h of real time since the last one (clock-change proof) |
| No warning before the end | ✅ |
| `elapsedRealtime`, not the wall clock; state survives reboots | ✅ Time while powered off isn't counted (approved) |

### §3 Locking and protection
| Requirement | Status |
|---|---|
| Lock type: whole device (overlay + `lockNow()`) | ✅ Screen turns off ~8 s after a lock period starts |
| Lock type: selected apps only | ✅ |
| Parent PIN, hashed | ✅ PBKDF2-SHA256, salted |
| Security question, answer normalized + hashed; required during lock periods | ✅ Also rate-limited like the PIN |
| Device admin against uninstall | ✅ |
| Block Accessibility / App info / Device admin / Force stop screens without the PIN | 🟡 The whole Settings app (and OEM security apps, package installer) is covered by package name, because reading screen content is ruled out (approved). Can be switched off. |
| Battery optimization exemption, OEM autostart guidance, restart on boot | ✅ |
| Escalating delay after wrong PINs | ✅ 4 free attempts, then 30 s → 1 h |

### §4 "Time's up" templates
| Requirement | Status |
|---|---|
| Ages 3–6: big character, cheerful, short sound or voice, minimal text | 🟡 Chime only; **voice message needs a recording** |
| Ages 7–9: short message + countdown to the refill | ✅ Round countdown + 3 activity cards |
| Ages 10–12: calmer, written message, remaining time shown clearly | ✅ |
| Time-of-day periods (study, play, meal, sleep) with their own message and design | ✅ Messages exactly as in the brief, masculine and feminine |
| Original artwork, easy to extend | 🟡 Drawn in code (no Lottie files yet); new themes = one entry in `TimeUpTemplates.kt` |

### §5 Bedtime
✅ On/off, start/end (may cross midnight), blocks even with budget left, follows the lock type (approved).

### §6 Parent settings
✅ App list with icons + search · budget and lock period · lock type · age group and schedule ·
bedtime · daily reset · change PIN and question · today's stats (total and per app).
Also added: sound toggle, settings-protection toggle, "Time's up" preview.

### §7 Onboarding
✅ Welcome · prominent disclosure (explicit "I agree") · each permission step by step with its
reason · PIN + security question · apps, time and lock type, child's gender and age group.

### §9 Tech stack
| Item | Status |
|---|---|
| Kotlin, Compose, Material 3, minSdk 26 | ✅ targetSdk 35 |
| AccessibilityService primary, UsageStatsManager fallback | ✅ |
| Foreground service for the timer | ✅ `specialUse` type |
| Room + DataStore | ✅ |
| AlarmManager for refills and bedtime | 🟡 Not needed: the foreground service re-evaluates every second while the screen is on, and refills are computed from elapsed time whenever they're next checked. |
| Hilt, MVVM | ✅ |
| Arabic (RTL) and English | ✅ |

### §10 Google Play readiness (Phase 3)
🟡 Drafts done: [`privacy-policy.md`](privacy-policy.md), [`play-compliance.md`](play-compliance.md)
(Accessibility declaration text, permission justifications, Data safety answers). No `INTERNET`
permission; backups disabled. ⏳ Release signing, store listing, Accessibility demo video.

### §12 Design and UX
✅ Palette, bundled Cairo/Nunito, 16 dp cards, bottom bar (Home / Apps / Schedule / Settings), gold
budget ring, status card, per-app bars, Apps list with switches and search, colour timeline (tap to
edit, drag to move; resize through the edit dialog), gold / outline / coral buttons ≥ 48 dp, large
PIN pad with dots and a light vibration, parent dark mode, RTL with mirrored icons, fade + scale
transition, friendly empty states, subtle "للأهل" button, positive child copy.
🟡 Lottie → code-drawn animations. ⏳ Lullaby for the sleep screen. ⏳ A formal WCAG AA/TalkBack
audit (key contrasts were checked by hand).

## Phase 1.5 (scheduled 2026-09-26)

Comes before Phase 2. Technical notes for each task are in [`handoff.md` §6](handoff.md#6-phase-15-design-notes).
**Standing rule for every task:** full Arabic (RTL) and English support, masculine and feminine child
copy where the child sees text, and parent dark mode.

| # | Task | Priority | Status |
|---|---|---|---|
| 0 | **Detection freeze after the Accessibility service reconnects** (found on Honor: limited app and Settings not blocked during a lock, no degraded alert) | P0 bug | 🟡 Fixed and verified on the emulator (2026-09-27); re-test on Honor |
| 1 | **Guided onboarding polish.** Permissions stay manual (Android doesn't let an app grant Accessibility or Device admin to itself, and Play forbids auto-clicking them). Make each step as guiding as possible: per-brand instructions (Samsung, Xiaomi, Honor/Huawei, Oppo/Vivo) for *Allow restricted settings* and autostart, return to the app automatically once a permission is on, and a final "Test protection" step. | P1 | ⏳ |
| 2 | **Educational content during the lock period.** The parent allows specific content while locked: (a) an **allow-list of educational apps** that stay usable during the lock, and/or (b) a **safe mini-browser** inside the lock screen that opens only parent-approved sites (Quran, stories, language learning). | P1 | ⏳ Needs your decisions (below) |
| 3 | **Smoother Settings / uninstall protection.** Replace the flashing cover with a calm screen that asks for the PIN (plus the security question during a lock period). A correct answer opens Settings normally. Add *Uninstall Nour Time* in the parent Settings tab: PIN + security question → Nour Time removes its own Device admin → Android's normal uninstall dialog. | P1 | 🟡 Done on the emulator (2026-09-27); the "flashing" didn't reproduce there, re-check on Honor |
| 4 | **Language support.** Arabic and English stay complete for every new screen and string (standing requirement, checked in review). | Always | ✅ Ongoing |

**Decisions needed for task 2:**
- Allow-list, mini-browser, or both? The allow-list is simpler and fully offline. The mini-browser
  needs the `INTERNET` permission (Phase 1 has none), so the privacy policy and Play Data safety
  answers change.
- Does time spent in allowed content count against anything, or is it free?
- Does it also apply at bedtime, and in whole-phone lock mode?
- Mini-browser: the parent types the site addresses, or picks from a built-in list we curate?

**Task 3 notes to confirm:**
- Android can't tell *which* Settings page is open without reading screen content (ruled out), so
  the PIN screen appears for the whole Settings app, as today, just calmer.
- Once Device admin is removed it can't be switched back on silently. If the parent then decides
  not to uninstall, Nour Time asks them to turn it back on (one system confirmation).

## Strictly pending

1. **Phase 1.5** tasks 0–3 (above).
2. **Device testing** (step 8) on Samsung and Xiaomi (Honor started): PiP, battery savers, autostart, reboot during a lock, Force stop / uninstall attempts, whole-phone screen-off. [`testing-checklist.md`](testing-checklist.md).
3. **Audio assets:** a short recorded voice message for ages 3–6 (masculine and feminine) and a soft lullaby for the sleep screen.
4. **Release:** signing keystore, Play App Signing, store listing, Accessibility demo video.
5. **Phase 2 decisions:** pairing method (code / QR / parent mode), Firebase project and region, what the parent can change remotely, how bonus time interacts with the lock period.
6. **Optional:** Lottie animations if an illustrator provides them; Device Owner mode if Safe Mode must be blocked.

## Known limitations (documented, not bugs)
- Safe Mode disables all third-party apps; blocking it needs Device Owner (factory reset + adb).
- The usage-stats fallback can't see picture-in-picture windows (only Accessibility can).
- The lock period doesn't progress while the phone is powered off (by design, approved).
- Changing the phone's time zone shifts bedtime and the schedule (the Settings app is protected, so this needs the parent).
