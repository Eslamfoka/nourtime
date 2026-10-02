"""
Groups the Arabic clips that still need a natural voice into batches that a TTS app reads in one go
(one long recording per batch), so all clips share one voice and tone. Like goes with like: letters,
then each word category (animals, food, ...), colors and labels, whole numbers in order, then the
"و + number" parts. slice_batches.py cuts the recordings apart again.

    python tools/audio/make_batches.py                 # every batch -> manus_batches/
    python tools/audio/make_batches.py --test-numbers  # one test batch: the numbers 1-25

Each line ends with a period and lines are separated by an empty line: in Arabic TTS that gives the
longest, most reliable pause (a dash can be read out, a comma pauses only briefly).
Writes manus_batches/<batch>.txt and manus_batches/batches.csv (batch, position, text, file name).
"""
import argparse
import csv
import json
from pathlib import Path

import generate_audio as g

OUT = g.ROOT / "manus_batches"
INSTRUCTION = ("اقرأ القائمة التالية بالعربية الفصحى بصوت واضح وهادئ لطفل صغير، سطرًا سطرًا كما هي بالتشكيل، "
               "واسكت ثانيتين كاملتين بعد كل سطر. لا تقرأ أي شيء غير الأسطر.")


def clip_groups(only_missing: bool = True) -> list[tuple[str, list[str]]]:
    """(group name, clips) in reading order."""
    manifest = json.loads(g.MANIFEST.read_text(encoding="utf-8"))["ar"]
    clips = [c for c in g.load_lists(refresh=False)["ar"]
             if not only_missing or manifest.get(c, {}).get("voice") not in ("human", "gemini-aistudio")]
    pack = json.loads((g.ROOT / "app/src/main/assets/learning/letters/ar.json").read_text(encoding="utf-8"))
    concepts = json.loads((g.ROOT / "app/src/main/assets/learning/concepts.json").read_text(encoding="utf-8"))
    category = {c["id"]: c["category"] for c in concepts["concepts"]}
    word_cat = {w: category.get(cid, "other") for cid, w in pack["words"].items()}
    names = [l["name"] for l in pack["letters"]]

    groups: dict[str, list[str]] = {}
    for c in clips:
        if c.startswith("+"):
            key = "numbers-and"
        elif c.isdigit():
            key = "numbers"
        elif c in names or len(c) == 1:
            key = "letters"
        elif c in word_cat:
            key = "words-" + word_cat[c]
        elif c in pack["colors"].values():
            key = "colors-labels"
        else:
            key = "colors-labels"
        groups.setdefault(key, []).append(c)
    groups.get("numbers", []).sort(key=int)
    groups.get("numbers-and", []).sort(key=lambda c: int(c[1:]))
    order = ["letters"] + sorted(k for k in groups if k.startswith("words-")) + ["colors-labels", "numbers", "numbers-and"]
    return [(k, groups[k]) for k in order if k in groups]


def unique_voiced(clips: list[str]) -> list[tuple[str, str]]:
    """(voiced text, file name) once per different text (باء and the tile ب say the same thing)."""
    seen = {}
    for c in clips:
        seen.setdefault(g.ar_voiced(c), f"{g.key(c)}.mp3")
    return list(seen.items())


def write(batches: list[tuple[str, list[tuple[str, str]]]]) -> None:
    OUT.mkdir(exist_ok=True)
    for old in OUT.glob("*.txt"):
        old.unlink()
    index = []
    for name, rows in batches:
        body = "\n\n".join(text + "." for text, _ in rows)
        (OUT / f"{name}.txt").write_text(INSTRUCTION + "\n\n" + body + "\n", encoding="utf-8")
        index += [(name, i, text, fn) for i, (text, fn) in enumerate(rows, 1)]
    with (OUT / "batches.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["batch", "position", "Arabic_Text_With_Tashkeel", "Required_Filename.mp3"])
        w.writerows(index)
    for name, rows in batches:
        print(f"  {name}.txt: {len(rows)} lines")
    print(f"{len(batches)} batches -> {OUT}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--size", type=int, default=25)
    ap.add_argument("--test-numbers", action="store_true", help="only one test batch: the numbers 1-25")
    args = ap.parse_args()
    if args.test_numbers:
        # All of 1-25, even the ones a volunteer recorded, so the test is a full, even list.
        write([("test_numbers_01_25", unique_voiced([str(n) for n in range(1, 26)]))])
        return
    batches = []
    n = 0
    for group, clips in clip_groups():
        rows = unique_voiced(clips)
        for i in range(0, len(rows), args.size):
            n += 1
            batches.append((f"batch_{n:02d}_{group}", rows[i:i + args.size]))
    write(batches)


if __name__ == "__main__":
    main()
