"""
Imports Arabic clips made outside this repo (e.g. by hand or by another agent in Google AI Studio)
from tools/audio/incoming/ into the app.

    python tools/audio/import_audio.py

Each file in tools/audio/incoming/ must be named like a row of missing_arabic_audio.csv
(`<16 hex>.mp3`; .wav/.ogg/.m4a/.webm with the same name are fine too). Every clip that says the same
text gets the file (the letter name باء and the tile ب, for example). Files are trimmed, loudness-
matched and re-encoded like all other clips. Real human recordings are never replaced.
"""
import csv
import json
import sys
from pathlib import Path

import generate_audio as g

INCOMING = g.HERE / "incoming"
CSV = g.ROOT / "missing_arabic_audio.csv"
EXTS = (".mp3", ".wav", ".ogg", ".m4a", ".webm", ".aac", ".flac")


def main() -> None:
    rows = {}
    with CSV.open(encoding="utf-8-sig", newline="") as f:
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
            if g.ar_voiced(clip) != voiced or manifest["ar"].get(clip, {}).get("voice") == "human":
                continue
            out = g.ASSETS / "ar" / f"{g.key(clip)}.mp3"
            g.encode(raw, out)
            manifest["ar"][clip] = {"file": out.name, "voice": "gemini-aistudio", "voiced": voiced, "imported": src.name}
            done += 1
    g.MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    missing = [f"{s}.mp3  {v}" for s, v in rows.items() if s not in files]
    print(f"imported {done} clips from {len(rows) - len(missing)} files")
    if unknown:
        print("files not in the CSV (ignored):", ", ".join(sorted(unknown)))
    if missing:
        print(f"{len(missing)} rows still missing (they keep the old voice):")
        print("\n".join("  " + m for m in missing))


if __name__ == "__main__":
    sys.exit(main())
