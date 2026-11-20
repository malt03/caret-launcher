package com.android.launcher3.graphics;

import android.content.Context;

/**
 * Stand-in for Launcher3's ThemeManager. Holds the folder shape, which TextLauncher derives from
 * the selected icon shape the same way ThemeManager.parseIconState does (folderRadiusRatio).
 */
public class ThemeManager {
    public static final Instance INSTANCE = new Instance();

    private ShapeDelegate mFolderShape = new ShapeDelegate.Circle();

    public ShapeDelegate getFolderShape() {
        return mFolderShape;
    }

    /** TextLauncher: ThemeManager.parseIconState's folder shape for the given radius ratio. */
    public void setFolderRadiusRatio(float folderRadius) {
        mFolderShape = folderRadius == 1f
                ? new ShapeDelegate.Circle()
                : new ShapeDelegate.RoundedSquare(folderRadius);
    }

    /** Stand-in for DaggerSingletonObject. */
    public static class Instance {
        private final ThemeManager mManager = new ThemeManager();

        public ThemeManager get(Context context) {
            return mManager;
        }
    }
}
