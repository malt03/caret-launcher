package com.android.launcher3.util;

import android.content.Context;

import com.android.launcher3.icons.GraphicsUtils;

/** Stand-in for Launcher3's Themes. Members copied from util/Themes.java as needed. */
public class Themes {
    public static int getAttrColor(Context context, int attr) {
        return GraphicsUtils.getAttrColor(context, attr);
    }
}
