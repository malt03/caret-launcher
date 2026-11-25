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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.graphics;

import android.content.Context;

import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfoWithIcon;

/**
 * Stand-in for Launcher3's PreloadIconDelegate. CaretLauncher shows installed apps only, so no
 * item is pending (ItemInfoWithIcon.shouldShowPendingIcon is always false).
 */
public class PreloadIconDelegate {
    public static FastBitmapDrawable newPendingIcon(ItemInfoWithIcon info, Context context,
            int creationFlags) {
        return info.newIcon(context, creationFlags);
    }
}
