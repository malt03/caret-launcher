package com.android.launcher3.apppairs;

import android.graphics.drawable.Drawable;

import com.android.launcher3.model.data.AppPairInfo;

/** Stand-in for Launcher3's AppPairIconGraphic. TextLauncher has no app pairs; never reached. */
public class AppPairIconGraphic {
    public static Drawable composeDrawable(AppPairInfo info, AppPairIconDrawingParams params) {
        throw new UnsupportedOperationException("No app pairs in TextLauncher");
    }
}
