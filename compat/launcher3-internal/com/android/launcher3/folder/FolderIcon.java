package com.android.launcher3.folder;

import android.content.Context;
import android.view.View;

/**
 * Stand-in for Launcher3's FolderIcon: the folder on the home screen. TextLauncher's folder icon
 * view extends this and keeps these members up to date.
 */
public abstract class FolderIcon extends View {
    /** Draws the round background, like FolderIcon.mBackground. */
    public PreviewBackground mBackground;

    public FolderIcon(Context context) {
        super(context);
        mBackground = new PreviewBackground(context);
    }

    public abstract ClippedFolderIconLayoutRule getLayoutRule();

    public abstract PreviewItemManager getPreviewItemManager();
}
