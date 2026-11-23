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

package com.android.launcher3.logging;

import android.content.Context;

import com.android.launcher3.model.data.ItemInfo;

/**
 * Stand-in for Launcher3's StatsLogManager (android-17.0.0_r1). TextLauncher logs nothing: the
 * logger takes the same calls as Launcher3's and drops them. Only the events copied code logs are
 * declared.
 */
public class StatsLogManager {

    /** Launcher3's event identifiers. */
    public interface EventEnum {
        int getId();
    }

    public enum LauncherEvent implements EventEnum {
        LAUNCHER_FOLDER_OPEN(551),
        LAUNCHER_TAP_TO_ADD_DEEP_SHORTCUT(2446),
        LAUNCHER_WIDGET_PICKER_OPEN_ANIMATION_BEGIN(1979),
        LAUNCHER_WIDGET_PICKER_OPEN_ANIMATION_END(1980),
        LAUNCHER_WIDGET_PICKER_APP_EXPAND_ANIMATION_BEGIN(1981),
        LAUNCHER_WIDGET_PICKER_APP_EXPAND_ANIMATION_END(1982),
        LAUNCHER_WIDGET_ADD_BUTTON_TAP(1622);

        private final int mId;

        LauncherEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    /** Builds and logs an event; TextLauncher's logs nothing. */
    public interface StatsLogger {
        default StatsLogger withItemInfo(ItemInfo itemInfo) {
            return this;
        }

        default void log(EventEnum event) {
        }

        default void sendToInteractionJankMonitor(EventEnum event, android.view.View view) {
        }
    }

    /** Returns the logger of the context; TextLauncher's logs nothing. */
    public static StatsLogManager newInstance(Context context) {
        return new StatsLogManager();
    }

    public StatsLogger logger() {
        return new StatsLogger() { };
    }
}
