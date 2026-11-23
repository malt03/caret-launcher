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

package com.android.launcher3.util

import android.content.Context
import android.view.WindowManager

/**
 * Stand-in for Launcher3's WindowBlurState (android-17.0.0_r1): whether windows can be blurred now.
 * TextLauncher: read once from WindowManager.isCrossWindowBlurEnabled.
 */
class WindowBlurState private constructor(context: Context) {
    val value: Boolean =
        context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled

    companion object {
        @JvmStatic fun getInstance(context: Context) = WindowBlurState(context)
    }
}
