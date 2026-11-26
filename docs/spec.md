# CaretLauncher Specification

## Concept

A text-input-first Android home app (launcher) whose keyboard is always shown. The app name is Caret Launcher (after the "caret", the symbol that marks the input position; only on devices set to Simplified Chinese it is "Caret 桌面". See [research/app-name](../research/app-name/README.md)). The applicationId is `com.malt03.caretlauncher`. This spec and the source write it as CaretLauncher.

- On the home screen, the search field always has focus and the keyboard is shown
- Typed text searches apps incrementally, and Enter launches the top result right away
- When nothing is typed, the apps, widgets and folders the user placed freely are shown

The target environment is minSdk 37 / targetSdk 37.

---

## 1. Search field and keyboard

### 1.1 Always focused

The search field always has focus and the keyboard is shown. Focus is set and the keyboard is shown again at the following times.

| When | Main hook |
|---|---|
| When returning from an app to the home screen | `onResume` |
| When the home button is pressed on the home screen | `onNewIntent` |
| When the window gets focus back, such as after unlocking or after closing the notification shade | `onWindowFocusChanged(true)` |
| When the keyboard is closed while the home screen stays in front | Watching the IME visibility (see [1.4](#14-back-navigation)) |
| When edit mode ends | See [5.4](#54-edit-mode) |
| When the all apps list closes | See [6.6](#66-closing) |

As exceptions, the keyboard is hidden and focus is cleared during the following.

- Edit mode ([5.4](#54-edit-mode))
- While the all apps list is open and the user has not yet tapped the search field ([6.4](#64-open-state))

### 1.2 Clearing the input

- The input is cleared every time the launcher comes back to the front (this includes returning from an app, the home button, and unlocking)
- The input is also cleared when an app is launched. But it is cleared only after the launched app covers the screen (after the launcher is no longer visible), so that the home screen layout does not show partway through the launch
- The input is also cleared on back navigation (see [1.4](#14-back-navigation))

### 1.3 Placement and appearance

- The search field sits right above the keyboard (it follows the IME inset)
- The UI makes it clear that the field has focus
- The text shown when nothing is typed (the hint) is "Search apps", the same as the search box of the Launcher3 all apps list. When one or more apps are chosen for the share buttons ([2.7](#27-text-sharing)), the typed text is used for more than app search, so the hint is "Search" (the translation of Launcher3's `abandoned_search`)
- The result that Enter launches (the first search result) gets a focus outline (see [2.6](#26-keyboard-focus))

### 1.4 Back navigation

The back gesture cannot be fully blocked. It is effectively disabled in the following ways.

1. **The Activity's back handling**
   Consumed with `OnBackPressedCallback`, doing nothing.
2. **The keyboard closing**
   While the keyboard is shown, the keyboard (IME) receives back navigation first and closes itself, so the app cannot stop it. Instead, when the launcher detects that the keyboard was closed while the home screen is in front, it clears the input and shows the keyboard again at once. As a result, back navigation works as "clear the input". If a long-press menu or a folder is open, that is closed too, as with Launcher3's back handling (the long-press menu takes priority, and only one of them is closed at a time). The closing animation may be visible for a moment.
3. **Gesture exclusion rects** (`setSystemGestureExclusionRects`)
   Not used. Only up to 200dp vertically per edge can be excluded, and only for the app's own window, so it has no effect on the keyboard area.

---

## 2. App search

### 2.1 What is searched

- Only the apps shown in the launcher (`LauncherApps.getActivityList`) are searched
- Only apps in the personal profile are covered. The work profile and Private Space are not supported (see [8. Out of scope](#8-out-of-scope))
- Each app has two kinds of strings to match against
  - The app name (label)
  - Search keywords set by the user (an app can have several. See [4. Editing search keywords](#4-editing-search-keywords))
- App shortcuts, contacts, settings entries, web search, folders and so on are not searched (see [8. Out of scope](#8-out-of-scope)). Handing the typed text to a web search or LLM app is done with the share buttons in [2.7](#27-text-sharing)

### 2.2 Normalization

Both the query and the strings to match against get the same normalization before they are compared.

1. NFKC normalization (full-width letters and digits become half-width, half-width kana become full-width)
2. Lowercasing
3. Katakana to hiragana (subtract 0x60 from `ァ`(U+30A1) to `ヶ`(U+30F6))

With this, an app name or search keyword in katakana also matches when typed in hiragana. The readings of kanji are not obtained automatically, so the user covers them by registering hiragana search keywords.

### 2.3 Sort order

For a query `q` (already normalized), the strongest match type among an app's strings to match against is that app's match type.

| Match type | Condition |
|---|---|
| Exact match | string == `q` |
| Prefix match | string starts with `q` |
| Substring match | string contains `q` |

Results are sorted in the following priority (groups).

1. **Exact match**: within the group, by learning score, highest first
2. **Learned**: apps other than exact matches whose learning score for `q` is greater than 0. By score, highest first
3. **Prefix match**
4. **Substring match**

Within groups 3 and 4, results are sorted by app name. App name order is Launcher3's `LabelComparator`, the same as the all apps list ([6.4](#64-open-state)) (names that start with letters, digits and similar characters come first, in the collation order of the device language).

> The learning score uses only records of launches made with exactly the same query as `q`. Records for other queries that start with `q` (for example `sl` when `q` is `s`) are not used.

### 2.4 Launching with Enter

- Enter launches the first result (the IME action is `actionGo`)
- An Enter pressed during composition in a Japanese IME is treated as committing the text, and does not launch anything (the IME's standard behavior)
- When there are no results, or nothing is typed, nothing happens
- Tapping a result also launches it

### 2.5 Search result display

- When there is input, search results are shown in place of the home screen layout area
  - Switching between the home screen and search results has no animation, so that it reacts to input instantly. Launcher3's SearchTransitionController is not brought over
- It looks like the same grid as the Launcher3 app list. Each cell is an icon with the app name below it
  - The number of columns is the same as the home screen grid ([5.1](#51-grid))
  - The home screen layout does not show app names, but search results do
- Results are aligned to the bottom, with the first result in the position closest to the search field (the left end of the bottom row). The second and later results go to the right, and when a row fills up they continue in the row above

### 2.6 Keyboard focus

- Items in search results, on the home screen and inside folders can be selected with the arrow keys of a physical keyboard and the like, as in Launcher3. The focus outline looks and moves like Launcher3's (16dp rounded corners, a 2dp double line, colored to match the theme)
- Every time the input changes, the first result (the one Enter launches) gets the outline. Keyboard focus stays in the search field; only the outline is added
  - This matches Pixel Launcher's search. AOSP Launcher3 does not have it (Launcher3 only remembers the first result as the Enter target and shows no outline)
- The arrow keys can move focus out of the search field. The outline follows focus, and Enter launches that item (or opens it, for a folder)
- When a folder is opened with a key, focus moves to the first item in the folder, as in Launcher3, and the arrow keys select items inside the folder. When the folder closes, focus returns to the folder icon. When the folder is opened by touch, the search field keeps focus (and the keyboard stays shown)
- Typing a character while an item has focus moves focus back to the search field, and that character is entered (same as Launcher3)
- While an item has focus, the keyboard is hidden. Back navigation at this time clears the input and returns to the search field, as in [1.4](#14-back-navigation) (if a folder is open, the folder is closed first)

### 2.7 Text sharing

The typed text can be shared to other apps (`ACTION_SEND` with `text/plain`). This is meant for handing it to web search or LLM apps (Gemini, ChatGPT, Claude and so on). The research and discussion are in [Handing the search field's text to other apps](text-handoff-research.md).

- **Share buttons**: the icons of the apps the user chose (up to 3) are lined up at the right end inside the search field, in the order they were chosen. When there is no input (including whitespace only), they are shown with the disabled look (Material's disabled opacity, 38%), and tapping them does nothing
  - Long-pressing a button opens the "Share text" dialog whether or not there is input (see "How to choose" below). Since long press opens it, the buttons have no tooltip. In TalkBack, while there is no input the tap action is removed, and the long-press action is named "Share text"
  - The icon and label are the ones the app gave its share target (the `ResolveInfo` icon and label, the same as the share sheet). The label is used as the button's description and tooltip
  - Tapping shares the typed text (with leading and trailing whitespace removed) to that app. As with launching an app, the input is cleared after the launched app covers the screen ([1.2](#12-clearing-the-input)). It is not recorded for launch learning ([3](#3-learning-from-launches))
  - What the receiving app does is up to the app. LLM apps only put the text in their input box, and the user presses send
  - The behavior of Enter ([2.4](#24-launching-with-enter)) does not change
  - The buttons also appear in search within the all apps list ([6.5](#65-searching-within-the-list))
  - If a chosen app is uninstalled or stops accepting shares, its button is not shown
- **How to choose**: "Share text" in the long-press menu of an empty area ([5.3](#53-editing-actions-following-launcher3)) opens a dialog
  - The dialog lists the chosen apps. Drag a row's handle to reorder, and × removes one. In TalkBack, the row actions "Move up" and "Move down" reorder
  - "Add" shows the apps that accept shares (the ones that appear in the share sheet, minus those already chosen) in the dialog, in the same grid as the Android share sheet. The tapped app is added to the end. Once 3 are chosen, "Add" can no longer be pressed
    - The look and rules are the share sheet's (AOSP IntentResolver) (see [What we take from IntentResolver](launcher3-porting.md#what-we-take-from-intentresolver) in the Launcher3 porting policy). Up to 5 per row (fewer if they do not fit in the dialog). Each cell is an icon with the app name below it. If the share target's name (the Activity label) differs from the app name, it is shown below in a lighter color (the app name takes 1 line, or up to 2 lines if there is no target name). As in the share sheet, apps that hold the permission to substitute the name and icon (`SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON`) show the share target's name and icon (such as Google's "Save")
    - If the same app has several share targets with the same name, they are merged into one cell with ▼ to the right of the name. Pressing it shows a small dialog, as in the share sheet, with the app's icon and name and the share target names listed below, and the choice is made there
    - The order is by app name (as in the share sheet, the collation order of the device language)
    - The share buttons' descriptions and the rows of "Share text" use the same app name and share target name
    - The OS app picker (`ACTION_PICK_ACTIVITY`, the Settings app's `ActivityPicker`) is not used, because its look is old and has not been updated. The share sheet and the 「アプリケーションを選択」 ("Choose an app") screen launch the chosen app, so they cannot be used just for choosing
  - While the dialog is open, the keyboard is hidden, and when it closes the search field gets focus back
  - When the dialog opens, apps that no longer accept shares are removed from the chosen ones

---

## 3. Learning from launches

### 3.1 What is recorded

When an app is launched from search results, the following pair is recorded.

`(normalized query, app) → (score, lastUpdated)`

- An app is identified by `ComponentName` and `UserHandle`
- Launches from the home screen layout with no input are not recorded

### 3.2 Score (frequency weighted toward recent launches)

Instead of the raw launch count, a score that decays over time is used. Let the half-life be `H`.

- **On launch**: compute `score = score × 0.5^((now − lastUpdated) / H) + 1`, and set `lastUpdated = now`
- **When sorting**: use `score × 0.5^((now − lastUpdated) / H)` as the value at that time

This approach has the following properties.

- Without storing the full launch history, two values per record express both "how often" and "how recently"
- Pairs that have not been used for a while drop in rank naturally
- The initial value of `H` is 14 days, to be tuned through use
- Records whose computed score falls below a set value (for example 0.05) are deleted

---

## 4. Editing search keywords

- Each app can have several search keywords added and removed
- Opening the edit screen gives focus to the field for adding one, and the keyboard comes up. A registered keyword can be removed with the × on its right
- The entry point for editing is "Search keywords" in the app's long-press menu ([5.3](#53-editing-actions-following-launcher3)). It can be opened from anywhere: the home screen, search results, or the all apps list
- There is no settings screen ([8. Out of scope](#8-out-of-scope)). Appearance settings are left to "Wallpaper & style" ([7. Icons and themes](#7-icons-and-themes))
- Registered keywords are matched with the same normalization as [2.2](#22-normalization). A keyword registered in katakana is treated the same as hiragana

---

## 5. Home screen layout (when nothing is typed)

The user can place any apps, widgets and folders. The basics follow the standard settings and interactions of AOSP Launcher3, and only the points written below differ. Parts that affect how it feels bring over Launcher3's implementation ([Launcher3 porting policy](launcher3-porting.md)).

### 5.1 Grid

- The number of columns, icon size and cell width follow Launcher3's standard grid settings (`grid-option` in `res/xml/device_profiles.xml`). The way they are chosen is also the same as Launcher3's `InvariantDeviceProfile`: among the candidates (`display-option`) that fit the device type (phone, foldable, tablet), the column count of the one closest to the device's width and height is used, and the icon and text sizes are an average of the three closest candidates weighted by distance (`invDistWeightedInterpolate`). For example, a Pixel 6a gets 5 columns and icons of about 56dp. Pixel Launcher uses Google's own settings (4 columns and 66dp on a Pixel 6a), so icon sizes do not match
  - As in Launcher3, if there are candidates with the same column count as the grid the items were placed on (`DeviceGridState`), the choice is made among them (`filterByColumnCount`). Even when the display size or navigation mode changes, the column count is kept where possible (the row count may change)
  - Unlike Launcher3, the name of the chosen grid (`GRID_NAME`) is not remembered; it is decided again from the display every time. There is no setting to choose a grid either. The tablet grid does not scale to fit the screen (`isScalable`)
- App names are not shown. The cell height is reduced by that much (icon and padding only)
- The area where items can be placed is, like one page of the Launcher3 home screen, from below the status bar to above the hotseat (in this launcher, the search field). It is measured with the keyboard hidden
  - The row count is as many rows as fit in that height (the height divided by the icon-plus-padding height, rounded down). The cell height is that height divided by the row count, as in Launcher3
  - Launcher3 takes the row count from the grid settings (`numRows`), but in this launcher, which shows no app names, cells would be stretched vertically, so it uses as many rows as fit
  - As in Launcher3's `InvariantDeviceProfile`, the column and row counts are determined by the device's displays, not the current window. They are computed from the smallest width and height usable on any built-in display in any orientation (an estimate that excludes the status bar and navigation bar; for a phone in landscape, width and height are swapped; on a two-panel screen ([5.5](#55-two-panel-layout-inner-screen-of-a-foldable)) half the width is counted), so they do not change with screen rotation, folding, split screen or the keyboard
    - The size of the built-in display that is not in use (the outer or inner screen of a foldable, whichever is not being used) is obtained with `DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS` (added in Android 16 QPR2). minSdk was set to 37 to use this (AGP's minSdk cannot be set to 36.1)
    - Built-in displays too small to show the home screen (smaller than Launcher3's smallest setting, 255×400dp, such as the small outer screen of a device that folds vertically) are not counted
    - On large screens, a cutout is counted as being at the top in every orientation. A cutout in a corner can stay on the top edge after rotation, and rotating it with the orientation as Launcher3's estimate does would make the grid change depending on the orientation at launch
  - When the window is shorter than the display's row count (split screen), cells stay at their smallest height and the home screen scrolls vertically. Launcher3 shrinks the whole home screen, but this launcher already shows what is behind the keyboard by vertical scrolling, so it does the same here
  - While the keyboard is shown, the visible area is smaller, so the home screen scrolls vertically. It can scroll only down to the lowest row that has items, and the empty rows below that stay hidden behind the keyboard. The area itself does not grow
  - Items outside the area, and items that overlap other items, are deleted at load time, as in Launcher3 (`LoaderCursor.checkItemPlacement`)
  - When the grid changes (a change in display size or navigation mode, or a different display), items are moved to the new grid before loading, as in Launcher3's `GridSizeMigrationLogic`. When the columns are the same and rows increase, the added rows go on top and everything shifts down. Otherwise, items are placed again, packed from the left of the top row in order. Widgets are shrunk to the provider's minimum size (counted in cells of the new grid; 2×2 if the provider is unknown). Items that do not fit would go on a new page in Launcher3, but there is only one page, so they are deleted
  - After loading, the ids of widgets not on the home screen (such as ones removed by migration or by the placement check) are deleted from the widget host (Launcher3's `removeGhostWidgets`). Widgets in the middle of being added (while a permission or configuration screen is shown) are not deleted
- Screen orientation is the same as Launcher3's `RotationHelper`. On phones, the home screen stays portrait (the device's natural orientation) even with auto-rotate on. On large-screen devices (shortest side 600dp or more) it rotates. The orientation is locked during a drag
- When the home screen can scroll vertically, a scrollbar stays shown at the right edge. Its color and thickness match the all apps list's scrollbar (in place of Launcher3's page indicator)
- The keyboard makes the layout area smaller, but the initial implementation does nothing about it. This will be decided after it is actually built

### 5.2 What can be placed

- **Apps**: take 1 cell. The app name is not shown
- **App shortcuts**: placed from the app's long-press menu ([5.3](#53-editing-actions-following-launcher3)). They take 1 cell and carry the app's badge in the corner of the icon (as in Launcher3). The name is not shown. Tapping launches it. While placed, it is pinned with the system, and removing it unpins it. Shortcuts that disappear on the app's side are also removed from the home screen
- **Widgets**: take several cells. Shown with `AppWidgetHost`, and the size when added and resizing follow Launcher3. Scrolling inside widgets is not considered
  - Widgets are added from "Widgets" in the long-press menu of an empty area. This opens the same widget picker as Launcher3 (Android 17) (the Compose version, `WidgetPickerActivity`). The left side shows lists of suggestions, apps and categories (such as conversations) with search, the right side shows a preview of the selection, and tapping the preview shows "Add", which places it on the home screen (the picker closes). Long-pressing the preview and dragging closes the picker and moves to the home screen, as in Launcher3, and places it in the cell where the finger is lifted (other items are pushed aside; dropping on "Cancel" in the position of the trash stops it). For shortcuts in the list (an app's shortcut creation screen, `ACTION_CREATE_SHORTCUT`), placing them with "Add" or by drag opens that app's creation screen, as in Launcher3, and places the shortcut it creates (nothing is placed if the user backs out of the creation screen). The position is the first empty cell for "Add", or the dropped cell for a drag. If that cell is taken by the time the creation screen returns, it goes in the first empty cell (Launcher3 makes a folder when dropped on an icon, but CaretLauncher does not). Pin requests from other apps are not supported yet
  - When adding, if the launcher lacks permission to create widgets, the OS confirmation screen (`ACTION_APPWIDGET_BIND`) is shown (Launcher3 holds the permission as a system app, so it does not show this). So that the confirmation or configuration screen is not lost in the switch to the home screen when the picker closes, the add process starts after the home screen is back in front. Widgets with a configuration screen are added through it, as in Launcher3 (widgets that declare the configuration can be skipped do not go through it). If the user backs out partway, the widget is not added
    - Even if the home screen is recreated while the confirmation or configuration screen is shown (an orientation or theme change, for example), the in-progress state is saved, as in Launcher3, and continues on return
  - The position for "Add" is the first empty cell on the home screen, the same as apps added from a menu. For a drag, it is the dropped cell, as in Launcher3 (other items are pushed aside, and if there is not enough room the widget is shrunk down to the minimum size it declares). The size is determined as in Launcher3, by dividing the size the widget declares by the home screen cell size (`LauncherAppWidgetProviderInfo.initSpans`). The position and size are decided when "Add" is pressed in the picker (when dropped, for a drag), and are still used after returning from the confirmation or configuration screen (if that cell has been taken in the meantime, the first empty cell at that time). If there is no room, "No room on this home screen" is shown and the widget is not added (Launcher3 adds a page)
  - Widgets get the same 6dp padding around them as in Launcher3 (since there is no gap between cells)
  - Long-pressing shows a menu and a resize frame, as in Launcher3 (Android 17). The menu has "Remove", and "Settings" for widgets that can be reconfigured (opens the widget's configuration screen). Moving without lifting the finger closes the menu and frame and switches to a drag, so it can be moved. On switching to a drag, as in Launcher3, it shrinks to its size on the home screen as shrunk during editing, minus 5% of its original size (so it fits inside the drop target's outline; not smaller than 70dp in either direction). Dropping on Remove takes it off the home screen
  - The resize frame is the same as Launcher3's Compose version (`AppWidgetResizeFrameCompose`). Dragging the round handles at the middle of the frame's four sides changes the size by whole cells (from a corner, both directions at once). Tapping a handle shows "＋／−" buttons that make it one cell bigger or smaller (an Android 17 change so that it can be operated with single-finger taps alone). Other items are pushed aside, as in Launcher3. The size range comes from the minimum and maximum sizes the widget declares (`LauncherAppWidgetProviderInfo.initSpans`), and the widget is told every time its size changes
  - The resize frame appears on long press, after a drop, and right after adding (same as Launcher3). Tapping outside the frame closes it (and the menu too, if shown). When the home screen scrolls or moves with the keyboard, the frame follows the widget
  - Widgets whose provider app is gone are deleted at load time
- **Unavailable items**: shown in gray, as in Launcher3. A shortcut disabled by the app that created it shows a message on tap that it cannot be used. For apps that are suspended or restricted, tapping shows the system's explanation (the same in search results). When shortcuts cannot be queried, the item keeps showing with the remembered icon or a default icon
- **When an app is gone** (same as Launcher3): uninstalled apps are removed from the home screen and folders. When an app on an SD card is temporarily unavailable, it stays with its remembered icon in gray, and tapping shows a message that it cannot be used (it returns when it becomes available). When an app's launch activity changes (such as a switch to an alternate icon), the item is moved to that app's current launch activity and kept
  - On app add, update, removal or suspension, shortcut changes, and icon updates, only the changed items are updated, as in Launcher3. The whole home screen is not rebuilt, so unrelated drags and open folders stay as they are (a drag is canceled only when the item being dragged, or the contents of the folder it was dragged from, disappear). The long-press menu closes, as in Launcher3's `dismissInvalidPopup`, when its item disappears, or when anything is updated while a folder or widget menu (items without shortcuts) is open. The whole screen is rebuilt only for icon theme or shape changes, and for changes during loading
- **When there is no room**: if there is no empty cell when adding with "＋" in the long-press menu or when dropping, "No room on this home screen" is shown and nothing is placed, as in Launcher3 (Launcher3 adds a page for "＋", but the home screen has one page, so it only shows the message). Making a folder by dropping onto an app, and putting items into a folder, still work
- **Folders**:
  - They cannot be named
  - The icon is a preview of the apps inside (as in Launcher3)
  - Tapping opens the contents. Apps inside also do not show app names
  - The contents are split into pages, as in Launcher3. One page is the columns × rows of a Launcher3 folder (`FolderGridOrganizer`), and what does not fit goes on the next page. Swiping left and right turns pages, and the bottom row of the folder shows a page indicator (dots) and left and right arrows (tapping an arrow also turns the page). In Launcher3 the bottom row sits next to the name field, but since there is no name, it is not shown when there is only one page (and the folder is that much shorter)
  - The folder icon's preview is made from the items on the first page, as in Launcher3. Closing the folder while a second or later page is open makes that page's items slide out sideways on the icon and get replaced by the first page's items (same as Launcher3)
  - Notification dots appear on items on any page, as in Launcher3, and the folder icon's dot is decided by the contents of all pages
  - Going past the edge of a page with the arrow keys turns to the next page and moves focus to that item (Launcher3's `PagedView`). Focus moves only inside the folder and does not go to home screen items behind it
  - The first time a folder with several pages is opened, the indicator's entrance animation plays once, as in Launcher3 (Launcher3 remembers this, but this launcher forgets it when the app restarts)
  - It closes on touching outside, back navigation, or typing. When an app inside is launched, it stays open, as in Launcher3, and closes when the launcher is hidden. Dragging an item inside does not close it ([5.3](#53-editing-actions-following-launcher3))
  - Folders themselves are not searched

### 5.3 Editing actions (following Launcher3)

- **Long-pressing an app** (the same on the home screen and in search results):
  - A menu appears in place
  - Moving a set distance without lifting the finger closes the menu and switches to a drag (the same as Pixel Launcher)
  - The menu items are "Search keywords" and "App info", followed by that app's shortcuts (up to 4, as in Launcher3). Tapping a shortcut launches it
    - Items take one line, as in Launcher3, and what does not fit is truncated at the end. "Search keywords" is a noun without "Edit …", and in languages where it still does not fit, the short form is used (「Keywords」 in English, 「キーワード」 in Japanese), the same as the long and short labels of Launcher3's app shortcuts (`DeepShortcutView`). The screen reader still reads the long form
  - Shortcuts can be long-pressed and dragged to the home screen or into a folder. Pressing the "+" at the right end of the row places it in the first empty cell on the home screen (when pressed from search results, the input is cleared and the home screen is shown; when pressed from the all apps list, the list closes with its animation first and then the item is placed, as in Launcher3, so the placed icon is seen bouncing)
  - The long-press menu of a shortcut placed on the home screen or in a folder has only "App info"
  - The menu's look, position, opening and closing match Launcher3's long-press menu. It is kept within the area above the keyboard so that it does not overlap the keyboard
- **Dragging from search results**: when the drag starts, the input is cleared and the display switches to the home screen layout. The item can be placed on the home screen as is (the equivalent of dragging from the app list in Launcher3)
- **Long-pressing a folder**: a menu appears in place, and moving without lifting the finger closes the menu and switches to a drag (same as Launcher3). The only menu item is "Remove", which takes the folder and its contents off the home screen
- **Dragging inside a folder** (same as Launcher3):
  - Long-pressing and moving an item inside an open folder keeps the folder open. The home screen behind it enters edit mode ([5.4](#54-edit-mode))
  - Moving over the folder makes other items move out of the way so the gap moves (after a 250ms pause), and the item is placed where it is released. The order is saved
  - When there are several pages, bringing the icon's center close to the left or right edge of the folder (within 45% of a cell's width) shifts the page slightly to reveal the next page (a hint), and holding it there for 500ms turns to the next page. Once the page turn finishes (750ms and 150ms for the page turn), reordering continues on that page, so items can be placed across pages
  - Moving it out of the folder closes the folder after 400ms, and it can then be placed on the home screen. Coming back cancels the close
  - When released where it cannot be dropped (including a cancel), it goes back to its original position in the folder
  - Holding a home screen item, an app from search results or the all apps list, or a shortcut from a long-press menu over a folder icon on the home screen opens the folder after 800ms, and the item can be placed anywhere inside. The folder opens at the last page that has room. Moving out of the open folder closes it
- **Long-pressing a widget**: shows the menu and resize frame, and moving the finger switches to a drag ([5.2](#52-what-can-be-placed))
- **Remove and uninstall**: during a drag, dropping on "Remove" at the top takes the item off the home screen, and dropping on "Uninstall" uninstalls it. While dragging something not yet on the home screen (an app from search results, a shortcut from a menu), "Remove" becomes "Cancel". Uninstall is not shown for shortcuts, widgets or system apps. It is also not shown for apps of a user whose device admin forbids controlling or uninstalling apps (`DISALLOW_APPS_CONTROL`, `DISALLOW_UNINSTALL_APPS`) (same as Launcher3's `SecondaryDropTarget`)
- **Creating and dissolving folders**: dropping an app or shortcut onto another app or shortcut makes a folder. When a folder is down to one item, the folder is dissolved
- **Long-pressing an empty area**: shows a menu (Launcher3's `WorkspaceTouchListener` and `WorkspaceLongPressOptions`). The items are as follows, in the same order as Launcher3 (there is no "Home settings"; "Share text" is in its position). As in Launcher3, the menu closes when the launcher is hidden (in this launcher, `onStop`). Dialog-style screens (such as choosing the wallpaper app) do not hide the launcher, so the menu stays during them (the same in Launcher3)
  - "Wallpaper & style": opens the OS "Wallpaper & style" ([7. Icons and themes](#7-icons-and-themes)). As in Launcher3, it opens `ACTION_SET_WALLPAPER`. The setting that decides which app opens (`wallpaper_picker_package`) is filled in by the device on Pixel, but it is empty for this launcher, so if several apps can handle it, a chooser appears
  - "Widgets": adds a widget ([5.2](#52-what-can-be-placed))
  - "Share text": opens the dialog that chooses the apps for the share buttons ([2.7](#27-text-sharing)). In the position of Launcher3's "Home settings"
  - "Notification dots": shown only while notification access is not granted. Replaces "Notification dots" in Launcher3's "Home settings" ([7.5](#75-notification-dots)). Pressing it closes the menu and shows a dialog
  - "Apps list": opens [6. All apps list](#6-all-apps-list) with the usual animation ([6.3](#63-helping-users-discover-it)). On two panels ([5.5](#55-two-panel-layout-inner-screen-of-a-foldable)) the list is always shown, so this item is absent

### 5.4 Edit mode

While editing, the keyboard is hidden so the whole screen can be used for editing.

- **Starts**: when a drag starts, when a widget's resize frame appears, when adding from the widget list
- **Ends**: on drop, on tapping outside the frame while resizing, on back navigation, on pressing the home button, and when another screen comes to the front and the launcher pauses (the drag is canceled, as in Launcher3's `onPause`)
- **After it ends**: focus goes back to the search field and the keyboard is shown
- **Appearance**: the same as Launcher3 while editing (SpringLoadedState). The only difference is that there is just one home screen (scrolling vertically)
  - When editing starts, the layout immediately takes its size with the keyboard hidden (the whole area where items can be placed), without waiting for the keyboard to finish hiding
  - An open folder stays where it is, as in Launcher3, and only the home screen behind it shrinks. When editing ends and the keyboard comes back, if the folder would overlap the keyboard, it is moved up to fit above the keyboard
  - The home screen (the visible area) is scaled down to fit between below the Remove and Uninstall buttons at the top and 76dp above the search field. At least 48dp is left on each side (the width at which Launcher3 shows the neighboring pages). The scale-down takes 150ms with the ZOOM_OUT motion
  - The scaled-down home screen gets a rounded background (35% of the accent color), and a 2dp outline while a drag is over it
  - The wallpaper is darkened with Launcher3's drag scrim (20% of system_neutral1_900, 150ms linear)
  - The wallpaper is blurred by the amount of a 15% depth, as in Launcher3 (the same mechanism as the all apps list blur)
  - While the resize frame is shown, the keyboard is only hidden; the home screen is not scaled down and gets no background (in Launcher3 too, the resize frame does not enter SpringLoadedState). When the frame appears after a drop, it goes straight to the frame without showing the keyboard
- **Back navigation**: back navigation in edit mode only ends edit mode. The input clearing in [1.4](#14-back-navigation) is not done
- **Keyboard watching**: in edit mode, the keyboard is not shown again when it closes

### 5.5 Two-panel layout (inner screen of a foldable)

As in Launcher3, on a device with both a phone-sized display and a large-screen display (shortest side 600dp or more) (`TYPE_MULTI_DISPLAY`), the large-screen window is split into left and right panels (`DeviceProfile.isTwoPanels`). Launcher3 shows two pages side by side, but this launcher's home screen has one page, so the left is the all apps list and the right is the home screen. Large-screen-only devices (tablets) do not use two panels.

- The split is at the middle of the screen (same as Launcher3). The fold position (`FoldingFeature`) is not looked at
- The right panel is the home screen from [5.1](#51-grid). The grid is the same as on the outer screen, so the layout does not change when folding or unfolding
- The left panel is the all apps list (the sort order and display from [6.4](#64-open-state)), always shown. It looks like the sheet of Launcher3's large-screen all apps list (only the top corners are rounded, it extends to the bottom of the screen, and continues behind the search field and keyboard; the color is the same as the all apps list sheet). It has no handle, since there is no pull gesture (the space for it is kept). The list is cut off above the search field
- The Remove and Uninstall buttons during a drag appear above the right panel (the home screen)
- The search field spans the full width of the screen, right above the keyboard ([1.3](#13-placement-and-appearance)). Both panels end above it
- When there is input, search results are shown in place of the all apps list on the left (bottom-aligned as in [2.5](#25-search-result-display), with the first result closest to the search field). The home screen stays shown on the right
- There are no gestures to open or close the all apps list ([6.1](#61-opening), [6.6](#66-closing))
- An icon can be long-pressed in the list or search results on the left and dragged to the home screen on the right (unlike [6.4](#64-open-state), the list does not close)
- When folding, unfolding, entering split screen and so on changes whether two panels are used, the Activity is recreated

### 5.6 Swipe down for the notification shade

As in Pixel Launcher (Launcher3 Quickstep's `StatusBarTouchController`), swiping down from anywhere on the home screen opens the notification shade.

- It counts as a swipe down when the vertical movement exceeds the touch slop and is larger than the horizontal movement. If the touch first moves past the touch slop horizontally, that touch does not open it
- Launcher3 hands the rest of the touch to System UI so the shade follows the finger, but that path (`SystemUiProxy`) is only available to the launcher that provides the recents screen. This launcher calls `StatusBarManager.expandNotificationsPanel` (the `EXPAND_STATUS_BAR` permission) as soon as it counts the touch as a swipe down, and the shade opens with its own animation. It does not follow the finger
- Since it does not follow the finger, the device vibrates as it opens (specific to this launcher; Launcher3's swipe down has no vibration). The vibration is the one Launcher3 plays when a swipe crosses its threshold (`MSDLToken.SWIPE_THRESHOLD_INDICATOR`, the same as the swipe that opens the all apps list)
- Since it overlaps with the home screen's vertical scrolling, it opens only when the home screen is scrolled all the way to the top. When scrolled down, the home screen scrolls back up as before
- It opens only in what corresponds to Launcher3's NORMAL state. It does not open while editing ([5.4](#54-edit-mode)), when there is input, or while the all apps list is shown
- When a folder or long-press menu is open, it receives the touch first, as in Launcher3, and touching outside closes it (that touch does not open the shade). A swipe down from inside an open folder opens it, as in Launcher3. It does not open while a widget's resize frame is shown
- On two panels ([5.5](#55-two-panel-layout-inner-screen-of-a-foldable)), a swipe that starts in the all apps list on the left does not open it (the list scrolls)
- A swipe that starts on the navigation bar does not open it (same as Launcher3)

---

## 6. All apps list

On a two-panel screen ([5.5](#55-two-panel-layout-inner-screen-of-a-foldable)), the all apps list is always shown in the left panel. The gestures for opening and closing and the presentation in this chapter are for all other screens.

To the user, it should look as if the all apps list is normally hidden below the keyboard and appears when the search field is pulled up along with it.

### 6.1 Opening

- Swipe up on the search field
- With the home screen layout scrolled all the way to the bottom, swipe up further
  - If the layout fits on the screen and does not scroll, swiping up from anywhere on the home screen opens it
  - Just arriving at the bottom by inertial scrolling (a fling) does not open it. It opens only when pulled with the finger
- A swipe that starts on the keyboard cannot be caught, because the keyboard is a separate window

### 6.2 Open and close feel

- Up to the threshold, the finger's movement meets resistance and moves things only a little (a feeling of being caught)
- The moment it crosses the threshold, a haptic plays, the search field pops up, and it switches to following the finger
- If the finger is lifted before the threshold, it springs back to its original position
- If the finger is lifted after crossing the threshold, it animates to the top and opens
- The keyboard is hidden at the same moment the field pops up past the threshold
- Closing ([6.6](#66-closing)) feels the same. Only the direction is downward; the resistance up to the threshold, the haptic at the threshold, and the spring back when released before it are the same
- When it returns to the original state (when released before the threshold, or when a close is pulled back to stay open), nothing changes, as in Launcher3. The typed text, focus and keyboard stay as they are
- Touching during the open or close animation grabs the sheet where it is, as in Launcher3, and it follows the finger. On release it opens or closes by the same rules. A tap does not leave it stopped partway either

### 6.3 Helping users discover it

The pull-up gesture is not shown anywhere on the screen. Instead, opening it from "Apps list" in the long-press menu of an empty area ([5.3](#53-editing-actions-following-launcher3)) also uses the usual animation. Showing the search field rising and the list coming out lets the user discover that "there is a list below the keyboard, and pulling up brings it out". As in Launcher3, a tap haptic (`TAP_HIGH_EMPHASIS`) plays as it starts to open in this case.

### 6.4 Open state

- The search field sticks to the top edge of the screen, and right below it is a sheet with a handle that contains the all apps list (the bottom sheet of Launcher3's `allAppsSheetForHandheld`; the same look as Pixel Launcher)
- The top edge of the sheet is always right below the search field. It rises as the search field moves from above the keyboard to the top of the screen, so the sheet looks as if it comes out from behind the keyboard where it was hidden (in Launcher3 and Pixel Launcher it comes up from the bottom of the screen, but this matches this launcher's "list hidden below the keyboard" structure)
- As it opens, the background (wallpaper and home screen) is blurred. The blur is the same as Launcher3's depth, up to 30dp at the bottom sheet's depth. The home screen shrinks to 0.97×. The blur grows gradually with the finger's movement. The sheet is translucent over the blur (the same color as Pixel Launcher; light in the light theme, dark in the dark theme). When the device does not support window blur, an opaque color is used, as in Launcher3 (system_accent2_200, or 800 in the dark theme), with no blur
- Focus is cleared and the keyboard is not shown (the keyboard watching in [1.4](#14-back-navigation) also stops)
- **Sort order**: by app name (the same as Launcher3's `AlphabeticalAppsList`). Moving the scrollbar thumb shows a bubble with the section heading, A to Z for letters and あ・か・さ… (the kana rows a, ka, sa…) for kana, and jumps to that heading. No heading rows are shown in the list (same as Launcher3)
- **Display**: each app's icon and app name. As in Launcher3, themed icons and the chosen shape apply only to icons on the home screen and in folders; icons in the list and search results are the regular icons in the OS shape (the same in Pixel Launcher)
- **Long press**: the same as [5.3](#53-editing-actions-following-launcher3). A menu appears, and moving a set distance switches to a drag. When the drag starts, the list closes and the display switches to the home screen layout

### 6.5 Searching within the list

- Tapping the search field gives it focus, brings up the keyboard, and starts searching within the list
- The sort order and launching with Enter are the same as [2.3](#23-sort-order) and [2.4](#24-launching-with-enter). Learning is also recorded, as in [3](#3-learning-from-launches)
- Results are lined up top-aligned from right below the field. The first result comes in the position closest to the field
- Switching between the list and search results has no animation (for the same reason as [2.5](#25-search-result-display))
- Back navigation behaves normally. The first press while the keyboard is shown only closes the keyboard, keeps the input, and shows the filtered list without focus. The second closes the list ([6.6](#66-closing))
- While searching within the list, the keyboard watching in [1.4](#14-back-navigation) (clearing the input and showing the keyboard again when it closes) is not done

### 6.6 Closing

- The OS back navigation (when the keyboard is shown, the first closes the keyboard and the second closes the list)
- Swipe down on the search field
- With the list scrolled all the way to the top, swipe down further. When searching within the list, with the search results scrolled all the way to the top (Launcher3's `getActiveRecyclerView`)
- The home button
- On closing, the input is cleared, the search field goes back right above the keyboard, and it gets focus with the keyboard shown
- When an app was launched from the list, the list is closed when the user comes back to the home screen

---

## 7. Icons and themes

Appearance settings are left to the OS as much as possible, and the launcher keeps no settings of its own.

### 7.1 Wallpaper

- Choosing the wallpaper is left to the OS "Wallpaper & style"
- The launcher only makes its background transparent to show the wallpaper (enable `windowShowWallpaper` in the theme)
- It can be opened from "Wallpaper & style" in the long-press menu of an empty area ([5.3](#53-editing-actions-following-launcher3))

### 7.2 Theme colors

- How colors are decided (automatically from the wallpaper, or by choosing a basic color) is left to the OS settings
- Text shown over the wallpaper (app names in search results) and the status bar and navigation bar icons are decided by the wallpaper's color hints, as in Launcher3 (`WallpaperColorHints`, `Themes.getActivityThemeRes`). On wallpapers that suit dark text, the text is black (no shadow); otherwise it is white with a shadow. It is not decided by whether light mode is on. When the wallpaper changes and its hints change, the launcher's screen is recreated
- The launcher's own UI follows the system's dynamic color
  - Covers: the search field, result highlighting, menus and so on

### 7.3 Themed icons and icon shape

- Turning them on or off and choosing the shape are done in the OS "Wallpaper & style"
- For this, the launcher exposes a ContentProvider shaped like AOSP Launcher3's (details in the implementation notes in the Appendix)
  - **Themed icons**: when on, icons on the home screen and in folders show the monochrome icon the app provides (`AdaptiveIconDrawable.getMonochrome()`), tinted with the system palette. They are not applied in search results or the all apps list ([6.4](#64-open-state)). Off by default, as in Launcher3
  - **Apps without a monochrome icon**: when themed icons are on, a monochrome icon is generated automatically and tinted the same way (the same behavior as Pixel Launcher)
  - **Icon shape**: icons on the home screen and in folders are clipped to the chosen shape. It applies to both themed and regular icons. The default is a circle
- **Implementation baseline**: icon generation and display follow the AOSP Launcher3 implementation at the baseline tag ([Launcher3 porting policy](launcher3-porting.md))
  - Covers: themed icons, automatic monochrome icon generation, clipping to a shape
  - References: Launcher3 and its icon library `frameworks/libs/systemui/iconloaderlib` (`MonochromeIconFactory` and others)
  - Even if automatic generation sits behind a feature flag in Launcher3 and is off by default, this launcher turns it on
- **Scope**: every place that shows icons
  - The home screen layout
  - Folder previews and contents
  - Search results
  - The all apps list
- **Status**: this is not a public API but an internal AOSP convention. An OS update may make it stop working
- **How to proceed**:
  - Early in implementation, check on a real Pixel (Android 17) whether the options appear in "Wallpaper & style"
  - If they do not appear, or stop working, switch to the launcher's own settings

### 7.4 Supported languages

- The default is English. Strings specific to this launcher are translated into every language Launcher3 is translated into (83 locales besides English), plus Romansh (a Google Play listing language that Launcher3 lacks)
- "Search apps", "App info", "Remove" and "Uninstall" use Launcher3's translation for each language as is (to use the same terms as Pixel). Romansh has no Launcher3 translation, so "Search apps" and "Search" are translated by this project, and the other Launcher3 strings (such as "App info" in the long-press menu) appear in English

### 7.5 Notification dots

As in Launcher3, a dot is shown at the top right of the icon of an app that has notifications.

- Covers icons on the home screen, inside folders, in the all apps list, and in search results. For folders, if anything inside has a notification, the folder icon also gets a dot. An app shortcut placed on the home screen gets one only when there is a notification aimed at that shortcut
- Which notifications count is also the same as Launcher3. Notifications whose channel does not allow dots, notifications with neither title nor text, and group summary notifications are not counted
- When notifications arrive or go away, the icons on screen change right away (dots enter and leave with a scale animation)
- The color is the theme's third accent color (`system_accent3_200` in light, `system_accent3_100` in dark). The position matches the top right corner of the icon shape
- If the OS "Notification dot on app icon" setting is off, dots are not shown (as in Launcher3, the launcher stops receiving notifications, and resumes when it is turned back on)
- Dots need the OS "Notification access" permission (Pixel Launcher is a system app, so it does not). While it is not granted, "Notification dots" appears in the long-press menu of an empty area (5.3). Pressing it shows the same "Notification access needed" dialog as Launcher3's settings screen, and "Change settings" opens the OS notification access screen. Once granted, the item no longer appears. It is also not shown when the OS notification dot setting is off
- Rows that list notification contents in the long-press menu (Launcher3's notification rows) are not included, since Pixel Launcher does not have them either

---

## 8. Out of scope

| Item | Reason |
|---|---|
| Initial-letter matching (such as `gm` for Google Maps) | Search keywords registered by the user are enough |
| Fixed assignments for exact matches | Exact matches on search keywords already come first, so registering one does the same thing |
| Searching anything other than apps (shortcuts, contacts, the web and so on) | Not included by policy. Handing the typed text to a web search or LLM app is done with the share buttons in [2.7](#27-text-sharing) |
| Searching folders | Not included by policy |
| Dealing with the layout area made smaller by the keyboard | To be decided after building it |
| Scrolling inside widgets | Not considered |
| Showing app names in the home screen layout | Not shown; rows are made shorter instead |
| Folder names | Folders cannot be named |
| Work profile | Not supported for now. Records are keyed by the app and `UserHandle` pair, so it can be added later |
| Private Space | Not supported. `ACCESS_HIDDEN_PROFILES` is not declared, so apps inside are not visible. While this launcher is the default, Private Space cannot be opened from this launcher |
| Settings screen | None. Search keywords are edited from the app's long-press menu, and the share button apps ([2.7](#27-text-sharing)) are chosen from the long-press menu of an empty area. Appearance settings are left to "Wallpaper & style". Choosing the default home app is done in the OS settings. The licenses (NOTICE and Apache License 2.0) are shown from "Additional settings in the app" in the OS "App info" (`ACTION_APPLICATION_PREFERENCES`). The screen it opens looks like the OS settings and has only a "Licenses" row, which shows the license text when pressed. The version is not shown, since the OS "App info" shows it. There is no entry point inside the launcher |
| Resetting learning data, exporting and importing search keywords | Not included, since there is no settings screen |
| Cloud backup (restoring from Google backup, reinstalling) | Not done (`allowBackup="false"`). Launcher3's BackupAgent and its handling of apps being restored are not ported, so restoring would give a home screen where apps and widgets not yet installed at load time are gone. After a restore or reinstall, it starts from scratch. Device-to-device transfer (moving data from an old device by cable or wirelessly) is not stopped by `allowBackup`, so it is left to that, and the app's data is moved in full. `res/xml/data_extraction_rules.xml` (`android:dataExtractionRules`, for Android 12 and later) says the same: it excludes everything from cloud backup and leaves device transfer to include everything. Right after a transfer, apps and widgets not yet installed may disappear for the same reason |
| The recents (App Switcher) screen | Cannot be built. This screen is drawn by Pixel Launcher (Quickstep) regardless of the default launcher. While CaretLauncher is the default, it becomes Quickstep's fallback screen (`RecentsActivity` with `OverviewBlurFallbackStyle`), and its background is an opaque color (`overviewScrimColorFallback`) instead of a blurred wallpaper |

---

## 9. Open questions

None at this time.

---

## Appendix: Implementation notes

- Manifest
  - Give the home Activity an intent-filter with `MAIN` + `HOME` + `DEFAULT`
  - Set `launchMode="singleTask"`
  - Set `windowSoftInputMode="stateAlwaysVisible|adjustResize"`
- Package visibility: declare the `MAIN`/`LAUNCHER` intent in `<queries>`. For the icons and labels of the share buttons ([2.7](#27-text-sharing)), also declare `SEND` with `text/plain`
- App additions, removals and updates are detected with `LauncherApps.Callback`
- Showing the keyboard: use `WindowInsetsControllerCompat.show(WindowInsetsCompat.Type.ime())`
- Watching the keyboard's visibility: use the IME visibility from `WindowInsetsCompat`
- Detecting vertical swipes on the search field:
  - Detect them in the parent ViewGroup's `onInterceptTouchEvent`
  - Once the movement passes the touch slop and the vertical distance is larger than the horizontal, the parent intercepts the touch (the EditText receives `ACTION_CANCEL`)
  - A single-line EditText does not scroll vertically, so there is no conflict. Tap to focus, horizontal cursor movement, and long-press selection still reach the EditText
- Detecting the pull after reaching the edge of the home screen layout:
  - An upward swipe is caught as opening or closing the all apps list only when the home screen can no longer scroll down at the moment the finger touches down (decided at the start of the touch, as in Launcher3's `AllAppsSwipeController.canInterceptTouch`). Just arriving at the bottom by a fling does not make that touch an open or close
  - The pull at the top of the all apps list is the same: it is caught only when the list is at the top at the moment the finger touches down (`shouldContainerScroll`)
- The open and close motion:
  - The catch is expressed by applying damping (Launcher3's `OverScroll.dampedScroll`) to the movement up to the threshold (48dp). After crossing the threshold, the search field catches up with the finger in 150ms
  - The spring back and the motion to fully open use animations whose duration is set from the velocity at release, as in Launcher3 (`BaseSwipeDetector.calculateDuration`, `scrollInterpolatorForVelocity`)
  - The keyboard's showing and hiding is synchronized with `WindowInsetsAnimationCompat`
- Haptics: as in Launcher3, play `SWIPE_THRESHOLD_INDICATOR` from `msdllib` (see [Where haptics play](launcher3-porting.md#where-haptics-play) in the Launcher3 porting policy)
- Integration with "Wallpaper & style" ([7.3](#73-themed-icons-and-icon-shape)):
  - This was checked against the AOSP `android-17.0.0_r1` source (Launcher3's `graphics/LauncherCustomizationProvider.kt`, `graphics/GridCustomizationsProxy.java`, `shapes/ShapesProvider.kt`, and ThemePicker's `res/values/strings.xml`)
  - **How it is found**: add meta-data to the home Activity whose value is the Provider's authority (in Launcher3, `<applicationId>.grid_control`)
    - `com.android.launcher3.grid.control`: the one in Launcher3's manifest
    - `com.android.launcher3.themedicon.option`: the one ThemePicker uses to find where to query themed icons
  - **Permissions**: the Provider is exported, and the caller is checked in code (the same as Launcher3's `enforceCallerPermission`). A call is accepted if the caller holds `android.permission.BIND_WALLPAPER` or `<applicationId>.permission.GRID_CONTROL` (declared in the manifest as `signature|knownSigner`, with the allowed signatures in `grid_control_known_signers`), or, as a stopgap until migration, if the SHA-256 of the caller's signature is in `grid_control_known_signers`. `grid_control_known_signers` is empty, as upstream, so for now only callers holding `BIND_WALLPAPER` and callers with the same signature as this launcher get through
  - **Reads (query)**:
    - `/icon_themed`, `/get_icon_themed`: return one row with a `boolean_value` column (1/0)
    - `/shape_options`: columns `shape_key`, `shape_title`, `path` (a 100×100 SVG path), `is_default`. The selected shape is the `is_default` one
    - `/list_options` (grid): return null. No grid options are offered
    - `/workspace_items_label_hidden` (the setting that hides app names on the home screen, added in Android 17): return null, since this launcher always hides them
  - **Writes (update)**: on success, return 1 and call `notifyChange`
    - `/icon_themed`, `/set_icon_themed`: save `boolean_value`
    - `/shape`: save `shape_key`
    - `/default_grid`: return 0 (the grid cannot be changed)
  - **Shape options** (`ShapesProvider.kt`): five, `circle` (the default), `square`, `four_sided_cookie`, `seven_sided_cookie` and `arch`. The SVG paths are ported as is when brought over
  - **Preview** (`get_preview` via `call`): as in Launcher3, draw a preview of the home screen into a `SurfaceControlViewHost` and return the `SurfacePackage` and a `Messenger` that receives notices of new selections. What is drawn is the home screen with the keyboard hidden (the home screen page and the search field); the keyboard is not drawn. "Wallpaper & style" draws the wallpaper underneath. When the shape or themed icons are chosen again, the setting changes at the moment of the choice, as upstream, and the preview is redrawn. The still image `get_preview_bitmap` is not returned
  - Known issue (from a third-party launcher's implementation, [pie-launcher-remake #28](https://github.com/jgoedde/pie-launcher-remake/issues/28)): "Wallpaper & style" caches the launcher it connects to. Right after switching home apps, it may connect to the previous launcher until that process ends
