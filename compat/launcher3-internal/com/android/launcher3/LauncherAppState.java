/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3;

import android.content.Context;

import com.android.launcher3.icons.IconCache;

/**
 * Stand-in for Launcher3's LauncherAppState. Only the icon cache (icons for folders, the long-press
 * menu and the widget picker), the InvariantDeviceProfile (widget spans and the widget picker) and
 * the model.
 */
public class LauncherAppState {
    private static final LauncherAppState INSTANCE = new LauncherAppState();
    private IconCache mIconCache;
    private LauncherModel mModel;
    private final InvariantDeviceProfile mInvariantDeviceProfile = new InvariantDeviceProfile();

    public static InvariantDeviceProfile getIDP(Context context) {
        return INSTANCE.mInvariantDeviceProfile;
    }

    public InvariantDeviceProfile getInvariantDeviceProfile() {
        return mInvariantDeviceProfile;
    }

    public static LauncherAppState getInstance(Context context) {
        return INSTANCE;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    /** CaretLauncher: the app makes the icon cache (Dagger in Launcher3) and sets it here. */
    public void setIconCache(IconCache iconCache) {
        mIconCache = iconCache;
    }

    /** CaretLauncher: the app makes the model (Dagger in Launcher3) and sets it here. */
    public void setModel(LauncherModel model) {
        mModel = model;
    }
}
