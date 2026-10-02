"""
Listens to every recorded clip with speech recognition (faster-whisper) and lists the ones that
don't sound like their text, for a person to check. Recognition is not perfect (short words,
single letters), so this is a review list, not a test.

    pip install faster-whisper
    python tools/audio/verify_audio.py [--model small] [--lang ar]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from difflib import SequenceMatcher
from pathlib import Path

from faster_whisper import WhisperModel

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "audio"
MANIFEST = HERE / "manifest.json"

TASHKEEL = re.compile(r"[ً-ٰٟـ]")
PUNCT = re.compile(r"[^\w\s]|_")


def norm(s: str, lang: str) -> str:
    s = TASHKEEL.sub("", s.lower())
    if lang == "ar":
        s = re.sub("[أإآٱ]", "ا", s).replace("ة", "ه").replace("ى", "ي").replace("ؤ", "و").replace("ئ", "ي")
        s = s.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789"))
    s = PUNCT.sub(" ", s).replace("-", " ")
    return " ".join(s.split())


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="small")
    ap.add_argument("--lang", choices=["ar", "en"], action="append")
    ap.add_argument("--threshold", type=float, default=0.75)
    args = ap.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    model = WhisperModel(args.model, device="cpu", compute_type="int8")
    report = []
    for lang in args.lang or ["ar", "en"]:
        for clip, entry in sorted(manifest.get(lang, {}).items()):
            path = ASSETS / lang / entry["file"]
            segments, _ = model.transcribe(str(path), language=lang, beam_size=1, vad_filter=False,
                                           initial_prompt=None, condition_on_previous_text=False)
            heard = " ".join(s.text for s in segments).strip()
            want = norm(entry["voiced"].replace("...", " "), lang)
            got = norm(heard, lang)
            score = SequenceMatcher(None, want.replace(" ", ""), got.replace(" ", "")).ratio()
            digits_ok = clip.lstrip("+").isdigit() and clip.lstrip("+") in got.split()
            ok = score >= args.threshold or digits_ok
            checked = locals().get("checked", 0) + 1
            if checked % 50 == 0:
                print(f"  checked {checked}", flush=True)
            if not ok:
                report.append({"lang": lang, "clip": clip, "voiced": entry["voiced"], "heard": heard, "score": round(score, 2)})
                print(f"{lang} {clip!r}: wanted {entry['voiced']!r}, heard {heard!r} ({score:.2f})", flush=True)
    out = HERE / "verify-report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(report)} to review -> {out}")


if __name__ == "__main__":
    sys.exit(main())
