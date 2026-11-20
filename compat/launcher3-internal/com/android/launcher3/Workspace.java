package com.android.launcher3;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Stand-in for Launcher3's Workspace (android-17.0.0_r1). In TextLauncher it holds the home grid.
 * Members are copied from Workspace.java as copied code needs them.
 */
public class Workspace<T> extends FrameLayout {
    public static final int REORDER_TIMEOUT = 650;

    public Workspace(Context context) {
        super(context);
    }

    public void setPivotToScaleWithSelf(View sibling) {
        sibling.setPivotY(getPivotY() + getTop()
                - sibling.getTop() - sibling.getTranslationY());
        sibling.setPivotX(getPivotX() + getLeft()
                - sibling.getLeft() - sibling.getTranslationX());
    }
}
