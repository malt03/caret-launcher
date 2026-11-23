/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.view.View;
import android.view.WindowInsetsController;

/**
 * Stand-in for Launcher3's SystemUiController (android-17.0.0_r1): sets whether the system bars
 * have light (dark-icon) or dark (light-icon) content. TextLauncher: one state, the latest.
 */
public class SystemUiController {

    // Various UI states in increasing order of priority
    public static final int UI_STATE_BASE_WINDOW = 0;
    public static final int UI_STATE_SCRIM_VIEW = 1;
    public static final int UI_STATE_WIDGET_BOTTOM_SHEET = 2;
    public static final int UI_STATE_FULLSCREEN_TASK = 3;
    public static final int UI_STATE_ALLAPPS = 4;

    public static final int FLAG_LIGHT_NAV = 1 << 0;
    public static final int FLAG_DARK_NAV = 1 << 1;
    public static final int FLAG_LIGHT_STATUS = 1 << 2;
    public static final int FLAG_DARK_STATUS = 1 << 3;

    private final View mWindow;

    public SystemUiController(View v) {
        mWindow = v;
    }

    public void updateUiState(int uiState, int flags) {
        WindowInsetsController controller = mWindow.getWindowInsetsController();
        if (controller == null) return;
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
        if ((flags & FLAG_LIGHT_STATUS) != 0) {
            controller.setSystemBarsAppearance(mask, mask);
        } else if ((flags & FLAG_DARK_STATUS) != 0) {
            controller.setSystemBarsAppearance(0, mask);
        }
    }
}
