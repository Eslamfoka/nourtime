"""
Groups missing_arabic_audio.csv into batches of text that a TTS app reads in one go (one long audio
file per batch), so all clips share one voice and tone. slice_batches.py cuts them apart again.

    python tools/audio/make_batches.py [--size 25]

Writes manus_batches/batch_01.txt ... and manus_batches/batches.csv (batch, position, text, file name).
"""
import argparse
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CSV = ROOT / "missing_arabic_audio.csv"
OUT = ROOT / "manus_batches"
INSTRUCTION = ("اقرأ القائمة التالية بالعربية الفصحى بصوت واضح وهادئ لطفل صغير، سطرًا سطرًا كما هي بالتشكيل، "
               "واسكت ثانيتين كاملتين بعد كل سطر. لا تقرأ أي شيء غير الأسطر.")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--size", type=int, default=25)
    args = ap.parse_args()
    with CSV.open(encoding="utf-8-sig", newline="") as f:
        rows = [(r["Arabic_Text_With_Tashkeel"], r["Required_Filename.mp3"]) for r in csv.DictReader(f)]
    # The CSV is already in a sensible order (letters, words, colors, labels, numbers); keep it.
    OUT.mkdir(exist_ok=True)
    for old in OUT.glob("batch_*.txt"):
        old.unlink()
    index = []
    batches = [rows[i:i + args.size] for i in range(0, len(rows), args.size)]
    for b, batch in enumerate(batches, 1):
        name = f"batch_{b:02d}"
        lines = [text + "." for text, _ in batch]
        (OUT / f"{name}.txt").write_text(INSTRUCTION + "\n\n" + "\n".join(lines) + "\n", encoding="utf-8")
        index += [(name, i, text, fn) for i, (text, fn) in enumerate(batch, 1)]
    with (OUT / "batches.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["batch", "position", "Arabic_Text_With_Tashkeel", "Required_Filename.mp3"])
        w.writerows(index)
    print(f"{len(batches)} batches of up to {args.size} -> {OUT}")


if __name__ == "__main__":
    main()
