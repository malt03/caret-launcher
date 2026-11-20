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
