package com.android.launcher3.dragndrop;

import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * Stand-in for Launcher3's dragndrop.FolderAdaptiveIcon. DragView only checks the type for spring
 * icons, which TextLauncher does not use; never instantiated.
 */
public abstract class FolderAdaptiveIcon extends AdaptiveIconDrawable {
    public FolderAdaptiveIcon(Drawable bg, Drawable fg) {
        super(bg, fg);
    }

    public abstract android.graphics.Path getIconMask();
}
