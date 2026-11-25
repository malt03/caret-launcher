#!/usr/bin/env python3
"""Takes the Google Play screenshots of each store listing language on an emulator.

For every language it sets the device locale, writes the language's photo app keywords
(store/photo-keywords/<lang>.json) into the launcher's search keywords, and takes the six screens
of the English set: 01_home, 02_search, 03_all_apps, 04_search_keywords, 05_share_text and
06_themed_icons (themed icons are switched on once, in English, for a second pass). The images go
to app/src/main/play/listings/<lang>/graphics/<phone-screenshots|large-tablet-screenshots>/,
which git ignores for every language but en-US. At the end the device's locale, the launcher's
search keywords and the icon style are put back.

The device needs, once (see CLAUDE.md): the debug launcher installed and set as home, the store
tools APK installed (:store-tools:installDebug), the home screen arranged with the clock widget,
Gemini and Chrome as the share buttons, the store wallpaper, themed icons off, and Gboard's
"show recently copied text" suggestion off. A language whose Gboard asks for a layout the first
time (Japanese) needs that answered once.

Usage: scripts/store-screenshots.py --serial emulator-5556 --device phone [--langs de-DE,fr-FR]
"""
import argparse
import base64
import html
import json
import os
import re
import sqlite3
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LISTINGS = os.path.join(ROOT, "app/src/main/play/listings")
KEYWORDS = os.path.join(ROOT, "store/photo-keywords")
RES = os.path.join(ROOT, "app/src/main/res")
PKG = "com.malt03.caretlauncher"
PHOTOS_KEY = "com.google.android.apps.photos/com.google.android.apps.photos.home.HomeActivity#0"

# Device-specific places: an empty cell of the home screen (for its long-press menu), and the
# swipes that bring the picker's Icons item into view.
PROFILES = {
    "phone": {
        "dir": "phone-screenshots",
        "empty_home": (560, 760),
        "picker_swipes": [(950, 1000, 100, 1000), (540, 2100, 540, 1500)],
    },
    "tablet": {
        "dir": "large-tablet-screenshots",
        "empty_home": (1100, 450),
        "picker_swipes": [(2200, 600, 600, 600)],
    },
}

# Google Play's listing language -> the device locale.
# (Android names Chinese by script, and has no es-419 or plain ar among its device locales.)
DEVICE_LOCALE = {"iw-IL": "he-IL", "no-NO": "nb-NO", "id": "id-ID", "sk": "sk-SK", "ro": "ro-RO",
                 "uk": "uk-UA", "vi": "vi-VN", "th": "th-TH", "ar": "ar-EG", "es-419": "es-MX",
                 "zh-CN": "zh-Hans-CN", "zh-TW": "zh-Hant-TW", "zh-HK": "zh-Hant-HK"}
# Google Play's listing language -> the app's resource qualifiers to read the menu texts from.
RES_QUALIFIERS = {"en-US": [""], "es-419": ["es-rUS"], "es-US": ["es-rUS"], "no-NO": ["nb"],
                  "id": ["in"], "iw-IL": ["iw"]}


class Device:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args, binary=False, check=True):
        r = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, check=check)
        return r.stdout if binary else r.stdout.decode("utf-8", "replace")

    def shell(self, cmd):
        return self.adb("shell", cmd)

    def tap(self, x, y):
        self.shell(f"input tap {x} {y}")

    def swipe(self, x1, y1, x2, y2, ms=400):
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")

    def long_press(self, x, y):
        self.swipe(x, y, x, y, 1100)

    def key(self, name):
        self.shell(f"input keyevent {name}")

    def screencap(self):
        return self.adb("exec-out", "screencap", "-p", binary=True)

    def ui_xml(self):
        for attempt in range(3):
            if self.adb("shell", "uiautomator dump /sdcard/ui.xml", check=False).find("dumped") >= 0:
                break
            time.sleep(2)
        return self.adb("exec-out", "cat", "/sdcard/ui.xml")

    def nodes(self):
        """The UI nodes of the window in front: (text, (left, top, right, bottom), class)."""
        xml = self.ui_xml()
        out = []
        for m in re.finditer(r"<node [^>]*>", xml):
            n = m.group(0)
            text = html.unescape(re.search(r' text="([^"]*)"', n).group(1))
            b = tuple(map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', n).group(1))))
            cls = re.search(r' class="([^"]*)"', n).group(1)
            out.append((text, b, cls))
        return out

    def find(self, texts, timeout=40):
        """The center of the first node whose text is one of [texts]."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            for text, b, _ in self.nodes():
                if text in texts:
                    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
            time.sleep(0.5)
        raise RuntimeError(f"not on screen: {texts}")

    def search_field(self, timeout=40):
        """The search field's bounds, once the launcher shows it (an emulator can be slow)."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            for _, b, cls in self.nodes():
                if cls == "android.widget.EditText":
                    return b
            time.sleep(1)
        raise RuntimeError("no search field")

    def set_search_text(self, text):
        encoded = base64.b64encode(text.encode("utf-8")).decode()
        for attempt in range(3):  # UiAutomation can time out connecting on a busy emulator
            out = self.shell(f"am instrument -w -e text64 {encoded} com.malt03.caretlauncher.storetools/.SetSearchText")
            if "set" in out:
                return
            time.sleep(3)
        raise RuntimeError(f"could not set the search text: {out.strip()}")

    def set_locale(self, locale):
        self.shell(f"cmd locale set-device-locale {locale}")
        time.sleep(6)

    def locale(self):
        return self.shell("cmd locale get-device-locale").strip()

    def demo_status_bar(self):
        self.shell("settings put global sysui_demo_allowed 1")
        for cmd in ["enter", "clock -e hhmm 0900", "battery -e level 100 -e plugged false",
                    "network -e wifi show -e level 4 -e fully true", "network -e mobile hide",
                    "notifications -e visible false"]:
            command, *extra = cmd.split(" ", 1)
            self.shell(f"am broadcast -a com.android.systemui.demo -e command {command} {extra[0] if extra else ''}")

    def launcher_db(self):
        return self.adb("exec-out", "run-as", PKG, "cat", "databases/launcher.db", binary=True)

    def put_launcher_db(self, data):
        with tempfile.NamedTemporaryFile(suffix=".db") as f:
            f.write(data)
            f.flush()
            self.adb("push", f.name, "/data/local/tmp/launcher.db")
        self.shell(f"run-as {PKG} sh -c 'cat /data/local/tmp/launcher.db > databases/launcher.db "
                   f"&& rm -f databases/launcher.db-journal'")
        self.shell("rm /data/local/tmp/launcher.db")


def png24(png, path):
    """Saves a screencap as a 24-bit PNG (Google Play takes no alpha)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(["magick", "png:-", "-alpha", "off", "-define", "png:color-type=2", f"PNG24:{path}"],
                   input=png, check=True)


def mean_in(png, box):
    l, t, r, b = box
    out = subprocess.run(["magick", "png:-", "-crop", f"{r - l}x{b - t}+{l}+{t}", "+repage",
                          "-colorspace", "gray", "-format", "%[fx:mean]", "info:"],
                         input=png, capture_output=True, check=True)
    return float(out.stdout)


def with_cursor(device, box, frames=6):
    """The frame in which the field's blinking cursor shows (the field is darkest then)."""
    shots = [device.screencap() for _ in range(frames)]
    return min(shots, key=lambda png: mean_in(png, box))


def res_string(lang, name):
    language, _, region = lang.partition("-")
    qualifiers = RES_QUALIFIERS.get(lang) or ([f"{language}-r{region}"] if region else []) + [language]
    for q in qualifiers:
        path = os.path.join(RES, f"values-{q}" if q else "values", "strings.xml")
        if os.path.exists(path):
            m = re.search(rf'<string name="{name}">(.*?)</string>', open(path, encoding="utf-8").read())
            if m:
                return html.unescape(m.group(1)).strip('"').replace("\\'", "'")
    return res_string("en-US", name)


def set_photo_keywords(device, keywords):
    """Replaces the photo app's search keywords (the launcher is stopped meanwhile)."""
    device.shell(f"am force-stop {PKG}")
    with tempfile.NamedTemporaryFile(suffix=".db") as f:
        f.write(device.launcher_db())
        f.flush()
        db = sqlite3.connect(f.name)
        db.execute("DELETE FROM search_terms WHERE app_key = ?", (PHOTOS_KEY,))
        db.executemany("INSERT INTO search_terms (app_key, term) VALUES (?, ?)",
                       [(PHOTOS_KEY, k) for k in keywords])
        db.commit()
        db.close()
        device.put_launcher_db(open(f.name, "rb").read())


def home(device, wait=2):
    """Goes home and waits until the search field shows."""
    device.key("HOME")
    time.sleep(wait)
    return device.search_field()


def settled_home(device, warmup_text):
    """Home after a locale change: the keyboard in the new language and every home item loaded."""
    home(device, 4)
    # Gboard switches to the new language only when it is shown again; until then a
    # screenshot can have the previous language's keyboard.
    device.set_search_text(warmup_text)
    time.sleep(2)
    field = home(device)
    # The model reloads after a locale change; wait until the widget and the icons are back
    # and their number stops changing.
    last, deadline = None, time.time() + 40
    while time.time() < deadline:
        xml = device.ui_xml()
        icons = len(re.findall(r'class="android.widget.TextView"[^>]*content-desc="[^"]+"', xml))
        if "android.appwidget.AppWidgetHostView" in xml and icons and icons == last:
            break
        last = icons
        time.sleep(2)
    time.sleep(2)
    return field


def shoot_language(device, profile, lang, out):
    keywords = json.load(open(os.path.join(KEYWORDS, f"{lang}.json"), encoding="utf-8"))["keywords"]
    device.set_locale(DEVICE_LOCALE.get(lang, lang))
    set_photo_keywords(device, keywords)
    field = settled_home(device, keywords[0])
    png24(with_cursor(device, field), os.path.join(out, "01_home.png"))

    device.set_search_text(keywords[0])
    # The photo app's name, as the only result shows it (the name in this locale).
    photos = []
    deadline = time.time() + 20
    while not photos and time.time() < deadline:
        time.sleep(2)
        photos = [t for t, b, cls in device.nodes() if t and cls != "android.widget.EditText" and b[3] <= field[1]]
    if not photos:
        raise RuntimeError(f"{lang}: {keywords[0]} found no app")
    time.sleep(1)
    png24(with_cursor(device, field), os.path.join(out, "02_search.png"))

    cx, cy = (field[0] + field[2]) // 2, (field[1] + field[3]) // 2
    for attempt in range(3):  # the swipe sometimes doesn't open all apps on a slow emulator
        home(device)
        device.swipe(cx, cy, cx, 150)
        try:
            device.find({photos[0]}, timeout=25)  # one dump can take 8 s
            break
        except RuntimeError:
            if attempt == 2:
                raise
    time.sleep(2)
    png24(device.screencap(), os.path.join(out, "03_all_apps.png"))

    device.long_press(*device.find({photos[0]}))
    time.sleep(1.5)
    device.tap(*device.find({res_string(lang, "menu_search_terms"), res_string(lang, "menu_search_terms_short")}))
    time.sleep(3)
    png24(device.screencap(), os.path.join(out, "04_search_keywords.png"))
    device.key("BACK")
    time.sleep(1)
    device.key("BACK")
    home(device)
    time.sleep(2)

    device.long_press(*profile["empty_home"])
    time.sleep(1.5)
    device.tap(*device.find({res_string(lang, "share_text_title")}))
    time.sleep(3)
    png24(device.screencap(), os.path.join(out, "05_share_text.png"))
    device.key("BACK")
    home(device)


def set_themed_icons(device, profile, themed):
    """Wallpaper & style -> Icons -> Minimal (themed) or Default, in English."""
    device.shell("am start -n com.google.android.apps.wallpaper/com.android.customization.picker.CustomizationPickerActivity")
    device.find({"Wallpaper & style"}, timeout=90)  # it can stay black for a while on a slow emulator
    time.sleep(3)
    for swipe in profile["picker_swipes"]:
        device.swipe(*swipe, 600)
        time.sleep(3)
    device.tap(*device.find({"Icons"}))
    time.sleep(4)
    device.tap(*device.find({"Minimal" if themed else "Default"}))
    time.sleep(3)
    device.tap(*device.find({"Apply"}))
    time.sleep(3)
    home(device, 8)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--serial", required=True)
    p.add_argument("--device", required=True, choices=PROFILES)
    p.add_argument("--langs", help="comma-separated listing languages (default: all but en-US)")
    p.add_argument("--no-themed", action="store_true", help="skip 06_themed_icons")
    args = p.parse_args()

    profile = PROFILES[args.device]
    langs = args.langs.split(",") if args.langs else sorted(
        d for d in os.listdir(LISTINGS) if d != "en-US" and os.path.exists(os.path.join(KEYWORDS, f"{d}.json")))
    device = Device(args.serial)
    supported = set(device.shell("cmd locale list-device-locales").split())
    unknown = [l for l in langs if DEVICE_LOCALE.get(l, l) not in supported]
    if unknown:
        sys.exit(f"no device locale for: {unknown}")
    original_locale = device.locale()
    original_db = device.launcher_db()
    device.demo_status_bar()
    failed = []
    try:
        for lang in langs:
            print(f"{lang}: screens 1-5", flush=True)
            try:
                shoot_language(device, profile, lang, os.path.join(LISTINGS, lang, "graphics", profile["dir"]))
            except Exception as e:  # keep going; the language is reported at the end
                failed.append(lang)
                print(f"{lang}: FAILED {e}", flush=True)
                device.key("BACK")
                device.key("HOME")
        if not args.no_themed:
            device.set_locale("en-US")
            set_themed_icons(device, profile, True)
            try:
                for lang in langs:
                    print(f"{lang}: themed icons", flush=True)
                    device.set_locale(DEVICE_LOCALE.get(lang, lang))
                    keywords = json.load(open(os.path.join(KEYWORDS, f"{lang}.json"), encoding="utf-8"))["keywords"]
                    field = settled_home(device, keywords[0])
                    png24(with_cursor(device, field),
                          os.path.join(LISTINGS, lang, "graphics", profile["dir"], "06_themed_icons.png"))
            finally:
                device.set_locale("en-US")
                set_themed_icons(device, profile, False)
    finally:
        device.shell(f"am force-stop {PKG}")
        device.put_launcher_db(original_db)
        device.set_locale(original_locale)
        device.key("HOME")
    if failed:
        print("failed:", ",".join(failed))
        sys.exit(1)


if __name__ == "__main__":
    main()
