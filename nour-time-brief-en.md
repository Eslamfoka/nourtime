# Nour Time – وقت نور — App Brief (Android)

**App name:** Nour Time (Arabic: وقت نور). Use it as the app name and in the package name, e.g. `com.nourtime.app`.

> This brief is for Claude Code. Build in phases, and finish Phase 1 completely before moving on.
> Child-facing messages are intentionally kept in Arabic, because they must appear in the app exactly as written.

## 1. Overview
Nour Time is an Android app for parents that limits a child's usage of specific apps (YouTube, TikTok, Facebook, etc.). It runs automatically in the background. As soon as the child opens any selected app, a timer starts. When the time budget runs out, a child-friendly "Time's up" screen appears, and either the whole device or only the selected apps get locked, depending on the parent's setting. Only the parent's PIN can unlock it.

- **First use:** the device of a 5-year-old girl.
- **Later goal:** publish on Google Play for ages 3–12.
- **Requirement:** comply with Google Play's parental-control policies from day one.

## 2. Time Logic (most important)
- **One shared budget:** for example 60 minutes, configurable by the parent. The budget is consumed by any selected app. Switching from one selected app to another continues the same countdown instead of restarting it.
- **Only active usage counts:** the timer runs while a selected app is in the foreground. It pauses when the child leaves the selected apps or the screen turns off.
- **Lock period:** when the budget hits zero, a lock period starts (6 hours by default, configurable). When the lock period ends, the budget refills completely and automatically.
- **Unused budget:** if the budget isn't used up, the remainder stays available. There is also an optional setting to reset the budget daily at a time the parent chooses.
- **No warning before the end:** the lock happens immediately when the budget runs out.
- **Tamper-proof timing:** measure time with `SystemClock.elapsedRealtime()`, not the device clock, so changing the clock manually can't break the timer. Persist all state so it survives reboots.

## 3. Locking & Protection
### Lock type (parent setting)
- **Whole device:** the app's own full-screen lock (overlay / lock activity) covers everything and can only be dismissed with the PIN. The app can also turn off the screen with `DevicePolicyManager.lockNow()`.
- **Selected apps only:** any attempt to open a selected app shows the "Time's up" screen immediately, while the rest of the device keeps working.

### Security layers
1. **Parent PIN:** unlocks the lock screen and opens the app's settings. Store it hashed.
2. **Parent-written security question:** if the child learns the PIN and unlocks during a lock period, the selected apps (and the app's settings) still won't open until the correct answer is entered. Store the answer hashed after normalization: strip spaces, ignore letter case, and treat Arabic hamza/alef variants as equal.
3. **Uninstall & disable protection:**
   - Use Device Admin to prevent uninstalling the app.
   - Block sensitive settings screens without the PIN: Accessibility, App info, Device admin, and Force stop.
   - Request an exemption from Battery Optimization.
   - Guide the parent to enable Autostart on Xiaomi, Oppo, Huawei, and similar devices.
   - Restart automatically on `BOOT_COMPLETED`.
4. **Brute-force protection:** after several wrong PIN attempts, add an escalating delay.

## 4. "Time's up" Screen (Templates)
The design changes based on two factors:

**A) Age group** (set by the parent):
- **Ages 3–6:** large characters and illustrations, cheerful colors, a short sound or voice message, and as little text as possible.
- **Ages 7–9:** illustrations with a short message and a countdown to the next refill.
- **Ages 10–12:** a calmer design, a written message, and the remaining time shown clearly.

**B) Time of day** (based on the device clock): the parent defines a schedule of periods, and each period gets its own message and design:
- Study time, e.g. "دلوقتي وقت المذاكرة 📚"
- Play time, e.g. "روح العب لعبة حلوة برّه الشاشة"
- Meal time, e.g. "يلا ناكل سوا 🍽️"
- Sleep time, e.g. "تصبح على خير 🌙"

**Technical notes:** use original artwork only (vector / Lottie), with no copyrighted characters. Make the template system easy to extend with new themes later.

## 5. Bedtime (optional feature)
- An on/off toggle in the settings.
- When enabled, usage is fully blocked during a period the parent sets (e.g. 9 PM to 7 AM), even if budget remains.

## 6. Parent Settings (on the child's device)
- Choose the selected apps from a list of all installed apps, with icons and search.
- Budget length and lock-period length.
- Lock type: whole device or selected apps only.
- Age group and the daily period schedule for the "Time's up" screen.
- Bedtime (optional).
- Daily reset (optional).
- Change the PIN and the security question.
- A simple stats screen: time used today, in total and per app.

## 7. Onboarding
1. Welcome screen explaining the idea.
2. A clear **Prominent Disclosure** for every permission and why it's needed (required by Google Play).
3. Grant permissions step by step, with an explanation for each: Accessibility, Usage Access, Display over other apps, Device Admin, Notifications, and Battery Optimization.
4. Create a PIN and a security question.
5. Choose the apps, set the time, and pick the age group.

## 8. Phase 2: Remote Parent Control (optional)
- **Pairing:** the parent pairs their phone with the child's device using a pairing code or QR, or uses the same app in "Parent mode".
- **Rewards:** the parent can grant bonus time as a reward, e.g. "you did something nice, here's 20 minutes". Bonus time can't be added from the child's device itself, only remotely by the parent.
- **Monitoring & control:** view usage and the current budget, and change settings remotely.
- **Suggested tech:** Firebase (Auth + Firestore + FCM).
- **Important constraint:** Phase 1 must work fully offline. Remote control is an add-on on top of it.

## 9. Suggested Tech Stack
- **Language & UI:** Kotlin + Jetpack Compose + Material 3, minSdk 26.
- **Foreground app detection:** AccessibilityService as the primary method, with UsageStatsManager as a fallback.
- **Background work:** a Foreground Service for the timer.
- **Storage:** Room + DataStore.
- **Scheduling:** AlarmManager for refills and bedtime.
- **Protection:** DeviceAdminReceiver.
- **Architecture:** Hilt for DI, clean MVVM.
- **Languages:** Arabic (RTL) and English from the start.

## 10. Google Play Readiness (Phase 3)
- A clear privacy policy.
- Compliance with the Families policy and the Accessibility API policy, declaring the parental-control purpose.
- No collection of the child's personal data in Phase 1, since everything is stored on the device.

## 11. Implementation Order
> Apply the design system in Section 12 from the very first screen.

1. Onboarding, permissions, and PIN.
2. App selection and launch detection.
3. Time engine: budget, pausing, lock period, and refill.
4. Both lock-screen types and the security question.
5. Uninstall and disable protection.
6. Templates by age and time of day.
7. Bedtime and stats.
8. Thorough testing on Samsung and Xiaomi devices, including tampering scenarios: reboot, clock change, Force stop, and uninstall attempts.
9. Phase 2: remote control.

## 12. Design & UX (UI/UX)

### Visual identity
- **Main character:** "Nour", a small, glowing, friendly star. It is an original character, not copied from any existing work, and appears on all child screens in different states: playing, studying, eating, sleeping.
- **Logo:** the Nour star with a simple clock face in its center.
- **Colors:**

| Use | Color | Code |
|---|---|---|
| Primary (the "light") | Warm golden yellow | `#FFC857` |
| Text, headings, and sleep-mode background | Night navy | `#1E2A4A` |
| Light background | Cream | `#FFF8EC` |
| Accent for important buttons | Coral | `#FF7A6B` |
| Success and enabled states | Mint | `#5CC8A8` |

- **Dark mode:** supported in the parent UI.
- **Fonts:** Cairo or Tajawal for Arabic, Nunito for English. Bundle the fonts with the app rather than downloading them.

### Parent UI: calm and practical
- **General look:** cards with rounded corners (16dp), comfortable spacing, and simple icons (Material Symbols Rounded).
- **Navigation:** a bottom bar with 4 sections: Home, Apps, Schedule, Settings.
- **Home:**
  - A large circular ring showing the remaining budget, in gold.
  - A status card showing one of three states: available, in use now, or locked with a countdown to the next refill.
  - Simple bars showing today's usage per app.
- **Apps:** a list with each app's icon, its name, and an on/off switch, with a search field at the top.
- **Schedule:** a daily timeline split into colored periods, one color per period: study blue, play green, meal orange, sleep purple. Any period can be edited by dragging or tapping it.
- **Buttons:**
  - Primary buttons are filled gold with navy text.
  - Secondary buttons are outline only.
  - Dangerous actions, such as deleting or disabling protection, use coral and require confirmation.
  - The minimum size for any button is 48dp.
- **PIN screen:** a large number pad, dots that fill as digits are entered, and a light vibration on a wrong PIN.

### Child screens: playful and positive
- **General principles:**
  - Nothing should feel like a punishment or an error: no red, no X marks, and no word for "forbidden".
  - The message is always positive, e.g. "برافو! خلّصت وقتك، يلا نعمل حاجة حلوة".
  - Animations are smooth (Lottie), and sound is optional with its own toggle in the settings.
- **Parent button:** small and subtle in a corner, a 🔒 icon labeled "للأهل", which opens the PIN screen. It shouldn't catch the child's attention.

**Templates by time of day:**

| Period | Background | Nour | Message |
|---|---|---|---|
| Play | Light sky and clouds | Bouncing a ball | "يلا نلعب برّه الشاشة!" with suggestions: drawing, blocks, ball |
| Study | Cream and light blue | Holding a book and pencil | "وقت نتعلم حاجة جديدة 📚" |
| Meal | Warm orange | A plate in front of her | "يلا ناكل سوا 🍽️" |
| Sleep | Navy, twinkling stars, and a moon | Sleeping | "تصبحي على خير 🌙" with an optional soft lullaby |
| Default | Light gold | Waving goodbye | "خلص وقتك! نرجع نتقابل بعد شوية" with a refill countdown |

**Adjustments by age:**
- **Ages 3–6:** rely on images and sound, with a big character in the center of the screen, a short voice message, and almost no text.
- **Ages 7–9:** short text, a circular countdown to the refill, and 3 cards with suggested off-screen activities.
- **Ages 10–12:** a cleaner design with calmer colors, a clear countdown, a motivational line, and no big cartoon character.

### Experience details
- The transition to the lock screen uses a smooth animation (fade + scale), never an abrupt cut.
- Full RTL support, with directional icons mirrored.
- Sufficient color contrast for readability (WCAG AA).
- Friendly empty states, e.g. a message saying no apps have been selected yet instead of a blank screen.
