"""
Cuts the long batch recordings (one per manus_batches/batch_NN.txt) into one clip per line, checks
each piece with speech recognition and names it as in manus_batches/batches.csv, ready for
import_audio.py.

    python tools/audio/slice_batches.py            # reads tools/audio/incoming-batches/batch_NN.*
    python tools/audio/import_audio.py             # then installs the clips into the app

Cutting: ffmpeg's silencedetect finds the pauses. Some lines have short pauses inside them
("سَيَّارَةُ إِسْعَاف"), so the minimum pause length is searched (longest first) until the
recording splits into exactly as many pieces as the batch has lines. A batch that never splits
right is reported and skipped, never guessed.

Checking: each piece is transcribed (faster-whisper) and compared with its line; pieces that
don't match go to tools/audio/incoming-review/ for a person to listen (recognition is weak on
single short words, so many of those are fine).
"""
from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
INDEX = ROOT / "manus_batches" / "batches.csv"
IN = HERE / "incoming-batches"
OUT = HERE / "incoming"
REVIEW = HERE / "incoming-review"
EXTS = (".wav", ".mp3", ".ogg", ".m4a", ".webm", ".aac", ".flac")
TASHKEEL = re.compile(r"[ً-ٰٟـ]")


def norm(s: str) -> str:
    s = TASHKEEL.sub("", s)
    s = re.sub("[أإآٱ]", "ا", s).replace("ة", "ه").replace("ى", "ي")
    return re.sub(r"[^\w]", "", s)


def speech_spans(path: Path, min_pause: float, noise: str, start: float = 0.0) -> list[tuple[float, float]]:
    """Where someone is speaking, between pauses of at least [min_pause] seconds."""
    r = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(path), "-af", f"silencedetect=noise={noise}:d={min_pause}",
                        "-f", "null", "-"], capture_output=True, text=True)
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", r.stderr)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", r.stderr)]
    dur = float(re.search(r"Duration: (\d+):(\d+):([\d.]+)", r.stderr).group(3)) + \
        60 * float(re.search(r"Duration: (\d+):(\d+)", r.stderr).group(2)) + \
        3600 * float(re.search(r"Duration: (\d+)", r.stderr).group(1))
    spans, cursor = [], start
    for s, e in zip(starts, ends + [dur] * (len(starts) - len(ends))):
        if e <= start:
            continue
        if s - cursor > 0.12:
            spans.append((cursor, s))
        cursor = e
    if dur - cursor > 0.12:
        spans.append((cursor, dur))
    return spans


def split(path: Path, expected: int, start: float = 0.0) -> list[tuple[float, float]] | None:
    for noise in ("-40dB", "-35dB", "-45dB", "-30dB"):
        for tenths in range(25, 2, -1):  # 2.5 s down to 0.3 s
            spans = speech_spans(path, tenths / 10, noise, start)
            if len(spans) == expected:
                return spans
    return None


def cut(path: Path, start: float, end: float, out: Path) -> None:
    pad = 0.08
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-ss", f"{max(0, start - pad):.3f}",
                    "-to", f"{end + pad:.3f}", "-ac", "1", "-ar", "24000", str(out)], check=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-check", action="store_true", help="skip speech recognition")
    ap.add_argument("--min-score", type=float, default=0.6)
    ap.add_argument("--start", type=float, default=0.0,
                    help="seconds to skip first (e.g. when the voice read the instructions aloud)")
    args = ap.parse_args()
    lines = defaultdict(list)
    with INDEX.open(encoding="utf-8-sig", newline="") as f:
        for r in csv.DictReader(f):
            lines[r["batch"]].append((r["Arabic_Text_With_Tashkeel"], Path(r["Required_Filename.mp3"]).stem))
    model = None
    if not args.no_check:
        from faster_whisper import WhisperModel
        model = WhisperModel("small", device="cpu", compute_type="int8")
    OUT.mkdir(exist_ok=True)
    ok = bad = 0
    for batch, items in sorted(lines.items()):
        src = next((p for p in IN.glob(f"{batch}.*") if p.suffix.lower() in EXTS), None)
        if not src:
            print(f"{batch}: no recording yet")
            continue
        spans = split(src, len(items), args.start)
        if not spans:
            print(f"{batch}: couldn't split into {len(items)} pieces (lines skipped or merged?); redo this batch")
            bad += len(items)
            continue
        for (text, stem), (s, e) in zip(items, spans):
            out = OUT / f"{stem}.wav"
            cut(src, s, e, out)
            if model:
                segs, _ = model.transcribe(str(out), language="ar", beam_size=5, condition_on_previous_text=False)
                heard = " ".join(x.text for x in segs)
                score = SequenceMatcher(None, norm(text), norm(heard)).ratio()
                if score < args.min_score:
                    # Recognition is unsure: a person listens (copy it into incoming/ if it's right).
                    REVIEW.mkdir(exist_ok=True)
                    out.replace(REVIEW / out.name)
                    print(f"  {batch} #{items.index((text, stem)) + 1}: wanted {text!r}, heard {heard.strip()!r}: "
                          f"moved to incoming-review/{out.name}")
                    bad += 1
                    continue
            ok += 1
        print(f"{batch}: {len(items)} pieces cut")
    print(f"done: {ok} clips ready in {OUT} ({bad} to redo). Next: python tools/audio/import_audio.py")


if __name__ == "__main__":
    sys.exit(main())
