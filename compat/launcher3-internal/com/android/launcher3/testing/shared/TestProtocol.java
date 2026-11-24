/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.testing.shared;

/**
 * Protocol for custom accessibility events for communication with UI Automation tests.
 *
 * <p>Stand-in for Launcher3's TestProtocol (android-17.0.0_r1). Only the message that
 * FastScrollRecyclerView sends and the state ordinals that LauncherState uses; TextLauncher does
 * not run Launcher3's UI Automation tests.
 */
public final class TestProtocol {
    public static final String SCROLL_FINISHED_MESSAGE = "TAPL_SCROLL_FINISHED";

    // The launcher states' ordinals, which LauncherState uses.
    public static final int NORMAL_STATE_ORDINAL = 0;
    public static final int SPRING_LOADED_STATE_ORDINAL = 1;
    public static final int OVERVIEW_STATE_ORDINAL = 2;
    public static final int OVERVIEW_MODAL_TASK_STATE_ORDINAL = 3;
    public static final int QUICK_SWITCH_STATE_ORDINAL = 4;
    public static final int ALL_APPS_STATE_ORDINAL = 5;
    public static final int BACKGROUND_APP_STATE_ORDINAL = 6;
    public static final int HINT_STATE_ORDINAL = 7;
    public static final int HINT_STATE_TWO_BUTTON_ORDINAL = 8;
    public static final int OVERVIEW_SPLIT_SELECT_ORDINAL = 9;
    public static final int EDIT_MODE_STATE_ORDINAL = 10;
    public static final int DESKTOP_DRAG_MODE_ORDINAL = 11;

    public static String stateOrdinalToString(int ordinal) {
        switch (ordinal) {
            case NORMAL_STATE_ORDINAL:
                return "Normal";
            case SPRING_LOADED_STATE_ORDINAL:
                return "SpringLoaded";
            case OVERVIEW_STATE_ORDINAL:
                return "Overview";
            case OVERVIEW_MODAL_TASK_STATE_ORDINAL:
                return "OverviewModal";
            case QUICK_SWITCH_STATE_ORDINAL:
                return "QuickSwitch";
            case ALL_APPS_STATE_ORDINAL:
                return "AllApps";
            case BACKGROUND_APP_STATE_ORDINAL:
                return "Background";
            case HINT_STATE_ORDINAL:
                return "Hint";
            case HINT_STATE_TWO_BUTTON_ORDINAL:
                return "Hint2Button";
            case OVERVIEW_SPLIT_SELECT_ORDINAL:
                return "OverviewSplitSelect";
            case EDIT_MODE_STATE_ORDINAL:
                return "EditMode";
            case DESKTOP_DRAG_MODE_ORDINAL:
                return "DesktopDragMode";
            default:
                return "Unknown";
        }
    }
}
