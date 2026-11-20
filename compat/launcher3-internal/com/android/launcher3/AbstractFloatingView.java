package com.android.launcher3;

import com.android.launcher3.views.ActivityContext;

/**
 * Stand-in for Launcher3's AbstractFloatingView. TextLauncher's floating views (open folders,
 * menus) close themselves, so closing by type is a no-op.
 */
public abstract class AbstractFloatingView {
    public static final int TYPE_DISCOVERY_BOUNCE = 1 << 6;

    public static void closeOpenViews(ActivityContext activity, boolean animate, int type) { }
}
