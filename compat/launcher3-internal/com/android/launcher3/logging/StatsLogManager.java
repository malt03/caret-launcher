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
        LAUNCHER_TAP_TO_ADD_DEEP_SHORTCUT(2446);

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
    }

    public StatsLogger logger() {
        return new StatsLogger() { };
    }
}
