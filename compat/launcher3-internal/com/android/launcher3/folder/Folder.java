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

import static com.android.launcher3.model.data.FolderInfo.willAcceptItemType;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.FocusFinder;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Flags;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.graphics.PathWrapper;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.ClipPathView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stand-in for Launcher3's Folder (android-17.0.0_r1): the open folder, one for each folder icon,
 * added to the drag layer while it is open. Opening and closing, the outside touch, the drag
 * source, adding and removing content, the background and clip drawing and the keyboard focus
 * handling are copied from Folder.java. Left out: the name field and its suggestions, pages,
 * dragging over the open folder to reorder it (TextLauncher closes the folder when a drag
 * starts), accessibility drags and the database writes (the icon reports content changes to the
 * app). The app makes the item views ({@link #inflateIcon}).
 */
public abstract class Folder extends AbstractFloatingView implements ClipPathView, DragSource,
        View.OnLongClickListener, DragController.DragListener {
    private static final Rect sTempRect = new Rect();

    public final ActivityContext mActivityContext;
    private final LauncherDelegate mLauncherDelegate;
    public FolderInfo mInfo;
    /** No footer and no name in TextLauncher; kept for the animation, never shown. */
    public View mFooter;
    private final View mFolderName;
    protected final FolderPagedView mContent;
    protected FolderIcon mFolderIcon;
    protected final GradientDrawable mBackground = new GradientDrawable();
    private PathWrapper mClipPath;
    /** Set while the close animation runs: the content is drawn outside the clip then. */
    private boolean mIsAnimatingClosed = false;
    private AnimatorSet mCurrentAnimator;

    private boolean mDestroyed;
    private View mCurrentDragView;
    private boolean mIsDragInProgress = false;
    private boolean mDeleteFolderOnDropCompleted = false;
    private boolean mSuppressFolderDeletion = false;
    private boolean mItemAddedBackToSelfViaIcon = false;

    public Folder(Context context, ActivityContext activityContext,
            LauncherDelegate launcherDelegate) {
        super(context, null);
        mActivityContext = activityContext;
        mLauncherDelegate = launcherDelegate;
        setOrientation(VERTICAL);
        mContent = new FolderPagedView(context);
        mContent.setFolder(this);
        mFooter = new View(context);
        mFolderName = new View(context);
        addView(mContent, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        addView(mFooter, new LayoutParams(0, 0));
        setWillNotDraw(false);
        setFocusableInTouchMode(true);
    }

    /** TextLauncher: the folder's item view (R.layout.folder_application). */
    protected abstract BubbleTextView inflateIcon();

    @Override
    public Drawable getBackground() {
        return mBackground;
    }

    public boolean onLongClick(View v) {
        return startDrag(v, new DragOptions());
    }

    public boolean startDrag(View v, DragOptions options) {
        Object tag = v.getTag();
        if (tag instanceof ItemInfo) {
            mCurrentDragView = v;

            addDragListener(options);
            callBeginDragShared(v, options);
        }
        return true;
    }

    void callBeginDragShared(View v, DragOptions options) {
        mLauncherDelegate.beginDragShared(v, this, options);
    }

    void addDragListener(DragOptions options) {
        mActivityContext.getDragController().addDragListener(this);
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        if (dragObject.dragSource != this) {
            return;
        }
        mIsDragInProgress = true;
        // TextLauncher: the folder closes when a drag starts, so its views are left to the close
        // animation instead of being removed here.

        // We do not want to get events for the item being removed, as they will get handled
        // when the drop completes
        removeFolderContent(true, dragObject.dragInfo);
        mItemAddedBackToSelfViaIcon = false;
    }

    @Override
    public void onDragEnd() {
        mIsDragInProgress = false;
        mActivityContext.getDragController().removeDragListener(this);
    }

    public FolderIcon getFolderIcon() {
        return mFolderIcon;
    }

    public void setFolderIcon(FolderIcon icon) {
        mFolderIcon = icon;
    }

    @Override
    protected void onAttachedToWindow() {
        // requestFocus() causes the focus onto the folder itself, which doesn't cause visual
        // effect but the next arrow key can start the keyboard focus inside of the folder, not
        // the folder itself.
        requestFocus();
        super.onAttachedToWindow();
    }

    @Override
    public View focusSearch(int direction) {
        // When the folder is focused, further focus search should be within the folder contents.
        return FocusFinder.getInstance().findNextFocus(this, null, direction);
    }

    public FolderInfo getInfo() {
        return mInfo;
    }

    void bind(FolderInfo info) {
        mInfo = info;
    }

    /** TextLauncher: FolderIcon.inflateIcon binds the folder; here the app does. */
    public void bindToIcon(FolderIcon icon) {
        setFolderIcon(icon);
        bind(icon.getFolderInfo());
        icon.setFolder(this);
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     */
    public void animateOpen() {
        List<ItemInfo> items = mInfo.getContents();
        if (!shouldAnimateOpen(items)) {
            return;
        }
        Folder openFolder = getOpen(mActivityContext);
        closeOpenFolder(openFolder);

        mContent.bindItems(items);
        mIsOpen = true;

        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (getParent() == null) {
            BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(0, 0);
            lp.customPosition = true;
            dragLayer.addView(this, lp);
        }
        centerAboutIcon();

        mDeleteFolderOnDropCompleted = false;

        cancelRunningAnimations();
        // TextLauncher: the copied animation reads the laid out folder, so it starts on the next
        // frame; the folder stays invisible until then.
        setVisibility(INVISIBLE);
        post(() -> {
            if (!mIsOpen) return;
            setVisibility(VISIBLE);
            // Folder.onAttachedToWindow's requestFocus did not take while the folder was
            // invisible.
            requestFocus();
            AnimatorSet animatorSet = getFolderAnimationManager()
                    .createAnimatorSet(/* isOpening */ true);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    // TextLauncher: FolderIcon.setIconVisible(false) and its leave-behind.
                    mFolderIcon.setVisibility(INVISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mContent.setFocusOnFirstChild();
                }
            });
            addAnimationStartListeners(animatorSet);
            animatorSet.start();
        });
    }

    private void addAnimationStartListeners(AnimatorSet a) {
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mCurrentAnimator = a;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentAnimator = null;
            }
        });
    }

    /**
     * Determines whether we should animate the folder opening.
     */
    boolean shouldAnimateOpen(List<ItemInfo> items) {
        return items != null && items.size() > 1;
    }

    private FolderAnimationCreator getFolderAnimationManager() {
        return new FolderAnimationSpringBuilderManager(this,
                ThemeManager.INSTANCE.get(getContext()).getFolderShape(), mLauncherDelegate);
    }

    /**
     * If there's a folder already open, we want to close it before opening another one.
     */
    boolean closeOpenFolder(Folder openFolder) {
        if (openFolder != null && openFolder != this) {
            // Close any open folder before opening a folder.
            openFolder.close(true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_FOLDER) != 0;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (!animate && mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }

        if (animate && getParent() != null && getVisibility() == VISIBLE) {
            animateClosed();
        } else {
            closeComplete(false);
        }
    }

    private void cancelRunningAnimations() {
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }
    }

    private void animateClosed() {
        if (mIsAnimatingClosed) {
            cancelRunningAnimations();
            return;
        }

        int size = getIconsInReadingOrder().size();
        if (size <= 1) {
            closeComplete(false);
            return;
        }

        cancelRunningAnimations();
        AnimatorSet animatorSet = getFolderAnimationManager()
                .createAnimatorSet(/* isOpening */ false);

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimatingClosed = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                closeComplete(true);
                mIsAnimatingClosed = false;
            }
        });
        addAnimationStartListeners(animatorSet);
        animatorSet.start();
    }

    private void closeComplete(boolean wasAnimated) {
        mIsOpen = false;
        // TextLauncher: Launcher3's open folder always has the focus. TextLauncher's has it only
        // when it was opened with keys and nothing took it since (the home button gives it to
        // the search field), and only then does it go back to the icon.
        boolean hadFocus = hasFocus();

        // TODO: Clear all active animations.
        BaseDragLayer parent = (BaseDragLayer) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
        clearFocus();
        if (mFolderIcon != null) {
            mFolderIcon.setVisibility(View.VISIBLE);
            if (wasAnimated && hadFocus) {
                mFolderIcon.requestFocus();
            }
        }

        if (getItemCount() <= 1) {
            if (!mIsDragInProgress && !mSuppressFolderDeletion) {
                replaceFolderWithFinalItem();
            } else if (mIsDragInProgress) {
                mDeleteFolderOnDropCompleted = true;
            }
        } else if (!mIsDragInProgress) {
            mContent.unbindItems();
        }
        mSuppressFolderDeletion = false;

        if (Flags.enableExpressiveFolderExpansion()) {
            FolderAnimationSpringBuilderManager.resetLauncherScale(mLauncherDelegate);
            FolderAnimationSpringBuilderManager.resetScrimAndZoom(mLauncherDelegate);
        }
    }

    public void onDropCompleted(final View target, final DragObject d,
            final boolean success) {
        if (success) {
            if (getItemCount() <= 1) {
                mDeleteFolderOnDropCompleted = true;
            }
            if (mDeleteFolderOnDropCompleted && !mItemAddedBackToSelfViaIcon
                    && target != this) {
                replaceFolderWithFinalItem();
            }
        } else {
            // The drag failed, we need to return the item to the folder
            // TextLauncher: the folder is closed, so the item goes back through its icon; only
            // once the drag had started, as a long press that never moved did not take it out.
            ItemInfo info = d.dragInfo;
            if (mFolderIcon.getParent() != null && !mInfo.getContents().contains(info)) {
                mFolderIcon.onDrop(d, true /* itemReturnedOnFailedDrop */);
            }
        }

        mDeleteFolderOnDropCompleted = false;
        mIsDragInProgress = false;
        mItemAddedBackToSelfViaIcon = false;
        mCurrentDragView = null;
    }

    public void notifyDrop() {
        if (mIsDragInProgress) {
            mItemAddedBackToSelfViaIcon = true;
        }
    }

    boolean getIsDragInProgress() {
        return mIsDragInProgress;
    }

    /**
     * TextLauncher: Folder.centerAboutIcon without the folder bounding box: centered horizontally
     * in the drag layer, vertically on the icon, and kept inside the drag layer.
     */
    private void centerAboutIcon() {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) getLayoutParams();
        BaseDragLayer parent = mActivityContext.getDragLayer();
        int width = getFolderWidth();
        int height = getFolderHeight();

        parent.getDescendantRectRelativeToSelf(mFolderIcon, sTempRect);
        int centerY = sTempRect.centerY();
        int centeredTop = centerY - height / 2;

        int margin = mContent.getPaddingTop();
        int left = (parent.getWidth() - width) / 2;
        int top = Utilities.boundToRange(centeredTop, margin, parent.getHeight() - height - margin);

        lp.width = width;
        lp.height = height;
        lp.x = left;
        lp.y = top;

        mBackground.setBounds(0, 0, width, height);
    }

    protected int getContentAreaHeight() {
        return mContent.getDesiredHeight();
    }

    int getFolderWidth() {
        return mContent.getDesiredWidth();
    }

    int getFolderHeight() {
        return getContentAreaHeight();
    }

    public int getItemCount() {
        return mInfo.getContents().size();
    }

    void replaceFolderWithFinalItem() {
        mDestroyed = mLauncherDelegate.replaceFolderWithFinalItem(this);
    }

    public boolean isDestroyed() {
        return mDestroyed;
    }

    // This is used so the item doesn't immediately appear in the folder when added. In one case
    // we need to create the illusion that the item isn't added back to the folder yet, to
    // to correspond to the animation of the icon back into the folder. This is
    public void hideItem(ItemInfo info) {
        View v = getViewForInfo(info);
        if (v != null) {
            v.setVisibility(INVISIBLE);
        }
    }

    public void showItem(ItemInfo info) {
        View v = getViewForInfo(info);
        if (v != null) {
            v.setVisibility(VISIBLE);
        }
    }

    /** Add an app or shortcut */
    public void addFolderContent(ItemInfo item) {
        addFolderContent(item, mInfo.getContents().size(), true);
    }

    /** Add an app or shortcut for a specified rank */
    public void addFolderContent(ItemInfo item, int rank, boolean animate) {
        if (!willAcceptItemType(item.itemType)) {
            throw new RuntimeException("tried to add an illegal type into a folder");
        }

        rank = Utilities.boundToRange(rank, 0, mInfo.getContents().size());
        mInfo.getContents().add(rank, item);
        // TextLauncher: Folder.updateItemLocationsInDatabaseBatch's ranks; the app saves them.
        updateRanks();

        mFolderIcon.onItemsChanged(animate);
        mFolderIcon.onContentAdded(item, rank);
    }

    /** Remove all matching app or shortcut. Does not change the DB. */
    public void removeFolderContent(boolean animate, ItemInfo... items) {
        List<ItemInfo> itemArray = Arrays.asList(items);
        mInfo.getContents().removeAll(itemArray);
        updateRanks();

        mFolderIcon.onItemsChanged(animate);
        mFolderIcon.onContentRemoved(itemArray);
    }

    private void updateRanks() {
        List<ItemInfo> contents = mInfo.getContents();
        for (int i = 0; i < contents.size(); i++) contents.get(i).rank = i;
    }

    View getViewForInfo(final ItemInfo item) {
        return mContent.iterateOverItems((info, view) -> info == item);
    }

    public ArrayList<View> getIconsInReadingOrder() {
        ArrayList<View> items = new ArrayList<>();
        for (int i = 0; i < mContent.getChildCount(); i++) items.add(mContent.getChildAt(i));
        return items;
    }

    /** Single page: every child is on page 0. */
    public List<View> getItemsOnPage(int page) {
        return getIconsInReadingOrder();
    }

    /**
     * Returns a folder which is already open or null
     */
    public static Folder getOpen(ActivityContext activityContext) {
        return getOpenView(activityContext, TYPE_FOLDER);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = (BaseDragLayer) getParent();
            if (!dl.isEventOverView(this, ev)
                    && mLauncherDelegate.interceptOutsideTouch(ev, dl, this)) {
                return true;
            }
        }
        return false;
    }

    public FolderPagedView getContent() {
        return mContent;
    }

    public View getFolderName() {
        return mFolderName;
    }

    /**
     * Alternative to using {@link #getClipToOutline()} as it only works with derivatives of
     * rounded rect.
     */
    @Override
    public void setClipPath(PathWrapper clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mBackground.setBounds(0, 0, getWidth(), getHeight());
        if (mClipPath != null) {
            // clipPath is non-null while clip reveal animation is playing
            int count = canvas.save();
            canvas.clipPath(mClipPath.getPath());
            mBackground.draw(canvas);

            if (!mIsAnimatingClosed) {
                // If animating open, clip the content to the clip path to keep in bounds.
                super.dispatchDraw(canvas);
            }
            canvas.restoreToCount(count);
            if (mIsAnimatingClosed) {
                // If animating closed, avoid clipping content as preview items protrude past clip.
                super.dispatchDraw(canvas);
            }
        } else {
            mBackground.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }
}
