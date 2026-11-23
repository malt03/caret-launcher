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

package com.android.launcher3.config;

/**
 * Defines a set of flags used to control various launcher behaviors.
 * <p>
 * <p>All the flags should be defined here with appropriate default values.
 *
 * <p>Stand-in for Launcher3's FeatureFlags (android-17.0.0_r1): only IS_STUDIO_BUILD.
 */
public final class FeatureFlags {

    private FeatureFlags() { }

    /**
     * True when the build has come from Android Studio and is being used for local debugging.
     * TextLauncher: false, as in a platform build (BuildConfig.IS_STUDIO_BUILD), so the checks
     * that only debug builds of Launcher3 make are not made.
     */
    public static final boolean IS_STUDIO_BUILD = false;
}
