# Nour Time: progress against the brief

Status as of 2026-09-25. Legend: ✅ done · 🟡 done with a noted difference · ⏳ pending.

Phase 1 is feature-complete on the emulator (Android 12 and 15). What's left is testing on real
phones, recorded audio, release plumbing, and Phase 2.

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
| 8 | Testing on Samsung and Xiaomi, tampering scenarios | ⏳ | Needs real phones. Checklist: [`testing-checklist.md`](testing-checklist.md). |
| 9 | Phase 2: remote control | ⏳ | Not started; needs your decisions (see below). |

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

## Strictly pending

1. **Device testing** (step 8) on Samsung and Xiaomi: PiP, battery savers, autostart, reboot during a lock, Force stop / uninstall attempts, whole-phone screen-off. [`testing-checklist.md`](testing-checklist.md).
2. **Audio assets:** a short recorded voice message for ages 3–6 (masculine and feminine) and a soft lullaby for the sleep screen.
3. **Release:** signing keystore, Play App Signing, store listing, Accessibility demo video.
4. **Phase 2 decisions:** pairing method (code / QR / parent mode), Firebase project and region, what the parent can change remotely, how bonus time interacts with the lock period.
5. **Optional:** Lottie animations if an illustrator provides them; Device Owner mode if Safe Mode must be blocked.

## Known limitations (documented, not bugs)
- Safe Mode disables all third-party apps; blocking it needs Device Owner (factory reset + adb).
- The usage-stats fallback can't see picture-in-picture windows (only Accessibility can).
- The lock period doesn't progress while the phone is powered off (by design, approved).
- Changing the phone's time zone shifts bedtime and the schedule (the Settings app is protected, so this needs the parent).
