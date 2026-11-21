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
