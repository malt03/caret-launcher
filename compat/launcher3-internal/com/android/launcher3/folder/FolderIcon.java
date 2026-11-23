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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.View;
import android.view.ViewDebug;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Alarm;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.IconViewController;
import com.android.launcher3.popup.Poppable;
import com.android.launcher3.popup.PoppableType;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 *
 * <p>Stand-in for Launcher3's FolderIcon (android-17.0.0_r1), made from FolderIcon.java by
 * deleting what TextLauncher does not use (the folder name and its suggestions, touch handling,
 * floating icons, leave-behinds, accessibility titles); changes are marked
 * with "TextLauncher:". TextLauncher's folder icon view extends this. Folders have no names
 * (docs/spec.md 5.2). As in Launcher3, a {@link Folder} is bound to the icon
 * ({@link Folder#bindToIcon}).
 */
// TextLauncher: a View with no name label, which the app's folder icon view extends; no floating
// icons or custom touch handling.
public abstract class FolderIcon extends View implements DraggableView, Reorderable, Poppable,
        IconViewController {

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    @Thunk ActivityContext mActivity;
    @Thunk Folder mFolder;
    public FolderInfo mInfo;

    static final int DROP_IN_ANIMATION_DURATION = 400;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;

    PreviewBackground mBackground = new PreviewBackground(getContext());
    private boolean mBackgroundIsVisible = true;

    FolderGridOrganizer mPreviewVerifier;
    final ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private final PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private final List<ItemInfo> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private final Alarm mOpenAlarm = new Alarm(getContext().getMainLooper());

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private final FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private final DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private float mScaleForReorderBounce = 1f;

    private static final Property<FolderIcon, Float> DOT_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mDotScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mDotScale = value;
            folderIcon.invalidate();
        }
    };


    // TextLauncher: FolderIcon(Context, AttributeSet) and inflateIcon in one, since the app's view
    // extends this instead of being inflated. The app sets the click listener; there is no
    // content description, accessibility delegate or custom actions listener.
    public FolderIcon(Context context, ActivityContext activity, FolderInfo folderInfo) {
        super(context);

        mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        mPreviewItemManager = new PreviewItemManager(this);
        mDotParams = new DotRenderer.DrawParams();
        mDotParams.setDotColor(Themes.getAttrColor(context, R.attr.notificationDotColor));
        // TextLauncher: the ThemeManager stand-in gives the shape info itself (no IconState).
        mDotParams.shapeInfo = ThemeManager.INSTANCE.get(context).getIconShapeInfo();

        // TextLauncher: from inflateIcon.
        DeviceProfile grid = activity.getDeviceProfile();
        setTag(folderInfo);
        mInfo = folderInfo;
        mActivity = activity;
        mDotRenderer = new DotRenderer(
                grid.getWorkspaceProfile().getIconSizePx()
        );

        updateDotInfo();

        mPreviewVerifier = createFolderGridOrganizer(activity.getDeviceProfile());
        mPreviewVerifier.setFolderInfo(folderInfo);
        updatePreviewItems(false);
    }

    public void animateBgShadowAndStroke() {
        mBackground.fadeInBackgroundShadow();
        mBackground.animateBackgroundStroke();
    }

    public void getPreviewBounds(Rect outBounds) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getBounds(outBounds);
        // The preview items go outside of the bounds of the background.
        Utilities.scaleRectAboutCenter(outBounds, ICON_OVERLAP_FACTOR);
    }

    public Folder getFolder() {
        return mFolder;
    }

    // TextLauncher: not private; Folder.bindToIcon calls it (inflateFolderAndIcon in Launcher3).
    public void setFolder(Folder folder) {
        mFolder = folder;
    }

    private boolean willAcceptItem(ItemInfo item) {
        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    // TextLauncher: the caller passes the CellLayout (the icon's parent is the home grid, whose
    // parent is not a CellLayout).
    public void onDragEnter(ItemInfo dragInfo, CellLayout cl) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();

        mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        // TextLauncher: no WorkspaceItemFactory (AppInfo); every drag carries a WorkspaceItemInfo.
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof PendingAddShortcutInfo)
                        || Folder.willAccept(dragInfo))) {
            mOpenAlarm.setAlarm(ON_OPEN_DELAY);
        }
    }

    OnAlarmListener mOnOpenListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mFolder.beginExternalDrag();
        }
    };

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
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final ItemInfo item, DragObject d, Rect finalRect,
            float scaleRelativeToDragLayer, int index, boolean itemReturnedOnFailedDrop) {
        item.cellX = -1;
        item.cellY = -1;
        DragView animateView = d.dragView;
        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null && mActivity instanceof Launcher) {
            final Launcher launcher = (Launcher) mActivity;
            DragLayer dragLayer = launcher.getDragLayer();
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
                        // TextLauncher: draws the preview item again; nothing else redraws the
                        // icon after a failed drag returns its item (Tex-9tv6).
                        invalidate();
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

    /** Keep the notification dot up to date with the sum of all the content's dots. */
    public void updateDotInfo() {
        boolean hadDot = mDotInfo.hasDot();
        mDotInfo.reset();
        for (ItemInfo si : mInfo.getContents()) {
            mDotInfo.addDotInfo(mActivity.getDotInfoForItem(si));
        }
        boolean isDotted = mDotInfo.hasDot();
        float newDotScale = isDotted ? 1f : 0f;
        // Animate when a dot is first added or when it is removed.
        if ((hadDot ^ isDotted) && isShown()) {
            animateDotScale(newDotScale);
        } else {
            cancelDotScaleAnim();
            mDotScale = newDotScale;
            invalidate();
        }
    }

    public ClippedFolderIconLayoutRule getLayoutRule() {
        if (Flags.enableExpressiveFolderExpansion() && mPreviewLayoutRule.getIconSize() == 0) {
            // Make sure the layout rule is initialized
            mPreviewItemManager.recomputePreviewDrawingParams();
        }
        return mPreviewLayoutRule;
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    public boolean hasDot() {
        return mDotInfo != null && mDotInfo.hasDot();
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

    @Override
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

        drawDot(canvas);
    }

    public void drawDot(Canvas canvas) {
        if (!mForceHideDot && ((mDotInfo != null && mDotInfo.hasDot()) || mDotScale > 0)) {
            Rect iconBounds = mDotParams.iconBounds;
            // FolderIcon draws the icon to be top-aligned (with padding) & horizontally-centered
            int iconSize = mActivity.getDeviceProfile().getWorkspaceProfile().getIconSizePx();
            iconBounds.left = (getWidth() - iconSize) / 2;
            iconBounds.right = iconBounds.left + iconSize;
            iconBounds.top = getPaddingTop();
            iconBounds.bottom = iconBounds.top + iconSize;

            float iconScale = (float) mBackground.previewSize / iconSize;
            Utilities.scaleRectAboutCenter(iconBounds, iconScale);

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getAcceptScaleProgress());
            mDotRenderer.draw(canvas, mDotParams);
        }
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
    public int getIconHeight() {
        Rect rect = new Rect();
        getPreviewBounds(rect);
        return rect.height();
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
        updateDotInfo();
        // TextLauncher: no content description (no folder titles).
        updatePreviewItems(animate);
        invalidate();
        requestLayout();
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
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

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getPreviewBounds(bounds);
    }

    @NonNull
    @Override
    public PoppableType getPoppableType() {
        return PoppableType.FOLDER;
    }

    // TextLauncher: no folder name, so there is no text alpha to animate.
    @Nullable
    @Override
    public MultiPropertyFactory<AnimatedFloat>.MultiProperty getFloatingViewTextAlpha() {
        return null;
    }

    // TextLauncher: members TextLauncher adds.

    /** TextLauncher: the folder's info for callers outside the package (Launcher3 reads mInfo). */
    public FolderInfo getFolderInfo() {
        return mInfo;
    }
}
