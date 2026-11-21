/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.apppairs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.launcher3.BubbleTextView;

/** Stand-in for Launcher3's AppPairIcon. TextLauncher has no app pairs; never instantiated. */
public abstract class AppPairIcon extends View {
    public AppPairIcon(Context context) {
        super(context);
    }

    public abstract BubbleTextView getTitleTextView();

    /** AppPairIcon.getIconDrawableArea, for folder/PreviewItemManager. */
    public abstract IconDrawableArea getIconDrawableArea();

    /** Stand-in for AppPairIconDrawableBackground's view. */
    public interface IconDrawableArea {
        Drawable getDrawable();
    }
}
