# Prompt for Manus (copy everything below the line)

Attach the 13 files `manus_batches/batch_01.txt` … `batch_13.txt` to Manus.

---

You will record 13 long Arabic audio files for a children's educational app using a Google AI Studio app in the browser. I (the account owner) will log in to Google myself if you are asked to sign in. Never enter, store or ask for passwords; if a login screen appears, stop and ask me to log in.

**Input:** 13 text files, `batch_01.txt` to `batch_13.txt`. Each one starts with a one-line Arabic instruction, then a blank line, then up to 25 short lines (Modern Standard Arabic words or numbers with full diacritics, each ending with a period).

**Page:** https://aistudio.google.com/apps/drive/1AI3eea2N05aZkz8vLd9ScNbtiY2eSDwC?showAssistant=true&showPreview=true

**Steps:**
1. Open the page and wait until the app in the preview panel is loaded.
2. Choose ONE voice and keep it for all 13 files. Preferred: **Kore** (a clear female voice). If Kore isn't offered, pick the clearest female voice and tell me which one you used.
3. For each batch file, in order (batch_01 first):
   a. Paste the **whole content of the file exactly as written** (instruction line included; keep every diacritic, change nothing). If the app has a separate style/instruction field, put the first line there and the list of lines in the text field instead.
   b. Generate the audio and wait until it has completely finished.
   c. Download it and name it exactly after the batch: `batch_01.wav` (or `batch_01.mp3` if the app only gives MP3). Do not edit, trim or convert the audio.
   d. Check the recording: it must read **every line, in order, with a clear pause (about 1–2 seconds) between lines**, and must not read the instruction line or any numbers/punctuation aloud. If lines are skipped, merged without a pause, or the instruction is read aloud, generate that batch again (up to 3 tries). If it still fails, note it in `failed.txt` and continue.
4. Work at a normal human pace, one batch at a time, no parallel tabs. If the site shows a limit or an error, wait a few minutes and continue.

**Output:** one ZIP named `arabic_batches.zip` with the 13 files `batch_01` … `batch_13` (no subfolders), plus `voice.txt` with the voice name you used, plus `failed.txt` if any batch failed. Send me the ZIP.

**Do not:** change any text, split or merge batches, use different voices, or include other files.
