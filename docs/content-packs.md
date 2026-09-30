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
