package com.android.launcher3.widget;

import android.content.Context;
import android.widget.FrameLayout;

/** Stand-in for Launcher3's LauncherAppWidgetHostView. Widgets are not ported yet. */
public class LauncherAppWidgetHostView extends FrameLayout {
    public LauncherAppWidgetHostView(Context context) {
        super(context);
    }

    public float getScaleToFit() {
        return 1f;
    }
}
