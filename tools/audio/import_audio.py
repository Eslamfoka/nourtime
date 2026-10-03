"""
Imports Arabic clips made outside this repo (e.g. by hand or by another agent in Google AI Studio)
from tools/audio/incoming/ into the app.

    python tools/audio/import_audio.py

Each file in tools/audio/incoming/ must be named like a row of missing_arabic_audio.csv
(`<16 hex>.mp3`; .wav/.ogg/.m4a/.webm with the same name are fine too). Every clip that says the same
text gets the file (the letter name باء and the tile ب, for example). Files are trimmed, loudness-
matched and re-encoded like all other clips. Real human recordings are kept unless --replace-human.
"""
import csv
import json
import sys
from pathlib import Path

import generate_audio as g

INCOMING = g.HERE / "incoming"
CSVS = [g.ROOT / "missing_arabic_audio.csv", g.ROOT / "manus_batches" / "batches.csv"]
EXTS = (".mp3", ".wav", ".ogg", ".m4a", ".webm", ".aac", ".flac")


def _missing_only() -> set[str]:
    """Rows of the missing list (not the test batches) that are still not imported."""
    p = CSVS[0]
    if not p.is_file():
        return set()
    with p.open(encoding="utf-8-sig", newline="") as f:
        return {Path(r["Required_Filename.mp3"]).stem for r in csv.DictReader(f)}


def main() -> None:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--replace-human", action="store_true",
                    help="also replace volunteer recordings (e.g. so all numbers share one voice)")
    ap.add_argument("--voice", default="gemini-aistudio", help="where the clips came from, e.g. elevenlabs")
    args = ap.parse_args()
    rows = {}
    for path in CSVS:
        if path.is_file():
            with path.open(encoding="utf-8-sig", newline="") as f:
                for r in csv.DictReader(f):
                    rows[Path(r["Required_Filename.mp3"]).stem] = r["Arabic_Text_With_Tashkeel"]
    files = {p.stem.lower(): p for p in INCOMING.rglob("*") if p.suffix.lower() in EXTS}
    manifest = json.loads(g.MANIFEST.read_text(encoding="utf-8"))
    clips = [c for c in g.load_lists(refresh=False)["ar"]]

    done, unknown = 0, [n for n in files if n not in rows]
    for stem, voiced in rows.items():
        src = files.get(stem)
        if not src:
            continue
        raw = src.read_bytes()
        for clip in clips:
            if g.ar_voiced(clip) != voiced:
                continue
            if manifest["ar"].get(clip, {}).get("voice") == "human" and not args.replace_human:
                continue
            out = g.ASSETS / "ar" / f"{g.key(clip)}.mp3"
            g.encode(raw, out)
            manifest["ar"][clip] = {"file": out.name, "voice": args.voice, "voiced": voiced, "imported": src.name}
            done += 1
    g.MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    import fetch_human_audio  # credits list follows the clips that are still volunteer recordings
    fetch_human_audio.write_credits(manifest)
    missing = [f"{s}.mp3  {v}" for s, v in rows.items() if s not in files and s in _missing_only()]
    print(f"imported {done} clips from {len(rows) - len(missing)} files")
    if unknown:
        print("files not in the CSV (ignored):", ", ".join(sorted(unknown)))
    if missing:
        print(f"{len(missing)} rows still missing (they keep the old voice):")
        print("\n".join("  " + m for m in missing))


if __name__ == "__main__":
    sys.exit(main())
