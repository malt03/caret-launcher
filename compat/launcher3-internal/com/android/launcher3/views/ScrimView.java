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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;

/** Stand-in for Launcher3's ScrimView: a full-size view behind floating views such as folders. */
public class ScrimView extends View {
    public ScrimView(Context context) {
        super(context);
    }

    /**
     * Set foreground and background color to this ScrimView
     */
    public void setScrimColors(ScrimColors scrimColors) {
        this.setBackgroundColor(scrimColors.getBackgroundColor());
        setForeground(new ColorDrawable(scrimColors.getForegroundColor()));
    }

    /**
     * returns foreground and background color of this ScrimView
     */
    public ScrimColors getScrimColors() {
        int backgroundColor;
        if (getBackground() instanceof ColorDrawable colorDrawable) {
            backgroundColor = colorDrawable.getColor();
        } else {
            backgroundColor = Color.TRANSPARENT;
        }

        int foregroundColor;
        if (getForeground() instanceof ColorDrawable colorDrawable) {
            foregroundColor = colorDrawable.getColor();
        } else {
            foregroundColor = Color.TRANSPARENT;
        }

        return new ScrimColors(backgroundColor, foregroundColor);
    }
}
