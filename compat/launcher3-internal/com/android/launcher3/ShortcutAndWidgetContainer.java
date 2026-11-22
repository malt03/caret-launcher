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
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

/**
 * Stand-in for Launcher3's ShortcutAndWidgetContainer (android-17.0.0_r1): the view that holds
 * and lays out the items of a {@link CellLayout}. Members are copied from
 * ShortcutAndWidgetContainer.java for icons and folders only (no widgets, no QSB, no translation
 * provider, no wallpaper drop command). TextLauncher's home grid extends this.
 */
public abstract class ShortcutAndWidgetContainer extends ViewGroup {

    private int mCellWidth;
    private int mCellHeight;
    private Point mBorderSpace = new Point();

    private int mCountX;
    private int mCountY;

    private boolean mInvertIfRtl = false;

    public ShortcutAndWidgetContainer(Context context) {
        super(context);
    }

    public void setCellDimensions(int cellWidth, int cellHeight, int countX, int countY,
            Point borderSpace) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mCountX = countX;
        mCountY = countY;
        mBorderSpace = borderSpace;
    }

    public View getChildAt(int cellX, int cellY) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();

            if ((lp.getCellX() <= cellX) && (cellX < lp.getCellX() + lp.cellHSpan)
                    && (lp.getCellY() <= cellY) && (cellY < lp.getCellY() + lp.cellVSpan)) {
                return child;
            }
        }
        return null;
    }

    public void setupLp(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                mBorderSpace);
    }

    // Set whether or not to invert the layout horizontally if the layout is in RTL mode.
    public void setInvertIfRtl(boolean invert) {
        mInvertIfRtl = invert;
    }

    /**
     * TextLauncher: the icons center themselves in their cells (iconCenterVertically), so no cell
     * padding is set on them. Widgets are not scaled (DeviceProfile.getAppWidgetScale is 1 outside
     * multi-window), and get the workspace's widget padding ([getWidgetPadding]).
     */
    public void measureChild(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (child instanceof LauncherAppWidgetHostView) {
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    1f, 1f, mBorderSpace, getWidgetPadding(getContext(), mBorderSpace));
        } else {
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    mBorderSpace);
        }
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        child.measure(childWidthMeasureSpec, childheightMeasureSpec);
    }

    /**
     * WorkspaceProfile's widgetPadding: when the cells have less than MIN_WIDGET_PADDING_DP
     * between them, the widgets keep that much space from each other.
     */
    public static Rect getWidgetPadding(Context context, Point borderSpace) {
        float minSpacing = ResourceUtils.pxFromDp(MIN_WIDGET_PADDING_DP,
                context.getResources().getDisplayMetrics());
        Rect widgetPadding = new Rect();
        if (borderSpace.x < minSpacing || borderSpace.y < minSpacing) {
            widgetPadding.right = Math.round(Math.max(0f, minSpacing - borderSpace.x));
            widgetPadding.left = widgetPadding.right;
            widgetPadding.bottom = Math.round(Math.max(0f, minSpacing - borderSpace.y));
            widgetPadding.top = widgetPadding.bottom;
        }
        return widgetPadding;
    }

    /** WorkspaceProfile.MIN_WIDGET_PADDING_DP */
    private static final float MIN_WIDGET_PADDING_DP = 6f;

    public boolean invertLayoutHorizontally() {
        return mInvertIfRtl && Utilities.isRtl(getResources());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                layoutChild(child);
            }
        }
    }

    /**
     * Core logic to layout a child for this ViewGroup.
     */
    public void layoutChild(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        int childLeft = lp.x;
        int childTop = lp.y;
        child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (child != null) {
            Rect r = new Rect();
            child.getDrawingRect(r);
            requestRectangleOnScreen(r);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }
}
