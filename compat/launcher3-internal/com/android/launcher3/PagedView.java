/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Stand-in for Launcher3's PagedView (android-17.0.0_r1) for a view that has one page: the open
 * folder's content (folder/FolderPagedView.java), whose folders have one page in TextLauncher.
 * It shows its first page inside its padding and does not scroll, so it is never in a page
 * transition and every page's scroll is 0 (keyboard/ViewGroupFocusHelper.java checks both). The
 * home grid scrolls vertically and is not a PagedView.
 */
public abstract class PagedView<T> extends ViewGroup {
    public PagedView(Context context) {
        this(context, null);
    }

    public PagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** PagedView.getCurrentPage: always the first page. */
    public int getCurrentPage() {
        return 0;
    }

    /** PagedView.getNextPage: the page shown next, always the first page. */
    public int getNextPage() {
        return 0;
    }

    public int getPageCount() {
        return getChildCount();
    }

    public View getPageAt(int index) {
        return getChildAt(index);
    }

    /** PagedView.setCurrentPage: there is only the first page to show. */
    public void setCurrentPage(int currentPage) { }

    /** PagedView.isPageInTransition: a single page never moves. */
    public boolean isPageInTransition() {
        return false;
    }

    /** PagedView.getScrollForPage: no page is scrolled to. */
    public int getScrollForPage(int index) {
        return 0;
    }

    /** PagedView.getDestinationPage: always the first page. */
    public int getDestinationPage() {
        return 0;
    }

    /**
     * PagedView.onMeasure: the pages get this view's size inside its padding (measureChildren with
     * MATCH_PARENT pages); there are no insets or zoomed-out sizes.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        measureChildren(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
        setMeasuredDimension(widthSize, heightSize);
    }

    /** PagedView.onLayout for the first page, which is not scrolled: inside the padding. */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int childLeft = getPaddingLeft();
            int childTop = getPaddingTop();
            child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(),
                    childTop + child.getMeasuredHeight());
        }
    }

    /** PagedView.generateDefaultLayoutParams: the pages fill the view. */
    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }
}
