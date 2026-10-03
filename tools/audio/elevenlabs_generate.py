"""
Records the Arabic clips that still need a natural voice with the ElevenLabs API, one request per
line (no long batch recording to cut apart), into tools/audio/incoming/ for import_audio.py.

    python tools/audio/elevenlabs_generate.py --list          # models + voices on the account
    python tools/audio/elevenlabs_generate.py --limit 5       # try a few first
    python tools/audio/elevenlabs_generate.py                 # everything still missing
    python tools/audio/import_audio.py --voice elevenlabs     # then install them

Needs ELEVENLABS_API_KEY in tools/audio/keys.env (or the environment). The voice and settings are
the ones the owner approved on the numbers 1-25 test (2026-10-03): Sarah, speed 0.90, stability
0.76, similarity 0.88. Each clip is checked with speech recognition; unsure ones go to
incoming-review/ for a person to listen (digits like "13" for ثلاثة عشر count as a match).
"""
from __future__ import annotations

import argparse
import csv
import os
import sys
import time
from difflib import SequenceMatcher
from pathlib import Path

import requests

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ROWS = ROOT / "missing_arabic_audio.csv"
OUT = HERE / "incoming"
REVIEW = HERE / "incoming-review"
API = "https://api.elevenlabs.io/v1"

SETTINGS = {"stability": 0.76, "similarity_boost": 0.88, "style": 0.0, "use_speaker_boost": True, "speed": 0.90}


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


def speak(key: str, voice: str, model: str, text: str) -> bytes:
    for attempt in range(5):
        r = requests.post(f"{API}/text-to-speech/{voice}?output_format=mp3_44100_128",
                          headers={"xi-api-key": key},
                          json={"text": text, "model_id": model, "language_code": "ar", "seed": 7,
                                "voice_settings": SETTINGS}, timeout=60)
        if r.status_code == 429 or r.status_code >= 500:  # busy: wait and try again
            time.sleep(5 * (attempt + 1))
            continue
        if r.status_code == 400 and "language_code" in r.text:  # model doesn't take a language hint
            r = requests.post(f"{API}/text-to-speech/{voice}?output_format=mp3_44100_128",
                              headers={"xi-api-key": key},
                              json={"text": text, "model_id": model, "seed": 7, "voice_settings": SETTINGS},
                              timeout=60)
        if not r.ok:
            raise SystemExit(f"ElevenLabs said {r.status_code}: {r.text[:300]}")
        return r.content
    raise SystemExit("ElevenLabs stayed busy; run again later (finished clips are kept)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true", help="show the account's models and voices")
    ap.add_argument("--model", default="eleven_multilingual_v2")
    ap.add_argument("--voice", default="Sarah", help="voice name (or id with --voice-id)")
    ap.add_argument("--voice-id")
    ap.add_argument("--limit", type=int, help="only this many clips (for a try)")
    ap.add_argument("--force", action="store_true", help="record again even if the file is there")
    ap.add_argument("--no-check", action="store_true", help="skip speech recognition")
    args = ap.parse_args()
    key = load_key()

    if args.list:
        for m in get("/models", key):
            langs = {l["language_id"] for l in m.get("languages", [])}
            print(f"model {m['model_id']:28} {'arabic' if 'ar' in langs else ''}  {m.get('name', '')}")
        for v in get("/voices", key)["voices"]:
            print(f"voice {v['voice_id']}  {v['name']}")
        sub = get("/user/subscription", key)
        print(f"characters used {sub['character_count']} of {sub['character_limit']}")
        return

    voice = args.voice_id or find_voice(key, args.voice)
    with ROWS.open(encoding="utf-8-sig", newline="") as f:
        rows = [(r["Arabic_Text_With_Tashkeel"], Path(r["Required_Filename.mp3"]).stem) for r in csv.DictReader(f)]
    todo = [(t, s) for t, s in rows if args.force or not any((d / f"{s}.mp3").is_file() for d in (OUT, REVIEW))]
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
        out.write_bytes(speak(key, voice, args.model, text))
        if model:
            heard = " ".join(x.text for x in model.transcribe(str(out), language="ar", beam_size=5,
                                                               condition_on_previous_text=False)[0]).strip()
            score = SequenceMatcher(None, norm(text), norm(heard)).ratio()
            if score < 0.6 and not heard.replace(" ", "").isdigit():
                REVIEW.mkdir(exist_ok=True)
                out.replace(REVIEW / out.name)
                print(f"  listen: {text!r} heard {heard!r} -> incoming-review/{out.name}")
                unsure += 1
                continue
        ok += 1
        if i % 25 == 0:
            print(f"  {i}/{len(todo)}")
    print(f"done: {ok} ready in incoming/, {unsure} to listen to in incoming-review/ "
          f"(move good ones to incoming/). Next: python tools/audio/import_audio.py --voice elevenlabs")


if __name__ == "__main__":
    sys.exit(main())
