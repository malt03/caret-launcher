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

package com.android.launcher3.folder;

import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;
import static com.android.launcher3.model.data.FolderInfo.willAcceptItemType;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.app.animation.Interpolators;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Flags;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Stand-in for Launcher3's FolderIcon (android-17.0.0_r1): the folder on the home screen.
 * TextLauncher's folder icon view extends this. The preview is drawn by the copied
 * PreviewItemManager; the members below are copied from FolderIcon.java, changes are marked with
 * "TextLauncher:".
 *
 * TextLauncher: folders have no names and no notification dots (docs/spec.md 5.2), and no
 * {@link Folder} view is bound to the icon: the open folder is built each time it opens. The
 * calls FolderIcon makes on its Folder go to {@link FolderContent}.
 */
public abstract class FolderIcon extends View implements DraggableView, Reorderable {
    protected ActivityContext mActivity;
    protected FolderInfo mInfo = new FolderInfo();

    static final int DROP_IN_ANIMATION_DURATION = 400;

    PreviewBackground mBackground = new PreviewBackground(getContext());
    private boolean mBackgroundIsVisible = true;

    FolderGridOrganizer mPreviewVerifier;
    final ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private final PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private final List<ItemInfo> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private float mScaleForReorderBounce = 1f;

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);

    private final FolderContent mFolder = new FolderContent();

    // TextLauncher: FolderIcon(Context) and inflateIcon in one.
    public FolderIcon(Context context, ActivityContext activity) {
        super(context);
        mActivity = activity;
        mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        mPreviewItemManager = new PreviewItemManager(this);
        mPreviewVerifier = createFolderGridOrganizer(activity.getDeviceProfile());
        mPreviewVerifier.setFolderInfo(mInfo);
        updatePreviewItems(false);
    }

    public FolderInfo getFolderInfo() {
        return mInfo;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean shouldCenterIcon = mActivity.getDeviceProfile().getWorkspaceProfile()
                .getIconCenterVertically();
        if (shouldCenterIcon) {
            int iconSize = mActivity.getDeviceProfile().getWorkspaceProfile().getIconSizePx();
            // TextLauncher: no folder name, so only the icon is centered.
            int cellHeightPx = iconSize;
            setPadding(getPaddingLeft(), (MeasureSpec.getSize(heightMeasureSpec)
                    - cellHeightPx) / 2, getPaddingRight(), getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    /** TextLauncher: stands in for the Folder that Launcher3 binds to the icon. */
    public FolderContent getFolder() {
        return mFolder;
    }

    private boolean willAcceptItem(ItemInfo item) {
        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    /**
     * TextLauncher: the folder does not open while an item is held over it (SPRING_LOADING_ENABLED
     * in Launcher3), because the open folder is not a drop target here.
     */
    public void onDragEnter(ItemInfo dragInfo, CellLayout cl) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();

        mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
    }

    public Drawable prepareCreateAnimation(final View destView) {
        return mPreviewItemManager.prepareCreateAnimation(destView);
    }

    public void performCreateAnimation(final ItemInfo destInfo, final View destView,
            final ItemInfo srcInfo, final DragObject d, Rect dstRect,
            float scaleRelativeToDragLayer) {
        prepareCreateAnimation(destView);
        getFolder().addFolderContent(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                .start();

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, d, dstRect, scaleRelativeToDragLayer, 1,
                false /* itemReturnedOnFailedDrop */);
    }

    public void performDestroyAnimation(Runnable onCompleteRunnable) {
        // This will animate the final item in the preview to be full size.
        mPreviewItemManager.createFirstItemAnimation(true /* reverse */, onCompleteRunnable)
                .start();
    }

    public void onDragExit() {
        mBackground.animateToRest();
    }

    private void onDrop(final ItemInfo item, DragObject d, Rect finalRect,
            float scaleRelativeToDragLayer, int index, boolean itemReturnedOnFailedDrop) {
        item.cellX = -1;
        item.cellY = -1;
        DragView animateView = d.dragView;
        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        // TextLauncher: the activity context is not a Launcher; its drag layer is the DragLayer.
        if (animateView != null) {
            DragLayer dragLayer = (DragLayer) mActivity.getDragLayer();
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                // TextLauncher: the home grid has no transition transform to set.
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
            }

            int numItemsInPreview = Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index + 1);
            boolean itemAdded = false;
            if (itemReturnedOnFailedDrop || index >= MAX_NUM_ITEMS_IN_PREVIEW) {
                List<ItemInfo> oldPreviewItems = new ArrayList<>(mCurrentPreviewItems);
                getFolder().addFolderContent(item, index, false);
                mCurrentPreviewItems.clear();
                mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));

                if (!oldPreviewItems.equals(mCurrentPreviewItems)) {
                    int newIndex = mCurrentPreviewItems.indexOf(item);
                    if (newIndex >= 0) {
                        // If the item dropped is going to be in the preview, we update the
                        // index here to reflect its position in the preview.
                        index = newIndex;
                    }

                    mPreviewItemManager.hidePreviewItem(index, true);
                    mPreviewItemManager.onDrop(oldPreviewItems, mCurrentPreviewItems, item);
                    itemAdded = true;
                } else {
                    getFolder().removeFolderContent(false, item);
                }
            }

            if (!itemAdded) {
                getFolder().addFolderContent(item, index, true);
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, numItemsInPreview, center);
            center[0] = Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < MAX_NUM_ITEMS_IN_PREVIEW ? 1f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;

            // TextLauncher: search results use the home icon size, so there is no container
            // scale for drags from them.

            final int finalIndex = index;
            dragLayer.animateView(animateView, to, finalAlpha,
                    finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    Interpolators.DECELERATE_2,
                    () -> {
                        mPreviewItemManager.hidePreviewItem(finalIndex, false);
                        mFolder.showItem(item);
                    },
                    DragLayer.ANIMATION_END_DISAPPEAR, null);

            mFolder.hideItem(item);

            if (!itemAdded) mPreviewItemManager.hidePreviewItem(index, true);
            // TextLauncher: no folder name suggestions.
        } else {
            getFolder().addFolderContent(item);
        }
    }

    public void onDrop(DragObject d, boolean itemReturnedOnFailedDrop) {
        // TextLauncher: every drag carries an app's WorkspaceItemInfo; no copy is needed.
        ItemInfo item = d.dragInfo;
        mFolder.notifyDrop();
        onDrop(item, d, null, 1.0f,
                itemReturnedOnFailedDrop ? item.rank : mInfo.getContents().size(),
                itemReturnedOnFailedDrop
        );
    }

    public ClippedFolderIconLayoutRule getLayoutRule() {
        if (Flags.enableExpressiveFolderExpansion() && mPreviewLayoutRule.getIconSize() == 0) {
            // Make sure the layout rule is initialized
            mPreviewItemManager.recomputePreviewDrawingParams();
        }
        return mPreviewLayoutRule;
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index), curNumItems, mTmpParams);

        mTmpParams.transX += mBackground.basePreviewOffsetX;
        mTmpParams.transY += mBackground.basePreviewOffsetY;

        float intrinsicIconSize = mPreviewItemManager.getIntrinsicIconSize();
        float offsetX = mTmpParams.transX + (mTmpParams.scale * intrinsicIconSize) / 2;
        float offsetY = mTmpParams.transY + (mTmpParams.scale * intrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mTmpParams.scale;
    }

    public void setFolderBackground(PreviewBackground bg) {
        mBackground = bg;
        mBackground.setInvalidateDelegate(this);
    }

    public void setIconVisible(boolean visible) {
        mBackgroundIsVisible = visible;
        invalidate();
    }

    public boolean getIconVisible() {
        return mBackgroundIsVisible;
    }

    public PreviewBackground getFolderBackground() {
        return mBackground;
    }

    public PreviewItemManager getPreviewItemManager() {
        return mPreviewItemManager;
    }

    public void getPreviewBounds(Rect outBounds) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getBounds(outBounds);
        // The preview items go outside of the bounds of the background.
        Utilities.scaleRectAboutCenter(outBounds, ICON_OVERLAP_FACTOR);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // TextLauncher: no selection highlight.
        super.dispatchDraw(canvas);

        if (!mBackgroundIsVisible) return;

        mPreviewItemManager.recomputePreviewDrawingParams();

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackground(canvas);
        }

        if (mCurrentPreviewItems.isEmpty() && !mAnimating) return;

        mPreviewItemManager.draw(canvas);

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackgroundStroke(canvas);
        }
        // TextLauncher: no notification dots.
    }

    /**
     * Returns the list of items which should be visible in the preview
     */
    public List<ItemInfo> getPreviewItemsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mInfo).previewItemsForPage(page, mInfo.getContents());
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || super.verifyDrawable(who);
    }

    private void updatePreviewItems(boolean animate) {
        mPreviewItemManager.updatePreviewItems(animate);
        mCurrentPreviewItems.clear();
        mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
    }

    /**
     * Updates the preview items which match the provided condition
     */
    public void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        mPreviewItemManager.updatePreviewItems(itemCheck);
    }

    public void onItemsChanged(boolean animate) {
        updatePreviewItems(false);
        updatePreviewItems(animate);
        invalidate();
        requestLayout();
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getPreviewBounds(bounds);
    }

    /**
     * TextLauncher: called when an item was added to the folder's contents, after the icon has
     * updated. The app saves its home model here.
     */
    protected void onContentAdded(ItemInfo item, int rank) { }

    /** TextLauncher: called when items were removed from the folder's contents. */
    protected void onContentRemoved(List<ItemInfo> items) { }

    /**
     * TextLauncher: Folder.notifyDrop. The app's folder drag source learns here that its item was
     * dropped back onto this icon (Folder.mItemAddedBackToSelfViaIcon).
     */
    protected void onNotifyDrop() { }

    /**
     * TextLauncher: the calls FolderIcon makes on its Folder. Folder.addFolderContent and
     * removeFolderContent are copied without the database and the open folder's views.
     */
    public class FolderContent {
        private boolean mIsOpen = false;

        public boolean isDestroyed() {
            return false;
        }

        public boolean isOpen() {
            return mIsOpen;
        }

        /** TextLauncher: set by the app's open folder. */
        public void setOpen(boolean open) {
            mIsOpen = open;
        }

        /** Folder.hideItem hides the item's view in the open folder; the folder is closed here. */
        public void hideItem(ItemInfo info) { }

        public void showItem(ItemInfo info) { }

        /** Folder.notifyDrop: an item was dropped on the icon. */
        public void notifyDrop() {
            onNotifyDrop();
        }

        public void addFolderContent(ItemInfo item) {
            addFolderContent(item, mInfo.getContents().size(), true);
        }

        /** Add an app or shortcut for a specified rank */
        public void addFolderContent(ItemInfo item, int rank, boolean animate) {
            if (!willAcceptItemType(item.itemType)) {
                throw new RuntimeException("tried to add an illegal type into a folder");
            }

            rank = (int) Utilities.boundToRange(rank, 0, mInfo.getContents().size());
            mInfo.getContents().add(rank, item);
            updateRanks();

            onItemsChanged(animate);
            onContentAdded(item, rank);
        }

        /** TextLauncher: Folder.updateItemLocationsInDatabaseBatch's ranks, without the DB. */
        private void updateRanks() {
            List<ItemInfo> contents = mInfo.getContents();
            for (int i = 0; i < contents.size(); i++) contents.get(i).rank = i;
        }

        /** Remove all matching app or shortcut. Does not change the DB. */
        public void removeFolderContent(boolean animate, ItemInfo... items) {
            List<ItemInfo> itemArray = Arrays.asList(items);
            mInfo.getContents().removeAll(itemArray);
            updateRanks();

            onItemsChanged(animate);
            onContentRemoved(itemArray);
        }
    }
}
