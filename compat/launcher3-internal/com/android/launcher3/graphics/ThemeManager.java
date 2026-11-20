package com.android.launcher3.graphics;

import android.content.Context;

/**
 * Stand-in for Launcher3's ThemeManager. Holds the icon state (theme on/off and shape) and the
 * folder shape, which TextLauncher derives from the selected icon shape the same way
 * ThemeManager.parseIconState does (folderRadiusRatio).
 */
public class ThemeManager {
    public static final Instance INSTANCE = new Instance();

    private ShapeDelegate mFolderShape = new ShapeDelegate.Circle();

    public ShapeDelegate getFolderShape() {
        return mFolderShape;
    }

    /** Only read by DragView for spring icons, which TextLauncher does not use. */
    public ShapeDelegate getIconShape() {
        return mFolderShape;
    }

    private boolean mIconThemeEnabled = false;
    private com.android.launcher3.icons.IconShape mIconShapeData;

    public boolean isIconThemeEnabled() {
        return mIconThemeEnabled;
    }

    /** ThemeManager.getIconShapeData().getValue(): the selected icon shape at the icon size. */
    public com.android.launcher3.icons.IconShape getIconShapeData() {
        return mIconShapeData;
    }

    /**
     * TextLauncher: the icon state comes from Wallpaper & style (docs/spec.md 7.3) through the
     * app's icon loader, which sets it here.
     */
    public void setIconState(boolean iconThemeEnabled,
            com.android.launcher3.icons.IconShape iconShapeData) {
        mIconThemeEnabled = iconThemeEnabled;
        mIconShapeData = iconShapeData;
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
