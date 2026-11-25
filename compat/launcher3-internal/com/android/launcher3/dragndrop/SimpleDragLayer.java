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

package com.android.launcher3.dragndrop;

import android.content.Context;
import android.util.AttributeSet;

import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

/**
 * Stand-in for Launcher3's SimpleDragLayer (android-17.0.0_r1): the root of an activity that is
 * not the launcher, here the widget picker. TextLauncher: it has no touch controllers.
 */
public class SimpleDragLayer<T extends Context & ActivityContext> extends BaseDragLayer<T> {

    public SimpleDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void recreateControllers() {
        super.recreateControllers();
        mControllers = new TouchController[] {};
    }
}
