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

import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;
import static com.android.launcher3.AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME;
import static com.android.launcher3.BubbleTextView.DISPLAY_FOLDER;
import static com.android.launcher3.Flags.enableCursorDrivenWorkflows;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.FLAG_MULTI_PAGE;
import static com.android.launcher3.LauncherState.FLAG_WORKSPACE_ICONS_BEING_DRAGGED;
import static com.android.launcher3.LauncherState.FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED;
import static com.android.launcher3.LauncherState.FLAG_WORKSPACE_INACCESSIBLE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.config.FeatureFlags.FOLDABLE_SINGLE_PAGE;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;

import static java.util.Objects.requireNonNull;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.DragAndDropPermissions;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;

import com.android.app.animation.Interpolators;
import com.android.launcher3.accessibility.AccessibleDragListenerAdapter;
import com.android.launcher3.accessibility.WorkspaceAccessibilityHelper;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.LauncherDragController;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.popup.Poppable;
import com.android.launcher3.popup.Popup;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.touch.WorkspaceTouchListener;
import com.android.launcher3.util.EdgeEffectCompat;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.ViewEx;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.util.WidgetSizeHandler;


import com.google.android.msdl.data.model.MSDLToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 *
 * @param <T> Class that extends View and PageIndicator
 */
/**
 * Stand-in for Launcher3's Workspace (android-17.0.0_r1), made from Workspace.java by keeping the
 * drag and drop: the workspace as a drag source and drop target, the folder creation and the
 * reorder while dragging, the drops from elsewhere (the all apps list, the search results, the
 * widget picker) and the item removal. Pages, the hotseat, the overlay, the wallpaper offsets,
 * state transitions, the touch handling, the saved state and the system drags are deleted;
 * changes are marked with "TextLauncher:".
 *
 * <p>TextLauncher has one page, in a vertical scroll container (the home screen's HomeScrollView),
 * so the workspace is not a PagedView: it holds the scroll container, and members for the one
 * page are added at the end of the class.
 */
// TextLauncher: a FrameLayout that holds the page's scroll container instead of a PagedView of
// CellLayouts; not a touch listener, Insettable, state handler or overlay callback. Abstract: the
// home screen's subclass gives the long-click listener, the page and its scroll container.
public abstract class Workspace<T> extends FrameLayout
        implements DropTarget, DragSource, CellLayoutContainer,
        DragController.DragListener, StateHandler<LauncherState>,
        WorkspaceLayoutManager, LauncherBindableItemsContainer {

    /**
     * The value that {@link #mTransitionProgress} must be greater than for
     * {@link #transitionStateShouldAllowDrop()} to return true.
     */
    private static final float ALLOW_DROP_TRANSITION_PROGRESS = 0.25f;

    /**
     * The value that {@link #mTransitionProgress} must be greater than for
     * {@link #isFinishedSwitchingState()} ()} to return true.
     */
    private static final float FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS = 0.5f;

    private static final boolean ENFORCE_DRAG_EVENT_ORDER = false;

    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;

    public static final int DEFAULT_PAGE = 0;

    private final int mAllAppsIconSize;

    @Thunk
    final WallpaperManager mWallpaperManager;

    protected ShortcutAndWidgetContainer mDragSourceInternal;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Thunk
    public final IntSparseArrayMap<CellLayout> mWorkspaceScreens = new IntSparseArrayMap<>();

    @Thunk
    final IntArray mScreenOrder = new IntArray();

    /**
     * CellInfo for the cell that is currently being dragged
     */
    protected CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    @Thunk
    int[] mTargetCell = new int[2];

    private int mDragOverX = -1;

    private int mDragOverY = -1;

    /**
     * The CellLayout that is currently being dragged over
     */
    @Thunk
    CellLayout mDragTargetLayout = null;

    /**
     * The CellLayout that we will show as highlighted
     */
    private CellLayout mDragOverlappingLayout = null;

    /**
     * The CellLayout which will be dropped to
     */
    private CellLayout mDropToLayout = null;

    @Thunk
    final Launcher mLauncher;

    @Thunk
    DragController mDragController;

    protected final int[] mTempXY = new int[2];

    private final float[] mTempFXY = new float[2];

    private final Rect mTempRect = new Rect();

    @Thunk
    float[] mDragViewVisualCenter = new float[2];

    private boolean mIsSwitchingState = false;

    public static final int REORDER_TIMEOUT = 650;

    protected final Alarm mReorderAlarm = new Alarm();

    private PreviewBackground mFolderAddBg;

    private PreviewBackground mFolderCreateBg;

    /** The underlying view that we are dragging something over. */
    private View mDragOverView = null;

    private FolderIcon mDragOverFolderIcon = null;

    private boolean mCreateUserFolderOnDrop = false;

    private boolean mAddToExistingFolderOnDrop = false;

    // Relating to the animation of items being dropped externally
    public static final int ANIMATE_INTO_POSITION_AND_DISAPPEAR = 0;

    public static final int ANIMATE_INTO_POSITION_AND_REMAIN = 1;

    public static final int ANIMATE_INTO_POSITION_AND_RESIZE = 2;

    public static final int COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION = 3;

    public static final int CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION = 4;

    // Related to dragging, folder creation and reordering
    private static final int DRAG_MODE_NONE = 0;

    private static final int DRAG_MODE_CREATE_FOLDER = 1;

    private static final int DRAG_MODE_ADD_TO_FOLDER = 2;

    private static final int DRAG_MODE_REORDER = 3;

    protected int mDragMode = DRAG_MODE_NONE;

    @Thunk
    int mLastReorderX = -1;

    @Thunk
    int mLastReorderY = -1;

    private float mTransitionProgress;

    private final StatsLogManager mStatsLogManager;

    private final MSDLPlayerWrapper mMSDLPlayerWrapper;

    /**
     * State tracking accessibility focus for items being moved.
     * @param movedItemCell the item being moved
     * @param deferAccessibilityFocus defers focus to the layout transition end
     * if a screen is removed.
     */
    private record MoveItemA11yState(View movedItemCell, boolean deferAccessibilityFocus) {}

    private MoveItemA11yState mMoveItemA11yState = new MoveItemA11yState(
        /* movedItemCell= */ null,
        /* deferAccessibilityFocus= */ false);

    @VisibleForTesting
    public void setMoveItemA11yState(View movedItemCell, boolean deferAccessibilityFocus) {
        mMoveItemA11yState = new MoveItemA11yState(movedItemCell, deferAccessibilityFocus);
    }

    private final WorkspaceStateTransitionAnimation mStateTransitionAnimation;

    @Nullable
    private DragController.DragListener mAccessibilityDragListener;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attributes set containing the Workspace's customization values.
     */
    public Workspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context  The application's context.
     * @param attrs    The attributes set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
        // TextLauncher: no launcher UI state.
        mStateTransitionAnimation = new WorkspaceStateTransitionAnimation(mLauncher, this);
        mWallpaperManager = WallpaperManager.getInstance(context);
        mAllAppsIconSize = mLauncher.getDeviceProfile().getAllAppsProfile().getIconSizePx();
        // TextLauncher: one page, so no wallpaper offsets (WallpaperOffsetInterpolator).

        setHapticFeedbackEnabled(false);
        // TextLauncher: no pages to initialize (initWorkspace); the app adds the page.

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(true);
        // TextLauncher: the page's scroll container has the touch listener (WorkspaceTouchListener).
        mStatsLogManager = StatsLogManager.newInstance(context);
        mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context);
    }

    /**
     * Estimates the size of an item using spans: hSpan, vSpan.
     *
     * @return MAX_VALUE for each dimension if unsuccessful.
     */
    public int[] estimateItemSize(ItemInfo itemInfo) {
        int[] size = new int[2];
        // TextLauncher: the page is not a child of the workspace (getPageAt).
        if (getPageCount() > 0) {
            // Use the first page to estimate the child position
            CellLayout cl = getPageAt(0);
            boolean isWidget = itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;

            Rect r = estimateItemPosition(cl, 0, 0, itemInfo.spanX, itemInfo.spanY);

            float scale = 1;
            if (isWidget) {
                DeviceProfile profile = mLauncher.getDeviceProfile();
                final PointF appWidgetScale = profile.getAppWidgetScale(null);
                scale = Utilities.shrinkRect(r, appWidgetScale.x, appWidgetScale.y);
            }
            size[0] = r.width();
            size[1] = r.height();

            if (isWidget) {
                size[0] /= scale;
                size[1] /= scale;
            }
            return size;
        } else {
            size[0] = Integer.MAX_VALUE;
            size[1] = Integer.MAX_VALUE;
            return size;
        }
    }

    public float getWallpaperOffsetForCenterPage() {
        // TextLauncher: one page, so the wallpaper is not scrolled.
        return 0;
    }

    public Rect estimateItemPosition(CellLayout cl, int hCell, int vCell, int hSpan, int vSpan) {
        Rect r = new Rect();
        cl.cellToRect(hCell, vCell, hSpan, vSpan, r);
        return r;
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragStart", 0, 0);
        }

        // TextLauncher: the occupancy is rebuilt from the page's views first, as a safeguard;
        // Launcher3 relies on addInScreen and removeView alone to keep it.
        getPageAt(0).rebuildOccupied(null);
        if (mDragInfo != null && mDragInfo.cell != null) {
            CellLayout layout = (CellLayout) (mDragInfo.cell instanceof LauncherAppWidgetHostView
                    ? dragObject.dragView.getContentViewParent().getParent()
                    : mDragInfo.cell.getParent().getParent());
            layout.markCellsAsUnoccupiedForView(mDragInfo.cell);
        }

        // TextLauncher: no page layers (updateChildrenLayersEnabled).

        // Do not add a new page if it is a accessible drag which was not started by the workspace.
        // We do not support accessibility drag from other sources and instead provide a direct
        // action for move/add to homescreen.
        // When a accessible drag is started by the folder, we only allow rearranging withing the
        // folder.
        boolean addNewPage = !(options.isAccessibleDrag && dragObject.dragSource != this);
        if (addNewPage) {
            // TextLauncher: one page, so no extra empty screen (addExtraEmptyScreenOnDrag).

            if (dragObject.dragInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    && dragObject.dragSource != this) {
                // When dragging a widget from different source, move to a page which has
                // enough space to place this widget (after rearranging/resizing). We special case
                // widgets as they cannot be placed inside a folder.
                // Start at the current page and search right (on LTR) until finding a page with
                // enough space. Since an empty screen is the furthest right, a page must be found.
                int currentPage = getDestinationPage();
                for (int pageIndex = currentPage; pageIndex < getPageCount(); pageIndex++) {
                    CellLayout page = (CellLayout) getPageAt(pageIndex);
                    if (page.hasReorderSolution(dragObject.dragInfo)) {
                        setCurrentPage(pageIndex);
                        break;
                    }
                }
            }
        }

        if (mAccessibilityDragListener != null) {
            mAccessibilityDragListener.onDragStart(dragObject, options);
        }
        // TextLauncher: no edit mode state (EDIT_MODE), and no mouse drags
        // (enableCursorDrivenWorkflows is off in the release).
        mLauncher.getStateManager().goToState(SPRING_LOADED);
        mStatsLogManager.logger().withItemInfo(dragObject.dragInfo)
                .withInstanceId(dragObject.logInstanceId)
                .log(LauncherEvent.LAUNCHER_ITEM_DRAG_STARTED);
    }

    @Override
    public void onDragEnd() {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnd", 0, 0);
        }

        // TextLauncher: no page layers (updateChildrenLayersEnabled), and one page, so no extra
        // empty screen to remove once the state is NORMAL again.

        if (mAccessibilityDragListener != null) {
            mAccessibilityDragListener.onDragEnd();
        }
        mDragInfo = null;
        mDragSourceInternal = null;
    }

    private void requestA11yFocusOnMovedItem() {
        View toFocusCell = mMoveItemA11yState.movedItemCell();
        toFocusCell.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        mMoveItemA11yState = new MoveItemA11yState(
            /* movedItemCell= */ null,
            /* deferAccessibilityFocus= */ false);
    }

    @Override
    public CellLayout getScreenWithId(int screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    @Override
    public int getCellLayoutId(CellLayout layout) {
        int index = mWorkspaceScreens.indexOfValue(layout);
        if (index != -1) {
            return mWorkspaceScreens.keyAt(index);
        }
        return -1;
    }

    public int getPageIndexForScreenId(int screenId) {
        // TextLauncher: one page, which is not a child of the workspace.
        return mWorkspaceScreens.get(screenId) != null ? 0 : -1;
    }

    @Override
    public int getCellLayoutIndex(CellLayout cellLayout) {
        // TextLauncher: one page, which is not a child of the workspace.
        return getPageIndexForScreenId(getCellLayoutId(cellLayout));
    }

    @Override
    public int getPanelCount() {
        // TextLauncher: one page, also with two panels (the second shows the all apps list).
        return 1;
    }

    public IntArray getScreenOrder() {
        return mScreenOrder;
    }

    public boolean isSwitchingState() {
        return mIsSwitchingState;
    }

    /**
     * This differs from isSwitchingState in that we take into account how far the transition
     * has completed.
     */
    public boolean isFinishedSwitchingState() {
        return !mIsSwitchingState
                || (mTransitionProgress > FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS);
    }

    /**
     * Returns whether a drag should be allowed to be started from the current workspace state.
     */
    public boolean workspaceIconsCanBeDragged() {
        return mLauncher.getStateManager().getState().hasFlag(FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED);
    }

    public void onWallpaperTap(MotionEvent ev) {
        final int[] position = mTempXY;
        getLocationOnScreen(position);

        int pointerIndex = ev.getActionIndex();
        position[0] += (int) ev.getX(pointerIndex);
        position[1] += (int) ev.getY(pointerIndex);

        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                ev.getAction() == MotionEvent.ACTION_UP
                        ? WallpaperManager.COMMAND_TAP : WallpaperManager.COMMAND_SECONDARY_TAP,
                position[0], position[1], 0, null);
    }

    private void onStartStateTransition() {
        mIsSwitchingState = true;
        mTransitionProgress = 0;

        // TextLauncher: no page layers (updateChildrenLayersEnabled).
    }

    private void onEndStateTransition() {
        mIsSwitchingState = false;
        // TextLauncher: one page, so no adjacent pages to draw (mForceDrawAdjacentPages), and no
        // page layers (updateChildrenLayersEnabled).
        mTransitionProgress = 1;

        updateAccessibilityFlags();
    }

    /**
     * Sets the current workspace {@link LauncherState} and updates the UI without any animations
     */
    @Override
    public void setState(LauncherState toState) {
        onStartStateTransition();
        mLauncher.getStateManager().getState().onLeavingState(mLauncher, toState);
        mStateTransitionAnimation.setState(toState);
        onEndStateTransition();
    }

    /**
     * Sets the current workspace {@link LauncherState}, then animates the UI
     */
    @Override
    public void setStateWithAnimation(
            LauncherState toState, StateAnimationConfig config, PendingAnimation animation) {
        StateTransitionListener listener = new StateTransitionListener();
        mLauncher.getStateManager().getState().onLeavingState(mLauncher, toState);
        mStateTransitionAnimation.setStateWithAnimation(toState, config, animation);

        // TextLauncher: one page, so no adjacent pages to draw (FLAG_MULTI_PAGE,
        // mForceDrawAdjacentPages).
        invalidate(); // This will call dispatchDraw(), which calls getVisiblePages().

        ValueAnimator stepAnimator = ValueAnimator.ofFloat(0, 1);
        stepAnimator.addUpdateListener(listener);
        stepAnimator.addListener(listener);
        animation.add(stepAnimator);
    }

    public WorkspaceStateTransitionAnimation getStateTransitionAnimation() {
        return mStateTransitionAnimation;
    }

    @Override
    public void announceForAccessibility(CharSequence text) {
        // Don't announce if apps is on top of us.
        if (!mLauncher.isInState(ALL_APPS)) {
            super.announceForAccessibility(text);
        }
    }

    public void updateAccessibilityFlags() {
        // TODO: Update the accessibility flags appropriately when dragging.
        int accessibilityFlag =
                mLauncher.getStateManager().getState().hasFlag(FLAG_WORKSPACE_INACCESSIBLE)
                        ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        if (!mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            int total = getPageCount();
            for (int i = 0; i < total; i++) {
                updateAccessibilityFlags(accessibilityFlag, (CellLayout) getPageAt(i));
            }
            setImportantForAccessibility(accessibilityFlag);
        }
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo() {
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
            // TAPL tests verify that workspace is not present in Overview and AllApps states.
            // TAPL can work only if UIDevice is set up as setCompressedLayoutHeirarchy(false).
            // Hiding workspace from the tests when it's
            // IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS.
            return AccessibilityNodeInfo.obtain();
        }
        return super.createAccessibilityNodeInfo();
    }

    private void updateAccessibilityFlags(int accessibilityFlag, CellLayout page) {
        page.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        page.getShortcutsAndWidgets().setImportantForAccessibility(accessibilityFlag);
        page.setContentDescription(null);
        page.setAccessibilityDelegate(null);
    }

    public void startDrag(CellInfo cellInfo, DragOptions options) {
        View child = cellInfo.cell;

        mDragInfo = cellInfo;
        child.setVisibility(INVISIBLE);

        if (options.isAccessibleDrag) {
            // TextLauncher: the page is the child of its scroll container, not of this workspace,
            // and there is no hotseat.
            mAccessibilityDragListener = new AccessibleDragListenerAdapter(
                    (ViewGroup) getPageScrollContainer(), WorkspaceAccessibilityHelper::new);
        }

        beginDragShared(child, this, options);
    }

    public void beginDragShared(View child, DragSource source, DragOptions options) {
        Object dragObject = child.getTag();
        if (!(dragObject instanceof ItemInfo)) {
            String msg = "Drag started with a view that has no tag set. This "
                    + "will cause a crash (issue 11627249) down the line. "
                    + "View: " + child + "  tag: " + child.getTag();
            throw new IllegalStateException(msg);
        }
        beginDragShared(child, null, source, (ItemInfo) dragObject,
                new DragPreviewProvider(child), options);
    }

    /**
     * Core functionality for beginning a drag operation for an item that will be dropped within
     * the workspace
     */
    public DragView beginDragShared(View child, DraggableView draggableView, DragSource source,
            ItemInfo dragObject, DragPreviewProvider previewProvider, DragOptions dragOptions) {
        float iconScale = 1f;
        if (child instanceof BubbleTextView) {
            Drawable icon = ((BubbleTextView) child).getIcon();
            if (icon instanceof FastBitmapDrawable) {
                iconScale = ((FastBitmapDrawable) icon).getAnimatedScale();
            }
        }

        // Clear the pressed state if necessary
        child.clearFocus();
        child.setPressed(false);
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedBackground();
        }

        if (draggableView == null && child instanceof DraggableView) {
            draggableView = (DraggableView) child;
        }

        final View contentView = previewProvider.getContentView();
        final float scale;
        // The draggable drawable follows the touch point around on the screen
        final Drawable drawable;
        if (contentView == null) {
            drawable = previewProvider.createDrawable();
            scale = previewProvider.getScaleAndPosition(drawable, mTempXY);
        } else {
            drawable = null;
            scale = previewProvider.getScaleAndPosition(contentView, mTempXY);
        }

        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        Rect dragRect = new Rect();

        if (draggableView != null) {
            draggableView.getSourceVisualDragBounds(dragRect);
            dragLayerY += dragRect.top;
        }


        if (child.getParent() instanceof ShortcutAndWidgetContainer) {
            mDragSourceInternal = (ShortcutAndWidgetContainer) child.getParent();
        }

        if (child.getTag() instanceof ItemInfo item) {
            // TextLauncher: no home screen files (HomeScreenFilesUtilsKt.isFileSystemItem).
            if (child instanceof BubbleTextView) {
                BubbleTextView btv = (BubbleTextView) child;
                if (!dragOptions.isAccessibleDrag && !dragOptions.isMouseDrag) {
                    dragOptions.preDragCondition =
                            btv.startLongPressAction(mLauncher.getPopupControllerForAppIcons());
                }
                if (btv.isDisplaySearchResult()) {
                    dragOptions.preDragEndScale = (float) mAllAppsIconSize / btv.getIconSize();
                }
            } else if ((Flags.homeScreenEditImprovements() && child instanceof Poppable)
                    && !dragOptions.isAccessibleDrag && !dragOptions.isMouseDrag) {
                Popup popup = mLauncher.getPopupControllerForHomeScreenItems()
                        .show(child);
                if (popup != null) {
                    dragOptions.preDragCondition = popup.createPreDragCondition();
                }
            }
        }

        if (dragOptions.preDragCondition != null) {
            int xDragOffSet = dragOptions.preDragCondition.getDragOffset().x;
            int yDragOffSet = dragOptions.preDragCondition.getDragOffset().y;
            if (xDragOffSet != 0 || yDragOffSet != 0) {
                dragLayerX += xDragOffSet;
                dragLayerY += yDragOffSet;
            }
        }

        DragView dv = null;
        // TextLauncher: no system drags (getClipDataForSystemDrag).
        if (dv == null) {
            if (contentView != null) {
                if (Flags.homeScreenEditImprovements()
                        && ((ItemInfo) child.getTag()).itemType == ITEM_TYPE_APPWIDGET
                        && mDragController instanceof LauncherDragController dragController) {
                    dragOptions.deferDragToPreDragEnd = true;
                    dragOptions.preDragEndScale = (contentView.getMeasuredWidth()
                            + dragController.getWidgetDragScalePx(null, contentView, dragObject))
                            / contentView.getMeasuredWidth();
                }
                dv = mDragController.startDrag(
                        contentView,
                        draggableView,
                        dragLayerX,
                        dragLayerY,
                        source,
                        dragObject,
                        dragRect,
                        scale * iconScale,
                        scale,
                        dragOptions);
            } else {
                if (Flags.homeScreenEditImprovements()
                        && child.getTag() instanceof ItemInfo childItemInfo
                        && childItemInfo.itemType == ITEM_TYPE_APPWIDGET
                        && mDragController instanceof LauncherDragController dragController) {
                    dragOptions.preDragEndScale = (drawable.getIntrinsicWidth()
                            + dragController.getWidgetDragScalePx(drawable, null, dragObject))
                            / drawable.getIntrinsicWidth();
                }
                dv = mDragController.startDrag(
                        drawable,
                        draggableView,
                        dragLayerX,
                        dragLayerY,
                        source,
                        dragObject,
                        dragRect,
                        scale * iconScale,
                        scale,
                        dragOptions);
            }
        }

        return dv;
    }

    private boolean transitionStateShouldAllowDrop() {
        return (!isSwitchingState() || mTransitionProgress > ALLOW_DROP_TRANSITION_PROGRESS) &&
                workspaceIconsCanBeDragged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptDrop(DragObject d) {
        // If it's an external drop (e.g. from All Apps), check if it should be accepted
        CellLayout dropTargetLayout = mDropToLayout;
        if (d.dragSource != this) {
            // Don't accept the drop if we're not over a valid drop target at time of drop
            if (dropTargetLayout == null) {
                return false;
            }
            if (!transitionStateShouldAllowDrop()) return false;

            // TextLauncher: no system drags (SystemDragItemInfo).

            mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

            // We want the point to be mapped to the dragTarget.
            mapPointFromDropLayout(dropTargetLayout, mDragViewVisualCenter);

            int spanX;
            int spanY;
            if (mDragInfo != null) {
                final CellInfo dragCellInfo = mDragInfo;
                spanX = dragCellInfo.spanX;
                spanY = dragCellInfo.spanY;
            } else {
                spanX = d.dragInfo.spanX;
                spanY = d.dragInfo.spanY;
            }

            int minSpanX = spanX;
            int minSpanY = spanY;
            if (d.dragInfo instanceof PendingAddWidgetInfo) {
                minSpanX = ((PendingAddWidgetInfo) d.dragInfo).minSpanX;
                minSpanY = ((PendingAddWidgetInfo) d.dragInfo).minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, dropTargetLayout,
                    mTargetCell);
            float distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
            if (mCreateUserFolderOnDrop && willCreateUserFolder(d.dragInfo,
                    dropTargetLayout, mTargetCell, distance, true)) {
                return true;
            }

            if (mAddToExistingFolderOnDrop && willAddToExistingUserFolder(d.dragInfo,
                    dropTargetLayout, mTargetCell, distance)) {
                return true;
            }

            int[] resultSpan = new int[2];
            mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    null, mTargetCell, resultSpan, CellLayout.MODE_ACCEPT_DROP);
            boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

            // Don't accept the drop if there's no room for the item
            if (!foundCell) {
                onNoCellFound(dropTargetLayout, d.dragInfo, d.logInstanceId);
                return false;
            }
        }

        // TextLauncher: one page, so no extra empty screen to commit (commitExtraEmptyScreens).

        return true;
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell,
                                 float distance, boolean considerTimeout) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willCreateUserFolder(info, dropOverView, considerTimeout);
    }

    boolean willCreateUserFolder(ItemInfo info, View dropOverView, boolean considerTimeout) {
        if (dropOverView != null) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.getTmpCellX() != lp.getCellX()
                    || lp.getTmpCellY() != lp.getCellY())) {
                return false;
            }
        }

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            hasntMoved = dropOverView == mDragInfo.cell;
        }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false;
        }

        boolean aboveShortcut = Folder.willAccept(dropOverView.getTag())
                && ((ItemInfo) dropOverView.getTag()).container != CONTAINER_HOTSEAT_PREDICTION;
        boolean willBecomeShortcut = FolderInfo.willAcceptItemType(info.itemType);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, CellLayout target, int[] targetCell,
                                        float distance) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willAddToExistingUserFolder(dragInfo, dropOverView);
    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, View dropOverView) {
        if (dropOverView != null) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.getTmpCellX() != lp.getCellX()
                    || lp.getTmpCellY() != lp.getCellY())) {
                return false;
            }
        }

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }

        // TextLauncher: no home screen files (file system folders).

        return false;
    }

    boolean createUserFolderIfNecessary(View newView, int container, CellLayout target,
            int[] targetCell, float distance, boolean external, DragObject d) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View v = target.getChildAt(targetCell[0], targetCell[1]);

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            CellLayout cellParent = getParentCellLayoutForView(mDragInfo.cell);
            hasntMoved = (mDragInfo.cellX == targetCell[0] &&
                    mDragInfo.cellY == targetCell[1]) && (cellParent == target);
        }

        if (v == null || hasntMoved || !mCreateUserFolderOnDrop) return false;
        mCreateUserFolderOnDrop = false;
        final int screenId = getCellLayoutId(target);

        boolean aboveShortcut = Folder.willAccept(v.getTag());
        boolean willBecomeShortcut = Folder.willAccept(newView.getTag());

        if (aboveShortcut && willBecomeShortcut) {
            ItemInfo sourceInfo = (ItemInfo) newView.getTag();
            ItemInfo destInfo = (ItemInfo) v.getTag();
            // if the drag started here, we need to remove it from the workspace
            if (!external) {
                getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
            }

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer().getDescendantRectRelativeToSelf(v, folderLocation);
            target.removeView(v);
            mStatsLogManager.logger().withItemInfo(destInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_FOLDER_CREATED);
            FolderIcon fi = mLauncher.addFolder(target, container, screenId, targetCell[0],
                    targetCell[1]);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;

            // If the dragView is null, we can't animate
            boolean animate = d != null;
            if (animate) {
                // In order to keep everything continuous, we hand off the currently rendered
                // folder background to the newly created icon. This preserves animation state.
                fi.setFolderBackground(mFolderCreateBg);
                mFolderCreateBg = new PreviewBackground(getContext());
                fi.performCreateAnimation(destInfo, v, sourceInfo, d, folderLocation, scale);
            } else {
                fi.prepareCreateAnimation(v);
                fi.getFolder().addFolderContent(destInfo);
                fi.getFolder().addFolderContent(sourceInfo);
            }
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(View newView, CellLayout target, int[] targetCell,
            float distance, DragObject d, boolean external) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;

        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (!mAddToExistingFolderOnDrop) return false;
        mAddToExistingFolderOnDrop = false;

        return addToExistingFolder(dropOverView, d, external);
    }

    boolean addToExistingFolder(View dropOverView, DragObject d, boolean external) {
        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                mStatsLogManager.logger().withItemInfo(fi.mInfo).withInstanceId(d.logInstanceId)
                        .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED_ON_FOLDER_ICON);
                fi.onDrop(d, false /* itemReturnedOnFailedDrop */);
                // if the drag started here, we need to remove it from the workspace
                if (!external) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                }
                return true;
            }
        }

        // TextLauncher: no home screen files (file system folders).

        return false;
    }

    @Override
    public void prepareAccessibilityDrop() {}

    @Override
    public void onDrop(final DragObject d, DragOptions options) {
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            mapPointFromDropLayout(dropTargetLayout, mDragViewVisualCenter);
        }

        boolean droppedOnOriginalCell = false;

        boolean snappedToNewPage = false;
        boolean resizeOnDrop = false;
        Runnable onCompleteRunnable = null;
        if (d.dragSource != this || mDragInfo == null) {
            final int[] touchXY = new int[]{(int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1]};
            onDropExternal(touchXY, dropTargetLayout, d);
        } else {
            final View cell = mDragInfo.cell;
            if (mDragInfo != null) {
                int sourceScreenId = mDragInfo.screenId;
                int sourcePageIndex = getPageIndexForScreenId(sourceScreenId);
                // Now you have the source page index
                // TextLauncher: one page, which is never removed (willPageBeEmptyAfterItemRemoval).
                boolean deferAccessibilityFocus = false;
                View movedItemCell = cell;
                mMoveItemA11yState = new MoveItemA11yState(
                    /* movedItemCell= */ movedItemCell,
                    /* deferAccessibilityFocus= */ deferAccessibilityFocus);
            }
            boolean droppedOnOriginalCellDuringTransition = false;
            if (dropTargetLayout != null && !d.cancelled) {
                // Move internally
                boolean hasMovedLayouts = (getParentCellLayoutForView(cell) != dropTargetLayout);
                boolean hasMovedIntoHotseat = mLauncher.isHotseatLayout(dropTargetLayout);
                int container = hasMovedIntoHotseat ? CONTAINER_HOTSEAT : CONTAINER_DESKTOP;
                int screenId = (mTargetCell[0] < 0) ?
                        mDragInfo.screenId : getCellLayoutId(dropTargetLayout);
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;
                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.

                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0], (int)
                        mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);
                float distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                        mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

                // If the item being dropped is a shortcut and the nearest drop
                // cell also contains a shortcut, then create a folder with the two shortcuts.
                if (createUserFolderIfNecessary(cell, container, dropTargetLayout, mTargetCell,
                        distance, false, d)
                        || addToExistingFolderIfNecessary(cell, dropTargetLayout, mTargetCell,
                        distance, d, false)) {
                    // TextLauncher: no edit mode state (EDIT_MODE).
                    mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
                    return;
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                ItemInfo item = d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                CellPos originalPresenterPos = getCellPosMapper().mapModelToPresenter(item);
                droppedOnOriginalCell = originalPresenterPos.screenId == screenId
                        && item.container == container
                        && originalPresenterPos.cellX == mTargetCell[0]
                        && originalPresenterPos.cellY == mTargetCell[1];
                droppedOnOriginalCellDuringTransition = droppedOnOriginalCell && mIsSwitchingState;

                // When quickly moving an item, a user may accidentally rearrange their
                // workspace. So instead we move the icon back safely to its original position.
                boolean returnToOriginalCellToPreventShuffling = !isFinishedSwitchingState()
                        && !droppedOnOriginalCellDuringTransition && !dropTargetLayout
                        .isRegionVacant(mTargetCell[0], mTargetCell[1], spanX, spanY);
                int[] resultSpan = new int[2];
                if (returnToOriginalCellToPreventShuffling) {
                    mTargetCell[0] = mTargetCell[1] = -1;
                } else {
                    mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                            (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                            cell, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP);
                }

                boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

                // if the widget resizes on drop
                if (foundCell && (cell instanceof AppWidgetHostView) &&
                        (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY)) {
                    resizeOnDrop = true;
                    item.spanX = resultSpan[0];
                    item.spanY = resultSpan[1];
                    AppWidgetHostView awhv = (AppWidgetHostView) cell;
                    WidgetSizeHandler.updateSizeRanges(awhv, resultSpan[0], resultSpan[1]);
                }

                if (foundCell) {
                    int targetScreenIndex = getPageIndexForScreenId(screenId);
                    int snapScreen = getLeftmostVisiblePageForIndex(targetScreenIndex);
                    // On large screen devices two pages can be shown at the same time, and snap
                    // isn't needed if the source and target screens appear at the same time
                    if (snapScreen != mCurrentPage && !hasMovedIntoHotseat) {
                        snapToPage(snapScreen);
                        snappedToNewPage = true;
                    }
                    final ItemInfo info = (ItemInfo) cell.getTag();
                    if (hasMovedLayouts) {
                        // Reparent the view
                        CellLayout parentCell = getParentCellLayoutForView(cell);
                        if (parentCell != null) {
                            parentCell.removeView(cell);
                        } else if (mDragInfo.cell instanceof LauncherAppWidgetHostView) {
                            d.dragView.detachContentView(/* reattachToPreviousParent= */ false);
                        } else if (FeatureFlags.IS_STUDIO_BUILD) {
                            throw new NullPointerException("mDragInfo.cell has null parent");
                        }
                        addInScreen(cell, container, screenId, mTargetCell[0], mTargetCell[1],
                                info.spanX, info.spanY);
                    }

                    // update the item's position after drop
                    CellLayoutLayoutParams lp = (CellLayoutLayoutParams) cell.getLayoutParams();
                    lp.setTmpCellX(mTargetCell[0]);
                    lp.setCellX(mTargetCell[0]);
                    lp.setTmpCellY(mTargetCell[1]);
                    lp.setCellY(mTargetCell[1]);
                    lp.cellHSpan = item.spanX;
                    lp.cellVSpan = item.spanY;
                    lp.isLockedToGrid = true;

                    if (container != CONTAINER_HOTSEAT
                            && cell instanceof LauncherAppWidgetHostView) {

                        // We post this call so that the widget has a chance to be placed
                        // in its final location
                        onCompleteRunnable = getWidgetResizeFrameRunnable(options,
                                (LauncherAppWidgetHostView) cell, dropTargetLayout);
                    }
                    mLauncher.getModelWriter().modifyItemInDatabase(info, container, screenId,
                            lp.getCellX(), lp.getCellY(), item.spanX, item.spanY);
                } else {
                    if (!returnToOriginalCellToPreventShuffling) {
                        onNoCellFound(dropTargetLayout, d.dragInfo, d.logInstanceId);
                    }
                    if (mDragInfo.cell instanceof LauncherAppWidgetHostView) {
                        d.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                    }

                    // If we can't find a drop location, we return the item to its original position
                    CellLayoutLayoutParams lp = (CellLayoutLayoutParams) cell.getLayoutParams();
                    mTargetCell[0] = lp.getCellX();
                    mTargetCell[1] = lp.getCellY();
                    CellLayout layout = (CellLayout) cell.getParent().getParent();
                    layout.markCellsAsOccupiedForView(cell);
                }
            } else {
                // When drag is cancelled, reattach content view back to its original parent.
                if (cell instanceof LauncherAppWidgetHostView) {
                    d.dragView.detachContentView(/* reattachToPreviousParent= */ true);

                    final CellLayout cellLayout = getParentCellLayoutForView(cell);
                    boolean pageIsVisible = isVisible(cellLayout);

                    if (pageIsVisible) {
                        onCompleteRunnable = getWidgetResizeFrameRunnable(options,
                                (LauncherAppWidgetHostView) cell, cellLayout);
                    }
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent().getParent();
            if (d.dragView.hasDrawn()) {
                if (droppedOnOriginalCellDuringTransition) {
                    // Animate the item to its original position, while simultaneously exiting
                    // spring-loaded mode so the page meets the icon where it was picked up.
                    final RunnableList callbackList = new RunnableList();
                    final Runnable onCompleteCallback = onCompleteRunnable;
                    LauncherState currentState = mLauncher.getStateManager().getState();
                    mLauncher.getDragController().animateDragViewToOriginalPosition(
                            /* onComplete= */ callbackList::executeAllAndDestroy, cell,
                            currentState.getTransitionDuration(mLauncher, true /* isToState */));
                    // TextLauncher: no edit mode state (EDIT_MODE).
                    mLauncher.getStateManager().goToState(NORMAL, /* delay= */ 0,
                            onCompleteCallback == null
                                    ? null
                                    : forSuccessCallback(
                                            () -> callbackList.add(onCompleteCallback)));
                    mLauncher.getDropTargetBar().onDragEnd();
                    parent.onDropChild(cell);
                    return;
                }
                final ItemInfo info = (ItemInfo) cell.getTag();
                boolean isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                        || info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
                if (isWidget && dropTargetLayout != null) {
                    // animate widget to a valid place
                    int animationType = resizeOnDrop ? ANIMATE_INTO_POSITION_AND_RESIZE :
                            ANIMATE_INTO_POSITION_AND_DISAPPEAR;
                    animateWidgetDrop(info, parent, d.dragView, null, animationType, cell, false);
                } else {
                    int duration = snappedToNewPage ? ADJACENT_SCREEN_DROP_DURATION : -1;
                    mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, duration,
                            this);
                }
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                cell.setVisibility(VISIBLE);
            }
            parent.onDropChild(cell);

            // TextLauncher: no edit mode state (EDIT_MODE).
            mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY,
                    onCompleteRunnable == null ? null : forSuccessCallback(onCompleteRunnable));
            mStatsLogManager.logger().withItemInfo(d.dragInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED);
        }

        if (d.stateAnnouncer != null && !droppedOnOriginalCell) {
            d.stateAnnouncer.completeAction(R.string.item_moved);
        }
    }

    @Nullable
    private Runnable getWidgetResizeFrameRunnable(DragOptions options,
            LauncherAppWidgetHostView hostView, CellLayout cellLayout) {
        AppWidgetProviderInfo pInfo = hostView.getAppWidgetInfo();
        if (pInfo != null && pInfo.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) {
            return () -> {
                if (!isPageInTransition()) {
                    AppWidgetResizeFrame.showForWidget(hostView, cellLayout);
                }
            };
        }
        return null;
    }

    public void onNoCellFound(
            View dropTargetLayout, ItemInfo itemInfo, @Nullable InstanceId logInstanceId) {
        // TextLauncher: no hotseat (hotseat_out_of_space), and the Launcher stand-in is not a
        // Context.
        int strId = R.string.out_of_space;
        Toast.makeText(mLauncher.asContext(), mLauncher.asContext().getString(strId),
                Toast.LENGTH_SHORT).show();
        StatsLogManager.StatsLogger logger = mStatsLogManager.logger().withItemInfo(itemInfo);
        if (logInstanceId != null) {
            logger = logger.withInstanceId(logInstanceId);
        }
        logger.log(LauncherEvent.LAUNCHER_ITEM_DROP_FAILED_INSUFFICIENT_SPACE);
    }

    @Override
    public void onDragEnter(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnter", 1, 1);
        }

        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;

        mDropToLayout = null;
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1]);
    }

    @Override
    public void onDragExit(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragExit", -1, 0);
        }

        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        mDropToLayout = mDragTargetLayout;
        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }

        // Reset the previous drag target
        setCurrentDropLayout(null);
        setCurrentDragOverlappingLayout(null);

        // TextLauncher: one page, so no page switching while dragging (SpringLoadedDragController).
    }

    private void enforceDragParity(String event, int update, int expectedValue) {
        enforceDragParity(this, event, update, expectedValue);
        for (int i = 0; i < getChildCount(); i++) {
            enforceDragParity(getChildAt(i), event, update, expectedValue);
        }
    }

    private void enforceDragParity(View v, String event, int update, int expectedValue) {
        Object tag = v.getTag(R.id.drag_event_parity);
        int value = tag == null ? 0 : (Integer) tag;
        value += update;
        v.setTag(R.id.drag_event_parity, value);

        if (value != expectedValue) {
            Log.e(TAG, event + ": Drag contract violated: " + value);
        }
    }

    void setCurrentDropLayout(CellLayout layout) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.revertTempState();
            mDragTargetLayout.onDragExit();
        }
        mDragTargetLayout = layout;
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
        cleanupReorder(true);
        cleanupFolderCreation();
        setCurrentDropOverCell(-1, -1);
    }

    void setCurrentDragOverlappingLayout(CellLayout layout) {
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(false);
        }
        mDragOverlappingLayout = layout;
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(true);
        }
    }

    void setCurrentDropOverCell(int x, int y) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x;
            mDragOverY = y;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                cleanupAddToFolder();
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_ADD_TO_FOLDER) {
                cleanupReorder(true);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder();
                cleanupReorder(true);
            } else if (dragMode == DRAG_MODE_REORDER) {
                cleanupAddToFolder();
                cleanupFolderCreation();
            }
            mDragMode = dragMode;
        }
    }

    protected void cleanupFolderCreation() {
        if (mFolderCreateBg != null) {
            mFolderCreateBg.animateToRest();
        }

        // TextLauncher: no app pairs (AppPairIcon).
    }

    private void cleanupAddToFolder() {
        if (mDragOverFolderIcon != null) {
            mDragOverFolderIcon.onDragExit();
            mDragOverFolderIcon = null;
        }
        if (mFolderAddBg != null) {
            mFolderAddBg.animateToRest();
        }
    }

    protected void cleanupReorder(boolean cancelAlarm) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm();
        }
        mLastReorderX = -1;
        mLastReorderY = -1;
    }

    /*
    *
     * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
     * coordinate space. The argument xy is modified with the return result.
     */
    private void mapPointFromSelfToChild(View v, float[] xy) {
        xy[0] = xy[0] - v.getLeft();
        xy[1] = xy[1] - v.getTop();
    }

    /**
     * Updates the point in {@param xy} to point to the co-ordinate space of {@param layout}
     *
     * @param layout either hotseat of a page in workspace
     * @param xy     the point location in workspace co-ordinate space
     */
    private void mapPointFromDropLayout(CellLayout layout, float[] xy) {
        // TextLauncher: the page is in a scroll container, so the point is mapped through the drag
        // layer as for the hotseat, which accounts for the scroll (mapPointFromSelfToChild does
        // not).
        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, xy, true);
        mLauncher.getDragLayer().mapCoordInSelfToDescendant(layout, xy);
    }

    public void onDragOver(DragObject d) {
        // TextLauncher: no desktop drag mode (handleLauncherStateForDrag).

        // Skip drag over events while we are dragging over side pages
        if (!transitionStateShouldAllowDrop()) return;

        ItemInfo item = d.dragInfo;
        if (item == null) {
            if (FeatureFlags.IS_STUDIO_BUILD) {
                throw new NullPointerException("DragObject has null info");
            }
            return;
        }

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        // TextLauncher: one page, so no page switching while dragging (SpringLoadedDragController).
        setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1]);

        // Handle the drag over
        if (mDragTargetLayout != null) {
            // We want the point to be mapped to the dragTarget.
            mapPointFromDropLayout(mDragTargetLayout, mDragViewVisualCenter);

            int minSpanX = item.spanX;
            int minSpanY = item.spanY;
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX;
                minSpanY = item.minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], item.spanX, item.spanY,
                    mDragTargetLayout, mTargetCell);
            int reorderX = mTargetCell[0];
            int reorderY = mTargetCell[1];

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);

            float targetCellDistance = mDragTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

            manageFolderFeedback(targetCellDistance, d);

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                            mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            manageReorderOnDragOver(d, targetCellDistance, nearestDropOccupied, minSpanX, minSpanY,
                    reorderX, reorderY);

            if (mDragMode == DRAG_MODE_CREATE_FOLDER || mDragMode == DRAG_MODE_ADD_TO_FOLDER ||
                    !nearestDropOccupied) {
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.revertTempState();
                }
            }
        }
    }

    protected void manageReorderOnDragOver(DragObject d, float targetCellDistance,
            boolean nearestDropOccupied, int minSpanX, int minSpanY, int reorderX, int reorderY) {

        ItemInfo item = d.dragInfo;
        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        if (!nearestDropOccupied) {
            int[] span = new int[2];
            mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY,
                    child, mTargetCell, span, CellLayout.MODE_SHOW_REORDER_HINT);
            mDragTargetLayout.visualizeDropLocation(mTargetCell[0], mTargetCell[1], span[0],
                    span[1], d);
            nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                            mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);
        } else if ((mDragMode == DRAG_MODE_NONE || mDragMode == DRAG_MODE_REORDER)
                && (mLastReorderX != reorderX || mLastReorderY != reorderY)
                && targetCellDistance < mDragTargetLayout.getReorderRadius(mTargetCell, item.spanX,
                item.spanY)) {
            mReorderAlarm.cancelAlarm();
            mLastReorderX = reorderX;
            mLastReorderY = reorderY;
            mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY,
                    child, mTargetCell, new int[2], CellLayout.MODE_SHOW_REORDER_HINT);
            // Otherwise, if we aren't adding to or creating a folder and there's no pending
            // reorder, then we schedule a reorder
            ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                    minSpanX, minSpanY, item.spanX, item.spanY, d, child);
            mReorderAlarm.setOnAlarmListener(listener);
            mReorderAlarm.setAlarm(REORDER_TIMEOUT);
        }
    }

    /**
     * Updates {@link #mDragTargetLayout} and {@link #mDragOverlappingLayout}
     * based on the DragObject's position.
     *
     * The layout will be:
     * - The Hotseat if the drag object is over it
     * - A side page if we are in spring-loaded mode and the drag object is over it
     * - The current page otherwise
     *
     * @return whether the layout is different from the current {@link #mDragTargetLayout}.
     */
    private boolean setDropLayoutForDragObject(DragObject d, float centerX, float centerY) {
        CellLayout layout = null;
        // TextLauncher: no hotseat or neighbour pages; the drag is over the one page while it is
        // over the page's visible part, its scroll container (verifyInsidePage).
        View visiblePage = getPageScrollContainer();
        if (d.x >= visiblePage.getLeft() && d.x <= visiblePage.getRight()
                && d.y >= visiblePage.getTop() && d.y <= visiblePage.getBottom()) {
            layout = getPageAt(0);
        }

        // Update the current drop layout if the target changed
        if (layout != mDragTargetLayout) {
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);
            return true;
        }
        return false;
    }

    private void manageFolderFeedback(float distance, DragObject dragObject) {
        if (distance > mDragTargetLayout.getFolderCreationRadius(mTargetCell)) {
            if ((mDragMode == DRAG_MODE_ADD_TO_FOLDER
                    || mDragMode == DRAG_MODE_CREATE_FOLDER)) {
                setDragMode(DRAG_MODE_NONE);
            }
            return;
        }

        mDragOverView = mDragTargetLayout.getChildAt(mTargetCell[0], mTargetCell[1]);
        ItemInfo info = dragObject.dragInfo;
        boolean userFolderPending = willCreateUserFolder(info, mDragOverView, false);
        if (mDragMode == DRAG_MODE_NONE && userFolderPending) {
            if (Flags.msdlFeedback()) {
                mMSDLPlayerWrapper.playToken(MSDLToken.DRAG_INDICATOR_DISCRETE);
            }
            mFolderCreateBg = new PreviewBackground(getContext());
            // TextLauncher: the Launcher stand-in is not a Context.
            mFolderCreateBg.setup(mLauncher.asContext(), mLauncher, null,
                    mDragOverView.getMeasuredWidth(), mDragOverView.getPaddingTop());

            // The full preview background should appear behind the icon
            mFolderCreateBg.isClipping = false;

            // TextLauncher: no app pairs (AppPairIcon).

            mFolderCreateBg.animateToAccept(mDragTargetLayout, mTargetCell[0], mTargetCell[1]);
            mDragTargetLayout.clearDragOutlines();
            setDragMode(DRAG_MODE_CREATE_FOLDER);

            if (dragObject.stateAnnouncer != null) {
                ItemInfo dropOverInfo = (ItemInfo) mDragOverView.getTag();
                String pageDescription = getPageDescription(dropOverInfo.screenId);
                dragObject.stateAnnouncer.announce(WorkspaceAccessibilityHelper
                        .getDescriptionForDropOver(mDragOverView, getContext(), pageDescription));
            }
            return;
        }

        boolean willAddToFolder = willAddToExistingUserFolder(info, mDragOverView);
        if (willAddToFolder && mDragMode == DRAG_MODE_NONE) {
            if (mDragOverView instanceof FolderIcon) {
                mDragOverFolderIcon = ((FolderIcon) mDragOverView);
                // TextLauncher: the FolderIcon stand-in takes the page, whose drawing shows the
                // folder's accept animation.
                mDragOverFolderIcon.onDragEnter(info, mDragTargetLayout);
            }
            // TextLauncher: no home screen files (file system folders).

            if (mDragTargetLayout != null) {
                mDragTargetLayout.clearDragOutlines();
            }
            setDragMode(DRAG_MODE_ADD_TO_FOLDER);

            if (dragObject.stateAnnouncer != null) {
                ItemInfo dropOverInfo = (ItemInfo) mDragOverView.getTag();
                String pageDescription = getPageDescription(dropOverInfo.screenId);
                dragObject.stateAnnouncer.announce(WorkspaceAccessibilityHelper
                        .getDescriptionForDropOver(mDragOverView, getContext(), pageDescription));
            }
            return;
        }

        if (mDragMode == DRAG_MODE_ADD_TO_FOLDER && !willAddToFolder) {
            setDragMode(DRAG_MODE_NONE);
        }
        if (mDragMode == DRAG_MODE_CREATE_FOLDER && !userFolderPending) {
            setDragMode(DRAG_MODE_NONE);
        }
    }

    class ReorderAlarmListener implements OnAlarmListener {
        final float[] dragViewCenter;
        final int minSpanX, minSpanY, spanX, spanY;
        final DragObject dragObject;
        final View child;

        public ReorderAlarmListener(float[] dragViewCenter, int minSpanX, int minSpanY, int spanX,
                                    int spanY, DragObject dragObject, View child) {
            this.dragViewCenter = dragViewCenter;
            this.minSpanX = minSpanX;
            this.minSpanY = minSpanY;
            this.spanX = spanX;
            this.spanY = spanY;
            this.child = child;
            this.dragObject = dragObject;
        }

        public void onAlarm(Alarm alarm) {
            int[] resultSpan = new int[2];
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, mDragTargetLayout,
                    mTargetCell);

            mTargetCell = mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    child, mTargetCell, resultSpan, CellLayout.MODE_DRAG_OVER);

            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                mDragTargetLayout.revertTempState();
            } else {
                setDragMode(DRAG_MODE_REORDER);
            }

            mDragTargetLayout.visualizeDropLocation(mTargetCell[0], mTargetCell[1],
                    resultSpan[0], resultSpan[1], dragObject);
        }
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     * <p>
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final CellLayout cellLayout, DragObject d) {
        final int container = mLauncher.isHotseatLayout(cellLayout)
                ? CONTAINER_HOTSEAT
                : CONTAINER_DESKTOP;

        // TextLauncher: no system drags (SystemDragItemInfo).

        if (d.dragInfo instanceof PendingAddShortcutInfo) {
            // TextLauncher: the Launcher stand-in is not a Context.
            WorkspaceItemInfo si = ((PendingAddShortcutInfo) d.dragInfo)
                    .getActivityInfo(mLauncher.asContext()).createWorkspaceItemInfo();
            if (si != null) {
                d.dragInfo = si;
                si.container = container;
            }
        }

        ItemInfo info = d.dragInfo;
        // TextLauncher: no automation flags (AutomationRepository).
        int spanX = info.spanX;
        int spanY = info.spanY;
        if (mDragInfo != null) {
            spanX = mDragInfo.spanX;
            spanY = mDragInfo.spanY;
        }
        final int screenId = getCellLayoutId(cellLayout);
        // TextLauncher: one page, so no snapping to the drop's page.

        if (info instanceof PendingAddItemInfo) {
            final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) info;

            boolean findNearestVacantCell = true;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromWorkspaceCellVisualCenter(
                        mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
                if (willCreateUserFolder(d.dragInfo, cellLayout, mTargetCell, distance, true)
                        || willAddToExistingUserFolder(
                        d.dragInfo, cellLayout, mTargetCell, distance)) {
                    findNearestVacantCell = false;
                }
            }

            final ItemInfo item = d.dragInfo;
            boolean updateWidgetSize = false;
            if (findNearestVacantCell) {
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }
                int[] resultSpan = new int[2];
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, info.spanX, info.spanY,
                        null, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP_EXTERNAL);

                if (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY) {
                    updateWidgetSize = true;
                }
                item.spanX = resultSpan[0];
                item.spanY = resultSpan[1];
            }

            Runnable onAnimationCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    // Normally removeExtraEmptyScreen is called in Workspace#onDrop, but when
                    // adding an item that may not be dropped right away (due to a config activity)
                    // we defer the removal until the activity returns.
                    // TextLauncher: one page, so no extra empty screen (deferRemoveExtraEmptyScreen).

                    // When dragging and dropping from customization tray, we deal with creating
                    // widgets/shortcuts/folders in a slightly different way
                    mLauncher.addPendingItem(pendingInfo, container, screenId, mTargetCell,
                            item.spanX, item.spanY);
                    // TextLauncher: the home screen leaves the spring-loaded state here, where
                    // Launcher3 leaves it as its add flow completes (Launcher.onActivityResult,
                    // completeTwoStageWidgetDrop); the home screen's add flow waits until it is
                    // resumed and may show the system's bind dialog.
                    mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
                    mStatsLogManager.logger().withItemInfo(d.dragInfo)
                            .withInstanceId(d.logInstanceId)
                            .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED);
                }
            };
            boolean isWidget = pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    || pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

            AppWidgetHostView finalView = isWidget ?
                    ((PendingAddWidgetInfo) pendingInfo).boundWidget : null;

            if (finalView != null && updateWidgetSize) {
                WidgetSizeHandler.updateSizeRanges(finalView, item.spanX, item.spanY);
            }

            int animationStyle = ANIMATE_INTO_POSITION_AND_DISAPPEAR;
            // TextLauncher: the drag view always disappears; Launcher3 keeps it for a widget's
            // configuration activity (ANIMATE_INTO_POSITION_AND_REMAIN) until the result comes,
            // and TextLauncher has no WidgetAddFlowHandler (getHandler).
            animateWidgetDrop(info, cellLayout, d.dragView, onAnimationCompleteRunnable,
                    animationStyle, finalView, true);
        } else {
            // This is for other drag/drop cases, like dragging from All Apps
            mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
            // TODO(b/414409465) We could just create a new info making a copy with all the new
            //  needed values instead of choosing on each case what to modify.
            // TextLauncher: the home screen inflates the item (its HomeItemInflater is
            // Launcher3's ItemInflater).
            View view = mLauncher.inflateItem(info, cellLayout, container);
            d.dragInfo = info = (ItemInfo) view.getTag();

            // First we find the cell nearest to point at which the item is
            // dropped, without any consideration to whether there is an item there.
            if (touchXY != null) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromWorkspaceCellVisualCenter(
                        mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
                if (createUserFolderIfNecessary(view, container, cellLayout, mTargetCell, distance,
                        true, d)) {
                    return;
                }
                if (addToExistingFolderIfNecessary(view, cellLayout, mTargetCell, distance, d,
                        true)) {
                    return;
                }
            }

            if (touchXY != null) {
                // when dragging and dropping, just find the closest free spot
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, 1, 1,
                        null, mTargetCell, null, CellLayout.MODE_ON_DROP_EXTERNAL);
            } else {
                cellLayout.findCellForSpan(mTargetCell, 1, 1);
            }
            // Add the item to DB before adding to screen ensures that the container and other
            // values of the info is properly updated.
            mLauncher.getModelWriter().addOrMoveItemInDatabase(info, container, screenId,
                    mTargetCell[0], mTargetCell[1]);

            addInScreen(view, container, screenId, mTargetCell[0], mTargetCell[1],
                    info.spanX, info.spanY);
            cellLayout.onDropChild(view);
            cellLayout.getShortcutsAndWidgets().measureChild(view);

            if (d.dragView != null) {
                // We wrap the animation call in the temporary set and reset of the current
                // cellLayout to its final transform -- this means we animate the drag view to
                // the correct final location.
                setFinalTransitionTransform();
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, view, this);
                resetTransitionTransform();
            }

            // TextLauncher: no system drags, so no dropped URIs to move (HomeScreenFilesProvider).

            mStatsLogManager.logger().withItemInfo(d.dragInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED);
        }

    }

    private Drawable createWidgetDrawable(ItemInfo widgetInfo, View layout) {
        int[] unScaledSize = estimateItemSize(widgetInfo);
        int visibility = layout.getVisibility();
        layout.setVisibility(VISIBLE);

        int width = MeasureSpec.makeMeasureSpec(unScaledSize[0], MeasureSpec.EXACTLY);
        int height = MeasureSpec.makeMeasureSpec(unScaledSize[1], MeasureSpec.EXACTLY);
        layout.measure(width, height);
        layout.layout(0, 0, unScaledSize[0], unScaledSize[1]);
        Drawable widgetSnapshot = ViewEx.captureSnapshotAsDrawable(layout,
                /*debugString=*/ "NewWidgetDrop", unScaledSize[0], unScaledSize[1]);
        layout.setVisibility(visibility);
        return widgetSnapshot;
    }

    private void getFinalPositionForDropAnimation(int[] loc, float[] scaleXY,
            DragView dragView, CellLayout layout, ItemInfo info, int[] targetCell, boolean scale,
            final View finalView) {
        // Now we animate the dragView, (ie. the widget or shortcut preview) into its final
        // location and size on the home screen.
        int spanX = info.spanX;
        int spanY = info.spanY;

        Rect r = estimateItemPosition(layout, targetCell[0], targetCell[1], spanX, spanY);
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) {
            DeviceProfile profile = mLauncher.getDeviceProfile();
            // TextLauncher: LauncherAppWidgetHostView is the navigable widget view
            // (NavigableAppWidgetHostView).
            if (finalView instanceof LauncherAppWidgetHostView) {
                Rect widgetPadding = profile.getWorkspaceProfile().getWidgetPadding();
                r.left -= widgetPadding.left;
                r.right += widgetPadding.right;
                r.top -= widgetPadding.top;
                r.bottom += widgetPadding.bottom;
            }
            PointF appWidgetScale = profile.getAppWidgetScale(null);
            Utilities.shrinkRect(r, appWidgetScale.x, appWidgetScale.y);
        }

        mTempFXY[0] = r.left;
        mTempFXY[1] = r.top;
        setFinalTransitionTransform();
        float cellLayoutScale =
                mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(layout, mTempFXY, true);
        resetTransitionTransform();
        Utilities.roundArray(mTempFXY, loc);

        if (scale) {
            float dragViewScaleX = (1.0f * r.width()) / dragView.getMeasuredWidth();
            float dragViewScaleY = (1.0f * r.height()) / dragView.getMeasuredHeight();

            // The animation will scale the dragView about its center, so we need to center about
            // the final location.
            loc[0] -= (dragView.getMeasuredWidth() - cellLayoutScale * r.width()) / 2
                    - Math.ceil(layout.getUnusedHorizontalSpace() / 2f);
            loc[1] -= (dragView.getMeasuredHeight() - cellLayoutScale * r.height()) / 2;
            scaleXY[0] = dragViewScaleX * cellLayoutScale;
            scaleXY[1] = dragViewScaleY * cellLayoutScale;
        } else {
            // Since we are not cross-fading the dragView, align the drag view to the
            // final cell position.
            float dragScale = dragView.getInitialScale() * cellLayoutScale;
            loc[0] += (dragScale - 1) * dragView.getWidth() / 2;
            loc[1] += (dragScale - 1) * dragView.getHeight() / 2;
            scaleXY[0] = scaleXY[1] = dragScale;

            // If a dragRegion was provided, offset the final position accordingly.
            Rect dragRegion = dragView.getDragRegion();
            if (dragRegion != null) {
                loc[0] += cellLayoutScale * dragRegion.left;
                loc[1] += cellLayoutScale * dragRegion.top;
            }
        }
    }

    public void animateWidgetDrop(ItemInfo info, CellLayout cellLayout, final DragView dragView,
            final Runnable onCompleteRunnable, int animationType, @Nullable final View finalView,
            boolean external) {
        int[] finalPos = new int[2];
        float scaleXY[] = new float[2];
        boolean scalePreview = !(info instanceof PendingAddShortcutInfo);
        getFinalPositionForDropAnimation(finalPos, scaleXY, dragView, cellLayout, info, mTargetCell,
                scalePreview, finalView);

        // TextLauncher: the Launcher stand-in is not a Context.
        Resources res = mLauncher.asContext().getResources();
        final int duration = res.getInteger(R.integer.config_dropAnimMaxDuration) - 200;

        boolean isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET ||
                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
        if ((animationType == ANIMATE_INTO_POSITION_AND_RESIZE || external)
                && finalView != null
                && dragView.getContentView() != finalView) {
            Drawable crossFadeDrawable = createWidgetDrawable(info, finalView);
            dragView.crossFadeContent(crossFadeDrawable, (int) (duration * 0.8f));
        } else if (isWidget && external) {
            scaleXY[0] = scaleXY[1] = Math.min(scaleXY[0], scaleXY[1]);
        }

        DragLayer dragLayer = mLauncher.getDragLayer();
        if (animationType == CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION) {
            mLauncher.getDragLayer().animateViewIntoPosition(dragView, finalPos, 0f, 0.1f, 0.1f,
                    DragLayer.ANIMATION_END_DISAPPEAR, onCompleteRunnable, duration);
        } else {
            int endStyle;
            if (animationType == ANIMATE_INTO_POSITION_AND_REMAIN) {
                endStyle = DragLayer.ANIMATION_END_REMAIN_VISIBLE;
            } else {
                endStyle = DragLayer.ANIMATION_END_DISAPPEAR;
            }

            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    if (finalView != null) {
                        finalView.setVisibility(VISIBLE);
                    }
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }
                }
            };
            dragLayer.animateViewIntoPosition(dragView, finalPos[0],
                    finalPos[1], 1, scaleXY[0], scaleXY[1], onComplete, endStyle,
                    duration, this);
        }
    }

    public void setFinalTransitionTransform() {
        // TextLauncher: the workspace does not switch states while dropping (isSwitchingState is
        // always false: the states' look is the home screen's).
    }

    public void resetTransitionTransform() {
        // TextLauncher: see setFinalTransitionTransform.
    }

    /**
     * Return the current CellInfo describing our current drag; this method exists
     * so that Launcher can sync this object with the correct info when the activity is created/
     * destroyed
     */
    public CellInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     * <p>
     * pixelX and pixelY should be in the coordinate system of layout
     */
    @Thunk
    int[] findNearestArea(int pixelX, int pixelY,
                          int spanX, int spanY, CellLayout layout, int[] recycle) {
        return layout.findNearestAreaIgnoreOccupied(
                pixelX, pixelY, spanX, spanY, recycle);
    }

    // TextLauncher: public, called by the home screen (Launcher.setupViews in Launcher3).
    public void setup(DragController dragController) {
        // TextLauncher: one page, so no page switching while dragging (SpringLoadedDragController).
        mDragController = dragController;

        // TextLauncher: no page layers (updateChildrenLayersEnabled).
    }

    /**
     * Called at the end of a drag which originated on the workspace.
     */
    public void onDropCompleted(final View target, final DragObject d,
                                final boolean success) {
        if (success) {
            if (target != this && mDragInfo != null) {
                removeWorkspaceItem(mDragInfo.cell);
            }
        } else if (mDragInfo != null) {
            // When drag is cancelled, reattach content view back to its original parent.
            if (mDragInfo.cell instanceof LauncherAppWidgetHostView && d.dragView != null) {
                d.dragView.detachContentView(/* reattachToPreviousParent= */ true);
            }
            final CellLayout cellLayout = mLauncher.getCellLayout(
                    mDragInfo.container, mDragInfo.screenId);
            if (cellLayout != null) {
                cellLayout.onDropChild(mDragInfo.cell);
            } else if (FeatureFlags.IS_STUDIO_BUILD) {
                throw new RuntimeException("Invalid state: cellLayout == null in "
                        + "Workspace#onDropCompleted. Please file a bug. ");
            }
        }
        View cell = getViewByItemId(d.originalDragInfo.id);
        if (d.cancelled && cell != null) {
            cell.setVisibility(VISIBLE);
            // TextLauncher: a result that comes after the drag ended (the uninstall drop
            // target's, on resume) marks the item's cells as occupied again; Launcher3 leaves them
            // free until the next drag or binding.
            if (mDragInfo == null) {
                CellLayout page = getParentCellLayoutForView(cell);
                if (page != null) {
                    page.markCellsAsOccupiedForView(cell);
                }
            }
        }
        mDragInfo = null;
    }

    /**
     * For opposite operation. See {@link #addInScreen}.
     */
    public void removeWorkspaceItem(View v) {
        CellLayout parentCell = getParentCellLayoutForView(v);
        if (parentCell != null) {
            parentCell.removeView(v);
        } else if (FeatureFlags.IS_STUDIO_BUILD) {
            // When an app is uninstalled using the drop target, we wait until resume to remove
            // the icon. We also remove all the corresponding items from the workspace at
            // {@link Launcher#bindComponentsRemoved}. That call can come before or after
            // {@link Launcher#mOnResumeCallbacks} depending on how busy the worker thread is.
            Log.e(TAG, "mDragInfo.cell has null parent");
        }
        if (v instanceof DropTarget) {
            mDragController.removeDropTarget((DropTarget) v);
        }
    }

    public boolean isDropEnabled() {
        return true;
    }

    /**
     * Returns a specific CellLayout
     */
    // TextLauncher: public, called by the home screen (Launcher.addFolder in Launcher3).
    public CellLayout getParentCellLayoutForView(View v) {
        for (CellLayout layout : getWorkspaceAndHotseatCellLayouts()) {
            if (layout.getShortcutsAndWidgets().indexOfChild(v) > -1) {
                return layout;
            }
        }
        return null;
    }

    /**
     * Removes items that match the {@param matcher}. When applications are removed
     * as a part of an update, this is called to ensure that other widgets and application
     * shortcuts are not removed.
     *
     * @param persistChanges if true, any dependent changes will be persisted to the DB
     */
    public void removeItemsByMatcher(final Predicate<ItemInfo> matcher, boolean persistChanges) {
        for (CellLayout layout : getWorkspaceAndHotseatCellLayouts()) {
            ShortcutAndWidgetContainer container = layout.getShortcutsAndWidgets();
            // Iterate in reverse order as we are removing items
            for (int i = container.getChildCount() - 1; i >= 0; i--) {
                View child = container.getChildAt(i);
                ItemInfo info = (ItemInfo) child.getTag();

                if (matcher.test(info)) {
                    layout.removeViewInLayout(child);
                    if (child instanceof DropTarget) {
                        mDragController.removeDropTarget((DropTarget) child);
                    }
                } else if (child instanceof FolderIcon folderIcon) {
                    FolderInfo folderInfo = (FolderInfo) info;
                    ItemInfo[] matches = folderInfo.getContents().stream()
                            .filter(matcher)
                            .toArray(ItemInfo[]::new);
                    if (matches.length > 0) {
                        folderIcon.getFolder().removeFolderContent(false, matches);
                        if (folderIcon.getFolder().isOpen()) {
                            folderIcon.getFolder().close(false /* animate */);
                        }
                    }
                } else if (info instanceof AppPairInfo api) {
                    // If an app pair's member apps are being removed, delete the whole app pair.
                    if (api.anyMatch(matcher)) {
                        mLauncher.removeItem(child, info, persistChanges);
                    }
                }
            }
        }

        if (persistChanges) {
            // TextLauncher: one page, so no empty screens to strip (stripEmptyScreens).
        }
        // TextLauncher: the page is laid out again, as the views were removed in layout.
        for (CellLayout layout : getWorkspaceAndHotseatCellLayouts()) {
            layout.requestLayout();
        }
    }

    @Override
    public View mapOverItems(@NonNull ItemOperator op) {
        return mapOverCellLayouts(getWorkspaceAndHotseatCellLayouts(), op);
    }

    /**
     * Perform {param op} over all the items in the provided {param layouts} until a match is found
     */
    public static View mapOverCellLayouts(CellLayout[] layouts, ItemOperator op) {
        for (CellLayout layout : layouts) {
            // TODO(b/128460496) Potential race condition where layout is not yet loaded
            View result = layout == null ? null : layout.mapOverItems(op);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Remove workspace icons & widget information related to items in matcher.
     *
     * @param matcher the matcher generated by the caller.
     */
    public void persistRemoveItemsByMatcher(Predicate<ItemInfo> matcher,
                                            @Nullable final String reason) {
        mLauncher.getModelWriter().deleteItemsFromDatabase(matcher, reason);
        removeItemsByMatcher(matcher, true);
    }

    /**
     * Set the given view's pivot point to match the workspace's, so that it scales together. Since
     * both this view and workspace can move, transform the point manually instead of using
     * dragLayer.getDescendantCoordRelativeToSelf and related methods.
     */
    public void setPivotToScaleWithSelf(View sibling) {
        sibling.setPivotY(getPivotY() + getTop()
                - sibling.getTop() - sibling.getTranslationY());
        sibling.setPivotX(getPivotX() + getLeft()
                - sibling.getLeft() - sibling.getTranslationX());
    }

    // TextLauncher: not a PagedView, and one page (mNextPage).
    protected String getCurrentPageDescription() {
        return getPageDescription(mCurrentPage);
    }

    /**
     * @param page page index.
     * @return Description of the page at the given page index.
     */
    @Override
    public String getPageDescription(int page) {
        // TextLauncher: the pages are not children of this workspace (getPageCount), and there is
        // no extra empty screen (one page).
        int nScreens = getPageCount();
        if (nScreens == 0) {
            // When the workspace is not loaded, we do not know how many screen will be bound.
            return getContext().getString(R.string.home_screen);
        }
        int panelCount = getPanelCount();
        int currentPage = (page / panelCount) + 1;
        int totalPages = nScreens / panelCount + nScreens % panelCount;

        return getContext().getString(R.string.workspace_scroll_format, currentPage, totalPages);
    }

    @Override
    public CellPosMapper getCellPosMapper() {
        return mLauncher.getCellPosMapper();
    }

    private class StateTransitionListener extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator anim) {
            mTransitionProgress = anim.getAnimatedFraction();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            onStartStateTransition();
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            onEndStateTransition();
        }
    }

    // TextLauncher: members TextLauncher adds.

    /** PagedView.mCurrentPage: the one page. */
    protected int mCurrentPage = 0;

    /** The page's scroll container, the workspace's child that shows the page. */
    private View mPageScrollContainer;

    /**
     * Workspace.insertNewWorkspaceScreen for the one page: the page, and its scroll container,
     * which this workspace holds.
     */
    public void addPage(CellLayout page, View scrollContainer) {
        mWorkspaceScreens.put(FIRST_SCREEN_ID, page);
        mScreenOrder.add(FIRST_SCREEN_ID);
        mPageScrollContainer = scrollContainer;
        addView(scrollContainer, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mStateTransitionAnimation.applyChildState(
                mLauncher.getStateManager().getState(), page, 0);
    }

    /** The page's scroll container, in which the page is visible. */
    public View getPageScrollContainer() {
        return mPageScrollContainer;
    }

    /** PagedView.getPageCount */
    public int getPageCount() {
        return mWorkspaceScreens.size();
    }

    /** PagedView.getPageAt */
    public CellLayout getPageAt(int index) {
        return index == 0 ? mWorkspaceScreens.get(FIRST_SCREEN_ID) : null;
    }

    /** PagedView.getCurrentPage */
    public int getCurrentPage() {
        return mCurrentPage;
    }

    /** PagedView.getDestinationPage */
    public int getDestinationPage() {
        return 0;
    }

    /** PagedView.getNextPage */
    public int getNextPage() {
        return 0;
    }

    /** PagedView.setCurrentPage: there is only one. */
    public void setCurrentPage(int currentPage) {
    }

    /** PagedView.getLeftmostVisiblePageForIndex */
    public int getLeftmostVisiblePageForIndex(int pageIndex) {
        return 0;
    }

    /** PagedView.snapToPage: there is only one page. */
    public boolean snapToPage(int whichPage) {
        return false;
    }

    /** PagedView.isVisible: the one page is always shown. */
    protected boolean isVisible(View child) {
        return true;
    }

    /** PagedView.isPageInTransition: there are no page transitions. */
    public boolean isPageInTransition() {
        return false;
    }

    /**
     * Workspace.getWorkspaceAndHotseatCellLayouts. TextLauncher: the one page, and no hotseat.
     */
    CellLayout[] getWorkspaceAndHotseatCellLayouts() {
        return getPageCount() > 0 ? new CellLayout[] { getPageAt(0) } : new CellLayout[0];
    }
}
