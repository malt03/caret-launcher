/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.appwidget.AppWidgetProviderInfo;

/**
 * Stand-in for Launcher3's LauncherAppWidgetProviderInfo (android-17.0.0_r1): a widget provider
 * with the spans it takes on the home grid (initSpans).
 *
 * TextLauncher: it wraps the provider info instead of extending it (Launcher3 copies it through a
 * Parcel), and the app works out the spans for the grid as it is now (HomeWidgetSizes.initSpans),
 * not for every device profile.
 */
public class LauncherAppWidgetProviderInfo {
    public final AppWidgetProviderInfo info;
    public final int resizeMode;

    public int spanX;
    public int spanY;
    public int minSpanX;
    public int minSpanY;
    public int maxSpanX;
    public int maxSpanY;

    public LauncherAppWidgetProviderInfo(AppWidgetProviderInfo info) {
        this.info = info;
        this.resizeMode = info.resizeMode;
    }
}
