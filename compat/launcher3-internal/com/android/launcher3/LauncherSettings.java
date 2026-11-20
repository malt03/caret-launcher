package com.android.launcher3;

/** Stand-in for Launcher3's LauncherSettings. Only the item types and lookup flag that copied code reads. */
public class LauncherSettings {
    /** Values from LauncherSettings.Favorites (android-17.0.0_r1). */
    public static final class Favorites {
        public static final int ITEM_TYPE_APPLICATION = 0;
        public static final int ITEM_TYPE_FOLDER = 2;
        public static final int ITEM_TYPE_APPWIDGET = 4;
        public static final int ITEM_TYPE_DEEP_SHORTCUT = 6;
        public static final int ITEM_TYPE_APP_GROUP = 10;

        public static final com.android.launcher3.icons.cache.CacheLookupFlag DESKTOP_ICON_FLAG =
                com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withThemeIcon();
    }
}
