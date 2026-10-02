"""
Records the Learning Hub's voice clips with Microsoft neural voices (edge-tts) into
app/src/main/assets/audio/<language>/<key>.mp3.

    python tools/audio/generate_audio.py            # record what's missing or changed
    python tools/audio/generate_audio.py --force    # record everything again

The clip lists come from the app itself: the unit test SpeechCatalogTest writes them to
app/build/speech/<language>.txt (this script runs it when they're missing; --lists re-runs it).
Each clip's file name is the first 16 hex digits of the SHA-1 of its text, as in SpeechCatalog.kt.

Arabic is Fusha with full tashkeel (tools/audio/ar_tashkeel.json): a word without an entry
stops the run, so no Arabic is ever recorded unvocalized. Clips are trimmed, loudness-matched and
stored as mono MP3 (plays on every Android phone). Needs: pip install edge-tts, ffmpeg on PATH.

To move to a paid voice later (Azure, same voices), only `synthesize` changes.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import inspect
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import edge_tts
import edge_tts.communicate as communicate

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "audio"
LISTS = ROOT / "app" / "build" / "speech"
HERE = Path(__file__).resolve().parent
MANIFEST = HERE / "manifest.json"

VOICES = {
    # Arabic has no child voices; Zariyah is a warm, clear Fusha voice.
    "ar": {"voice": "ar-SA-ZariyahNeural", "rate": "-10%", "pitch": "+0Hz"},
    # A child's voice (the owner's pick from dist/voice-samples, 2026-10-02).
    "en": {"voice": "en-US-AnaNeural", "rate": "-5%", "pitch": "+0Hz"},
}

# Use the 96 kbps source (edge-tts asks for 48 kbps); we re-encode after trimming anyway.
_src = inspect.getsource(communicate)
_hi = _src.replace("audio-24khz-48kbitrate-mono-mp3", "audio-24khz-96kbitrate-mono-mp3")
if _hi != _src:
    exec(compile(_hi, communicate.__file__, "exec"), communicate.__dict__)

# ---------------------------------------------------------------- what the voice is given

AR = json.loads((HERE / "ar_tashkeel.json").read_text(encoding="utf-8"))
AR_WORDS = {**AR["words"], **AR["colors"], **AR["labels"]}
AR_NAMES = AR["letterNames"]
AR_FORMS = {k: v for k, v in AR["letterForms"].items() if not k.startswith("_")}

AR_UNITS = ["صِفْر", "وَاحِد", "اثْنَان", "ثَلَاثَة", "أَرْبَعَة", "خَمْسَة", "سِتَّة", "سَبْعَة", "ثَمَانِيَة", "تِسْعَة", "عَشَرَة"]
# Inside a compound ("five and twenty") the unit carries its nominative ending.
AR_UNITS_IN = ["", "وَاحِدٌ", "اثْنَانِ", "ثَلَاثَةٌ", "أَرْبَعَةٌ", "خَمْسَةٌ", "سِتَّةٌ", "سَبْعَةٌ", "ثَمَانِيَةٌ", "تِسْعَةٌ"]
AR_TEENS_UNIT = ["", "أَحَدَ", "اثْنَا", "ثَلَاثَةَ", "أَرْبَعَةَ", "خَمْسَةَ", "سِتَّةَ", "سَبْعَةَ", "ثَمَانِيَةَ", "تِسْعَةَ"]
AR_TENS = ["", "", "عِشْرُون", "ثَلَاثُون", "أَرْبَعُون", "خَمْسُون", "سِتُّون", "سَبْعُون", "ثَمَانُون", "تِسْعُون"]
AR_HUNDREDS = ["", "مِئَة", "مِئَتَان", "ثَلَاثُمِئَة", "أَرْبَعُمِئَة", "خَمْسُمِئَة", "سِتُّمِئَة", "سَبْعُمِئَة", "ثَمَانِمِئَة", "تِسْعُمِئَة"]


def ar_number(n: int) -> str:
    if n == 1000:
        return "أَلْف"
    if n >= 100 and n % 100 == 0:
        return AR_HUNDREDS[n // 100]
    if n <= 10:
        return AR_UNITS[n]
    if n < 20:
        return f"{AR_TEENS_UNIT[n - 10]} عَشَر"
    tens, unit = divmod(n, 10)
    return AR_TENS[tens] if unit == 0 else f"{AR_UNITS_IN[unit]} وَ{AR_TENS[tens]}"


EN_UNITS = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split()
EN_TENS = "_ _ twenty thirty forty fifty sixty seventy eighty ninety".split()


def en_number(n: int) -> str:
    if n == 1000:
        return "one thousand"
    if n >= 100 and n % 100 == 0:
        return f"{EN_UNITS[n // 100]} hundred"
    if n < 20:
        return EN_UNITS[n]
    tens, unit = divmod(n, 10)
    return EN_TENS[tens] if unit == 0 else f"{EN_TENS[tens]}-{EN_UNITS[unit]}"


class Unvocalized(Exception):
    pass


def ar_voiced(clip: str) -> str:
    if clip.startswith("+") and clip[1:].isdigit():
        return "وَ" + ar_number(int(clip[1:]))
    if clip.isdigit():
        return ar_number(int(clip))
    if "، " in clip:  # "ألف، أرنب": a letter's name, then its word
        name, word = clip.split("، ", 1)
        return f"{ar_voiced(name)}، {ar_voiced(word)}"
    if clip in AR_WORDS:
        return AR_WORDS[clip]
    if clip in AR_NAMES:
        return AR_NAMES[clip]
    if len(clip) == 1:  # a letter tile: its name
        if clip in AR_FORMS:
            return AR_FORMS[clip]
        for name, voiced in AR_NAMES.items():
            if name[0] == clip or (clip == "ا" and name == "ألف"):
                return voiced
    raise Unvocalized(clip)


def en_voiced(clip: str) -> str:
    if clip.isdigit():
        return en_number(int(clip))
    if ", " in clip:  # "A, Apple"
        name, word = clip.split(", ", 1)
        return f"{en_voiced(name)} ... {word}"
    if len(clip) == 1 and clip.isalpha():
        # A letter alone is read as its name ("a" alone would be the word "uh").
        return f"{clip.upper()}."
    return clip


VOICED = {"ar": ar_voiced, "en": en_voiced}

# ---------------------------------------------------------------- recording


def key(clip: str) -> str:
    return hashlib.sha1(clip.encode("utf-8")).hexdigest()[:16]


# Premium voice for the placeholders (clips with no human recording), used when ELEVENLABS_API_KEY is set.
ELEVENLABS = {"model": "eleven_multilingual_v2",
              # Voice ids from the ElevenLabs voice library; set ELEVENLABS_VOICE_AR / _EN to choose.
              "ar": os.environ.get("ELEVENLABS_VOICE_AR", ""), "en": os.environ.get("ELEVENLABS_VOICE_EN", "")}


def synthesize_elevenlabs(text: str, lang: str) -> bytes:
    import requests
    voice = ELEVENLABS[lang]
    if not voice:
        raise SystemExit(f"set ELEVENLABS_VOICE_{lang.upper()} to an ElevenLabs voice id")
    r = requests.post(f"https://api.elevenlabs.io/v1/text-to-speech/{voice}?output_format=mp3_44100_128",
                      headers={"xi-api-key": os.environ["ELEVENLABS_API_KEY"]},
                      json={"text": text, "model_id": ELEVENLABS["model"],
                            "voice_settings": {"stability": 0.6, "similarity_boost": 0.8}}, timeout=60)
    r.raise_for_status()
    return r.content


async def synthesize(text: str, lang: str) -> bytes:
    if os.environ.get("ELEVENLABS_API_KEY"):
        return await asyncio.to_thread(synthesize_elevenlabs, text, lang)
    v = VOICES[lang]
    for attempt in range(5):
        try:
            data = b""
            async for chunk in edge_tts.Communicate(text, v["voice"], rate=v["rate"], pitch=v["pitch"]).stream():
                if chunk["type"] == "audio":
                    data += chunk["data"]
            if data:
                return data
        except Exception as e:  # network hiccups: try again
            print(f"  retry {attempt + 1} for {text!r}: {e}", file=sys.stderr)
        await asyncio.sleep(2 * (attempt + 1))
    raise RuntimeError(f"no audio for {text!r}")


FILTERS = ",".join([
    # Trim the silence before and after the voice, leaving a short natural edge.
    "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.04",
    "areverse",
    "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.08",
    "areverse",
    # Every clip at the same loudness.
    "loudnorm=I=-16:TP=-1.5:LRA=11",
])


def encode(raw: bytes, out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "in.mp3"
        src.write_bytes(raw)
        tmp_out = Path(tmp) / "out.mp3"
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", str(src), "-af", FILTERS,
             "-ac", "1", "-ar", "24000", "-c:a", "libmp3lame", "-b:a", "40k", "-map_metadata", "-1", str(tmp_out)],
            check=True,
        )
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(tmp_out), str(out))


def load_lists(refresh: bool) -> dict[str, list[str]]:
    if refresh or not all((LISTS / f"{lang}.txt").is_file() for lang in VOICES):
        gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
        print("Writing the clip lists (SpeechCatalogTest)...")
        # The test also checks the recordings, so it "fails" while some are missing: the lists are written first.
        subprocess.run([str(ROOT / gradlew), ":app:testDebugUnitTest", "--tests", "*SpeechCatalogTest", "-q"], cwd=ROOT)
    return {lang: [l for l in (LISTS / f"{lang}.txt").read_text(encoding="utf-8").split("\n") if l] for lang in VOICES}


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="record every placeholder clip again")
    ap.add_argument("--lists", action="store_true", help="re-run the unit test that writes the clip lists")
    ap.add_argument("--jobs", type=int, default=6)
    args = ap.parse_args()

    lists = load_lists(args.lists)
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.is_file() else {}

    jobs, problems = [], []
    for lang, clips in lists.items():
        voice = VOICES[lang]["voice"]
        for clip in clips:
            try:
                voiced = VOICED[lang](clip)
            except Unvocalized:
                problems.append(f"{lang}: no tashkeel for {clip!r} (add it to tools/audio/ar_tashkeel.json)")
                continue
            out = ASSETS / lang / f"{key(clip)}.mp3"
            # A real human recording (fetch_human_audio.py) is never replaced by a synthetic voice.
            if manifest.get(lang, {}).get(clip, {}).get("voice") == "human" and out.is_file():
                continue
            if os.environ.get("ELEVENLABS_API_KEY"):
                voice = f"elevenlabs:{ELEVENLABS[lang]}"
            entry = {"voiced": voiced, "voice": voice, "file": out.name, "placeholder": True}
            if not args.force and out.is_file() and manifest.get(lang, {}).get(clip) == entry:
                continue
            jobs.append((lang, clip, voiced, out, entry))
    if problems:
        print("\n".join(problems), file=sys.stderr)
        sys.exit(1)

    print(f"{len(jobs)} clips to record")
    sem = asyncio.Semaphore(args.jobs)
    done = 0

    async def one(lang, clip, voiced, out, entry):
        nonlocal done
        async with sem:
            raw = await synthesize(voiced, lang)
            await asyncio.to_thread(encode, raw, out)
            manifest.setdefault(lang, {})[clip] = entry
            done += 1
            if done % 50 == 0:
                print(f"  {done}/{len(jobs)}")
                MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")

    await asyncio.gather(*(one(*j) for j in jobs))

    # Drop recordings of clips the games no longer say.
    for lang, clips in lists.items():
        wanted = {f"{key(c)}.mp3" for c in clips}
        for f in (ASSETS / lang).glob("*.mp3"):
            if f.name not in wanted:
                f.unlink()
        manifest[lang] = {c: e for c, e in manifest.get(lang, {}).items() if c in set(clips)}
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    total = sum(f.stat().st_size for f in ASSETS.rglob("*.mp3"))
    print(f"done: {sum(len(c) for c in lists.values())} clips, {total / 1e6:.1f} MB")


if __name__ == "__main__":
    asyncio.run(main())
