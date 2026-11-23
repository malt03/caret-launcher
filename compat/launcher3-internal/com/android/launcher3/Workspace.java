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
import android.graphics.Rect;
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
     * Estimates the size of an item using spans: hSpan, vSpan.
     *
     * @return MAX_VALUE for each dimension if unsuccessful.
     */
    public int[] estimateItemSize(ItemInfo itemInfo) {
        int[] size = new int[2];
        // TextLauncher: the home grid's CellLayout, which is not a child of the workspace.
        CellLayout cl = getFirstCellLayout();
        if (cl != null) {
            // Use the first page to estimate the child position
            // TextLauncher: no widget scale (DeviceProfile.getAppWidgetScale is 1 but in the
            // launcher preview).
            Rect r = estimateItemPosition(cl, 0, 0, itemInfo.spanX, itemInfo.spanY);
            size[0] = r.width();
            size[1] = r.height();
            return size;
        } else {
            size[0] = Integer.MAX_VALUE;
            size[1] = Integer.MAX_VALUE;
            return size;
        }
    }

    public Rect estimateItemPosition(CellLayout cl, int hCell, int vCell, int hSpan, int vSpan) {
        Rect r = new Rect();
        cl.cellToRect(hCell, vCell, hSpan, vSpan, r);
        return r;
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

    /** TextLauncher: the home grid's one CellLayout (Launcher3's first page), or null. */
    @Nullable
    protected abstract CellLayout getFirstCellLayout();
}
