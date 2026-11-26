# Handing search field text to other apps: research and options

Research on handing the text typed in the search field to web search or to LLM apps (Gemini, ChatGPT, Claude, Perplexity, and so on). As of 2026-11-25. Nothing is implemented yet.

- Environments checked
  - Emulator (AVD `Small_Phone`, Android 17 / API 37, with Google Play): Google app, Gemini, Chrome, Pixel Launcher. ChatGPT, Claude and Perplexity are not installed
  - Pixel 6a (Android 17 / API 37): Google app 17.76.14, Gemini 1.0.995038376, Chrome 157, ChatGPT 1.2026.314, Claude 1.261120.19. Perplexity is not installed. **Only queried with adb. Nothing was launched**
  - Launcher3 is the `android-17.0.0_r1` source (the one `scripts/check-upstream.py` fetches)
- Legend: **Device** (checked with adb on the Pixel 6a), **Emulator** (launched and checked on the emulator), **Public** (public information, with sources), **Unknown**

---

## 1. Android entry points

### 1.1 Overview

| Entry point | How the text is passed | Apps that receive it (Pixel 6a) | Default app | Notes |
|---|---|---|---|---|
| `Intent.ACTION_WEB_SEARCH` | `SearchManager.QUERY` (`"query"`) | Google (`GoogleSearch`), Chrome (`SearchActivity`) | On the 6a it is set to Google (`resolve-activity` returns Google). On the emulator it is not set, and the chooser ("Open with", "Just once" / "Always") appeared | Google shows search results right away. Chrome only puts the query in its search box and shows suggestions (**Emulator**) |
| `Intent.ACTION_SEARCH` | `SearchManager.QUERY` | For in-app search. The Google app does not receive it. Chrome receives it but has no `DEFAULT` category, so it only gets it when addressed explicitly | ― | Opens each app's own search screen. Not suited to "web search" |
| `SearchManager.INTENT_ACTION_GLOBAL_SEARCH` | `SearchManager.QUERY` | Google (`GoogleAppGlobalSearchImplicitGatewayInternal`) | Returned by `SearchManager.getGlobalSearchActivity()` (public API) | Launcher3's `OSEManager` uses it to pick the default search app (section 3) |
| `Intent.ACTION_SEND` (`text/plain`) | `Intent.EXTRA_TEXT` | Gemini, ChatGPT, Claude, Chrome, and every other app that accepts shares (over 30 on the 6a) | No default (you choose in the share sheet) | Gemini only puts the text in its input field and does not send it (**Emulator**) |
| `Intent.ACTION_PROCESS_TEXT` (`text/plain`) | `Intent.EXTRA_PROCESS_TEXT`, `EXTRA_PROCESS_TEXT_READONLY` | ChatGPT (`TextProcessorActivity`), Claude (`DeepLinkActivity`), Google Translate, Meta AI, Select to Speak | No default (the apps are listed in the text selection menu) | The Google app also has an entry point for Gemini (`ProcessTextGatewayActivity`), but it is disabled on the 6a (`disabledComponents`). On the emulator it is enabled, and the text went into Gemini's input field (not sent, **Emulator**) |
| `Intent.ACTION_ASSIST` | No standard argument for text | Google, ChatGPT (`AssistantProxyActivity`), Claude (`AssistantOverlayActivity`), Meta AI, Yahoo!乗換案内 | The default assistant (`ROLE_ASSISTANT`; Google on the 6a) | Only opens the assistant. Cannot pass text |
| `Intent.ACTION_VOICE_COMMAND` | None | Google only | ― | Voice input. Cannot pass text |
| `VoiceInteractionService` (`ROLE_ASSISTANT`) | ― | Google, ChatGPT, Claude and Meta AI have one (any of them can be the assistant) | Google on the 6a | Only the system can open a session. A launcher cannot use it |
| URL (`ACTION_VIEW`) | Query in the URL | See 1.3 below | The app whose App Links are verified, otherwise the browser | The text goes into a URL, so how well it works depends on the app |

### 1.2 Package visibility (`<queries>`)

CaretLauncher's manifest currently has only `MAIN`/`LAUNCHER` and `APPWIDGET_UPDATE` in `<queries>`.

- Just calling `startActivity` with an implicit intent does not need `<queries>` (the system resolves it)
- To look up the receiving apps and show them as rows (`queryIntentActivities`/`resolveActivity`), the intent must be declared in `<queries>`. The ones likely needed here:
  - `<intent><action android:name="android.intent.action.WEB_SEARCH" /></intent>`
  - `<intent><action android:name="android.intent.action.SEND" /><data android:mimeType="text/plain" /></intent>`
  - `<intent><action android:name="android.intent.action.PROCESS_TEXT" /><data android:mimeType="text/plain" /></intent>`
  - To look up which apps open a URL, `<intent><action android:name="android.intent.action.VIEW" /><category android:name="android.intent.category.BROWSABLE" /><data android:scheme="https" /></intent>`
- `MAIN`/`LAUNCHER` of every app is already visible, so icons and labels are available. But whether an app accepts SEND cannot be known without the declarations above

### 1.3 Per-app entry points (checked in the manifests)

| App | Share (SEND) | PROCESS_TEXT | URL | Assistant |
|---|---|---|---|---|
| Google (`com.google.android.googlequicksearchbox`) | Images and "Save" only (it does not search text) | Disabled on the 6a | `https://www.google.com/search?q=…` opens in Chrome (it is not in the Google app's App Links) | Holder of `ROLE_ASSISTANT` |
| Gemini (`com.google.android.apps.bard`) | Yes (goes into the input field, **Emulator**) | None (the Google app has one, but it is disabled on the 6a) | `https://gemini.google.com/app` is handled by the Google app's `MainAssistantDeeplinkAnimated` (App Links verified). Opening it from adb with `?q=` logged "Invalid MA deeplink Uri" and closed (**Emulator**). What happens when it is opened from the launcher is **Unknown** | As the Google app |
| ChatGPT (`com.openai.chatgpt`) | Yes (`MainActivity`) | Yes (`TextProcessorActivity`) | `https://chatgpt.com/` (handles the path `/`; App Links verified), `chatgpt://`. Whether it reads `?q=` cannot be told from the manifest | Has ASSIST and `VoiceInteractionService` |
| Claude (`com.anthropic.claude`) | Yes (`DeepLinkActivity`) | Yes (`DeepLinkActivity`) | `https://claude.ai/new` (App Links verified), `claude://`. Whether it reads `?q=` cannot be told from the manifest | Has ASSIST, VOICE_ASSIST and `VoiceInteractionService` |
| Perplexity | Not installed on the 6a or the emulator, so **Unknown**. On the 6a, `https://www.perplexity.ai/search?q=` opens in Chrome | | | |
| Chrome | Yes | None | http/https, `googlechrome://` | ― |

Public information fills in the rest as follows (**Public**).

- **ACTION_WEB_SEARCH**: The `Intent` javadoc says that if `QUERY` is an http/https URL, that page opens. Otherwise a web search runs. The Google app searches right away (**Emulator**). Chrome only opens its search screen with the query filled in (**Emulator**, Chromium's `SearchActivityUtils.getIntentQuery`). Firefox opens a new tab with the default search engine and searches right away. On DuckDuckGo, `SelectedTextSearchActivity` receives it (both checked in the source)
- **Gemini**: Google has no official documentation for `gemini.google.com/app?q=`. Someone built an extension because a conversation cannot be started from a URL ([gemini-url-prompt](https://github.com/elliot79313/gemini-url-prompt)), so it most likely does not work. "Ask Gemini" in text selection is the Google app's PROCESS_TEXT ([Android Authority](https://www.androidauthority.com/ask-gemini-text-select-3531976/)), and whether it is enabled varies by device (disabled on the 6a)
- **ChatGPT**: On the web, `chatgpt.com/?q=` fills the input field. It used to send automatically, but auto-send was stopped after an abuse report ([Tenable TRA-2025-22](https://www.tenable.com/security/research/tra-2025-22)). The behavior when it opens in the app is **Unknown**. It can be set as the default assistant ([9to5Google](https://9to5google.com/2025/03/14/chatgpt-default-assistant-android/))
- **Claude**: `claude://claude.ai/new?q=` is an official feature of the desktop app, and it only fills the input field ([Claude Help](https://support.claude.com/en/articles/14729294-open-claude-desktop-with-a-link)). For the mobile app, the only documented ones are `claude://code/new?q=` and `https://claude.ai/code/new?q=`, for Claude Code ([Claude Help](https://support.claude.com/en/articles/14898120-open-the-claude-mobile-app-with-a-link)). Text passed by share or PROCESS_TEXT is only appended to the current input field and is not sent ([anthropics/claude-code#27739](https://github.com/anthropics/claude-code/issues/27739))
- **Perplexity**: There is no official documentation for `perplexity.ai/search?q=`. It can be set as the default assistant ([9to5Google](https://9to5google.com/2025/01/24/perplexity-assistant-android/))

**Summary**:

- For web search, `ACTION_WEB_SEARCH` is the standard way, and the default app takes it all the way to search results
- With LLM apps, whether the text is passed by share, PROCESS_TEXT or URL, it only goes into the input field, and the user presses send. Only Claude's desktop URL is officially promised. The others are unofficial and may change
- PROCESS_TEXT and share are standard Android entry points and do not depend on app internals

---

## 2. Restrictions in Android 17

- **Android 17 adds no new restrictions** (**Public**, [Android 17 behavior changes (apps targeting 17)](https://developer.android.com/about/versions/17/behavior-changes-17), [all apps](https://developer.android.com/about/versions/17/behavior-changes-all)). No new changes are listed for intent matching, `ACTION_WEB_SEARCH`, `SearchManager` or App Links
  - The stricter background activity launch rules target `IntentSender`. They do not apply when a foreground launcher calls `startActivity` in response to a user action
  - The implicit grant of URI permissions on share (`ACTION_SEND`) is planned to go away in Android 18. This does not matter when only text is passed
- **Safer Intents** (added in Android 16, opted into by the receiving app; planned to become the default later, [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16))
  - What it does: even an explicit intent is not delivered unless it matches the target's intent filter. An intent with no action does not match
  - What to do: even when the intent names a component, make its action, type and category match the target's filter
- **Package visibility**: as in 1.2
- **How to find the default app**
  - Web search: `resolveActivity(ACTION_WEB_SEARCH, MATCH_DEFAULT_ONLY)` tells you. If no default is set, it returns the chooser (`ResolverActivity`)
  - Global search: `SearchManager.getGlobalSearchActivity()` is a public API
  - `RoleManager.ROLE_SEARCH_ENGINE`: added in API 37.2 (**Public**, [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)). But `getRoleHolders`, which returns the holder, is for the system. How other apps can find the holder is **Unknown**
  - `selected_search_engine` in `Settings.Secure`: an undocumented key that GMS writes. Upstream `OSEManager` reads it. SettingsProvider lets apps read keys that `Settings` does not define, so it looks readable, but this was not tried from an app (**Unknown**)
- **There is no way to pass text to the assistant (`ROLE_ASSISTANT`)**
  - `ACTION_ASSIST` opens it, but there is no standard extra for a string (`EXTRA_ASSIST_INPUT_HINT_KEYBOARD` is only a hint that the user is likely to type)
  - `SearchManager.launchAssist` is for the system. The system opens `VoiceInteractionService` sessions

---

## 3. How Launcher3 and Pixel Launcher handle it

### 3.1 AOSP Launcher3 (android-17.0.0_r1)

- The search field in the all apps list (`AllAppsSearchBarController.onEditorAction`) only calls `launchHighlightedItem()` (launches the top app) on Enter. There is no web search when no app is found
- The search results (`DefaultSearchAdapterProvider`) are also apps only. The "Search Play Store" row (`VIEW_TYPE_SEARCH_MARKET`) of older Launcher3 is gone. `ApiWrapper.getMarketSearchIntent` is for opening the store when the user taps the icon of an app that is still installing
- The hotseat search bar (QSB) is in `qsb/`. It was added in Android 17, and it does not handle typed text
  - `OSEManager` (On-device Search Engine): picks the default search app from `selected_search_engine` in `Settings.Secure` (the one chosen on the EU search engine choice screen). If that is not set, it uses the package of `SearchManager.getGlobalSearchActivity()`. If that is missing too, it uses `fallback_search_package_name` in `config.xml` (empty in AOSP). It also checks whether that app accepts `ACTION_SEARCH`
  - `OseWidgetManager`/`OseWidgetView`: puts that search app's widget in the hotseat. When it cannot, the fallback "Search" button sends `ACTION_SEARCH` to that app, launches that app, or opens an empty page in the browser. It does not pass text
  - On the 6a, both `selected_search_engine` and `getGlobalSearchActivity()` give the Google app
- **What we can take from upstream**: only how to decide which app counts as the default search target (the order in `OSEManager.reloadOse`). Upstream has no code that passes typed text, so we have to write it ourselves. Whether third-party apps can read `selected_search_engine` is covered in section 2

### 3.2 Pixel Launcher

Pixel Launcher's search is Google's private implementation and is not in AOSP. Public information (**Public**) says the following.

- Since December 2025, tapping the search bar at the bottom of the home screen opens the Google app's full-screen search ([9to5Google](https://9to5google.com/2025/12/16/pixel-launcher-search-google-app/)). The search field at the top of the all apps list is still Pixel Launcher's own search
- In Android 17 Beta 1, the search bar became the Google widget itself. A hidden setting lets you choose another search app, such as DuckDuckGo or Firefox ([Android Authority](https://www.androidauthority.com/android-17-beta-1-pixel-launcher-search-bar-redesign-3641175/)). Upstream `OSEManager`/`OseWidgetView` correspond to this
- Since April 2026, the bar says "Ask Google", and AI Mode can be selected ([9to5Google](https://9to5google.com/2026/04/29/android-search-ask-google-bar/))
- No material was found on which rows appear, and what Enter does, when you type a term that matches no app in the all apps search field (**Unknown**)

How other launchers do it (checked in the source) is also useful.

- Kvaesitso: web search uses `ACTION_WEB_SEARCH`. Search targets that the user adds are URL templates containing `${1}`, opened with `ACTION_VIEW`. In-app search collects the apps that accept `ACTION_SEARCH` and sends to them. Share sends `ACTION_SEND` through the chooser
- KISS: if the URL template is empty, it uses `ACTION_WEB_SEARCH`. Otherwise it replaces `%s` and opens the URL. It has a "search the web on Enter" setting
- Olauncher: `ACTION_WEB_SEARCH`
- No launcher was found that passes text directly to ChatGPT, Claude or Gemini

---

## 4. Options

### 4.1 Relation to the current spec

The current `docs/spec.md` is written on the policy of leaving out web search. Every option first needs these parts rewritten.

- 2.1: "Web search ... is not a search target"
- 2.4: "When there are 0 candidates ..., nothing happens"
- The table in section 8: "Search beyond apps (shortcuts, contacts, web, etc.): left out by policy"

### 4.2 Options

**Option A: When no app is found, Enter runs a web search**

- Send `ACTION_WEB_SEARCH` with `SearchManager.QUERY` to the default app (on the 6a, Google searches right away)
- Pros
  - No change to the screen. The smallest change
  - The same approach as Kvaesitso, KISS and Olauncher
  - No hard-coded app names
- Cons
  - The screen does not show what will happen, so a typo goes straight to search
  - On devices with no default set, the chooser appears on every Enter (the emulator was like this)
  - Web search is not available as soon as even one app is found

**Option B: Add "action" cells to the search results grid**

- Place cells that look the same as app cells after the apps
  - "Search the web": the icon is that of the default web search app. When the chooser would open, a magnifying glass
  - "Ask {app}": the icon is that of the app
- Search results are laid out from the bottom (2.5), so when there are apps, the action cells come above them. When there are 0 apps, an action cell becomes the top result (bottom left) and runs on Enter. This includes option A
- Pros
  - You can see what will happen
  - Reuses the grid from 2.5, the focus frame and arrow keys from 2.6, and tapping as they are
  - Even when apps are found, web search or an LLM can be chosen by tap or arrow keys
- Cons
  - Cells have little width for a label (about "Search the web" is all that fits)
  - Cells that are not apps need special cases in the search results adapter and in long press (no dragging, and so on)
  - Upstream Launcher3 has no such part, so it would be our own code

**Option C: How to choose the targets (what to show in option B's "Ask {app}" cells)**

- C1: list the apps that accept PROCESS_TEXT automatically
  - Pros: no hard-coded app names. The same set of apps as the text selection menu
  - Cons: apps that are not LLMs (Google Translate, Select to Speak) are included too. Gemini does not accept it on the 6a, so it does not appear
- C2: keep a table of known apps and how to pass text to each
  - Example: share for Gemini, PROCESS_TEXT or share for ChatGPT and Claude, URL for Perplexity
  - Pros: can pick the best way for each app
  - Cons: the table needs upkeep. Unofficial entry points may change
- C3: the user chooses
  - Add "Pass typed text" to the app long-press menu, and show only the apps turned on there as "Ask {app}"
  - Only apps that accept share or PROCESS_TEXT can be shown
  - Fits the policy of having no settings screen (section 8), and uses the same entry point as editing search keywords
  - Use PROCESS_TEXT if the app accepts it, otherwise share. PROCESS_TEXT is the entry point that means "process this text", and its handler is also a small, separate part of the app
  - Pros: nothing is hard-coded, and only the apps the user uses appear
  - Cons: nothing appears at first, so the feature is hard to discover. It needs something like turning on, by default, the installed apps that are in the C2 table

**Option D: Choose the target with a search keyword**

- Prefix a search keyword from section 4 (for example, `gpt` registered for ChatGPT) and type "`gpt 東京の天気`" ("Tokyo weather"), and a cell appears that passes "東京の天気" to ChatGPT
- Pros: quick to choose with the keyboard alone. Uses the existing search keyword mechanism
- Cons: needs a rule for the separator (a space). It cuts into the ordering of 2.3, which makes the spec more complex. It can wait until B and C are done

**Not adopted**

- `ACTION_ASSIST`/`VoiceInteractionService`: cannot pass text
- `?q=` URLs of LLM apps: apart from Claude's desktop app they are unofficial, and some do not work (Gemini) or have changed how they work (ChatGPT). PROCESS_TEXT and share can do the same thing
- `ACTION_SEARCH`: it is for search inside an app, and the Google app does not accept it

### 4.3 Limits common to LLM apps

- With every method, LLM apps only put the text in the input field and do not send it. The user has to press send. The launcher cannot change this
- When the text is passed by share, some apps append it to the input field of the conversation that is open now, instead of starting a new conversation (reported for Claude)

### 4.4 Proposal: where to start

1. **With option B, first add just one "Search the web" cell (this includes option A)** (Tex-uk3u)
   - `ACTION_WEB_SEARCH` is a standard entry point. The default app (Google on the 6a) takes it straight to search results, and no app names need to be hard-coded
   - It builds the base for action cells in the grid, so LLM rows can be added on top of it
   - Spec changes: 2.1, 2.4, 2.5, section 8. The frame from 2.6 is also shown when the top result is an action cell. The same cell also appears in search inside the all apps list (6.5). Launch learning (section 3) does not record action cells (the input is different almost every time)
   - Add `WEB_SEARCH` to `<queries>`
2. **Next, add "Ask {app}" with option B + option C3 (defaults from the C2 table)** (Tex-9hi3)
   - Before that, launch ChatGPT and Claude on the 6a and check how they behave when given text by PROCESS_TEXT and by share (whether a new conversation starts, or the text only goes into the input field). This time they were only queried with adb
3. Decide whether option D is needed after 2 is done

---

## 5. Decisions (2026-11-25)

We changed the 4.4 proposal as follows and implemented it (`docs/spec.md` 2.7).

- Text is passed only by share (`ACTION_SEND` with `text/plain`). Web search also works by sharing to a search app or a browser, so the `ACTION_WEB_SEARCH` cell (option A, option B's "Search the web", Tex-uk3u) is not built
- Nothing is mixed into the search results grid. The icons of the chosen apps (up to 3) appear as buttons at the right end of the search field
- The apps are chosen not in a settings screen but from "Share text" in the long-press menu on empty space (where Launcher3 has "Home settings"). A dialog lets you reorder them (drag the handle) and remove them (×)
- At first the app list was left to the OS activity picker (`ACTION_PICK_ACTIVITY`; `com.android.settings/.ActivityPicker` on both the Pixel 6a and the emulator), but it still has an old look. So we took parts of the Android share sheet (AOSP IntentResolver): the grid cell, how names are chosen, and how apps are grouped. The list now uses the same grid as the share sheet (`queryIntentActivities`). The share sheet and the "Open with" screen launch the chosen app, so they cannot be used just to choose one
- The icons and labels are the ones the app set on its share entry point (`ResolveInfo`). We added `SEND` with `text/plain` to `<queries>`
