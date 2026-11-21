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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.FastScrollRecyclerView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 *
 * <p>Stand-in made from Launcher3's AllAppsRecyclerView (android-17.0.0_r1) by deleting the
 * logging, the cursor-driven workflows (Flags.enableCursorDrivenWorkflows, disabled in the
 * release) and the letter list (Flags.letterFastScroller, disabled in the release). Changes are
 * marked with "TextLauncher:".
 */
public class AllAppsRecyclerView extends FastScrollRecyclerView {
    protected static final String TAG = "AllAppsRecyclerView";
    private static final boolean DEBUG = false;
    // TextLauncher: no search latency logging.
    private Consumer<View> mChildAttachedConsumer;

    protected final int mNumAppsPerRow;
    private final AllAppsFastScrollHelper mFastScrollHelper;
    // TextLauncher: no scroll logging or letter list.

    protected AlphabeticalAppsList mApps;

    public AllAppsRecyclerView(Context context) {
        this(context, null);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
        // TextLauncher: the columns of the all apps grid (InvariantDeviceProfile.numColumns).
        mNumAppsPerRow = ActivityContext.lookupContext(context).getDeviceProfile()
                .getAllAppsProfile().getNumShownAllAppsColumns();
        mFastScrollHelper = new AllAppsFastScrollHelper(this);
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
    }

    public AlphabeticalAppsList getApps() {
        return mApps;
    }

    protected void updatePoolSize() {
        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_ALL_APPS_DIVIDER, 1);
    }

    @Override
    public void onDraw(Canvas c) {
        if (DEBUG) {
            Log.d(TAG, "onDraw at = " + System.currentTimeMillis());
        }
        super.onDraw(c);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updatePoolSize();
    }

    public void onSearchResultsChanged() {
        // Always scroll the view to the top so the user can see the changed results
        scrollToTop();
    }

    // TextLauncher: no cursor-driven workflows (mouse clicks and drags are not ignored).

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);

        // TextLauncher: no logging.
        switch (state) {
            case SCROLL_STATE_DRAGGING:
                requestFocus();
                ActivityContext.lookupContext(getContext()).hideKeyboard();
                break;
        }
    }

    @Override
    public boolean canScrollVertically(int direction) {
        // direction > 0 checks for scrolling down. Overriding this helps resolve issues where
        // unmeasured items at the bottom are given a size of 0, underestimating the scroll range.
        if (direction > 0) {
            RecyclerView.LayoutManager layoutManager = getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
                int lastVisiblePosition =
                        linearLayoutManager.findLastCompletelyVisibleItemPosition();
                RecyclerView.Adapter<?> adapter = getAdapter();
                if (lastVisiblePosition != NO_POSITION && adapter != null) {
                    int targetCountLimit = adapter.getItemCount() - 1;
                    // If the last item is the unmeasurable decorative footer, ignore it so we don't
                    // get false positives when we are at the app list's bottom.
                    if (targetCountLimit >= 0 && adapter.getItemViewType(targetCountLimit)
                            == BaseAllAppsAdapter.VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO) {
                        targetCountLimit--;
                    }
                    if (lastVisiblePosition < targetCountLimit) {
                        return true; // There are more apps in the adapter, so we can scroll down.
                    }
                }
            }
        }
        return super.canScrollVertically(direction);
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public CharSequence scrollToPositionAtProgress(float touchFraction) {
        int rowCount = mApps.getNumAppRows();
        if (rowCount == 0) {
            return "";
        }

        // Find the fastscroll section that maps to this touch fraction
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        int count = fastScrollSections.size();
        if (count == 0) {
            return "";
        }
        int index = Utilities.boundToRange((int) (touchFraction * count), 0, count - 1);
        AlphabeticalAppsList.FastScrollSectionInfo section = fastScrollSections.get(index);
        mFastScrollHelper.smoothScrollToSection(section);
        return section.sectionName;
    }

    @Override
    public void onFastScrollCompleted() {
        super.onFastScrollCompleted();
        mFastScrollHelper.onFastScrollCompleted();
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
        return true;
    }

    @Override
    protected int getTopPaddingOffset() {
        return -getPaddingTop();
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        if (mApps == null) {
            return;
        }
        List<AllAppsGridAdapter.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0 || getChildCount() == 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        int scrollY = computeVerticalScrollOffset();
        if (scrollY < 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // TextLauncher: no letter list.
        // Only show the scrollbar if there is height to be scrolled
        int availableScrollBarHeight = getAvailableScrollBarHeight();
        int availableScrollHeight = getAvailableScrollHeight();
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        if (mScrollbar.isThumbDetached()) {
            if (!mScrollbar.isDraggingThumb()) {
                // Calculate the current scroll position, the scrollY of the recycler view accounts
                // for the view padding, while the scrollBarY is drawn right up to the background
                // padding (ignoring padding)
                int scrollBarY = (int)
                        (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

                int thumbScrollY = mScrollbar.getThumbOffsetY();
                int diffScrollY = scrollBarY - thumbScrollY;
                if (diffScrollY * dy > 0f) {
                    // User is scrolling in the same direction the thumb needs to catch up to the
                    // current scroll position.  We do this by mapping the difference in movement
                    // from the original scroll bar position to the difference in movement necessary
                    // in the detached thumb position to ensure that both speed towards the same
                    // position at either end of the list.
                    if (dy < 0) {
                        int offset = (int) ((dy * thumbScrollY) / (float) scrollBarY);
                        thumbScrollY += Math.max(offset, diffScrollY);
                    } else {
                        int offset = (int) ((dy * (availableScrollBarHeight - thumbScrollY)) /
                                (float) (availableScrollBarHeight - scrollBarY));
                        thumbScrollY += Math.min(offset, diffScrollY);
                    }
                    thumbScrollY = Math.max(0, Math.min(availableScrollBarHeight, thumbScrollY));
                    mScrollbar.setThumbOffsetY(thumbScrollY);
                    if (scrollBarY == thumbScrollY) {
                        mScrollbar.reattachThumbToScroll();
                    }
                } else {
                    // User is scrolling in an opposite direction to the direction that the thumb
                    // needs to catch up to the scroll position.  Do nothing except for updating
                    // the scroll bar x to match the thumb width.
                    mScrollbar.setThumbOffsetY(thumbScrollY);
                }
            }
        } else {
            synchronizeScrollBarThumbOffsetToViewScroll(scrollY, availableScrollHeight);
        }
    }

    /**
     * This will be called just before a new child is attached to the window. Passing in null will
     * remove the consumer.
     */
    protected void setChildAttachedConsumer(@Nullable Consumer<View> childAttachedConsumer) {
        mChildAttachedConsumer = childAttachedConsumer;
    }

    @Override
    public void onChildAttachedToWindow(@NonNull View child) {
        if (mChildAttachedConsumer != null) {
            mChildAttachedConsumer.accept(child);
        }
        super.onChildAttachedToWindow(child);
    }

    @Override
    public int getScrollBarTop() {
        return getResources().getDimensionPixelOffset(R.dimen.all_apps_header_top_padding);
    }

    @Override
    public int getScrollBarMarginBottom() {
        return getRootWindowInsets() == null ? 0
                : getRootWindowInsets().getSystemWindowInsetBottom();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // TextLauncher: no letter list or scroll logging.

}
