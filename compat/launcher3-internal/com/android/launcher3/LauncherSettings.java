/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import com.android.launcher3.icons.cache.CacheLookupFlag;

/** Stand-in for Launcher3's LauncherSettings. Only the item types and lookup flag that copied code reads. */
public class LauncherSettings {
    /** Values from LauncherSettings.Favorites (android-17.0.0_r1). */
    public static final class Favorites {
        public static final int CONTAINER_SHORTCUTS = -107;

        public static final int ITEM_TYPE_APPLICATION = 0;
        public static final int ITEM_TYPE_FOLDER = 2;
        public static final int ITEM_TYPE_APPWIDGET = 4;
        public static final int ITEM_TYPE_DEEP_SHORTCUT = 6;
        public static final int ITEM_TYPE_APP_GROUP = 10;

        public static final CacheLookupFlag DESKTOP_ICON_FLAG =
                CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withThemeIcon();
    }
}
