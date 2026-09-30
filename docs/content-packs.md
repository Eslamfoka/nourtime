# Learning Hub content packs

All Learning Hub content lives in JSON files under `app/src/main/assets/learning/`. Adding levels,
words, languages, drawings or illustrations means editing these files. **No code changes.**
The unit tests load every pack and fail the build if anything is wrong (see "Checks" below).

```
assets/learning/
  concepts.json            things with a picture (shared by all languages) + colors
  letters/levels.json      Letters & Words levels
  letters/ar.json          Arabic: alphabet + words + color names
  letters/en.json          English: the same for English
  listen/levels.json       Listen & Find levels (uses the words, colors and letters above)
  patterns/levels.json     What Comes Next? levels
  clock/levels.json        Tell the Time levels
  tracing/<language>.json  Letter Tracing: the strokes of each letter
  memory/levels.json       Memory Match levels
  words/levels.json        Word Builder levels
  sorting/levels.json      Sorting levels
  shop/levels.json         Little Shop levels
  math/levels.json         Smart Math levels
  connect/shapes.json      Number Connect drawings
  coloring/pictures.json   Coloring Match pictures
  images/<id>.webp         illustrations (optional; the emoji is used until one exists)
```

Every file starts with `"schema": 1`. The app skips a file with a schema it doesn't know, and ignores
fields it doesn't know, so newer packs never crash older versions of the app.

## Rules that apply everywhere
- **Ids** (levels, concepts, drawings): lowercase `a-z`, digits, `-` and `_`, up to 48 characters.
  **Never rename or reuse an id**: children's stars are saved by id. Levels can be added, reordered or
  inserted anywhere; a finished level stays open.
- **Order** in a `levels` list is play order: each level opens when the one before it is done.
- **`startAt`** lets older children begin further in: `{"AGES_7_9": "sub-10", "AGES_10_12": "add-20"}`.
- **Colors** are `"#RRGGBB"`.

## Words and pictures (`concepts.json` + `letters/<language>.json`)
A *concept* is a thing with a picture, the same in every language:
```json
{"id": "apple", "emoji": "🍎", "category": "food", "image": "apple"}
```
- `category` groups words (food, animals, body, …); levels can pick categories.
- `image` (optional) is the illustration `images/apple.webp`. Without it the emoji is shown.
- Two concepts may not share an emoji (a question would have two right answers).

Each language names the concepts and lists its alphabet:
```json
{
  "schema": 1, "language": "ar",
  "letters": [{"letter": "أ", "name": "ألف", "concept": "rabbit"}],
  "words": {"rabbit": "أرنب", "apple": "تفاحة"},
  "colors": {"red": "أحمر"}
}
```
- The word of a letter's concept must start with that letter (Arabic alef forms count as one).
- A concept without a word in a language simply doesn't appear in that language.
- **Adding a word:** one line in `concepts.json`, one line per language file.
- **Adding a language:** a new `letters/<tag>.json` (and, today, one entry in the `LearnLanguage`
  enum for its voice and the language switch).

## Letters & Words levels (`letters/levels.json`)
```json
{"id": "animals-1", "tasks": ["word_to_picture"], "choices": 3, "categories": ["animals"], "questions": 6}
```
- `tasks`: `letter_to_picture`, `letter_to_word`, `word_to_picture`, `name_to_color`,
  `color_to_name`. Several tasks take turns. `["letter_to_word", "letter_to_picture"]` is the
  "A, Apple → word → picture" flow for the same letter.
- `choices` 2–4; `firstLettersOnly` uses the first half of the alphabet; `categories` (empty = all).

## Listen & Find levels (`listen/levels.json`)
```json
{"id": "hear-animals-2", "tasks": ["listen_to_picture"], "choices": 2, "categories": ["animals"]}
```
- The child hears something (read aloud when the question appears, again on the big speaker button)
  and taps it. `tasks`: `listen_to_picture` (a word), `listen_to_color`, `listen_to_letter` (a letter's
  name), `listen_to_number` (0..`maxNumber`, default 10). Several tasks take turns.
- `choices` 2–4; `categories` and `firstLettersOnly` as in Letters & Words; words come from
  `letters/<language>.json`, in the language chosen on the level screen.
- Without a voice for that language on the phone, the word (or number) is shown written instead.

## What Comes Next? levels (`patterns/levels.json`)
```json
{"id": "colors-ab", "kind": "colors", "rules": ["ab"], "shown": 4, "choices": 2}
{"id": "twos", "kind": "numbers", "rules": ["step"], "steps": [2], "max": 20, "shown": 4}
```
- `kind`: `colors` (the language pack's colors, never white), `shapes` (● ■ ▲ ★ ♥ ◆), `pictures`
  (words' pictures, narrowed by `categories`) or `numbers`.
- Colors, shapes and pictures repeat a unit from `rules`: `ab`, `aab`, `abb`, `abc`, `aabb`, `abbc`,
  `abcd` (each letter a different item). Several units are mixed; the tests check that no wrong
  choice also fits any unit of the level.
- Numbers use `step` (one of `steps` added each time; negative counts down) or `double`. Every number,
  the answer included, stays in 0..`max`; `max` must be at least |step| × `shown` (and 2^`shown` for
  `double`).
- `shown` 3–6 items before the "?", `choices` 2–4.

## Tell the Time levels (`clock/levels.json`)
```json
{"id": "read-half", "tasks": ["clock_to_time"], "precision": "half", "choices": 3}
```
- `tasks`: `clock_to_time` (read the analog clock, pick the written time) and `time_to_clock` (read
  the time, pick the clock); several take turns.
- `precision`: `hour` (3:00), `half` (:00/:30), `quarter`, `five` (every 5 minutes) or `minute`.
  Times are generated, so each level gives endless questions; wrong answers include the classic
  mistakes (hands swapped, the next hour, a step off).

## Little Shop levels (`shop/levels.json`)
```json
{"id": "change-20", "coins": [1, 2, 5, 10], "modes": ["change"], "minPrice": 3, "maxPrice": 19, "paid": 20}
```
- `coins`: the coin values in the purse (different, 1..500, and **1 must be there** so any amount can
  be made). Coins have no currency sign; values only.
- `modes` (taking turns): `pay` (make the price exactly) and `change` (the price was paid with `paid`,
  give the change back; `paid` must be more than `maxPrice`).
- Prices are drawn from `minPrice`..`maxPrice`; `items` (1–8) things to buy from `categories`.

## Sorting levels (`sorting/levels.json`)
```json
{"id": "animals-food", "items": 4, "bins": [
  {"category": "animals", "emoji": "🐾", "labels": {"ar": "حيوانات", "en": "Animals"}},
  {"category": "food", "emoji": "🍽️", "labels": {"ar": "طعام", "en": "Food"}}]}
```
- 2 or 3 `bins`. A bin takes pictures of a word `category`, or numbers of a `parity` (`even` / `odd`,
  numbers 1..`max`). Each bin has a sign (`emoji`) and a name per language in `labels` (the words
  language picks it; English is the fallback).
- `items` (2–12) are spread as evenly as possible over the bins. The tests deal every level in every
  language, so a category with too few words fails the build.

## Word Builder levels (`words/levels.json`)
```json
{"id": "animals-5", "words": 4, "minLength": 3, "maxLength": 5, "categories": ["animals"], "extraTiles": 2}
```
- `words` to spell per level (1–10), taken from `letters/<language>.json` (words with a space are
  never used), `minLength`..`maxLength` letters, from `categories` (empty = all).
- `extraTiles` (0–6) wrong letters among the tiles, never letters of the word. `hint: true` shows the
  whole word faded above the one being built.
- The tests check that every level has enough fitting words in **every** language; add words or widen
  the lengths if a new language fails it.

## Memory Match levels (`memory/levels.json`)
```json
{"id": "words-food-4", "pairs": 4, "kinds": ["word_picture"], "categories": ["food"]}
```
- `pairs` 2–8 (4, 6, 8, … cards in rows of 2–4). `kinds` (mixed when several): `same_picture`,
  `word_picture`, `letter_picture` (a letter and a picture starting with it), `number_dots`
  (1..`maxNumber`, default 10, at least `pairs`), `color_name`.
- Words, letters and colors come from `letters/<language>.json` in the chosen words language. No two
  cards on the table show the same thing (so "Orange" the fruit and the color never meet).
- Stars: 3 within 1.5 moves per pair, 2 within 2.5.

## Letter Tracing (`tracing/<language>.json`)
```json
{"id": "ar-ba", "letter": "ب", "strokes": [{"path": "M86 40 Q88 70 62 70 L38 70 Q12 70 14 40"}, {"dot": [50, 84]}]}
```
- One file per language (`"language": "ar"`), levels in play order; ids must be unique across the
  languages (`ar-…`, `en-…`), since stars are saved by id.
- `strokes` in writing order. A stroke is SVG path data in a 100 × 100 box (`viewBox` can change it),
  drawn **from its first point in its direction**: the child starts at the green dot and follows it.
  One continuous line per stroke (no second `M`). A `dot` [x, y] is tapped instead of traced.
- The app resamples each stroke every 0.02 of the canvas; the test traces every shipped letter point
  by point, so a stroke that can't be finished (e.g. one that doubles back on itself too tightly)
  fails the build.

## Smart Math levels (`math/levels.json`)
```json
{"id": "mul-2-5", "ops": ["mul"], "minFactor": 2, "factor": 5, "choices": 4}
```
- `ops`: `add`, `sub`, `mul`, `div`, `compare`, and the missing-number ops `missing_add`
  (`7 + ? = 12`), `missing_sub` (`15 − ? = 9`), `missing_mul` (`4 × ? = 28`). Several are mixed.
- `max` bounds numbers in + − and comparisons; `min` (default 1) is the smallest number added or
  taken away (`max` must be at least 2 × `min` + 1); `factor`/`minFactor` bound × and ÷.
- `dots: true` draws dots under numbers (counting help). Numbers are generated, so each level gives
  endless different questions.

## Number Connect (`connect/shapes.json`)
```json
{"id": "fish", "emoji": "🐟", "closed": true, "image": "fish",
 "dots": [[0.1, 0.5, 0.38, 0.9], [0.68, 0.42, 0.38, 0.1], [0.9, 0.22]]}
```
- Dots are `[x, y]` in 0–1 (top left is 0,0), or `[x, y, curveX, curveY]`: the control point bends the
  line **arriving** at that dot (for dot 1 of a closed drawing: the closing line).
- Dots must be at least 0.13 apart (a finger mustn't cover two).
- `image` (optional): an illustration shown when the drawing is finished.

## Coloring Match (`coloring/pictures.json`)
```json
{"id": "apple", "emoji": "🍎", "colors": ["#E53935", "#43A047"], "extraColors": 1,
 "viewBox": [512, 512],
 "regions": [
   {"color": 0, "path": "M256 96 C…Z"},
   {"color": 1, "oval": [300, 80, 40, 20]}]}
```
- Each region is one of `box` [left, top, right, bottom], `oval` [cx, cy, rx, ry], `poly`
  [x0, y0, x1, y1, …] or `path` (SVG path data, all commands including arcs), in `viewBox` units.
- Later regions sit on top of earlier ones (an eye on a face). Every region must stay tappable.
- The colored reference is the same drawing in its colors, so no second image is needed.
- `extraColors`: wrong colors added to the palette from the top-level `distractors` list.

### From an illustrator's SVG
Ask for flat SVGs: one closed `<path>` per colorable part, a solid fill per part, no gradients,
masks, transforms or text (flatten them before export). Copy each path's `d` and fill into a region.
A converter script that does this automatically is on the roadmap.

## Illustrations (`images/`)
WebP (or PNG), square, about 512 × 512 px, transparent background, named after the concept or drawing
id. Decoded off the main thread at display size and cached. Large sets can move to a Play Asset
Delivery pack later without code changes.

## Checks (run by `./gradlew :app:testDebugUnitTest`)
`ContentPacksTest` loads every pack and fails on: invalid JSON or schema, bad or duplicate ids,
unknown ops/tasks/age groups, `startAt` pointing nowhere, words not starting with their letter,
duplicate letters/pictures/words, colors that aren't `#RRGGBB`, dots too close or outside the canvas,
coloring regions that can't be tapped, and every generated question is checked for one right answer.
In the app, a broken item is left out and logged (`LearningContent` in logcat) instead of crashing.

## Performance
Packs are read only when the hub opens (each once, off the main thread) and kept in memory. A test
parses and checks a 10,000-word pack; see its time in the test output. If a single game ever needs
hundreds of thousands of rows or search, the same JSON can be compiled into a prebuilt Room database
behind `ContentLoader`'s file interface without changing the games.
