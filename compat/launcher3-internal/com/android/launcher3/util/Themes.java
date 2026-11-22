/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.util;

import static android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT;
import static android.app.WallpaperColors.HINT_SUPPORTS_DARK_THEME;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.content.res.TypedArray;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.GraphicsUtils;

/** Stand-in for Launcher3's Themes. Members copied from util/Themes.java as needed. */
public class Themes {
    public static int getActivityThemeRes(Context context) {
        return getActivityThemeRes(context, WallpaperColorHints.get(context).getHints());
    }

    public static int getActivityThemeRes(Context context, int wallpaperColorHints) {
        boolean supportsDarkText = (wallpaperColorHints & HINT_SUPPORTS_DARK_TEXT) != 0;
        boolean isMainColorDark = (wallpaperColorHints & HINT_SUPPORTS_DARK_THEME) != 0;

        if (Utilities.isDarkTheme(context)) {
            return supportsDarkText ? R.style.AppTheme_Dark_DarkText
                    : isMainColorDark ? R.style.AppTheme_Dark_DarkMainColor : R.style.AppTheme_Dark;
        } else {
            return supportsDarkText ? R.style.AppTheme_DarkText
                    : isMainColorDark ? R.style.AppTheme_DarkMainColor : R.style.AppTheme;
        }
    }

    public static boolean getAttrBoolean(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        boolean value = ta.getBoolean(0, false);
        ta.recycle();
        return value;
    }

    public static int getColorAccent(Context context) {
        return getAttrColor(context, android.R.attr.colorAccent);
    }

    public static int getAttrColor(Context context, int attr) {
        return GraphicsUtils.getAttrColor(context, attr);
    }

    public static float getDialogCornerRadius(Context context) {
        // TextLauncher: Launcher3's themes come from Theme.DeviceDefault, which has the device's
        // dialog corner radius; this app's come from AppCompat (Material3), which sets
        // android:dialogCornerRadius to 2dp (abc_dialog_corner_radius_material). It is read with
        // the device default theme on top.
        Context deviceDefault = new ContextThemeWrapper(context,
                android.R.style.Theme_DeviceDefault_DayNight);
        return getDimension(deviceDefault, android.R.attr.dialogCornerRadius,
                context.getResources().getDimension(R.dimen.default_dialog_corner_radius));
    }

    public static float getDimension(Context context, int attr, float defaultValue) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float value = ta.getDimension(0, defaultValue);
        ta.recycle();
        return value;
    }
}
