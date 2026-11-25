/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.util;

import static com.android.launcher3.BuildConfig.WIDGETS_ENABLED;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

/**
 * Stand-in for Launcher3's ShortcutUtil (android-17.0.0_r1). The checks the long-press menu uses
 * are copied from ShortcutUtil.java.
 */
public class ShortcutUtil {
    public static boolean supportsShortcuts(ItemInfo info) {
        return isActive(info) && (isApp(info) || isPinnedShortcut(info));
    }

    public static boolean supportsDeepShortcuts(ItemInfo info) {
        return isActive(info) && isApp(info) && !!WIDGETS_ENABLED;
    }

    /**
     * Returns the shortcut id if the item is a pinned shortcut.
     */
    public static String getShortcutIdIfPinnedShortcut(ItemInfo info) {
        return isActive(info) && isPinnedShortcut(info)
                ? com.android.launcher3.shortcuts.ShortcutKey.fromItemInfo(info).getId() : null;
    }

    /**
     * Returns the person keys associated with the item. (Has no function right now.)
     */
    public static String[] getPersonKeysIfPinnedShortcut(ItemInfo info) {
        return isActive(info) && isPinnedShortcut(info)
                ? ((WorkspaceItemInfo) info).getPersonKeys()
                : com.android.launcher3.Utilities.EMPTY_STRING_ARRAY;
    }

    private static boolean isActive(ItemInfo info) {
        boolean isLoading = info instanceof WorkspaceItemInfo
                && ((WorkspaceItemInfo) info).hasPromiseIconUi();
        return !isLoading && !info.isDisabled();
    }

    private static boolean isApp(ItemInfo info) {
        return info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    }

    private static boolean isPinnedShortcut(ItemInfo info) {
        return info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                && info.container != ItemInfo.NO_ID
                && info instanceof WorkspaceItemInfo;
    }
}
