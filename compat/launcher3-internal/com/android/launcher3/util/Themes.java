package com.android.launcher3.util;

import android.content.Context;

import com.android.launcher3.icons.GraphicsUtils;

/** Stand-in for Launcher3's Themes. Members copied from util/Themes.java as needed. */
public class Themes {
    public static int getAttrColor(Context context, int attr) {
        return GraphicsUtils.getAttrColor(context, attr);
    }

    public static float getDialogCornerRadius(Context context) {
        return getDimension(context, android.R.attr.dialogCornerRadius,
                context.getResources().getDimension(com.android.launcher3.R.dimen.default_dialog_corner_radius));
    }

    public static float getDimension(Context context, int attr, float defaultValue) {
        android.content.res.TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float value = ta.getDimension(0, defaultValue);
        ta.recycle();
        return value;
    }
}
