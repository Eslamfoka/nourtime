"""
Records a voice pack's clips with the ElevenLabs API, one request per line (no long batch
recording to cut apart), for import_audio.py.

    python tools/audio/elevenlabs_generate.py --list                  # models + voices on the account
    python tools/audio/elevenlabs_generate.py --pack ar-eg --limit 5  # try a few first
    python tools/audio/elevenlabs_generate.py --pack ar-eg            # everything the pack still needs
    python tools/audio/import_audio.py --pack ar-eg --voice elevenlabs-nour   # then install them

Packs (folders under app/src/main/assets/audio, see VoicePack.kt):
  ar     Fusha. Jessica (premade) with the "[cheerfully]" tag: the owner's pick from voice tests on
         2026-10-03. Only clips not yet in Jessica's voice; volunteer recordings are replaced too so the
         pack is one voice (owner's choice).
         Files go to incoming/.
  ar-eg  Egyptian. The owner's own NOUR voice; numbers in Egyptian words (generate_audio.eg_number),
         everything else the written Fusha word so it matches the screen. Every clip. Files go to
         incoming-ar-eg/.
  en     American English. Liz ("Educational & excited"), replacing the volunteer recordings too so
         the pack is one voice (owner's choice). incoming-en/.
  en-gb  British English. Ana ("upbeat, for children's books"). incoming-en-gb/.

Needs ELEVENLABS_API_KEY in tools/audio/keys.env (or the environment). Each clip is checked with
speech recognition; an unsure clip is recorded once more with another seed, then goes to the
pack's review folder for a person to listen (digits like "13" for ثلاثة عشر count as a match).
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from difflib import SequenceMatcher
from pathlib import Path

import requests

import generate_audio as g

HERE = Path(__file__).resolve().parent
API = "https://api.elevenlabs.io/v1"
MODEL = "eleven_v4"

PACKS = {
    "ar": {"voice": "cgSgspJ2msm6clMCkdW9",  # Jessica
           "label": "elevenlabs-jessica", "tag": "[cheerfully] ", "replace_human": True,
           "settings": {"stability": 0.45, "similarity_boost": 0.80, "style": 0.35, "use_speaker_boost": True,
                        "speed": 0.92}},
    "en": {"voice": "wvk9Caj0nEx4l3I9LaR6",  # Liz
           "label": "elevenlabs-liz", "tag": "[cheerfully] ", "lang": "en", "replace_human": True,
           "settings": {"stability": 0.45, "similarity_boost": 0.80, "style": 0.35, "use_speaker_boost": True,
                        "speed": 0.92}},
    "en-gb": {"voice": "rCmVtv8cYU60uhlsOo1M",  # Ana
              "label": "elevenlabs-ana", "tag": "[cheerfully] ", "lang": "en",
              "settings": {"stability": 0.45, "similarity_boost": 0.80, "style": 0.35, "use_speaker_boost": True,
                           "speed": 0.92}},
    "ar-eg": {"voice": "bDnD7e0SdoJ1nFjISM4J",  # NOUR, the owner's voice
              "label": "elevenlabs-nour", "tag": "[cheerfully] ",
              # Close to NOUR's own sound (high similarity), lively, a little slow for children.
              "settings": {"stability": 0.40, "similarity_boost": 0.85, "style": 0.40, "use_speaker_boost": True,
                           "speed": 0.92}},
}


def folders(pack: str) -> tuple[Path, Path]:
    """Where a pack's new clips wait for import_audio.py, and the ones a person should hear first."""
    if pack == "ar":
        return HERE / "incoming", HERE / "incoming-review"
    return HERE / f"incoming-{pack}", HERE / f"incoming-{pack}-review"


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


def speak(key: str, pack: dict, text: str, seed: int = 7) -> bytes:
    body = {"text": pack["tag"] + text, "model_id": MODEL, "language_code": pack.get("lang", "ar"), "seed": seed,
            "voice_settings": pack["settings"]}
    for attempt in range(5):
        r = requests.post(f"{API}/text-to-speech/{pack['voice']}?output_format=mp3_44100_128",
                          headers={"xi-api-key": key}, json=body, timeout=60)
        if r.status_code == 429 or r.status_code >= 500:  # busy: wait and try again
            time.sleep(5 * (attempt + 1))
            continue
        if not r.ok:
            raise SystemExit(f"ElevenLabs said {r.status_code}: {r.text[:300]}")
        return r.content
    raise SystemExit("ElevenLabs stayed busy; run again later (finished clips are kept)")


def todo(pack_name: str, force: bool) -> list[tuple[str, str]]:
    """(text to say, file stem) once per different text, for the clips the pack still needs."""
    lang, voiced_of = g.PACK_VOICED[pack_name]
    label = PACKS[pack_name]["label"]
    manifest = json.loads(g.MANIFEST.read_text(encoding="utf-8")).get(pack_name, {})
    out, review = folders(pack_name)
    waiting = {p.stem for d in (out, review) if d.is_dir() for p in d.iterdir()}
    seen: dict[str, str] = {}
    for clip in g.load_lists(refresh=False)[lang]:
        entry = manifest.get(clip, {})
        keep = (label,) if pack_name in PACKS and PACKS[pack_name].get("replace_human") else (label, "human")
        if not force and entry.get("voice") in keep:
            continue
        seen.setdefault(voiced_of(clip), g.key(clip))
    return [(t, s) for t, s in seen.items() if force or s not in waiting]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true", help="show the account's models and voices")
    ap.add_argument("--pack", choices=list(PACKS), default="ar")
    ap.add_argument("--limit", type=int, help="only this many clips (for a try)")
    ap.add_argument("--force", action="store_true", help="record again even if done")
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

    pack = PACKS[args.pack]
    lines = todo(args.pack, args.force)
    if args.limit:
        lines = lines[:args.limit]
    cost = sum(len(pack["tag"]) + len(t) for t, _ in lines)
    print(f"{args.pack}: {len(lines)} clips to record (about {cost} credits), model {MODEL}", flush=True)

    model = None
    if not args.no_check:
        from faster_whisper import WhisperModel
        from slice_batches import norm
        model = WhisperModel("small", device="cpu", compute_type="int8")
    out_dir, review = folders(args.pack)
    out_dir.mkdir(exist_ok=True)
    ok = unsure = 0
    for i, (text, stem) in enumerate(lines, 1):
        out = out_dir / f"{stem}.mp3"

        def heard_right() -> tuple[bool, str]:
            heard = " ".join(x.text for x in model.transcribe(str(out), language=pack.get("lang", "ar"), beam_size=5,
                                                               condition_on_previous_text=False)[0]).strip()
            score = SequenceMatcher(None, norm(text).lower(), norm(heard).lower()).ratio()
            # Digits for a number said in words ("17", "و 93") are right too.
            return score >= 0.6 or norm(heard).removeprefix("و").isdigit(), heard

        out.write_bytes(speak(key, pack, text))
        if model:
            good, heard = heard_right()
            if not good:  # one more take with another seed
                out.write_bytes(speak(key, pack, text, seed=11))
                good, heard = heard_right()
            if not good:
                review.mkdir(exist_ok=True)
                out.replace(review / out.name)
                print(f"  listen: {text!r} heard {heard!r} -> {review.name}/{out.name}", flush=True)
                unsure += 1
                continue
        ok += 1
        if i % 25 == 0:
            print(f"  {i}/{len(lines)}", flush=True)
    print(f"done: {ok} ready in {out_dir.name}/, {unsure} to listen to in {review.name}/ (move good ones "
          f"over). Next: python tools/audio/import_audio.py --pack {args.pack} --voice {pack['label']}")


if __name__ == "__main__":
    sys.exit(main())
