package com.android.launcher3;

import android.content.Context;

import com.android.launcher3.icons.IconCache;

/** Stand-in for Launcher3's LauncherAppState. Only the icon cache that folder/PreviewItemManager reads. */
public class LauncherAppState {
    private static final LauncherAppState INSTANCE = new LauncherAppState();
    private final IconCache mIconCache = new IconCache();

    public static LauncherAppState getInstance(Context context) {
        return INSTANCE;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }
}
