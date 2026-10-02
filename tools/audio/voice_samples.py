"""
Records the same few words in several voices into dist/voice-samples/, so a person can listen and
pick the voice for the games (then change VOICES in generate_audio.py and run it with --force).

    python tools/audio/voice_samples.py
"""
import asyncio
from pathlib import Path

import generate_audio as g

OUT = g.ROOT / "dist" / "voice-samples"
SAMPLES = {
    "en": (["Dog", "Bed", "Book", "Bread", "Crocodile", "A, Apple", "B", "forty-five"],
           ["en-GB-MaisieNeural", "en-US-AnaNeural", "en-GB-SoniaNeural", "en-US-JennyNeural"]),
    "ar": (["عنكبوت", "ذرة", "أفوكادو", "ألف، أرنب", "ب", "+45", "وشاح"],
           ["ar-SA-ZariyahNeural", "ar-SA-HamedNeural", "ar-KW-NouraNeural", "ar-AE-FatimaNeural"]),
}


async def main():
    for lang, (words, voices) in SAMPLES.items():
        for voice in voices:
            g.VOICES[lang] = {**g.VOICES[lang], "voice": voice}
            for i, w in enumerate(words):
                voiced = g.VOICED[lang](w) if lang == "ar" or len(w) == 1 or ", " in w else w
                raw = await g.synthesize(voiced, lang)
                g.encode(raw, OUT / voice / f"{i + 1:02d}.mp3")
            print("ok", voice, flush=True)
    (OUT / "README.txt").write_text(
        "Same words in each voice (01.mp3, 02.mp3, ...), processed exactly like the game clips.\n"
        "English: " + ", ".join(SAMPLES["en"][0]) + "\nArabic: " + ", ".join(SAMPLES["ar"][0]) + "\n"
        "The games use en-GB-MaisieNeural and ar-SA-ZariyahNeural today.\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    asyncio.run(main())
