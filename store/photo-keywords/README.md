# Photo app search keyword examples (per language)

Examples of search keywords registered for the photo app (Google Photos), used in the store listing (the full description), the per-language screenshots (the search and search keyword screens) and the feature graphic. The file names are the Google Play listing language codes (the same as in `app/src/main/play/listings/`).

```json
{
  "keywords": ["the keywords registered for the photo app", "shown on the search keywords screen"],
  "appName": "the photo app's name on a device set to that language",
  "searchPrefix": "what the search screen and the feature graphic type: the start of appName",
  "reason": "why these were chosen (written in that language; en-US and ja-JP have none)"
}
```

- Japanese uses 「しゃしん」 and 「photo」. Kanji cannot be typed directly, so registering the reading makes a convincing example.
- English had no example as good as Japanese, so it uses "gallery" (the name of the gallery app on other makers' phones) and "fotos" (Spanish).
- The other languages were decided by asking subagents, in each language, to answer as native speakers of it (2026-11-26). The conditions: the keyword is not the start of the photo app's name on a device set to that language, it is short and something people would really register, and at least one of the two can be typed as is on the standard keyboard.
- The search does not ignore accents (docs/spec.md 2.2). Words with accents, such as "galería" and "álbum", must be typed with them.
- `appName` is the label of the Google Photos app (`/product/app/Photos/Photos.apk` on the Pixel_10 emulator, read with `aapt2 dump badging`) for that language, falling back as Android does. Before that check, some languages had a guessed name; with the real label, hi-IN's "photo" is now the start of the app name ("Photos").
- `searchPrefix` is the first two characters of `appName`, lowercased, with katakana as hiragana (Japanese types 「ふぉ」 for 「フォト」). Korean, Simplified and Traditional Chinese take one character, as their names are two. Romanian takes 「fo」, from the second word of 「Google Foto」, so that the photo app is not lost among the Google apps. The search screenshot and the feature graphic show it, so that typing the start of a name plainly finds the app; the keywords are for the search keywords screen, a secondary feature.
