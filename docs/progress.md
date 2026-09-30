# Nour Time: progress against the brief

Status as of 2026-09-30 (latest work: [`handoff.md` §14](handoff.md#14-resume-here-2026-09-30-end-of-day)). A copy of the brief with the status of every requirement, plus Phase 4, is in
[`brief-with-status.md`](brief-with-status.md). Legend: ✅ done · 🟡 done with a noted difference · ⏳ pending.

Phase 1 is feature-complete on the emulator (Android 12 and 15). What's left is testing on real
phones, recorded audio, release plumbing, Phase 1.5 (below) and Phase 2.

**2026-09-30:** the **Learning Hub** is built on branch `learning-hub` (not merged): four games
(Smart Math, Letters & Words, Number Connect, Coloring Match), earned minutes as a break inside the
lock, parent limits, and all content as data (JSON packs checked by the tests). Nine more games are on
the roadmap. See [Learning Hub](#learning-hub-gamification--education-planned-and-built-2026-09-30).
Next: the parent's phone shows the Learning Hub settings and the child's minutes.

**2026-09-30 night (owner asleep):** content expansion of the four games and the first roadmap games,
one commit each; see [`handoff.md` §15](handoff.md#15-night-of-2026-09-30--10-01-owner-asleep-more-content-then-new-games).

**2026-09-29:** Phase 2 and Phase 4 are merged and tested against the real Firebase project; the UX
backlog items U1–U4 (time pickers, single dashboard, Forgot PIN, language switcher) are built and
emulator-tested, waiting for the owner's test on the Honor.

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
| 9 | Phase 1.5: fixes and UX requests from device testing | 🟡 | All four tasks done on the emulator 2026-09-27; re-test on Honor. See [Phase 1.5](#phase-15-scheduled-2026-09-26). |
| 10 | Phase 2: remote control | 🟡 | Built and tested on two emulators with the Firebase emulator (2026-09-27), branch `phase2-remote-control`. Needs your real Firebase project, a real-phone test, and account deletion before publishing. See [Phase 2](#phase-2-remote-control-built-2026-09-27). |

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
(Accessibility declaration text, permission justifications, Data safety answers). `INTERNET` is used only by Phase 2
(Firebase, after pairing); backups disabled. ⏳ Release signing, store listing, Accessibility demo video.

### §12 Design and UX
✅ Palette, bundled Cairo/Nunito, 16 dp cards, bottom bar (Home / Apps / Schedule / Settings), gold
budget ring, status card, per-app bars, Apps list with switches and search, colour timeline (tap to
edit, drag to move; resize through the edit dialog), gold / outline / coral buttons ≥ 48 dp, large
PIN pad with dots and a light vibration, parent dark mode, RTL with mirrored icons, fade + scale
transition, friendly empty states, subtle "للأهل" button, positive child copy.
🟡 Lottie → code-drawn animations. ⏳ Lullaby for the sleep screen.
✅ Accessibility pass (2026-09-27): palette contrast computed for both themes. Text passes WCAG AA
(the error coral was darkened to `#B84232`, 5.2:1 on cream); field and button outlines now reach 3:1
(`#958870` light, `#6F7CA8` dark); focused text fields use the text color instead of gold (1.5:1);
dialog buttons use the text color. TalkBack: the PIN dots announce "2 of 4 digits entered" (live
region), the onboarding back button is labeled. Kept on purpose: the faint "For parents" button on the
"Time's up" screen (the brief wants it subtle) and gold for the budget ring/filled PIN dots (the numbers
and announcements carry the information). ⏳ A full TalkBack walk-through on a real phone.

## Phase 1.5 (scheduled 2026-09-26)

Comes before Phase 2. Technical notes for each task are in [`handoff.md` §6](handoff.md#6-phase-15-design-notes).
**Standing rule for every task:** full Arabic (RTL) and English support, masculine and feminine child
copy where the child sees text, and parent dark mode.

| # | Task | Priority | Status |
|---|---|---|---|
| 0 | **Detection freeze after the Accessibility service reconnects** (found on Honor: limited app and Settings not blocked during a lock, no degraded alert) | P0 bug | 🟡 Fixed and verified on the emulator (2026-09-27); re-test on Honor |
| 1 | **Guided onboarding polish.** Permissions stay manual (Android doesn't let an app grant Accessibility or Device admin to itself, and Play forbids auto-clicking them). Make each step as guiding as possible: per-brand instructions (Samsung, Xiaomi, Honor/Huawei, Oppo/Vivo) for *Allow restricted settings* and autostart, return to the app automatically once a permission is on, and a final "Test protection" step. | P1 | 🟡 Done on the emulator (2026-09-27); brand texts need checking on real phones |
| 2 | **Educational content during the lock period.** Decided 2026-09-27: an **allow-list of apps** only (no mini-browser, so Phase 1 stays offline); time in them is free; it applies during lock periods, bedtime and whole-phone lock. The lock screen shows the allowed apps as big buttons. | P1 | 🟡 Done on the emulator (2026-09-27) |
| 3 | **Smoother Settings / uninstall protection.** Replace the flashing cover with a calm screen that asks for the PIN (plus the security question during a lock period). A correct answer opens Settings normally. Add *Uninstall Nour Time* in the parent Settings tab: PIN + security question → Nour Time removes its own Device admin → Android's normal uninstall dialog. | P1 | 🟡 Done on the emulator (2026-09-27); the "flashing" didn't reproduce there, re-check on Honor |
| 4 | **Language support.** Arabic and English stay complete for every new screen and string (standing requirement, checked in review). | Always | ✅ Ongoing |

**Task 3 notes to confirm:**
- Android can't tell *which* Settings page is open without reading screen content (ruled out), so
  the PIN screen appears for the whole Settings app, as today, just calmer.
- Once Device admin is removed it can't be switched back on silently. If the parent then decides
  not to uninstall, Nour Time asks them to turn it back on (one system confirmation).

## Phase 2: remote control (built 2026-09-27)

Decisions (2026-09-27): same app with two modes · parent signs in with Google · pairing by QR code or
6-digit code, confirmed on the child's phone · parent sees status and usage, gives extra time, locks or
ends the lock, and changes every setting · extra time during a lock ends it and gives exactly that time.
Plan: [`superpowers/plans/2026-09-27-phase2-remote-control.md`](superpowers/plans/2026-09-27-phase2-remote-control.md).

| # | Task | Status |
|---|---|---|
| 1 | Firebase emulator tooling (`firebase/`) | ✅ |
| 2 | Firestore rules + 34 rules tests | ✅ |
| 3 | Firebase in the app (emulator in debug) | ✅ |
| 4 | Timer commands: bonus, lock now, end lock | ✅ |
| 5 | First launch: child phone or parent phone | ✅ |
| 6 | Pure model: pairing code, settings sync, commands, status | ✅ |
| 7 | Child phone: pair by QR/code, confirm, disconnect | ✅ Emulator |
| 8 | Child phone sync: status, usage, apps, settings both ways, commands, removal | ✅ Emulator |
| 9 | Parent phone: Google sign-in, child list, add a child | ✅ Emulator (QR scan needs a real camera) |
| 10 | Parent control screen | ✅ Emulator |
| 11 | Docs, privacy policy, Play notes | ✅ |

Pending for Phase 2: **your Firebase project** (README steps), a test on two real phones (including
scanning the QR code and a real Google sign-in), and an **account-deletion** flow (Play requirement
for apps with sign-in).

## Phase 2.5 and Phase 3 (2026-09-27)

| # | Task | Status |
|---|---|---|
| 1 | Account deletion: parent's *Delete my account*; child's data deleted on disconnect, removal and uninstall; rules + web page draft | ✅ Emulator (4 paths checked in Firestore) |
| 2 | Deferred review items (code TTL, status heartbeat, command expiry, bonus kept on budget edit, lost account, lazy Firebase) | ✅ Unit + rules tests, emulator |
| 3 | Release signing (`keystore.properties` / env vars), `bundleRelease` | ✅ Verified with a throwaway key |
| 4 | Accessibility pass (contrast, TalkBack labels) | ✅ Emulator; full TalkBack walk-through still to do on a phone |
| 5 | Store listing text, Arabic and English ([`store-listing.md`](store-listing.md)) | ✅ Draft |

Found and fixed while testing: after a restart (or re-pairing) the child's sync never started when the
device document hadn't changed (`MetadataChanges.INCLUDE`); "Add a child's phone" reopened on the last
result; the parent's child screen stayed blank after the phone was removed.

## Phase 4 and real-project testing (2026-09-28)

| # | Item | Status |
|---|---|---|
| 4a | Weekend limits | ✅ Emulator (branch `phase4`) |
| 4b | Usage history, last 7 days | ✅ Emulator |
| 4c | Ask for more time | ✅ Emulator end to end; 2 bugs fixed |
| — | Real Firebase project `nourtime-8d4ce` connected, rules published | ✅ |
| — | Honor onboarding with the real-project build | ✅ All permission steps auto-return; Test protection works |
| — | Phase 2 against the real project | ✅ Emulators (child API 31, parent API 35) and **Honor as the parent**: real-camera QR, commands, ask for time, restart while paired. Open: expired code, Firestore console check, Honor as the child |
| — | Bugs fixed while testing | ✅ Arabic pairing code shown reversed; approved bonus lost when the budget was lowered; parent said "tap Allow" while offline; privacy wording |

Issues found on Honor (details in [`handoff.md` §9](handoff.md#9-resume-here-updated-2026-09-28-0210)):
privacy wording says nothing leaves the phone (wrong once paired: **Play policy**), Honor-specific
Accessibility and overlay hints, the app-search list is cramped on 720p screens with the keyboard open,
and parent sign-in failed with a misleading message when the phone had no Google account (fixed).

## Learning Hub: Gamification & Education (planned and built 2026-09-30)

The owner's "Phase 3". When the time is up, the child sees a **Learning Hub** next to the usual
Time's up screen: a menu of mini-games (later also videos). Winning levels earns screen time.
Branch `learning-hub` (not merged yet). All four games are built and were played end to end on the
API 31 emulator from a real time-up lock, in Arabic (plus English letters). Morning summary and test
list: [`handoff.md` §12](handoff.md#12-learning-hub-2026-09-30-overnight).

### Games

| # | Game | Interaction | Status |
|---|---|---|---|
| L1 | **Smart Math** | Multiple choice. +, −, ×, ÷, comparisons (<, >, =) and missing numbers (7 + ? = 12), 45 levels from "1 + 2" with dots to mixed operations up to 1000 and ×12. Tap any number to hear it (TTS). Western (123) / Eastern (١٢٣) numerals toggle. | ✅ 45 levels (2026-09-30 night) |
| L2 | **Letters & Words** (Arabic + English) | Multiple choice with audio. Letter → word, letter → picture, word → picture, picture → word (reading), color name ↔ color, by category (animals, food, vehicles, body, nature, objects, clothes, toys, people, places). Tap the letter or word to hear "A, Apple" / "أ، أرنب". | ✅ 27 levels, 28 Arabic + 25 English letters, 176 words, 11 colors (2026-09-30 night) |
| L3 | **Number Connect** | Drawing: drag from dot 1 to 2 to 3 over a faded outline; segments can be lines or curves. | ✅ 33 drawings from a 3-dot triangle to a 16-point sun, numbers spoken as reached (2026-09-30 night) |
| L4 | **Coloring Match** | Tap a palette color, then tap a region of a black-and-white drawing to fill it, matching a colored reference. | ✅ 26 pictures from a 2-color balloon to a 7-color rainbow, up to 2 wrong colors in the palette (2026-09-30 night) |
| G1 | **Listen & Find** | Multiple choice by ear: a big speaker button says a word, color, letter name or number (TTS, in the chosen words language); the child taps the picture, swatch, letter or number. Written fallback without a voice. | ✅ 20 levels from 2 animal pictures to a mixed 10-question champion (2026-09-30 night) |
| G2 | **What Comes Next?** | Multiple choice: a row of colors, shapes, pictures or numbers ends in "?"; pick what continues it. Numbers read right to left with ١٢٣, each on its own chip. | ✅ 23 levels from red-green-red-green to doubling and steps of 25 up to 200 (2026-10-01 night) |
| G3 | **Tell the Time** | Multiple choice: read an analog clock and pick the written time, or the other way round. Clock numbers in the child's numerals. | ✅ 17 levels from o'clock with 2 choices to any minute (2026-10-01 night) |
| G4 | **Letter Tracing** | Drawing: follow each stroke of a big letter from its green start dot in the arrow's direction, in order, then tap its dots. Straying off the line is a mistake; lifting the finger keeps the progress. Arabic or English (the words language). | ✅ 28 Arabic letters (ا first, ظ last) and 26 English capitals (L first, S last) (2026-10-01 night) |
| G5 | **Memory Match** | Card grid: turn two cards, a pair stays up, a mismatch turns back after 1.2 s; cards flip and say what they show. Same picture, word ↔ picture, letter ↔ picture, number ↔ dots, color ↔ name. | ✅ 20 levels from 2 pairs of animals to a mixed table of 8 pairs (2026-10-01 night) |
| G6 | **Word Builder** | Tiles: see (and hear) a picture's word, put its letters in order by tapping them or dragging them up; the word is written as it grows, so Arabic letters join. Wrong tiles shake. | ✅ 11 levels from 2–3 letters with the word shown faded to 6–8 letters with 4 wrong tiles (2026-10-01 night) |
| G7 | **Sorting** | Drag and drop: one picture or number at a time, dropped into one of 2–3 group boxes (or tap a box); the right box counts it, the wrong one sends it back. | ✅ 13 levels from animals/food with 4 things to three groups of 12 and even/odd up to 1000 (2026-10-01 night) |
| G8 | **Little Shop** | A thing with a price tag; tap coins from the purse onto the counter to pay exactly (or give back the change from a note); going over sends the coins back. | ✅ 10 levels from 1-coins up to 5 to paying and giving change up to 200 (2026-10-01 night) |

### Goal: Google Play at scale
The four games prove the engine; the shipped **content** (45 math levels, 27 letters levels with 176
words, 33 drawings, 26 pictures after the 2026-09-30 night) is still a start. A public release needs far more content in several languages, plus real
illustrations. So all content is **data** (JSON packs), not code: adding a level, word, language,
drawing or illustration is a file edit, checked automatically by the tests. Authoring guide:
[`content-packs.md`](content-packs.md).

### Architecture

```
assets/learning/                content packs (JSON) + images/ (illustrations, optional)
core/learning/                  pure Kotlin, unit tested, no Android
  content/ContentLoader         reads + checks packs; bad items left out and reported, never a crash
  content/ContentJson           the pack file formats (schema 1; unknown fields ignored)
  Content                       Level (stable id), GamePack (levels + start per age), concepts, letters
  MathGame / LettersGame        question generators (a level spec gives endless questions)
  ConnectDots / Coloring        drawing games; Coloring regions include SVG paths (SvgPath)
  Round, Stars, RewardPolicy    scoring and minutes
  LevelProgress                 best stars per level **id** (safe when levels are added or reordered)
data/learning/
  LearningContentRepository     packs from assets, loaded once per game off the main thread
  LearningRepository            DataStore: progress, bank, minutes today, parent settings
feature/learning/
  LearningHub, QuestionScreen, DrawingScreens, LearningPicture (illustration or emoji), Speaker
```

**Why JSON packs, not a database (owner's question, 2026-09-30).** JSON is the source format content
people can write, review and diff, and that can later be downloaded as packs. It's small and fast:
each game's pack is parsed once when the hub opens, off the main thread; a unit test parses and checks
a **10,000-word** pack (time printed in the test output). A Room database only pays off for hundreds
of thousands of rows or search; the loader sits behind one file interface, so a prebuilt database (or
downloaded packs, or Play Asset Delivery for large image sets) can replace it without touching the
games. SVG: Android doesn't render SVG files, so drawings keep their SVG **path data** in the pack;
Compose's own `PathParser` draws it exactly, and a pure-Kotlin flattener makes it tappable.

**Mini-games engine.** Every game supplies a list of levels; a level produces a list of
questions from a seed. Math and Letters share one question model (a prompt plus 3–4 choices), so
they share the round runner, feedback, scoring and level-done screen. Number Connect and Coloring
have their own interaction but report the same result (`stars`, `passed`) to the same engine, so
progress, tutorials and rewards work the same for all four.

**Levels and tutorials.** Levels unlock one by one; the first unlocked level depends on the child's
age group (3–6 starts at the beginning, older children skip the easiest). The first time a game
opens, its first question is a **tutorial**: a pulsing hand points at the right answer and the
question is read aloud; it doesn't count toward the score. Stars come from first-try accuracy
(3 stars ≥ 90 %, 2 stars ≥ 70 %, 1 star otherwise). A wrong tap shakes the card and the child tries
again, so every level ends on success.

**Text-to-speech.** One `Speaker` per open hub (created on open, shut down on close). It speaks
with the game's language (`ar` or `en`), independent of the app language. Numbers are given to the
engine as digits with the right locale, so it says "eighty-nine" / "تسعة وثمانون" itself. If the
phone has no voice for that language (some OEM engines lack Arabic), the games still work without
sound and the speaker icon is hidden; the parent's Settings card says how to install the voice.

**Rewards.** Two decisions made here, for the owner to confirm:
1. **Earned time is a break inside the lock, not a parent bonus.** `TimerCommand.Bonus` ends a lock
   and, when the bonus runs out, a *full new* lock period starts (6 h by default). That's fine for a
   parent's gift but would punish a child who earns 5 minutes near the end of a lock. So
   `TimeRules.reward` opens the apps for the earned minutes while the lock clock keeps
   running in the background: when the minutes run out the lock continues with what's left; if the
   lock ends meanwhile, the normal full refill happens.
2. **Minutes are banked.** Each won level (at least 2 stars, so random tapping doesn't pay) adds
   the parent's "minutes per level" to a bank, up to the parent's daily maximum. The child taps
   **Use my minutes** when ready, so the hub doesn't vanish after every level.

Parent limits (Settings → Learning): Learning Hub on/off (default on), minutes per won level
(default 5), daily maximum (default 15 min; "0" = learning without rewards). The hub only appears in
**time's up** locks, not at bedtime or on protected Settings screens. Syncing these settings to the
parent's phone, and showing "minutes earned today" there, comes after the games work.

**Pictures.** Emoji are placeholders; real illustrations are planned (owner, 2026-09-30). A concept or
drawing names its illustration (`"image": "apple"` → `images/apple.webp`) and the app shows it,
falling back to the emoji until the file exists.

**Arabic is right to left** (owner, 2026-09-30): with ١٢٣ numerals, equations are written right to
left like Arabic schoolbooks and < / > are mirrored so the sign opens toward the bigger number.

### Number Connect (L3): design as built
- A shape is a list of dots in 0..1 coordinates; each segment to the next dot is a line or a curve
  (quadratic, with a control point). The faded outline is drawn from the same data, so one file
  describes the whole level. Shapes are in `connect/shapes.json`; an SVG-to-dots script can come
  when an illustrator provides drawings.
- A drag that starts near dot *k* and ends near dot *k+1* completes a segment; other drags snap back. Tolerance grows for ages 3–6. A quick swipe that passes over the
  next dot counts too (touch events come in steps).
- Tutorial level: an animated hand drags 1 → 2. Difficulty: more dots, curves, numbers beyond 10,
  then counting by 2s or letters (أ ب ت) instead of numbers.

### Coloring Match (L4): design as built
- A drawing is a list of closed regions (boxes, ovals, polygons or SVG paths), each with its target
  color, in `coloring/pictures.json`. The colored reference is the **same drawing** rendered with the
  target colors, so no second image is needed.
- Tap-to-fill: pure-Kotlin hit test; the topmost region wins (e.g. the fish's eye over its body).
- Win when every region matches; the palette shows only the colors used (plus one distractor from
  level 3 on). Tutorial: hand taps a color, then the matching region.
- Content: 6 simple original pictures. Illustrated ones come as SVG path data (see the guide).

### Roadmap: nine more games (added 2026-09-30; being built one by one since the night of 2026-09-30)
All nine fit the same foundation: a **content pack** (JSON, checked by the tests), levels with ids,
stars → `RewardPolicy`, the tutorial hand, TTS and the Learning Hub menu. What each one adds is an
**interaction engine**. Several reuse one, so the order below builds each engine once.

| # | Game | Ages | Engine (new or reused) | Content pack | Phase |
|---|---|---|---|---|---|
| G1 | **Listen & Find**: hear a word, color, letter or number, tap it | 3–6 | Choice round (reused) with a new speaker prompt card; own pack `listen/levels.json` | concepts + language packs (reused) | A · ✅ built 2026-09-30 night, 20 levels |
| G2 | **What Comes Next?**: continue a pattern of shapes, colors, pictures or numbers | 4–9 | Choice round (reused) + `PatternGame` generator; compact prompt row | `patterns/levels.json`: kind, units (ab, aab, …), number steps / doubling | A · ✅ built 2026-10-01 night, 23 levels |
| G3 | **Tell the Time**: read an analog clock | 7–12 | Choice round with new clock and time cards | `clock/levels.json`: hours, halves, quarters, 5-minute steps, any minute | A · ✅ built 2026-10-01 night, 17 levels |
| G4 | **Letter Tracing**: trace a letter's strokes in order | 3–7 | **Trace engine** (new): resampled stroke paths, start point + direction, dots to tap | `tracing/<language>.json`: stroke paths per letter, stroke order | B · ✅ built 2026-10-01 night, 28 Arabic + 26 English letters |
| G5 | **Memory Match**: flip cards to find pairs (word ↔ picture, number ↔ dots) | 4–12 | **Card grid engine** (new): flip, match, moves → stars | `memory/levels.json`: pairs, pair kinds, categories | B · ✅ built 2026-10-01 night, 20 levels |
| G6 | **Word Builder**: drag letters to spell a word; Arabic letters join as they're placed | 6–12 | **Tile engine** (new): tap or drag a tile up onto the word, next letter in order | concepts + language packs (reused); `words/levels.json` | C · ✅ built 2026-10-01 night, 11 levels |
| G7 | **Sorting**: drag items into groups (fruit / animals, even / odd) | 4–9 | Drag and drop onto group boxes (hit-tested) + tap | `sorting/levels.json`: bins = categories or even/odd, names per language | C · ✅ built 2026-10-01 night, 13 levels |
| G8 | **Little Shop**: pay with coins, count change | 7–12 | Coin purse + counter (tap coins on, tap to take back) | `shop/levels.json`: coin set, price ranges, pay / change | C · ✅ built 2026-10-01 night, 10 levels |
| G9 | **Short surahs and du'as**: listen and repeat, optional for the family | all | **Audio player** (new): verses, repeat, progress | `audio/…`: licensed recitations + texts; downloaded packs (size) | D, after owner's decision on sources and licensing |

**Order.** Phase A adds games on the existing choice engine (cheapest, fastest to more content).
Phase B adds the path and card engines. Phase C adds drag and drop. Phase D needs audio licensing.
**Game registry (done with G1):** `feature/learning/GameRegistry.kt` lists every game (tile, level
screen option, pack, how a level starts). A new game is a `GameId`, one registry entry, its pack in
`HubContent`, and its engine and screen; menu, level screen, progress, stars and rewards follow.

## UX backlog (future polish phase)

| # | Item | Priority | Status |
|---|---|---|---|
| U1 | **Playful time pickers.** Replace the plain slider (time budget) and the fixed extra-time buttons with three interactive themes the parent chooses from in Settings, or sets to switch randomly: **(1) circular timer dial**, like a smart stopwatch, drag around the ring to set the time; **(2) drag-and-drop time blocks/tokens**, gamified, each token worth a set amount (e.g. 15 min); **(3) liquid fill**, a shape fills up as time is added. Needs: works on both phones (child Settings and the parent's remote screen), RTL for Arabic, TalkBack (each theme must still expose the value and +/- actions), large touch targets, and reduced-motion support. Keep the quick +15/+30/+1 h answers for "ask for more time". Requested by the owner 2026-09-28. | **High** | ✅ Built 2026-09-29 (`ea3a866`, on master): Dial / Coins / Liquid / Surprise me, chosen in Settings; emulator-tested in Arabic. Waiting for the owner's test on the Honor |
| U2 | **Single dashboard** instead of the bottom bar: quick actions (Lock now / End the lock), tiles for Apps, Schedule and Settings; Permissions inside Settings. Requested 2026-09-29. | High | ✅ Built 2026-09-29 (`1aa5f8e`); emulator-tested. Waiting for the Honor test |
| U3 | **Forgot PIN** recovery with the security question, from the app and the Time's up screen. Requested 2026-09-29. | High | ✅ Built 2026-09-29 (`06e0163`); emulator-tested. Waiting for the Honor test |
| U4 | **Language switcher** (Phone language / Arabic / English), all Android versions. Requested 2026-09-29. | High | ✅ Built 2026-09-29 (`e6a3f23`); tested on Android 12 and 15 emulators. Waiting for the Honor test |

## Strictly pending

1. **Your Firebase project** (README steps 1–8, including the TTL policy), then a test on two real phones (QR scan, real Google sign-in).
2. **Publish the account-deletion page** (`docs/account-deletion.md`, fill in the contact email) and put its URL and the privacy policy URL in Play Console.
3. **Device testing** (step 8) on Samsung and Xiaomi (Honor started): PiP, battery savers, autostart, reboot during a lock, Force stop / uninstall attempts, whole-phone screen-off. [`testing-checklist.md`](testing-checklist.md).
4. **Audio assets:** a short recorded voice message for ages 3–6 (masculine and feminine) and a soft lullaby for the sleep screen (the 3–6 description no longer promises the voice message).
5. **Release:** create the upload key (README "Release signing"), Play App Signing, screenshots, feature graphic and the Accessibility demo video.
6. **Optional:** Lottie animations if an illustrator provides them; Device Owner mode if Safe Mode must be blocked; push notifications to the parent (needs Cloud Functions / Blaze).

## Known limitations (documented, not bugs)
- Safe Mode disables all third-party apps; blocking it needs Device Owner (factory reset + adb).
- The usage-stats fallback can't see picture-in-picture windows (only Accessibility can).
- The lock period doesn't progress while the phone is powered off (by design, approved).
- Changing the phone's time zone shifts bedtime and the schedule (the Settings app is protected, so this needs the parent).
