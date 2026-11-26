# en-US

The question and the answer, as written in the language.

## Question

You are a native speaker of American English and an experienced copywriter for app store pages.

We are localizing the feature graphic (1024×500) of the English (US) Google Play page of "Caret Launcher", an Android launcher (home screen app). On the left of the graphic are the app icon, the name "Caret Launcher" below it, and a two-line tagline below that. On the right is a search field with a few typed letters, and the photo app it found above it.

The Japanese tagline is:
- Line 1: 「常時キーボードを表示」 ("The keyboard is always shown")
- Line 2: 「数文字打って、アプリを起動」 ("Type a few letters and launch the app")

So that the wording and tone match the page, read these two files with the English description. Read only these two:
- /Users/malt03/Documents/projects/TextLauncher/app/src/main/play/listings/en-US/short-description.txt
- /Users/malt03/Documents/projects/TextLauncher/app/src/main/play/listings/en-US/full-description.txt

Rules:
- Two lines with the same content as the Japanese tagline. Write it the way an American copywriter would naturally put it, not as a literal translation.
- Each line at most 36 characters if possible (spaces included). Longer lines are set smaller, which looks worse.
- No promotional or ranking words such as "best" or "#1", no emoji, no all caps.

Answer in this form:
- Line 1: (the text and its number of characters)
- Line 2: (the text and its number of characters)
- Literal translation into Japanese:
- Reason: (briefly, why this wording)
- Other candidates: (1 or 2, each with its number of characters and why you did not choose it)

Do not read any other file, do not use any other tool, and do not write any file.

## Answer

- Line 1: The keyboard is always up (25 characters)
- Line 2: Type a few letters to open an app (33 characters)
- Literal translation into Japanese:
  - Line 1: 「キーボードはいつも出ている」
  - Line 2: 「数文字打って、アプリを開く」
- Reason: Both lines reuse the wording the page already uses. The short description says "Keyboard always up", and the first line of the full description says "the keyboard always up" and "Type a few letters to open an app". So the graphic and the text below it say the same thing. "Always up" is how Americans describe a keyboard that stays on screen. "Open" is the plain everyday verb and is the one the page uses everywhere, while "launch" is more technical. Both lines are short, full sentences, so they read evenly as a pair. Leave out end punctuation, since the Japanese tagline has none.
- Other candidates:
  - "Keyboard always up" (18 characters). It is the exact phrase from the short description, but as a headline it is clipped and reads like a spec note. It also feels uneven next to the full-sentence second line.
  - "Type a few letters, launch the app" (34 characters). It is closer to the Japanese 「起動」, but "launch" is more technical than "open" and doesn't match the page's wording. The comma split also reads more stiffly than "to open".
