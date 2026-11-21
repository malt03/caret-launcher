/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.CellLayout;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.graphics.PathWrapper;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.views.ClipPathView;

/**
 * Stand-in for Launcher3's FolderPagedView: the folder's content. TextLauncher's folders have a
 * single page, so the children are placed directly by their CellLayoutLayoutParams (x, y, width,
 * height, as CellLayout sets them up). Clip handling and the keyboard focus outline are copied
 * from FolderPagedView.java.
 */
public class FolderPagedView extends ViewGroup implements ClipPathView {
    private final ViewGroupFocusHelper mFocusIndicatorHelper;
    private PathWrapper mClipPath;

    public FolderPagedView(Context context) {
        super(context);
        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
    }

    /**
     * TextLauncher: FolderPagedView.createNewView sets this as each icon's focus listener; the
     * icons are made by the app's OpenFolder.
     */
    public ViewGroupFocusHelper getFocusIndicatorHelper() {
        return mFocusIndicatorHelper;
    }

    public int getCurrentPage() {
        return 0;
    }

    public int getDestinationPage() {
        return 0;
    }

    public int getPageCount() {
        return 1;
    }

    /** No CellLayout pages: the children are laid out here directly. */
    public CellLayout getPageAt(int index) {
        return null;
    }

    public CellLayout getCurrentCellLayout() {
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            child.measure(MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            int left = getPaddingLeft() + lp.x;
            int top = getPaddingTop() + lp.y;
            child.layout(left, top, left + lp.width, top + lp.height);
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mClipPath != null) {
            int count = canvas.save();
            canvas.clipPath(mClipPath.getPath());
            mFocusIndicatorHelper.draw(canvas);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(count);
        } else {
            mFocusIndicatorHelper.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }

    @Override
    public void setClipPath(PathWrapper clipPath) {
        mClipPath = clipPath;
        invalidate();
    }
}
