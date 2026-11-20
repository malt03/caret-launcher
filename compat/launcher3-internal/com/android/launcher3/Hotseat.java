package com.android.launcher3;

import android.content.Context;
import android.widget.FrameLayout;

/**
 * Stand-in for Launcher3's Hotseat: the view the folder animation scales together with the
 * workspace. In TextLauncher it holds the search field.
 */
public class Hotseat extends FrameLayout {
    public Hotseat(Context context) {
        super(context);
    }
}
