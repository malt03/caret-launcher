#!/usr/bin/env python3
"""Renders Caret Launcher's Google Play feature graphic (1024x500) for each listing language.

It fills store/feature-graphic.html with the language's texts and renders it with headless Chrome
to app/src/main/play/listings/<lang>/graphics/feature-graphic/1.png, as a 24-bit PNG without
alpha, which Google Play requires.

The texts of a language:
- store/feature-graphic/<lang>.json:
    "tagline": the lines under the app name;
    "name": the app name, when it is not "Caret Launcher" (the language's app_name);
    "typed": the text in the search field, when it is not the photo app's searchPrefix.
- store/photo-keywords/<lang>.json: "searchPrefix" (the start of the photo app's name, typed in the
  search field, as on the search screenshot) and "appName" (the photo app's name, found above it).

The fonts come from Google Fonts: Inter, with the language's Noto Sans for the scripts Inter lacks.
Arabic and Hebrew mirror the layout. Texts that are too long shrink to fit their line, and the script
warns about each one it shrank (the page reports them), so the output need not be checked by eye for
that; it fails if a text still overflows at the smallest size.

Usage: scripts/feature-graphic.py [--langs en-US,ja-JP,...] [--out DIR]
  Without --langs, it renders every language that has store/feature-graphic/<lang>.json.
  With --out, it writes DIR/<lang>.png instead (to preview without touching the listings).
"""
import argparse, html, json, os, re, shutil, string, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, "store")
TEXTS = os.path.join(STORE, "feature-graphic")
KEYWORDS = os.path.join(STORE, "photo-keywords")
LISTINGS = os.path.join(ROOT, "app/src/main/play/listings")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
WIDTH, HEIGHT = 1024, 500

# The Noto Sans family of the scripts that Inter does not cover (Inter has Latin, Greek and
# Cyrillic, Vietnamese included).
NOTO = {
    "ja-JP": "Noto Sans JP", "ko-KR": "Noto Sans KR", "zh-CN": "Noto Sans SC",
    "zh-TW": "Noto Sans TC", "zh-HK": "Noto Sans HK", "th": "Noto Sans Thai",
    "hi-IN": "Noto Sans Devanagari", "ar": "Noto Sans Arabic", "iw-IL": "Noto Sans Hebrew",
}
RTL = {"ar", "iw-IL"}
# The HTML lang attribute, which picks the right glyphs for Han characters.
HTML_LANG = {"iw-IL": "he", "zh-CN": "zh-Hans-CN", "zh-TW": "zh-Hant-TW", "zh-HK": "zh-Hant-HK"}


def page(lang):
    texts = json.load(open(os.path.join(TEXTS, f"{lang}.json"), encoding="utf-8"))
    photos = json.load(open(os.path.join(KEYWORDS, f"{lang}.json"), encoding="utf-8"))
    families = ["Inter:wght@400;600;700"]
    noto = NOTO.get(lang)
    if noto:
        families.append(noto.replace(" ", "+") + ":wght@400;600;700")
    tagline = "".join(f'<div data-fit="464" data-part="tagline line {i}">{html.escape(line)}</div>'
                      for i, line in enumerate(texts["tagline"], 1))
    template = string.Template(open(os.path.join(STORE, "feature-graphic.html"), encoding="utf-8").read())
    return template.substitute(
        lang=HTML_LANG.get(lang, lang),
        dir="rtl" if lang in RTL else "ltr",
        fonts="&".join("family=" + f for f in families),
        font_family=f"'{noto}'," if noto else "",
        name=html.escape(texts.get("name", "Caret Launcher")),
        tagline=tagline,
        label=html.escape(photos["appName"]),
        typed=html.escape(texts.get("typed", photos["searchPrefix"])),
    )


def chrome(source, *args):
    return subprocess.run([CHROME, "--headless", "--hide-scrollbars", "--force-device-scale-factor=1",
                           f"--window-size={WIDTH},{HEIGHT}", "--virtual-time-budget=10000", *args,
                           "file://" + source],
                          check=True, capture_output=True, text=True).stdout


def fit_report(source):
    """What the page's fit() shrank: [{part, text, max, width, from, size, overflow}]."""
    dom = chrome(source, "--dump-dom")
    found = re.search(r'data-fit-report="([^"]*)"', dom)
    if not found:
        sys.exit("The page did not report its fitting (fonts not loaded?)")
    return json.loads(html.unescape(found.group(1)))


def render(lang, out):
    """Renders [lang] to [out] and returns the warnings about texts that did not fit."""
    with tempfile.TemporaryDirectory() as work:
        shutil.copy(os.path.join(STORE, "feature-graphic-bg.png"), work)
        source = os.path.join(work, "feature-graphic.html")
        open(source, "w", encoding="utf-8").write(page(lang))
        shot = os.path.join(work, "shot.png")
        chrome(source, f"--screenshot={shot}")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        # Flatten onto the background color: Google Play takes no alpha.
        subprocess.run(["magick", shot, "-background", "#3B4A6B", "-alpha", "remove", "-alpha", "off",
                        "-crop", f"{WIDTH}x{HEIGHT}+0+0", "+repage", "PNG24:" + out], check=True)
        warnings = []
        for r in fit_report(source):
            what = (f"{lang}: the {r['part']} \"{r['text']}\" is {r['width']}px wide, "
                    f"{r['width'] - r['max']}px over its {r['max']}px")
            if r["overflow"]:
                warnings.append((True, f"{what}, and still overflows at {r['size']:g}px"))
            else:
                warnings.append((False, f"{what}; shrunk from {r['from']:g}px to {r['size']:g}px"))
    print(f"{lang}: {os.path.relpath(out, ROOT)}")
    for _, warning in warnings:
        print("  warning: " + warning, file=sys.stderr)
    return warnings


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--langs", help="comma-separated listing languages (default: all with texts)")
    parser.add_argument("--out", help="write DIR/<lang>.png instead of into the listings")
    args = parser.parse_args()
    available = sorted(f[:-5] for f in os.listdir(TEXTS) if f.endswith(".json"))
    langs = args.langs.split(",") if args.langs else available
    missing = [l for l in langs if l not in available]
    if missing:
        sys.exit(f"No texts in store/feature-graphic/ for: {', '.join(missing)}")
    warnings = []
    for lang in langs:
        out = (os.path.join(args.out, f"{lang}.png") if args.out else
               os.path.join(LISTINGS, lang, "graphics", "feature-graphic", "1.png"))
        warnings += render(lang, out)
    if warnings:
        print(f"\n{len(warnings)} text(s) did not fit at their size:", file=sys.stderr)
        for _, warning in warnings:
            print("  " + warning, file=sys.stderr)
    if any(overflow for overflow, _ in warnings):
        sys.exit("Some text overflows even at the smallest size")


if __name__ == "__main__":
    main()
