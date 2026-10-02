"""
Finds REAL HUMAN recordings for the Learning Hub's clips in open databases, checks them with speech
recognition and puts the best one of each into app/src/main/assets/audio/<lang>/<key>.mp3.

    python tools/audio/fetch_human_audio.py [--lang ar] [--lang en]

Sources (all on Wikimedia Commons, free licences only):
  - Lingua Libre recordings (File:LL-Q13955 (ara)-<speaker>-<word>.wav, Q1860 (eng) for English)
  - other Commons pronunciation files (Ar-<word>.ogg, En-us-<word>.ogg, En-uk-..., ...)
  - the audio linked from the word's Arabic and English Wiktionary pages
Searched with the word as written, with its tashkeel, and with alef/ة variants. Dialect recordings
(ary, apc, arz, ...) are skipped: Fusha only. Non-commercial (NC) and no-derivatives (ND) licences
are skipped.

Every candidate is transcribed (faster-whisper); one that doesn't say the word is dropped, and the
clearest match is kept. Clips with no human recording keep their current file, marked as a
placeholder in tools/audio/manifest.json (generate_audio.py --placeholders-only replaces them with a
premium voice once a key is set). Credits for every human clip go to
app/src/main/assets/audio/CREDITS.txt (CC BY / BY-SA need them; shown in the app).

Forvo is NOT used: its terms forbid scraping and re-use without a paid commercial licence.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from difflib import SequenceMatcher
from pathlib import Path

import requests

import generate_audio as g

UA = {"User-Agent": "NourTimeAudio/1.0 (educational app; eslamkwit22@gmail.com)"}
COMMONS = "https://commons.wikimedia.org/w/api.php"
HERE = Path(__file__).resolve().parent
CACHE = HERE / ".human-cache"
TASHKEEL = re.compile(r"[ً-ٰٟـ]")
LL_LANG = {"ar": "Q13955 (ara)", "en": "Q1860 (eng)"}
# Commons file-name prefixes that mean "this language" (not a dialect).
PREFIXES = {"ar": ("ar-",), "en": ("en-us-", "en-uk-", "en-gb-", "en-au-", "en-")}
BAD_LICENCE = re.compile(r"\bNC\b|\bND\b|non-?commercial|no-?deriv", re.I)

session = requests.Session()
session.headers.update(UA)


def api(url, params, tries=4):
    for i in range(tries):
        try:
            r = session.get(url, params={**params, "format": "json"}, timeout=40)
            if r.status_code == 200:
                return r.json()
        except Exception:
            pass
        time.sleep(2 * (i + 1))
    return {}


def strip(s: str) -> str:
    return TASHKEEL.sub("", s).strip()


def variants(word: str, lang: str) -> list[str]:
    """Spellings a recording's title might use."""
    out = {word, strip(word)}
    if lang == "ar":
        v = g.AR_WORDS.get(word) or g.AR_NAMES.get(word)
        if v:
            out.add(v)
        base = strip(word)
        out.add(re.sub("^[أإآ]", "ا", base))
        out.add(base.replace("ة", "ه"))
    else:
        out |= {word.lower(), word.capitalize()}
    return [v for v in out if v]


def norm(s: str, lang: str) -> str:
    s = strip(s).lower()
    if lang == "ar":
        s = re.sub("[أإآٱ]", "ا", s).replace("ة", "ه").replace("ى", "ي")
    s = re.sub(r"[^\w\s]|_", " ", s)
    return " ".join(s.split())


def title_word(title: str, lang: str) -> str | None:
    """The word a Commons audio file says, if its name follows a pronunciation-file pattern."""
    name = re.sub(r"^File:", "", title)
    name = re.sub(r"\.(wav|ogg|oga|flac|mp3|opus|webm)$", "", name, flags=re.I)
    m = re.match(r"LL-(Q\d+ \(\w+\))-(.+?)-(.+)$", name)
    if m:
        return m.group(3) if m.group(1) == LL_LANG[lang] else None
    low = name.lower()
    for p in PREFIXES[lang]:
        if low.startswith(p):
            return name[len(p):]
    return None


def candidates(word: str, lang: str) -> set[str]:
    titles: set[str] = set()
    for v in variants(word, lang):
        for q in (f'intitle:"{v}" filemime:audio',):
            r = api(COMMONS, {"action": "query", "list": "search", "srnamespace": 6, "srlimit": 50, "srsearch": q})
            titles |= {h["title"] for h in r.get("query", {}).get("search", [])}
        for wiki in ("en", "ar"):
            r = api(f"https://{wiki}.wiktionary.org/w/api.php", {"action": "parse", "page": v, "prop": "images"})
            titles |= {"File:" + t.replace("_", " ") for t in r.get("parse", {}).get("images", [])
                       if re.search(r"\.(wav|ogg|oga|flac|mp3|opus)$", t, re.I)}
    wanted = {norm(v, lang) for v in variants(word, lang)}
    return {t for t in titles if (w := title_word(t, lang)) is not None and norm(w, lang) in wanted}


def info(title: str) -> dict | None:
    r = api(COMMONS, {"action": "query", "titles": title, "prop": "imageinfo", "iiprop": "url|extmetadata"})
    pages = r.get("query", {}).get("pages", {})
    for p in pages.values():
        ii = (p.get("imageinfo") or [None])[0]
        if not ii:
            return None
        meta = ii.get("extmetadata", {})
        lic = meta.get("LicenseShortName", {}).get("value", "")
        if not lic or BAD_LICENCE.search(lic):
            return None
        artist = html.unescape(re.sub("<[^>]+>", "", meta.get("Artist", {}).get("value", ""))).strip()
        return {"title": title, "url": ii["url"], "licence": lic, "author": artist or "unknown",
                "page": "https://commons.wikimedia.org/wiki/" + title.replace(" ", "_")}
    return None


def download(c: dict) -> Path | None:
    CACHE.mkdir(exist_ok=True)
    f = CACHE / (g.key(c["title"]) + Path(c["url"]).suffix.lower())
    if not f.is_file():
        for i in range(4):
            try:
                r = session.get(c["url"], timeout=60)
                if r.status_code == 200 and len(r.content) > 1000:
                    f.write_bytes(r.content)
                    break
            except Exception:
                pass
            time.sleep(2 * (i + 1))
    return f if f.is_file() else None


# ---------------------------------------------------------------- what each clip should say


def spoken(clip: str, lang: str, letters: dict[str, str]) -> str | None:
    """The single word a human recording must say for [clip]; None when no recording can fit."""
    if clip.lstrip("+").isdigit():
        if clip.startswith("+"):
            return None  # "وخمسة وأربعون" (with its و) doesn't exist as a recording
        n = int(clip)
        return g.ar_number(n) if lang == "ar" else g.en_number(n)
    if ("، " in clip) or (", " in clip):
        return None  # phrases are played as the letter's clip + the word's clip
    if len(clip) == 1:
        return letters.get(clip) or letters.get(clip.upper())
    return clip


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", choices=["ar", "en"], action="append")
    ap.add_argument("--model", default="small")
    ap.add_argument("--min-score", type=float, default=0.8)
    args = ap.parse_args()
    langs = args.lang or ["ar", "en"]

    from faster_whisper import WhisperModel
    model = WhisperModel(args.model, device="cpu", compute_type="int8")

    lists = g.load_lists(refresh=False)
    manifest = json.loads(g.MANIFEST.read_text(encoding="utf-8")) if g.MANIFEST.is_file() else {}
    packs = {lang: json.loads((g.ROOT / "app/src/main/assets/learning/letters" / f"{lang}.json").read_text(encoding="utf-8"))
             for lang in ("ar", "en")}

    for lang in langs:
        # A letter tile, or a letter's name, is said by the letter's name.
        letters = {l["letter"]: l["name"] for l in packs[lang]["letters"]}
        if lang == "ar":
            letters.update({"ا": "ألف", "إ": "ألف"})
        else:
            letters = {k: k for k in letters}  # English letters are their own names ("B")
        jobs = {}
        for clip in lists[lang]:
            word = spoken(clip, lang, letters)
            if word:
                jobs[clip] = word
        words = sorted(set(jobs.values()))
        print(f"{lang}: searching {len(words)} words", flush=True)
        with ThreadPoolExecutor(6) as ex:
            found = dict(zip(words, ex.map(lambda w: sorted(candidates(w, lang)), words)))
        print(f"{lang}: {sum(1 for v in found.values() if v)}/{len(words)} words have candidate files", flush=True)

        best: dict[str, dict] = {}
        for word, titles in found.items():
            scored = []
            for t in titles[:6]:
                c = info(t)
                f = c and download(c)
                if not f:
                    continue
                segs, _ = model.transcribe(str(f), language=lang, beam_size=5, condition_on_previous_text=False)
                segs = list(segs)
                heard = " ".join(s.text for s in segs).strip()
                want = g.AR_WORDS.get(word) or g.AR_NAMES.get(word) or word
                score = SequenceMatcher(None, norm(want, lang).replace(" ", ""), norm(heard, lang).replace(" ", "")).ratio()
                conf = sum(s.avg_logprob for s in segs) / len(segs) if segs else -9
                # Recognition often writes numbers as digits ("45", "٤٥").
                number = next((c for c, w in jobs.items() if w == word and c.isdigit()), None)
                digits = norm(heard, lang).translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")).replace(",", "")
                if number is not None and number in digits.split():
                    score = 1.0
                scored.append((score, conf, c, f, heard))
                if score >= 0.95:
                    break  # a clear match: no need to check the other recordings
            ok = [s for s in scored if s[0] >= args.min_score]
            if ok:
                score, conf, c, f, heard = max(ok, key=lambda s: (s[0], s[1]))
                best[word] = {**c, "file": f, "heard": heard, "score": round(score, 2)}
                print(f"  {word}: {c['title'][5:]} ({c['licence']}) heard {heard!r}", flush=True)
            elif scored:
                print(f"  {word}: {len(scored)} found, none said it clearly (best {max(s[0] for s in scored):.2f})", flush=True)

        human = 0
        for clip, word in jobs.items():
            b = best.get(word)
            if not b:
                continue
            out = g.ASSETS / lang / f"{g.key(clip)}.mp3"
            g.encode(b["file"].read_bytes(), out)
            manifest.setdefault(lang, {})[clip] = {
                "file": out.name, "voice": "human", "voiced": word,
                "source": b["page"], "author": b["author"], "licence": b["licence"],
            }
            human += 1
        for clip, e in manifest.get(lang, {}).items():
            if e.get("voice") != "human":
                e["placeholder"] = True
        print(f"{lang}: {human}/{len(lists[lang])} clips are now human recordings", flush=True)

    g.MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    write_credits(manifest)


def write_credits(manifest: dict) -> None:
    rows = {}
    for lang, clips in manifest.items():
        for clip, e in clips.items():
            if e.get("voice") == "human":
                rows[e["source"]] = f"{e['voiced']} - {e['author']} - {e['licence']} - {e['source']}"
    text = ("Voice recordings from Wikimedia Commons / Lingua Libre, used under their free licences.\n"
            "Each line: word - recorded by - licence - source. Clips were trimmed and loudness-matched.\n\n"
            + "\n".join(sorted(rows.values())) + "\n")
    (g.ASSETS / "CREDITS.txt").write_text(text, encoding="utf-8")
    print(f"credits: {len(rows)} recordings -> {g.ASSETS / 'CREDITS.txt'}")


if __name__ == "__main__":
    sys.exit(main())
