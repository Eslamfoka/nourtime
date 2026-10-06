# Task 6 draft: voice messages for ages 3–6 and the lullaby (needs the owner's approval)

Written 2026-10-06 while the owner slept. **Nothing has been generated yet** (it costs ElevenLabs
credits and needs the owner's OK on the wording). Each message is short (2–4 s), friendly and says what
to do next, following the on-screen titles in `strings.xml` (`tu_*_title`). Masculine (m) and feminine
(f) only differ where Arabic needs it.

Suggested voices: Egyptian = NOUR (owner's clone), Fusha = Jessica, English = Liz (American) / Ana
(British), so the message follows the voice pack the parent picked in Learning Hub settings.

| Period | Egyptian (m) | Egyptian (f) | Fusha (m) | Fusha (f) | English |
|---|---|---|---|---|---|
| Default | خلص وقتك يا بطل! نتقابل تاني بعد شوية | خلص وقتك يا بطلة! نتقابل تاني بعد شوية | انْتَهى وَقْتُكَ يا بَطَل! نَلْتَقي بَعْدَ قَليل | انْتَهى وَقْتُكِ يا بَطَلة! نَلْتَقي بَعْدَ قَليل | Your time is up, superstar! See you again soon |
| Play | يلا نلعب برّه الشاشة! هات الكورة ولا المكعبات | (same) | هَيّا نَلْعَبُ بَعيدًا عَنِ الشّاشة! | (same) | Let's play away from the screen! Grab your blocks or your ball |
| Study | يلا نتعلم حاجة جديدة سوا | (same) | هَيّا نَتَعَلَّمُ شَيْئًا جَديدًا مَعًا | (same) | Time to learn something new together |
| Meal | يلا ناكل سوا! اغسل إيديك الأول | يلا ناكل سوا! اغسلي إيديكي الأول | هَيّا نَأْكُلُ مَعًا! اغْسِلْ يَدَيْكَ أَوَّلًا | هَيّا نَأْكُلُ مَعًا! اغْسِلي يَدَيْكِ أَوَّلًا | Let's eat together! Wash your hands first |
| Sleep | تصبح على خير يا حبيبي، نام كويس | تصبحي على خير يا حبيبتي، نامي كويس | تُصْبِحُ عَلى خَيْر، نَمْ جَيِّدًا | تُصْبِحينَ عَلى خَيْر، نامي جَيِّدًا | Good night, sweetheart. Sleep well |
| Parents only | دي حاجة للكبار، اطلب من بابا أو ماما | دي حاجة للكبار، اطلبي من بابا أو ماما | هذا لِلْكِبار، اطْلُبْ مِنْ أَبيكَ أَوْ أُمِّك | هذا لِلْكِبار، اطْلُبي مِنْ أَبيكِ أَوْ أُمِّك | This part is for grown-ups. Ask Mom or Dad |

## Lullaby (sleep screen, optional)
A soft 30–60 s instrumental loop (music box or soft piano, no words, slow, fading in). Options:
1. A CC0 / public-domain recording (e.g. Brahms' Lullaby played on a music box, from Wikimedia Commons
   or Freesound with CC0) — free, needs the licence noted in Settings > Voice credits.
2. ElevenLabs sound generation ("gentle music box lullaby, slow, calm") — check that the plan's
   licence covers commercial use of generated sound effects.

## How it would plug in (after approval)
- Clips: `assets/audio/<pack>/timeup_<period>_<m|f>.mp3`, generated with
  `tools/audio/elevenlabs_generate.py` like the game clips.
- `LockOverlay.playChime()` (ages 3–6 only) plays the chime, then the period's clip from the chosen
  pack; falls back to the chime alone if a clip is missing. Sound toggle still mutes everything.
