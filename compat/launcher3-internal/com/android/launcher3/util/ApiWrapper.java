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

import android.app.ActivityOptions;
import android.content.Context;

import com.android.launcher3.dagger.ApplicationContext;

import javax.inject.Inject;

/**
 * Stand-in for Launcher3's ApiWrapper (android-17.0.0_r1), with what the widget picker uses.
 */
public class ApiWrapper {

    private final Context mContext;

    @Inject
    public ApiWrapper(@ApplicationContext Context context) {
        mContext = context;
    }

    /**
     * Creates an ActivityOptions to play fade-out animation on closing targets
     */
    public ActivityOptions createFadeOutAnimOptions() {
        return ActivityOptions.makeCustomAnimation(mContext, 0, android.R.anim.fade_out);
    }
}
