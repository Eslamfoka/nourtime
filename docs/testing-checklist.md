# Device testing checklist (implementation step 8)

Run on at least one **Samsung** (One UI) and one **Xiaomi/Redmi** (MIUI/HyperOS) phone, with the
**debug** build so the Home screen shows the detection card and the *End budget now* / *End lock now*
buttons. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Don't run `uiautomator dump` while testing detection: it makes Android reconnect Nour Time's
Accessibility service.

Tip: `adb logcat -s NourA11y BlockCoordinator LockOverlay TimerService` shows every blocking decision.

## 1. Onboarding and permissions
- [ ] Sideloaded install on Android 13+: Accessibility switch is greyed out → "Open App info" → ⋮ → *Allow restricted settings* → switch works.
- [ ] Each permission step shows *Allowed* after returning from Settings.
- [ ] Xiaomi: the Autostart step appears and "Open settings" opens MIUI's autostart screen.
- [ ] Samsung: no Autostart step; check Settings → Battery → *Background usage limits* doesn't list Nour Time as sleeping.

## 2. Detection
- [ ] Open a limited app: Home debug card shows it as `foreground` and the ring counts down while it's open.
- [ ] Keyboard open in the limited app: timer keeps counting.
- [ ] Pull down the notification shade over the limited app: timer keeps counting.
- [ ] Home button / another app: timer stops.
- [ ] **Picture-in-picture** (YouTube: play a video, press Home): still counts; blocked when time is up.
- [ ] **Split screen** with a limited app: counts; blocked when time is up.
- [ ] Screen off and lock screen: nothing counts.

## 3. Time engine
- [ ] Two limited apps share one countdown (switching doesn't restart it).
- [ ] *End budget now* → lock period starts; Home shows *Locked* + refill countdown.
- [ ] *End lock now* → budget refills to the full amount.
- [ ] Raise/lower the budget in Settings while some was used: remaining adjusts by the difference.
- [ ] Daily reset: set it a few minutes ahead of now, wait → budget refills (only if ≥ 20 h since the last reset; set it on day 1, verify on day 2).

## 4. Lock screens
- [ ] Time up, *lock selected apps*: opening a limited app shows the "Time's up" screen immediately; any playing audio pauses; the *OK/حاضر* button goes home.
- [ ] Time up, *lock the whole phone*: every app is covered; no OK button; the lock screen (keyguard) and emergency calls remain reachable.
- [ ] Whole phone: about 8 seconds after the lock period starts (child on another app or the home screen) the screen turns off. Waking it shows the lock screen once, and it doesn't turn off again. If the parent unlocks the phone within those seconds, it doesn't turn off.
- [ ] Settings, App info and the uninstall dialog open straight on the PIN pad (no flashing). After PIN + answer, App info → Uninstall offers Android's *Deactivate & uninstall*.
- [ ] Settings → *Uninstall Nour Time* → answer → Android's uninstall dialog. Cancel it: Home shows Device admin → *Fix*.
- [ ] Parent path: *للأهل* → PIN → security question → overlay disappears for 15 minutes (or until the screen turns off).
- [ ] Whole phone: after the PIN, *Unlock the phone only* opens the phone but limited apps stay blocked.
- [ ] During a lock period, opening Nour Time asks the PIN **and** the security question.
- [ ] Wrong answers: after 4, an escalating wait appears (like the PIN).
- [ ] Bedtime: turn on with a window around "now" → limited apps blocked with the sleep screen even with budget left.
- [ ] Allowed during the lock: Settings → *Allowed during the lock* → choose an app. During a lock period and at bedtime the lock screen shows it under "You can still open"; tapping it opens it uncovered, in both lock types. In whole-phone mode, Home covers the phone again.
- [ ] Templates: Settings → *Preview the "Time's up" screen* for each period × age group, in Arabic and English, boy and girl.

## 5. Tampering (brief §3, step 8)
- [ ] **Clock change:** with *Automatic date & time* off, move the clock forward past the lock end / bedtime end → nothing unlocks early. (Changing it requires Settings, which is protected; test with the parent pass.)
- [ ] **Reboot during a lock:** lock stays; the remaining lock time doesn't shrink (time while powered off isn't counted).
- [ ] **Reboot:** protection comes back by itself (status notification reappears; Xiaomi needs Autostart).
- [ ] **Force stop:** Settings → Apps → Nour Time is covered by the "for grown-ups" screen. With the parent pass, Force stop works and Android turns the Accessibility service off → after reopening, Home shows *Protection needs attention* and limited apps are blocked (fail-closed) until it's turned back on.
- [ ] **Turn off Accessibility** (with the parent pass): alert notification appears; limited apps are blocked via the usage-stats fallback.
- [ ] **Uninstall:** from the launcher → Android refuses while Device admin is active. Settings → Device admin apps is covered by the "for grown-ups" screen.
- [ ] **Clear storage** from Settings → covered.
- [ ] **Safe mode** (hold power off → Safe mode): third-party apps are disabled. Known limitation (see report); note what the child could do.
- [ ] **Recents → swipe Nour Time away:** protection keeps running (foreground service).
- [ ] **Battery saver / ultra power saving** (Samsung) and **MIUI battery saver:** timer keeps working after 30+ minutes idle.

## 6. Parent UI
- [x] Arabic and English, light and dark mode, all four tabs.
- [x] Schedule: suggested day, tap to edit, drag to move, delete with confirmation.
- [x] Change PIN and security question from Settings.
- [x] Home "Used today" shows the minutes per app after using limited apps.

Run on the API 35 emulator, 2026-09-27: all pass. Dragging a short (1-hour) period didn't move it; fixed in `ScheduleTab.kt` (the drag now starts from where the finger went down). Polish found in the same run, all fixed afterwards: the Apps tab kept the search text typed during onboarding; the 3–6 age option mentioned a voice message that isn't recorded; the budget ring's rounded end left a notch when nearly full; the "change security question" screen from Settings said "Save and continue"; full-screen dialogs put their bottom button under the gesture bar (`FullScreenDialog`); dialog buttons were gold on cream (`NourDialogButton`).

## 7. Phase 2: parent's phone (needs two phones and a Firebase project or the emulator)
- [ ] Fresh install asks "Whose phone is this?"; an already set-up child phone goes straight to its PIN.
- [ ] Parent's phone: Google sign-in (real account needs the SHA-1 in Firebase).
- [ ] Child: Settings → Parent's phone → Connect → QR + code + countdown. Parent: Add a child's phone → **scan the QR** (real camera) → child shows "Allow this parent?" → Allow → parent's list shows the phone.
- [ ] Same with the typed code; a wrong code, an expired code (wait 10 min) and "Don't allow" each give a clear message.
- [ ] Parent: +15 min while locked → the child is unlocked with exactly 15 min; Lock now → the child's limited apps lock; End the lock → full budget.
- [ ] Parent changes the budget / lock type / limited apps / allowed apps / bedtime → the child's Settings show it within seconds; a change on the child shows up on the parent.
- [ ] Child phone offline (airplane mode) → parent sends extra time → nothing happens; back online → applied once.
- [ ] Parent: Remove this phone → the child's Settings show "Connect a parent's phone" again. Child: Disconnect → the phone disappears from the parent's list.
- [ ] Account deletion: child *Disconnect*, parent *Remove this phone*, parent *Delete my account* and child *Uninstall Nour Time* (cancel at Android's dialog) each leave no device, usage, app list or commands in Firestore (emulator REST, see handoff §7); the parent's account is gone after *Delete my account*. Child offline during *Disconnect* → "couldn't be deleted… Try again" → works once online. (Emulator, 2026-09-27: all pass.)
- [ ] Restart the child's phone while paired (or re-pair in the same session) → the parent's "Updated" time refreshes and a command still applies.
- [ ] Turn Accessibility off on the child → the parent sees "Protection needs attention" within about a minute.
- [ ] Arabic and English on both phones.

## 8. Phase 4: weekends, history, asking for time
- [ ] Settings → *Different limits on weekends*: on a weekend day the Home ring shows the weekend budget (the time left moves by the difference); the day chips start on Friday+Saturday in Arabic, Saturday+Sunday in English. (Emulator 2026-09-27: Sunday, 2 h weekend budget → "1:15:00 left of 2 hours".)
- [ ] The night before a weekend day uses the weekend bedtime; the night before a school day the normal one.
- [ ] The parent's phone shows and edits the same weekend section.
- [ ] Home → *Last 7 days*: a bar per day (today in gold), total, daily average, most used app and the change from the 7 days before. (Emulator 2026-09-27 with seeded days: 4 h 21 min, about 37 min a day, YouTube 3 h 6 min, 9% more.)
- [ ] The parent's phone shows the same card for the child; usage older than 14 days disappears from Firestore.
