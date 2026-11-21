/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;

import java.util.function.Predicate;

/**
 * Stand-in for Launcher3's Workspace (android-17.0.0_r1). In TextLauncher it holds the home grid.
 * Members are copied from Workspace.java as copied code needs them.
 */
public abstract class Workspace<T> extends FrameLayout {
    public static final int REORDER_TIMEOUT = 650;

    public Workspace(Context context) {
        super(context);
    }

    /** TextLauncher: no state transitions between pages and overview, so never switching. */
    public boolean isFinishedSwitchingState() {
        return true;
    }

    /**
     * Removes items that match the {@param matcher}. When applications are removed
     * as a part of an update, this is called to ensure that other widgets and application
     * shortcuts are not removed.
     *
     * TextLauncher: the home screen removes them from its model and its grid.
     */
    public abstract void persistRemoveItemsByMatcher(Predicate<ItemInfo> matcher,
            @Nullable final String reason);

    public void setPivotToScaleWithSelf(View sibling) {
        sibling.setPivotY(getPivotY() + getTop()
                - sibling.getTop() - sibling.getTranslationY());
        sibling.setPivotX(getPivotX() + getLeft()
                - sibling.getLeft() - sibling.getTranslationX());
    }
}
