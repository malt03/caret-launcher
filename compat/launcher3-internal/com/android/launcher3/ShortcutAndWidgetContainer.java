package com.android.launcher3;

import android.view.View;

/**
 * Stand-in for Launcher3's ShortcutAndWidgetContainer. TextLauncher's folders have no CellLayout
 * pages (FolderPagedView.getPageAt returns null), so this is never reached at runtime.
 */
public class ShortcutAndWidgetContainer {
    public void setupLp(View child) { }
}
