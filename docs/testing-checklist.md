# Device testing checklist (implementation step 8)

Run on at least one **Samsung** (One UI) and one **Xiaomi/Redmi** (MIUI/HyperOS) phone, with the
**debug** build so the Home screen shows the detection card and the *End budget now* / *End lock now*
buttons. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Tip: `adb logcat -s BlockCoordinator LockOverlay TimerService` shows every blocking decision.

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
- [ ] Parent path: *للأهل* → PIN → security question → overlay disappears for 15 minutes (or until the screen turns off).
- [ ] Whole phone: after the PIN, *Unlock the phone only* opens the phone but limited apps stay blocked.
- [ ] During a lock period, opening Nour Time asks the PIN **and** the security question.
- [ ] Wrong answers: after 4, an escalating wait appears (like the PIN).
- [ ] Bedtime: turn on with a window around "now" → limited apps blocked with the sleep screen even with budget left.
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
- [ ] Arabic and English, light and dark mode, all four tabs.
- [ ] Schedule: suggested day, tap to edit, drag to move, delete with confirmation.
- [ ] Change PIN and security question from Settings.
- [ ] Home "Used today" shows the minutes per app after using limited apps.
