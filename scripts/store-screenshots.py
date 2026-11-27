#!/usr/bin/env python3
"""Takes the Google Play screenshots of each store listing language on an emulator.

For every language it sets the device locale, writes the language's photo app keywords
(store/photo-keywords/<lang>.json) into the launcher's search keywords, and takes the screens of
the English set (themed icons are switched on once, in English, for a second pass). The search
screen types the start of the photo app's name (searchPrefix), the search keywords screen shows the
keywords. At the end the device's locale, the launcher's search keywords and the icon style are put
back.

- phone, tablet: 01_home, 02_search, 03_all_apps, 04_search_keywords, 05_share_text and
  06_themed_icons, in app/src/main/play/listings/<lang>/graphics/<phone-screenshots|
  large-tablet-screenshots>/, which git ignores for every language but en-US.
- fold (a Pixel 10 Pro Fold, unfolded; all apps always shows on the left): 01_home, 02_search,
  03_search_keywords, 04_share_text and 05_themed_icons cut to 1:1, then the home screen at 4:3
  (06_home_4x3, landscape with the screen widened) and folded at 10:16 (07_home_folded_10x16, the
  outer screen widened, with the items placed by hand for its 6-column grid), in
  store/screenshots/fold/<lang>/ (the API cannot upload them; Play Console takes them by hand).
  git keeps only en-US's, in store/screenshots/fold/.

The device needs, once (see CLAUDE.md): the debug launcher installed and set as home, the store
tools APK installed (:store-tools:installDebug), the home screen arranged with the clock widget,
Gemini and Chrome as the share buttons, the store wallpaper, themed icons off, and Gboard's
"show recently copied text" suggestion off. A language whose Gboard asks for a layout the first
time (Japanese) needs that answered once.

Every screen with the search field focused is taken in a frame where its blinking cursor and the
keyboard show, and the script fails if they don't. The status bar is the same in every language:
9:00 (12-hour) and no notification icons. --screens takes only some screens again (home,
search, all_apps, search_keywords, share_text, themed_icons, and on a fold home_4x3 and
home_folded).

Usage: scripts/store-screenshots.py --serial emulator-5556 --device phone|tablet|fold [--langs de-DE,fr-FR] [--screens home,search]
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
# swipes that bring the picker's Icons item into view. "names" are the screens' file names
# (None: not taken).
PHONE_NAMES = {"home": "01_home", "search": "02_search", "all_apps": "03_all_apps",
               "search_keywords": "04_search_keywords", "share_text": "05_share_text",
               "themed_icons": "06_themed_icons"}
PROFILES = {
    "phone": {
        "dir": "phone-screenshots",
        "names": PHONE_NAMES,
        "empty_home": (560, 760),
        "picker_swipes": [(950, 1000, 100, 1000), (540, 2100, 540, 1500)],
    },
    "tablet": {
        "dir": "large-tablet-screenshots",
        "names": PHONE_NAMES,
        "empty_home": (1100, 450),
        "picker_swipes": [(2200, 600, 600, 600)],
    },
    "fold": {
        "names": {"home": "01_home", "search": "02_search", "all_apps": None,
                  "search_keywords": "03_search_keywords", "share_text": "04_share_text",
                  "themed_icons": "05_themed_icons"},
        "crop": (2076, 2076, 0, 44),  # the inner screen (2076x2152) cut to 1:1
        "empty_home": (1560, 690),
        "apps_panel": (500, 300),  # a point near the top of all apps, to scroll it back up
        "picker_swipes": [(1500, 900, 300, 900), (1000, 1900, 1000, 1200)],
        # 06_home_4x3: the inner screen turned to landscape and widened to 4:3.
        "wide_size": "2076x2768",
        # 07_home_folded_10x16: the outer screen (1080x2364) widened to 10:16. Its grid becomes
        # 6 columns and the launcher packs the items, so they are placed again:
        # (cellX, cellY, spanX, spanY) of the widget, the folder, and the apps by package.
        "folded_size": "1480x2368",
        "folded_cells": {"widget": (0, 0, 2, 2), "folder": (5, 0, 1, 1),
                         "com.google.android.apps.maps": (5, 1, 1, 1),
                         "com.google.android.dialer": (0, 2, 1, 1),
                         "com.google.android.apps.messaging": (1, 2, 1, 1),
                         "com.google.android.gm": (2, 2, 1, 1),
                         "com.android.chrome": (5, 2, 1, 1)},
    },
}
FOLD_DIR = os.path.join(ROOT, "store/screenshots/fold")
# The screens --screens takes: the names' keys, and the foldable's two extra home screens.
SCREENS = list(PHONE_NAMES) + ["home_4x3", "home_folded"]
# A blinking cursor changes the field by about 50 (tablet) to 75 (phone) fully changed pixels;
# without it, the frames are the same.
CURSOR_PIXELS = 20

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
        # A foldable has two displays (inner, outer), and screencap needs to be told which.
        ids = re.findall(r"^Display (\d+)", self.adb("shell", "dumpsys SurfaceFlinger --display-id"), re.M)
        self.displays = ids if len(ids) > 1 else []
        self.display = self.displays[0] if self.displays else None

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
        display = ["-d", self.display] if self.display else []
        return self.adb("exec-out", "screencap", "-p", *display, binary=True)

    def unlocked(self):
        window = self.shell("dumpsys window")
        return "isKeyguardShowing=false" in window and "mSleeping=false" in window

    def wake_home(self, folded):
        """Wakes the screen past the lock screen (no lock is set) and goes home."""
        for attempt in range(4):
            self.key("WAKEUP")
            time.sleep(2)
            self.shell("wm dismiss-keyguard")
            time.sleep(3)
            if self.unlocked():
                break
            if attempt == 3:
                raise RuntimeError("the lock screen stays")
            # After unfolding, the inner screen sometimes stays asleep behind the lock screen
            # whatever keys it gets; folding and unfolding again wakes it.
            if not folded and attempt >= 1:
                self.adb("emu", "fold")
                time.sleep(8)
                self.key("WAKEUP")
                self.shell("wm dismiss-keyguard")
                time.sleep(3)
                self.adb("emu", "unfold")
                time.sleep(8)
        self.key("HOME")

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
        # The tablet's status bar keeps the previous locale's digits (9:00 instead of Arabic's
        # ٩:٠٠) until its configuration changes otherwise: night mode on and back does it.
        night = self.shell("cmd uimode night").strip().rsplit(" ", 1)[-1]
        self.shell(f"cmd uimode night {'no' if night == 'yes' else 'yes'}")
        time.sleep(3)
        self.shell(f"cmd uimode night {night}")
        time.sleep(4)
        self.demo_clock()  # in the new locale's format

    def demo_clock(self):
        self.shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0900")

    def locale(self):
        return self.shell("cmd locale get-device-locale").strip()

    def demo_status_bar(self):
        """The same status bar in every language: 9:00 (12-hour: 24-hour is 09:00 in some
        locales), and no notification icons (demo mode leaves them, such as the Safety Center's
        after a cold boot). Returns what put_status_bar_back takes."""
        time_12_24 = self.shell("settings get system time_12_24").strip()
        self.shell("settings put system time_12_24 12")
        self.shell("cmd statusbar send-disable-flag notification-icons")
        self.shell("settings put global sysui_demo_allowed 1")
        for cmd in ["enter", "battery -e level 100 -e plugged false",
                    "network -e wifi show -e level 4 -e fully true", "network -e mobile hide",
                    "notifications -e visible false"]:
            command, *extra = cmd.split(" ", 1)
            self.shell(f"am broadcast -a com.android.systemui.demo -e command {command} {extra[0] if extra else ''}")
        self.demo_clock()
        return time_12_24

    def put_status_bar_back(self, time_12_24):
        self.shell("cmd statusbar send-disable-flag none")
        if time_12_24 == "null":
            self.shell("settings delete system time_12_24")
        else:
            self.shell(f"settings put system time_12_24 {time_12_24}")

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


def png24(png, path, crop=None):
    """Saves a screencap as a 24-bit PNG (Google Play takes no alpha), cut to (w, h, x, y)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cut = ["-crop", "{}x{}+{}+{}".format(*crop), "+repage"] if crop else []
    subprocess.run(["magick", "png:-", *cut, "-alpha", "off", "-define", "png:color-type=2", f"PNG24:{path}"],
                   input=png, check=True)


def save(device, profile, png, out, screen):
    name = profile["names"][screen]
    png24(png, os.path.join(out, f"{name}.png"), profile.get("crop"))


def mean_in(png, box):
    l, t, r, b = box
    out = subprocess.run(["magick", "png:-", "-crop", f"{r - l}x{b - t}+{l}+{t}", "+repage",
                          "-colorspace", "gray", "-format", "%[fx:mean]", "info:"],
                         input=png, capture_output=True, check=True)
    return float(out.stdout)


def keyboard_in(png):
    """Whether the keyboard shows, judged from the screen: its light keys fill the bottom, where
    the wallpaper is dark without it."""
    out = subprocess.run(["magick", "png:-", "-gravity", "South", "-crop", "0x200+0+220", "+repage",
                          "-colorspace", "gray", "-format", "%[fx:mean]", "info:"],
                         input=png, capture_output=True, check=True)
    return float(out.stdout) > 0.6


def with_cursor(device, box, frames=8, attempts=4):
    """The frame in which the field's blinking cursor shows (the field is darkest then), with the
    keyboard showing.

    The frames have to differ in the field, or no frame has the cursor: the field can lose the
    focus, and even focused, its cursor sometimes doesn't blink until the field is tapped. And the
    keyboard sometimes hides with the cursor still blinking. Then the field is tapped (at its
    middle, past the text: the cursor stays at the end) and the frames are taken again."""
    l, t, r, b = box
    for attempt in range(attempts):
        if attempt:
            device.tap((l + r) // 2, (t + b) // 2)
            time.sleep(5)  # past the insertion handle that a tap shows for 4 s
        shots = [device.screencap() for _ in range(frames)]
        if not keyboard_in(shots[0]):
            continue
        means = [mean_in(png, box) for png in shots]
        # The difference as a number of fully changed pixels.
        if (max(means) - min(means)) * (r - l) * (b - t) > CURSOR_PIXELS:
            return shots[means.index(min(means))]
    raise RuntimeError("no blinking cursor in the search field, or no keyboard")


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


def settled_home(device, profile, warmup_text):
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
    if "apps_panel" in profile:  # all apps on the home screen keeps where it was scrolled to
        x, y = profile["apps_panel"]
        device.swipe(x, y, x, y + 900, 300)
        time.sleep(1)
        field = home(device)  # the swipe takes the focus off the field (another keyboard shows)
    time.sleep(2)
    return field


def out_dir(profile, lang):
    if "dir" in profile:
        return os.path.join(LISTINGS, lang, "graphics", profile["dir"])
    return FOLD_DIR if lang == "en-US" else os.path.join(FOLD_DIR, lang)


def shoot_language(device, profile, lang, out, screens):
    """Takes [screens] but themed_icons (taken in a pass of its own)."""
    photo = json.load(open(os.path.join(KEYWORDS, f"{lang}.json"), encoding="utf-8"))
    keywords = photo["keywords"]
    device.set_locale(DEVICE_LOCALE.get(lang, lang))
    set_photo_keywords(device, keywords)
    field = settled_home(device, profile, keywords[0])
    if "home" in screens:
        save(device, profile, with_cursor(device, field), out, "home")
    if screens & {"search", "all_apps", "search_keywords"}:  # they need the photo app's name
        shoot_photos(device, profile, lang, out, screens, photo, field)
    if "share_text" in screens:
        shoot_share_text(device, profile, lang, out)
    if "home_4x3" in screens:
        shoot_wide_home(device, profile, out)
    if "home_folded" in screens:
        shoot_folded_home(device, profile, out)


def shoot_photos(device, profile, lang, out, screens, photo, field):
    """search, all_apps and search_keywords, which find the photo app. The search types the start
    of the photo app's name (searchPrefix), so that the screenshot shows plainly that typing a few
    letters finds an app; other apps it matches show too."""
    name = photo["appName"]
    device.set_search_text(photo["searchPrefix"])
    found, deadline = False, time.time() + 20
    while not found and time.time() < deadline:
        time.sleep(2)
        found = any(t == name and b[3] <= field[1] for t, b, cls in device.nodes())
    if not found:
        raise RuntimeError(f"{lang}: {photo['searchPrefix']} did not find {name}")
    photos = [name]
    time.sleep(1)
    if "search" in screens:
        save(device, profile, with_cursor(device, field), out, "search")
    if not screens & {"all_apps", "search_keywords"}:
        home(device)
        return

    if profile["names"]["all_apps"]:  # search_keywords long-presses the app in all apps
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
        if "all_apps" in screens:
            save(device, profile, device.screencap(), out, "all_apps")
    else:  # all apps is always on screen
        home(device)
        time.sleep(2)

    if "search_keywords" in screens:
        device.long_press(*device.find({photos[0]}))
        time.sleep(1.5)
        device.tap(*device.find({res_string(lang, "menu_search_terms"), res_string(lang, "menu_search_terms_short")}))
        time.sleep(3)
        save(device, profile, device.screencap(), out, "search_keywords")
        device.key("BACK")
        time.sleep(1)
    device.key("BACK")
    home(device)
    time.sleep(2)


def shoot_share_text(device, profile, lang, out):
    device.long_press(*profile["empty_home"])
    time.sleep(1.5)
    device.tap(*device.find({res_string(lang, "share_text_title")}))
    time.sleep(3)
    save(device, profile, device.screencap(), out, "share_text")
    device.key("BACK")
    home(device)


def favorites(db_bytes):
    with tempfile.NamedTemporaryFile(suffix=".db") as f:
        f.write(db_bytes)
        f.flush()
        db = sqlite3.connect(f.name)
        rows = db.execute("SELECT _id, container, screen, cellX, cellY, spanX, spanY FROM favorites ORDER BY _id").fetchall()
        db.close()
    return rows


def shoot_wide_home(device, profile, out):
    """06_home_4x3: the inner screen in landscape, widened to 4:3 (the grid stays as it is)."""
    before = favorites(device.launcher_db())
    auto = device.shell("settings get system accelerometer_rotation").strip()
    rotation = device.shell("settings get system user_rotation").strip()
    device.shell("wm user-rotation lock 1")
    time.sleep(6)
    device.shell(f"wm size {profile['wide_size']}")
    try:
        time.sleep(10)
        field = home(device, 4)
        time.sleep(4)
        png24(with_cursor(device, field), os.path.join(out, "06_home_4x3.png"))
    finally:
        device.shell("wm size reset")
        device.shell("wm user-rotation " + ("free" if auto == "1" else f"lock {rotation}"))
        device.shell(f"settings put system user_rotation {rotation}")
        time.sleep(6)
        home(device)
    if favorites(device.launcher_db()) != before:
        raise RuntimeError("the 4:3 screen changed the home layout")


def shoot_folded_home(device, profile, out):
    """07_home_folded_10x16: the outer screen widened to 10:16, with the items placed again."""
    saved = device.launcher_db()
    device.adb("emu", "fold")
    device.display = device.displays[1]
    try:
        time.sleep(10)
        device.wake_home(folded=True)
        time.sleep(5)
        device.shell(f"wm size {profile['folded_size']}")
        time.sleep(10)
        device.shell(f"am force-stop {PKG}")
        with tempfile.NamedTemporaryFile(suffix=".db") as f:
            f.write(device.launcher_db())
            f.flush()
            db = sqlite3.connect(f.name)
            for key, (x, y, w, h) in profile["folded_cells"].items():
                where = {"widget": "itemType = 4", "folder": "itemType = 2"}.get(key, "intent LIKE ?")
                args = () if key in ("widget", "folder") else (f"%package={key};%",)
                n = db.execute(f"UPDATE favorites SET cellX = {x}, cellY = {y}, spanX = {w}, spanY = {h} "
                               f"WHERE container = -100 AND {where}", args).rowcount
                if n != 1:
                    raise RuntimeError(f"folded layout: {n} items for {key}")
            db.commit()
            db.close()
            device.put_launcher_db(open(f.name, "rb").read())
        field = home(device, 8)
        time.sleep(2)
        # The outer screen's field needs a tap to take the focus (the keyboard can show without it).
        device.tap((field[0] + field[2]) // 2, (field[1] + field[3]) // 2)
        time.sleep(3)
        png24(with_cursor(device, field), os.path.join(out, "07_home_folded_10x16.png"))
    finally:
        device.shell("wm size reset")
        time.sleep(3)
        device.adb("emu", "unfold")
        device.display = device.displays[0]
        time.sleep(8)
        device.shell(f"am force-stop {PKG}")
        device.put_launcher_db(saved)
        device.wake_home(folded=False)
        time.sleep(8)
    if favorites(device.launcher_db()) != favorites(saved):
        raise RuntimeError("could not put the home layout back after folding")


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
    p.add_argument("--screens", help="comma-separated screens to take again (default: all): " + ", ".join(SCREENS))
    args = p.parse_args()

    profile = PROFILES[args.device]
    names = {s for s, n in profile["names"].items() if n} | ({"home_4x3", "home_folded"} if "wide_size" in profile else set())
    screens = set(args.screens.split(",")) if args.screens else names
    if screens - names:
        sys.exit(f"no such screen on a {args.device}: {sorted(screens - names)}")
    langs = args.langs.split(",") if args.langs else sorted(
        d for d in os.listdir(LISTINGS) if d != "en-US" and os.path.exists(os.path.join(KEYWORDS, f"{d}.json")))
    device = Device(args.serial)
    supported = set(device.shell("cmd locale list-device-locales").split())
    unknown = [l for l in langs if DEVICE_LOCALE.get(l, l) not in supported]
    if unknown:
        sys.exit(f"no device locale for: {unknown}")
    original_locale = device.locale()
    original_db = device.launcher_db()
    time_12_24 = device.demo_status_bar()
    failed = []
    try:
        for lang in langs if screens - {"themed_icons"} else []:
            print(f"{lang}: {', '.join(s for s in SCREENS if s in screens and s != 'themed_icons')}", flush=True)
            try:
                shoot_language(device, profile, lang, out_dir(profile, lang), screens)
            except Exception as e:  # keep going; the language is reported at the end
                failed.append(lang)
                print(f"{lang}: FAILED {e}", flush=True)
                device.key("BACK")
                device.key("HOME")
        if "themed_icons" in screens:
            device.set_locale("en-US")
            set_themed_icons(device, profile, True)
            try:
                for lang in langs:
                    print(f"{lang}: themed icons", flush=True)
                    device.set_locale(DEVICE_LOCALE.get(lang, lang))
                    keywords = json.load(open(os.path.join(KEYWORDS, f"{lang}.json"), encoding="utf-8"))["keywords"]
                    field = settled_home(device, profile, keywords[0])
                    save(device, profile, with_cursor(device, field), out_dir(profile, lang), "themed_icons")
            finally:
                device.set_locale("en-US")
                set_themed_icons(device, profile, False)
    finally:
        device.shell(f"am force-stop {PKG}")
        device.put_launcher_db(original_db)
        device.set_locale(original_locale)
        device.put_status_bar_back(time_12_24)
        device.key("HOME")
    if failed:
        print("failed:", ",".join(failed))
        sys.exit(1)


if __name__ == "__main__":
    main()
