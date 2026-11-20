package com.android.launcher3.util.window;

import android.content.Context;
import android.view.Display;

/**
 * Stand-in for Launcher3's RefreshRateTracker (android-17.0.0_r1), used by the copied
 * anim/SpringAnimationBuilder.java. Launcher3 reads the frame time from its Dagger
 * FrameRateProvider; this reads the current display's refresh rate.
 */
public class RefreshRateTracker {
    public static int getSingleFrameMs(Context context) {
        Display display = context.getDisplay();
        float refreshRate = display != null ? display.getRefreshRate() : 60f;
        return refreshRate > 0 ? (int) (1000 / refreshRate) : 16;
    }
}
