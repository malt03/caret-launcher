# Photo app search keyword examples (per language)

Examples of search keywords registered for the photo app (Google Photos), used in the store listing (the full description), the per-language screenshots (the search and search keyword screens) and the feature graphic. The file names are the Google Play listing language codes (the same as in `app/src/main/play/listings/`).

```json
{
  "keywords": ["the word typed on the search screen", "a word shown with it on the search keywords screen"],
  "appName": "the photo app's name on a device set to that language",
  "reason": "why these were chosen (written in that language; en-US and ja-JP have none)"
}
```

- Japanese uses 「しゃしん」 and 「photo」. Kanji cannot be typed directly, so registering the reading makes a convincing example.
- English had no example as good as Japanese, so it uses "gallery" (the name of the gallery app on other makers' phones) and "fotos" (Spanish).
- The other languages were decided by asking subagents, in each language, to answer as native speakers of it (2026-11-26). The conditions: the keyword is not the start of the photo app's name on a device set to that language, it is short and something people would really register, and at least one of the two can be typed as is on the standard keyboard.
- The search does not ignore accents (docs/spec.md 2.2). Words with accents, such as "galería" and "álbum", must be typed with them.
- `appName` is the label of the Google Photos app (`/product/app/Photos/Photos.apk` on the Pixel_10 emulator, read with `aapt2 dump badging`) for that language, falling back as Android does. Before that check, some languages had a guessed name; with the real label, hi-IN's "photo" is now the start of the app name ("Photos").
