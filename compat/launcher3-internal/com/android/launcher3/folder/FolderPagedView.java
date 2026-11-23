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

import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewDebug;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.graphics.PathWrapper;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ClipPathView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Stand-in for Launcher3's FolderPagedView (android-17.0.0_r1), made from
 * FolderPagedView.java by deleting what TextLauncher does not use (the page indicator and the
 * footer, page scrolling and its hints, the view cache, app pairs, accessibility page
 * descriptions); changes are marked with "TextLauncher:". TextLauncher's folders have one page:
 * the {@link PagedView} stand-in shows its first page and does not scroll. As in Launcher3, the
 * page is a {@link CellLayout} that holds its icons in a {@link ShortcutAndWidgetContainer}.
 */
// TextLauncher: no page indicator (PageIndicatorDots).
public class FolderPagedView extends PagedView<Void> implements ClipPathView {

    private static final String TAG = "FolderPagedView";

    private static final int REORDER_ANIMATION_DURATION = 230;
    private static final int START_VIEW_REORDER_DELAY = 30;
    private static final float VIEW_REORDER_DELAY_FACTOR = 0.9f;

    private static final int[] sTmpArray = new int[2];

    public final boolean mIsRtl;

    private final ViewGroupFocusHelper mFocusIndicatorHelper;

    @Thunk final ArrayMap<View, Runnable> mPendingAnimations = new ArrayMap<>();

    private final FolderGridOrganizer mOrganizer;

    private int mAllocatedContentSize;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountY;

    private Folder mFolder;

    private PathWrapper mClipPath;

    // If the views are attached to the folder or not. A folder should be bound when its
    // animating or is open.
    private boolean mViewsBound = false;

    /**
     * TextLauncher: made by the folder in code, with the activity context the folder has (the
     * context does not lead to it here).
     */
    public FolderPagedView(Context context, ActivityContext activityContext) {
        this(
                context,
                null,
                createFolderGridOrganizer(activityContext.getDeviceProfile())
        );
    }

    public FolderPagedView(
            Context context,
            AttributeSet attrs,
            FolderGridOrganizer folderGridOrganizer
    ) {
        super(context, attrs);
        // TextLauncher: no view cache, which is all the activity context was for.
        mOrganizer = folderGridOrganizer;

        mIsRtl = Utilities.isRtl(getResources());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
        setClipChildren(false);
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
        // TextLauncher: no page indicator.
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     */
    private void setupContentDimensions(int count) {
        mAllocatedContentSize = count;
        mOrganizer.setContentSize(count);
        mGridCountX = mOrganizer.getCountX();
        mGridCountY = mOrganizer.getCountY();

        // Update grid size
        for (int i = getPageCount() - 1; i >= 0; i--) {
            getPageAt(i).setGridSize(mGridCountX, mGridCountY);
        }
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

    /**
     * Binds items to the layout.
     */
    public void bindItems(List<ItemInfo> items) {
        if (mViewsBound) {
            unbindItems();
        }
        arrangeChildren(items.stream().map(this::createNewView).collect(Collectors.toList()));
        mViewsBound = true;
    }

    /**
     * Removes all the icons from the folder
     */
    public void unbindItems() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            CellLayout page = (CellLayout) getChildAt(i);
            ShortcutAndWidgetContainer container = page.getShortcutsAndWidgets();
            for (int j = container.getChildCount() - 1; j >= 0; j--) {
                View iconView = container.getChildAt(j);
                iconView.setVisibility(View.VISIBLE);
                // TextLauncher: no view cache to recycle the icon in.
            }
            page.removeAllViews();
            // TextLauncher: no view cache to recycle the page in.
        }
        removeAllViews();
        mViewsBound = false;
    }

    /**
     * Returns true if the icons are bound to the folder
     */
    public boolean areViewsBound() {
        return mViewsBound;
    }

    /**
     * Creates and adds an icon corresponding to the provided rank
     * @return the created icon
     */
    public View createAndAddViewForRank(ItemInfo item, int rank) {
        View icon = createNewView(item);
        if (!mViewsBound) {
            return icon;
        }
        ArrayList<View> views = new ArrayList<>(mFolder.getIconsInReadingOrder());
        views.add(rank, icon);
        arrangeChildren(views);
        return icon;
    }

    /**
     * Adds the {@param view} to the layout based on {@param rank} and updated the position
     * related attributes. It assumes that {@param item} is already attached to the view.
     */
    public void addViewForRank(View view, ItemInfo item, int rank) {
        int pageNo = rank / mOrganizer.getMaxItemsPerPage();

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) view.getLayoutParams();
        lp.setCellXY(mOrganizer.getPosForRank(rank));
        getPageAt(pageNo).addViewToCellLayout(view, -1, item.getViewId(), lp, true);
    }

    @SuppressLint("InflateParams")
    public View createNewView(ItemInfo item) {
        if (item == null) {
            return null;
        }

        final View icon;
        // TextLauncher: no app pairs, and no view cache: the folder makes its icon views
        // (R.layout.folder_application).
        {
            icon = mFolder.inflateIcon();
            ((BubbleTextView) icon).applyFromWorkspaceItem((WorkspaceItemInfo) item);
        }

        icon.setOnClickListener(mFolder.mActivityContext.getItemOnClickListener());
        icon.setOnLongClickListener(mFolder);
        // TextLauncher: no custom actions listener (Flags.enableCursorDrivenWorkflows is off).
        icon.setOnFocusChangeListener(mFocusIndicatorHelper);

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) icon.getLayoutParams();
        Point pos = mOrganizer.getPosForRank(item.rank);
        if (lp == null) {
            icon.setLayoutParams(new CellLayoutLayoutParams(pos.x, pos.y, 1, 1));
        } else {
            lp.setCellXY(pos);
            lp.cellHSpan = lp.cellVSpan = 1;
        }

        return icon;
    }

    @Nullable
    @Override
    public CellLayout getPageAt(int index) {
        return (CellLayout) getChildAt(index);
    }

    @Nullable
    public CellLayout getCurrentCellLayout() {
        return getPageAt(getNextPage());
    }

    private CellLayout createAndAddNewPage() {
        DeviceProfile grid = mFolder.mActivityContext.getDeviceProfile();
        // TextLauncher: made in code (R.layout.folder_page, from the view cache, in Launcher3);
        // the page holds its container as a child, as CellLayout's constructor does there.
        ShortcutAndWidgetContainer container = new ShortcutAndWidgetContainer(getContext());
        CellLayout page = new CellLayout(getContext(), container,
                grid.getFolderProfile().getChildIconSizePx());
        page.addView(container);
        page.setCellDimensions(grid.getFolderProfile().getCellWidthPx(),
                grid.getFolderProfile().getCellHeightPx());
        page.getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        page.setInvertIfRtl(true);
        page.setGridSize(mGridCountX, mGridCountY);

        addView(page, -1, generateDefaultLayoutParams());
        return page;
    }

    public void removeItem(View v) {
        if (v == null) {
            return;
        }
        if (mFolder.getIsDragInProgress()) {
            // A drag is in progress, so we shouldn't immediately reshuffle the folder.
            for (int i = getChildCount() - 1; i >= 0; i--) {
                getPageAt(i).removeView(v);
            }
        } else {
            // This is a permanent removal, so rearrange the items immediately.
            ArrayList<View> views = new ArrayList<>(mFolder.getIconsInReadingOrder());
            views.remove(v);
            arrangeChildren(views);
        }
    }

    /**
     * Updates position and rank of all the children in the view.
     * It essentially removes all views from all the pages and then adds them again in appropriate
     * page.
     *
     * @param list the ordered list of children.
     */
    @SuppressLint("RtlHardcoded")
    public void arrangeChildren(List<View> list) {
        int itemCount = list.size();
        ArrayList<CellLayout> pages = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout page = (CellLayout) getChildAt(i);
            page.removeAllViews();
            pages.add(page);
        }
        mOrganizer.setFolderInfo(mFolder.getInfo());
        setupContentDimensions(itemCount);

        Iterator<CellLayout> pageItr = pages.iterator();
        CellLayout currentPage = null;

        int position = 0;
        int rank = 0;

        for (int i = 0; i < itemCount; i++) {
            View v = list.size() > i ? list.get(i) : null;
            if (currentPage == null || position >= mOrganizer.getMaxItemsPerPage()) {
                // Next page
                if (pageItr.hasNext()) {
                    currentPage = pageItr.next();
                } else {
                    currentPage = createAndAddNewPage();
                }
                position = 0;
            }

            if (v != null) {
                CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
                ItemInfo info = (ItemInfo) v.getTag();
                lp.setCellXY(mOrganizer.getPosForRank(rank));
                currentPage.addViewToCellLayout(v, -1, info.getViewId(), lp, true);

                // TextLauncher: the home screen loads the icons of the items in folders in full
                // (AppIconLoader), so there are no low-res preview icons to verify.
            }

            rank++;
            position++;
        }

        // Remove extra views.
        boolean removed = false;
        while (pageItr.hasNext()) {
            removeView(pageItr.next());
            removed = true;
        }
        if (removed) {
            setCurrentPage(0);
        }

        // TextLauncher: no page scrolling to overscroll.

        // Update the focus chain for all icons to ensure proper keyboard navigation.
        arrangeChildrenFocus(list);

        // TextLauncher: no footer (page indicator and folder name) to update.
    }

    /**
     * Updates the next focus forward ID for each child in the folder, skipping any null views.
     */
    private void arrangeChildrenFocus(List<View> list) {
        View lastFocusView = null;
        for (View view : list) {
            if (view == null) {
                continue;
            }
            if (lastFocusView != null) {
                lastFocusView.setNextFocusForwardId(view.getId());
            }
            lastFocusView = view;
        }
    }

    public int getDesiredWidth() {
        return getPageCount() > 0 ?
                (getPageAt(0).getDesiredWidth() + getPaddingLeft() + getPaddingRight()) : 0;
    }

    public int getDesiredHeight()  {
        return  getPageCount() > 0 ?
                (getPageAt(0).getDesiredHeight() + getPaddingTop() + getPaddingBottom()) : 0;
    }

    /**
     * @return the rank of the cell nearest to the provided pixel position.
     */
    public int findNearestArea(int pixelX, int pixelY) {
        int pageIndex = getNextPage();
        CellLayout page = getPageAt(pageIndex);
        page.findNearestAreaIgnoreOccupied(pixelX, pixelY, 1, 1, sTmpArray);
        if (mFolder.isLayoutRtl()) {
            sTmpArray[0] = page.getCountX() - sTmpArray[0] - 1;
        }
        return Math.min(mAllocatedContentSize - 1,
                pageIndex * mOrganizer.getMaxItemsPerPage()
                        + sTmpArray[1] * mGridCountX + sTmpArray[0]);
    }

    public View getFirstItem() {
        return getViewInCurrentPage(c -> 0);
    }

    private View getViewInCurrentPage(ToIntFunction<ShortcutAndWidgetContainer> rankProvider) {
        if (getChildCount() < 1 || getCurrentCellLayout() == null) {
            return null;
        }
        ShortcutAndWidgetContainer container = getCurrentCellLayout().getShortcutsAndWidgets();
        int rank = rankProvider.applyAsInt(container);
        if (mGridCountX > 0) {
            return container.getChildAt(rank % mGridCountX, rank / mGridCountX);
        } else {
            return container.getChildAt(rank);
        }
    }

    /**
     * Iterates over all its items in a reading order.
     * @return the view for which the operator returned true.
     */
    public View iterateOverItems(ItemOperator op) {
        for (int k = 0 ; k < getChildCount(); k++) {
            CellLayout page = getPageAt(k);
            for (int j = 0; j < page.getCountY(); j++) {
                for (int i = 0; i < page.getCountX(); i++) {
                    View v = page.getChildAt(i, j);
                    if ((v != null) && op.evaluate((ItemInfo) v.getTag(), v)) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sets the focus on the first visible child.
     */
    public void setFocusOnFirstChild() {
        CellLayout currentCellLayout = getCurrentCellLayout();
        if (currentCellLayout == null) {
            return;
        }
        View firstChild = currentCellLayout.getChildAt(0, 0);
        if (firstChild == null) {
            return;
        }
        firstChild.requestFocus();
    }

    /**
     * Finish animation all the views which are animating across pages
     */
    public void completePendingPageChanges() {
        if (!mPendingAnimations.isEmpty()) {
            ArrayMap<View, Runnable> pendingViews = new ArrayMap<>(mPendingAnimations);
            for (Map.Entry<View, Runnable> e : pendingViews.entrySet()) {
                e.getKey().animate().cancel();
                e.getValue().run();
            }
        }
    }

    public boolean rankOnCurrentPage(int rank) {
        int p = rank / mOrganizer.getMaxItemsPerPage();
        return p == getNextPage();
    }

    /**
     * Reorders the items such that the {@param empty} spot moves to {@param target}
     */
    public void realTimeReorder(int empty, int target) {
        if (!mViewsBound) {
            return;
        }
        completePendingPageChanges();
        int delay = 0;
        float delayAmount = START_VIEW_REORDER_DELAY;

        // Animation only happens on the current page.
        int pageToAnimate = getNextPage();
        int maxItemsPerPage = mOrganizer.getMaxItemsPerPage();

        int pageT = target / maxItemsPerPage;
        int pagePosT = target % maxItemsPerPage;

        if (pageT != pageToAnimate) {
            Log.e(TAG, "Cannot animate when the target cell is invisible");
        }
        int pagePosE = empty % maxItemsPerPage;
        int pageE = empty / maxItemsPerPage;

        int startPos, endPos;
        int moveStart, moveEnd;
        int direction;

        if (target == empty) {
            // No animation
            return;
        } else if (target > empty) {
            // Items will move backwards to make room for the empty cell.
            direction = 1;

            // If empty cell is in a different page, move them instantly.
            if (pageE < pageToAnimate) {
                moveStart = empty;
                // Instantly move the first item in the current page.
                moveEnd = pageToAnimate * maxItemsPerPage;
                // Animate the 2nd item in the current page, as the first item was already moved to
                // the last page.
                startPos = 0;
            } else {
                moveStart = moveEnd = -1;
                startPos = pagePosE;
            }

            endPos = pagePosT;
        } else {
            // The items will move forward.
            direction = -1;

            if (pageE > pageToAnimate) {
                // Move the items immediately.
                moveStart = empty;
                // Instantly move the last item in the current page.
                moveEnd = (pageToAnimate + 1) * maxItemsPerPage - 1;

                // Animations start with the second last item in the page
                startPos = maxItemsPerPage - 1;
            } else {
                moveStart = moveEnd = -1;
                startPos = pagePosE;
            }

            endPos = pagePosT;
        }

        // Instant moving views.
        while (moveStart != moveEnd) {
            int rankToMove = moveStart + direction;
            int p = rankToMove / maxItemsPerPage;
            int pagePos = rankToMove % maxItemsPerPage;
            int x = pagePos % mGridCountX;
            int y = pagePos / mGridCountX;

            final CellLayout page = getPageAt(p);
            final View v = page.getChildAt(x, y);
            if (v != null) {
                if (pageToAnimate != p) {
                    page.removeView(v);
                    addViewForRank(v, (ItemInfo) v.getTag(), moveStart);
                } else {
                    // Do a fake animation before removing it.
                    final int newRank = moveStart;
                    final float oldTranslateX = v.getTranslationX();

                    Runnable endAction = new Runnable() {

                        @Override
                        public void run() {
                            mPendingAnimations.remove(v);
                            v.setTranslationX(oldTranslateX);
                            ((CellLayout) v.getParent().getParent()).removeView(v);
                            addViewForRank(v, (ItemInfo) v.getTag(), newRank);
                        }
                    };
                    v.animate()
                        .translationXBy((direction > 0 ^ mIsRtl) ? -v.getWidth() : v.getWidth())
                        .setDuration(REORDER_ANIMATION_DURATION)
                        .setStartDelay(0)
                        .withEndAction(endAction);
                    mPendingAnimations.put(v, endAction);
                }
            }
            moveStart = rankToMove;
        }

        if ((endPos - startPos) * direction <= 0) {
            // No animation
            return;
        }

        CellLayout page = getPageAt(pageToAnimate);
        for (int i = startPos; i != endPos; i += direction) {
            int nextPos = i + direction;
            View v = page.getChildAt(nextPos % mGridCountX, nextPos / mGridCountX);
            if (page.animateChildToPosition(v, i % mGridCountX, i / mGridCountX,
                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                delay += delayAmount;
                delayAmount *= VIEW_REORDER_DELAY_FACTOR;
            }
        }
    }

    public int itemsPerPage() {
        return mOrganizer.getMaxItemsPerPage();
    }

    @Override
    public void setClipPath(PathWrapper clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    // TextLauncher: members TextLauncher adds.

    /**
     * The icons show their pressed state at once, as every ancestor of TextLauncher's icons says
     * (ui/NoPressDelay.kt); a ViewGroup delays it by default.
     */
    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }
}
