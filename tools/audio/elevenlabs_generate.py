"""
Records the Arabic clips that still need a natural voice with the ElevenLabs API, one request per
line (no long batch recording to cut apart), into tools/audio/incoming/ for import_audio.py.

    python tools/audio/elevenlabs_generate.py --list          # models + voices on the account
    python tools/audio/elevenlabs_generate.py --limit 5       # try a few first
    python tools/audio/elevenlabs_generate.py                 # everything still missing
    python tools/audio/import_audio.py --voice elevenlabs-jessica   # then install them

Needs ELEVENLABS_API_KEY in tools/audio/keys.env (or the environment). Voice: Jessica (a premade
voice, so it works on the free plan) with the "[cheerfully]" tone tag and lively settings, the
owner's pick from voice tests on 2026-10-03 (over Sarah and Laura). Covers the missing list and
the numbers 1-25 so every number shares the voice. Each clip is checked with speech recognition;
an unsure clip is recorded once more with another seed, then goes to incoming-review/ for a person
to listen (digits like "13" for ثلاثة عشر count as a match).
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
from difflib import SequenceMatcher
from pathlib import Path

import requests

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ROWS = [ROOT / "missing_arabic_audio.csv", ROOT / "manus_batches" / "batches.csv"]
OUT = HERE / "incoming"
REVIEW = HERE / "incoming-review"
API = "https://api.elevenlabs.io/v1"

SETTINGS = {"stability": 0.45, "similarity_boost": 0.80, "style": 0.35, "use_speaker_boost": True, "speed": 0.92}
TAG = "[cheerfully] "  # read by eleven_v3/v4 as direction, not spoken
LABEL = "elevenlabs-jessica"  # the manifest's "voice" for these clips


def load_key() -> str:
    env = HERE / "keys.env"
    if env.is_file():
        for line in env.read_text(encoding="utf-8").splitlines():
            k, _, v = line.partition("=")
            if k.strip() and v.strip():
                os.environ.setdefault(k.strip(), v.strip())
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key:
        raise SystemExit("add ELEVENLABS_API_KEY=... to tools/audio/keys.env")
    return key


def get(path: str, key: str) -> dict:
    r = requests.get(f"{API}{path}", headers={"xi-api-key": key}, timeout=30)
    r.raise_for_status()
    return r.json()


def find_voice(key: str, name: str) -> str:
    for v in get("/voices", key)["voices"]:
        if v["name"].lower().startswith(name.lower()):
            return v["voice_id"]
    raise SystemExit(f"no voice named {name!r} on this account (see --list)")


def speak(key: str, voice: str, model: str, text: str, seed: int = 7) -> bytes:
    text = TAG + text
    for attempt in range(5):
        r = requests.post(f"{API}/text-to-speech/{voice}?output_format=mp3_44100_128",
                          headers={"xi-api-key": key},
                          json={"text": text, "model_id": model, "language_code": "ar", "seed": seed,
                                "voice_settings": SETTINGS}, timeout=60)
        if r.status_code == 429 or r.status_code >= 500:  # busy: wait and try again
            time.sleep(5 * (attempt + 1))
            continue
        if r.status_code == 400 and "language_code" in r.text:  # model doesn't take a language hint
            r = requests.post(f"{API}/text-to-speech/{voice}?output_format=mp3_44100_128",
                              headers={"xi-api-key": key},
                              json={"text": text, "model_id": model, "seed": seed, "voice_settings": SETTINGS},
                              timeout=60)
        if not r.ok:
            raise SystemExit(f"ElevenLabs said {r.status_code}: {r.text[:300]}")
        return r.content
    raise SystemExit("ElevenLabs stayed busy; run again later (finished clips are kept)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true", help="show the account's models and voices")
    ap.add_argument("--model", default="eleven_v4")  # what the approved test used
    ap.add_argument("--voice", default="Jessica", help="voice name (or id with --voice-id)")
    ap.add_argument("--voice-id")
    ap.add_argument("--limit", type=int, help="only this many clips (for a try)")
    ap.add_argument("--only", nargs="+", help="only these file names (without .mp3)")
    ap.add_argument("--force", action="store_true", help="record again even if the file is there")
    ap.add_argument("--no-check", action="store_true", help="skip speech recognition")
    args = ap.parse_args()
    key = load_key()

    if args.list:
        try:
            for m in get("/models", key):
                langs = {l["language_id"] for l in m.get("languages", [])}
                print(f"model {m['model_id']:28} {'arabic' if 'ar' in langs else ''}  {m.get('name', '')}")
        except requests.HTTPError:
            print("(this key can't list models: give it the models_read permission to see them)")
        for v in get("/voices", key)["voices"]:
            print(f"voice {v['voice_id']}  {v['name']}")
        sub = get("/user/subscription", key)
        print(f"characters used {sub['character_count']} of {sub['character_limit']}")
        return

    voice = args.voice_id or find_voice(key, args.voice)
    by_stem = {}
    for path in ROWS:
        with path.open(encoding="utf-8-sig", newline="") as f:
            for r in csv.DictReader(f):
                by_stem.setdefault(Path(r["Required_Filename.mp3"]).stem, r["Arabic_Text_With_Tashkeel"])
    rows = [(t, s) for s, t in by_stem.items()]
    # Already in this voice (imported, or waiting in incoming/ or incoming-review/): don't pay for it twice.
    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))["ar"].values()
    done = {m["voiced"] for m in manifest if m.get("voice") == LABEL}
    waiting = {p.stem for d in (OUT, REVIEW) if d.is_dir() for p in d.iterdir()}
    todo = [(t, s) for t, s in rows if args.force or (t not in done and s not in waiting)]
    if args.only:
        todo = [(t, s) for t, s in rows if s in args.only]
    if args.limit:
        todo = todo[:args.limit]
    print(f"{len(todo)} clips to record ({sum(len(t) for t, _ in todo)} characters), model {args.model}")

    model = None
    if not args.no_check:
        from faster_whisper import WhisperModel
        from slice_batches import norm
        model = WhisperModel("small", device="cpu", compute_type="int8")
    OUT.mkdir(exist_ok=True)
    ok = unsure = 0
    for i, (text, stem) in enumerate(todo, 1):
        out = OUT / f"{stem}.mp3"
        def heard_right() -> tuple[bool, str]:
            heard = " ".join(x.text for x in model.transcribe(str(out), language="ar", beam_size=5,
                                                               condition_on_previous_text=False)[0]).strip()
            score = SequenceMatcher(None, norm(text), norm(heard)).ratio()
            return score >= 0.6 or heard.replace(" ", "").isdigit(), heard

        out.write_bytes(speak(key, voice, args.model, text))
        if model:
            good, heard = heard_right()
            if not good:  # one more take with another seed
                out.write_bytes(speak(key, voice, args.model, text, seed=11))
                good, heard = heard_right()
            if not good:
                REVIEW.mkdir(exist_ok=True)
                out.replace(REVIEW / out.name)
                print(f"  listen: {text!r} heard {heard!r} -> incoming-review/{out.name}")
                unsure += 1
                continue
        ok += 1
        if i % 25 == 0:
            print(f"  {i}/{len(todo)}")
    print(f"done: {ok} ready in incoming/, {unsure} to listen to in incoming-review/ "
          f"(move good ones to incoming/). Next: python tools/audio/import_audio.py --voice {LABEL}")


if __name__ == "__main__":
    sys.exit(main())
