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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.graphics.PathWrapper;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ClipPathView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stand-in for Launcher3's FolderPagedView (android-17.0.0_r1): the folder's content. Binding,
 * creating and arranging the item views, the clip handling and the keyboard focus outline are
 * copied from FolderPagedView.java. TextLauncher's folders have a single page, so there are no
 * CellLayout pages: the children are laid out here directly by their CellLayoutLayoutParams.
 */
public class FolderPagedView extends ViewGroup implements ClipPathView {
    private final ViewGroupFocusHelper mFocusIndicatorHelper;
    private PathWrapper mClipPath;

    private Folder mFolder;
    private FolderGridOrganizer mOrganizer;

    // If the views are attached to the folder or not. A folder should be bound when its
    // animating or is open.
    private boolean mViewsBound = false;

    private int mGridCountX;
    private int mGridCountY;

    public FolderPagedView(Context context) {
        super(context);
        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
        mOrganizer = createFolderGridOrganizer(folder.mActivityContext.getDeviceProfile());
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     */
    private void setupContentDimensions(int count) {
        mOrganizer.setContentSize(count);
        mGridCountX = mOrganizer.getCountX();
        mGridCountY = mOrganizer.getCountY();
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
            getChildAt(i).setVisibility(View.VISIBLE);
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

    /** TextLauncher: no view cache; the folder makes its icon views (R.layout.folder_application). */
    public View createNewView(ItemInfo item) {
        if (item == null) {
            return null;
        }

        final View icon = mFolder.inflateIcon();
        ((BubbleTextView) icon).applyFromWorkspaceItem((WorkspaceItemInfo) item);

        icon.setOnClickListener(mFolder.mActivityContext.getItemOnClickListener());
        icon.setOnLongClickListener(mFolder);
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

    /**
     * Updates position and rank of all the children in the view.
     * TextLauncher: one page, so every view is placed in this view.
     */
    public void arrangeChildren(List<View> list) {
        int itemCount = list.size();
        removeAllViews();
        mOrganizer.setFolderInfo(mFolder.getInfo());
        setupContentDimensions(itemCount);

        DeviceProfile.FolderProfile profile =
                mFolder.mActivityContext.getDeviceProfile().getFolderProfile();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int rank = 0; rank < itemCount; rank++) {
            View v = list.get(rank);
            if (v == null) continue;
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
            lp.setCellXY(mOrganizer.getPosForRank(rank));
            // TextLauncher: CellLayout.addViewToCellLayout sets up the position in its page.
            lp.setup(profile.getCellWidthPx(), profile.getCellHeightPx(), rtl, mGridCountX,
                    mGridCountY, new Point(0, 0));
            addView(v, lp);
        }
    }

    /** TextLauncher: removing an item when no drag is in progress rearranges the rest. */
    public void removeItem(View v) {
        if (v == null) {
            return;
        }
        if (mFolder.getIsDragInProgress()) {
            // A drag is in progress, so we shouldn't immediately reshuffle the folder.
            removeView(v);
        } else {
            // This is a permanent removal, so rearrange the items immediately.
            ArrayList<View> views = new ArrayList<>(mFolder.getIconsInReadingOrder());
            views.remove(v);
            arrangeChildren(views);
        }
    }

    public int getDesiredWidth() {
        return mGridCountX * mFolder.mActivityContext.getDeviceProfile().getFolderProfile()
                .getCellWidthPx() + getPaddingLeft() + getPaddingRight();
    }

    public int getDesiredHeight()  {
        return mGridCountY * mFolder.mActivityContext.getDeviceProfile().getFolderProfile()
                .getCellHeightPx() + getPaddingTop() + getPaddingBottom();
    }

    public View getFirstItem() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    /**
     * Iterates over all its items in a reading order.
     * @return the view for which the operator returned true.
     */
    public View iterateOverItems(ItemOperator op) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (op.evaluate((ItemInfo) v.getTag(), v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Sets the focus on the first visible child.
     */
    public void setFocusOnFirstChild() {
        View firstChild = getFirstItem();
        if (firstChild == null) {
            return;
        }
        firstChild.requestFocus();
    }

    /**
     * TextLauncher: FolderPagedView.createNewView sets this as each icon's focus listener.
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
