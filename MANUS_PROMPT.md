# Prompt for Manus (copy everything below the line)

---

You will generate Arabic audio clips for a children's educational app using a Google AI Studio app in the browser. I (the account owner) will log in to Google myself if you are asked to sign in. Never enter, store or ask for passwords; if a login screen appears, stop and ask me to log in.

**Input file:** `missing_arabic_audio.csv` (attached). It has 323 rows and two columns:
- `Arabic_Text_With_Tashkeel`: the exact text to speak (Modern Standard Arabic with full diacritics)
- `Required_Filename.mp3`: the exact file name for that row's audio (for example `1c1e43d61d9bb3a0.mp3`)

**Page:** https://aistudio.google.com/apps/drive/1AI3eea2N05aZkz8vLd9ScNbtiY2eSDwC?showAssistant=true&showPreview=true

**Steps:**
1. Open the page and wait until the app in the preview panel is loaded.
2. Choose ONE voice and keep it for every row. Preferred voice: **Kore** (a clear female voice). If Kore isn't offered, pick the clearest female voice and tell me which one you used. If the app has a style or instruction field, use: `اقرأ بالعربية الفصحى بنطق واضح وهادئ لطفل صغير، كما هي بالتشكيل، دون أي كلمة إضافية`
3. For each row of the CSV, in order:
   a. Paste the text from `Arabic_Text_With_Tashkeel` **exactly as written** (keep every diacritic, add nothing).
   b. Generate the audio and wait until it has finished.
   c. Download it and rename the file to exactly the `Required_Filename.mp3` value of that row. If the app only gives WAV or another format, keep that format but use the same name before the extension (for example `1c1e43d61d9bb3a0.wav`). Do not convert, trim or edit the audio.
   d. Listen to (or check) that the file isn't empty or silent.
4. Work at a normal human pace: about one clip every 10–20 seconds, no parallel tabs. If the site shows a limit or an error, wait a few minutes and continue where you stopped.
5. If a row fails 3 times (no audio comes back), skip it and add it to `failed.txt` with its file name and text. Rows whose text starts with `وَ` (like `وَخَمْسَةٌ وَأَرْبَعُون`) are known to fail sometimes; try them once more at the end.

**Output:** one ZIP named `arabic_audio.zip` containing all the renamed audio files in a single folder (no subfolders), plus `failed.txt` if anything failed, plus `voice.txt` with the voice name you used. Send me the ZIP.

**Do not:** change any text, merge rows, use different voices for different rows, or include any files other than the ones above.
