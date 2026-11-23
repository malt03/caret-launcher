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

import static com.android.launcher3.dragndrop.DraggableView.DRAGGABLE_ICON;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_PREVIEW_OFFSET;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.view.View;
import android.view.ViewDebug;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.celllayout.ReorderAlgorithm;
import com.android.launcher3.celllayout.ReorderParameters;
import com.android.launcher3.celllayout.ReorderPreviewAnimation;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;

import com.google.android.msdl.data.model.MSDLToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

/**
 * Stand-in for Launcher3's CellLayout (android-17.0.0_r1), made from CellLayout.java by deleting
 * what TextLauncher does not use (measuring and laying out, touch and accessibility, saved state,
 * the folder leave-behind, the hotseat and folder container types, the database writes, the
 * debug visualizations); changes are marked with "TextLauncher:".
 *
 * <p>It holds the reorder algorithm's state (celllayout/ReorderAlgorithm.java,
 * celllayout/ViewCluster.kt), applies reorder results and keeps the drop location outline. The
 * home screen's is never attached: TextLauncher's grid view creates one, keeps its geometry in
 * sync, holds its children as its {@link ShortcutAndWidgetContainer} and draws its drop location
 * and delegated cell drawings. An open folder's page (folder/FolderPagedView.java) is attached as
 * in Launcher3: it holds its container as its child, which fills it, and draws nothing itself.
 */
// TextLauncher: a FrameLayout instead of a ViewGroup: it does not measure or lay out its
// container itself (the home screen's is not attached; a folder page's container fills it), and
// is a View because copied code reads View members of CellLayout (clipChildren and so on).
public class CellLayout extends FrameLayout {
    @ViewDebug.ExportedProperty(category = "launcher")
    @Thunk int mCellWidth;
    @ViewDebug.ExportedProperty(category = "launcher")
    @Thunk int mCellHeight;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected Point mBorderSpace;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCountY;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    @Thunk final int[] mTmpPoint = new int[2];
    @Thunk final int[] mTempLocation = new int[2];

    @Thunk final Rect mTempOnDrawCellToRect = new Rect();

    protected GridOccupancy mOccupied;
    public GridOccupancy mTmpOccupied;

    private final ArrayList<DelegatedCellDrawing> mDelegatedCellDrawings = new ArrayList<>();

    private static final int[] BACKGROUND_STATE_ACTIVE = new int[] { android.R.attr.state_active };
    private static final int[] BACKGROUND_STATE_DEFAULT = EMPTY_STATE_SET;
    protected final Drawable mBackground;

    // If we're actively dragging something over this screen, mIsDragOverlapping is true
    private boolean mIsDragOverlapping = false;

    // These arrays are used to implement the drag visualization on x-large screens.
    // They are used as circular arrays, indexed by mDragOutlineCurrent.
    @Thunk final CellLayoutLayoutParams[] mDragOutlines = new CellLayoutLayoutParams[4];
    @Thunk final float[] mDragOutlineAlphas = new float[mDragOutlines.length];
    private final InterruptibleInOutAnimator[] mDragOutlineAnims =
            new InterruptibleInOutAnimator[mDragOutlines.length];

    // Used as an index into the above 3 arrays; indicates which is the most current value.
    private int mDragOutlineCurrent = 0;

    @Thunk final ArrayMap<CellLayoutLayoutParams, Animator> mReorderAnimators = new ArrayMap<>();
    @Thunk final ArrayMap<Reorderable, ReorderPreviewAnimation> mShakeAnimators = new ArrayMap<>();

    private boolean mItemPlacementDirty = false;

    // Used to visualize the grid and drop locations
    private boolean mVisualizeDropLocation = true;
    private RectF mVisualizeGridRect = new RectF();
    private Paint mVisualizeGridPaint = new Paint();
    private int mGridVisualizationRoundingRadius;
    private int mGridColor = 0;
    protected float mSpringLoadedProgress = 0f;

    // When a drag operation is in progress, holds the nearest cell to the touch point
    private final int[] mDragCell = new int[2];
    private final int[] mDragCellSpan = new int[2];

    private boolean mDragging = false;
    private boolean mPlayDragHaptics = false;

    private final TimeInterpolator mEaseOutInterpolator;
    protected final ShortcutAndWidgetContainer mShortcutsAndWidgets;

    public static final float DEFAULT_SCALE = 1f;

    public static final int MODE_SHOW_REORDER_HINT = 0;
    public static final int MODE_DRAG_OVER = 1;
    public static final int MODE_ON_DROP = 2;
    public static final int MODE_ON_DROP_EXTERNAL = 3;
    public static final int MODE_ACCEPT_DROP = 4;
    private static final boolean DESTRUCTIVE_REORDER = false;

    public static final float REORDER_PREVIEW_MAGNITUDE = 0.12f;
    public static final int REORDER_ANIMATION_DURATION = 150;
    @Thunk final float mReorderPreviewAnimationMagnitude;

    public final int[] mDirectionVector = new int[2];

    ItemConfiguration mPreviousSolution = null;

    private final Rect mTempRect = new Rect();

    private final MSDLPlayerWrapper mMSDLPlayerWrapper;

    /**
     * TextLauncher: made by TextLauncher's grid view, which is the ShortcutAndWidgetContainer and
     * draws this layout. The icon size is the device profile's, which CellLayout reads itself.
     */
    public CellLayout(Context context, ShortcutAndWidgetContainer children, int iconSizePx) {
        super(context);
        // TextLauncher: no container type (always the workspace) and no device profile.
        mIconSizePx = iconSizePx;

        mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context);

        // A ViewGroup usually does not draw, but CellLayout needs to draw a rectangle to show
        // the user where a dragged item will land when dropped.
        setWillNotDraw(false);
        setClipToPadding(false);
        setClipChildren(false);

        // TextLauncher: the grid view (or the folder) sets the cell size and border space
        // (setCellGeometry, setCellDimensions).
        mBorderSpace = new Point();

        // TextLauncher: the grid view sets the grid size (setGridSize).
        mCountX = 1;
        mCountY = 1;
        mOccupied =  new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);

        Resources res = getResources();

        mBackground = getContext().getDrawable(R.drawable.bg_celllayout);
        // TextLauncher: no callback; the view that shows the page draws it (getPageBackground).
        mBackground.setAlpha(0);

        mGridColor = Themes.getAttrColor(getContext(), R.attr.workspaceAccentColor);
        mGridVisualizationRoundingRadius =
                res.getDimensionPixelSize(R.dimen.grid_visualization_rounding_radius);
        // TextLauncher: WorkspaceProfile.gridVisualizationPaddingX / Y, for visualizeGrid.
        mGridVisualizationPaddingX =
                res.getDimensionPixelSize(R.dimen.grid_visualization_horizontal_cell_spacing);
        mGridVisualizationPaddingY =
                res.getDimensionPixelSize(R.dimen.grid_visualization_vertical_cell_spacing);
        mReorderPreviewAnimationMagnitude = (REORDER_PREVIEW_MAGNITUDE
                * mIconSizePx); // TextLauncher: the icon size from the constructor.

        // Initialize the data structures used for the drag visualization.
        mEaseOutInterpolator = Interpolators.DECELERATE_QUINT; // Quint ease out
        mDragCell[0] = mDragCell[1] = -1;
        mDragCellSpan[0] = mDragCellSpan[1] = -1;
        for (int i = 0; i < mDragOutlines.length; i++) {
            mDragOutlines[i] = new CellLayoutLayoutParams(0, 0, 0, 0);
        }

        // When dragging things around the home screens, we show a green outline of
        // where the item will land. The outlines gradually fade out, leaving a trail
        // behind the drag path.
        // Set up all the animations that are used to implement this fading.
        final int duration = res.getInteger(R.integer.config_dragOutlineFadeTime);
        final float fromAlphaValue = 0;
        final float toAlphaValue = (float)res.getInteger(R.integer.config_dragOutlineMaxAlpha);

        Arrays.fill(mDragOutlineAlphas, fromAlphaValue);

        for (int i = 0; i < mDragOutlineAnims.length; i++) {
            final InterruptibleInOutAnimator anim =
                    new InterruptibleInOutAnimator(duration, fromAlphaValue, toAlphaValue);
            anim.getAnimator().setInterpolator(mEaseOutInterpolator);
            final int thisIndex = i;
            anim.getAnimator().addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    // If an animation is started and then stopped very quickly, we can still
                    // get spurious updates we've cleared the tag. Guard against this.
                    mDragOutlineAlphas[thisIndex] = (Float) animation.getAnimatedValue();
                    CellLayout.this.invalidate();
                }
            });
            // The animation holds a reference to the drag outline bitmap as long is it's
            // running. This way the bitmap can be GCed when the animations are complete.
            mDragOutlineAnims[i] = anim;
        }

        // TextLauncher: the grid view is the container; it is not added to this layout.
        mShortcutsAndWidgets = children;
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
    }

    /**
     * Change sizes of cells
     *
     * @param width  the new width of the cells
     * @param height the new height of the cells
     */
    public void setCellDimensions(int width, int height) {
        // TextLauncher: no fixed cell size to keep (mFixedCellWidth / Height): the layout does not
        // work its cell size out from its own size.
        mCellWidth = width;
        mCellHeight = height;
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
    }

    public void setGridSize(int x, int y) {
        mCountX = x;
        mCountY = y;
        mOccupied = new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
        requestLayout();
    }

    // Set whether or not to invert the layout horizontally if the layout is in RTL mode.
    public void setInvertIfRtl(boolean invert) {
        mShortcutsAndWidgets.setInvertIfRtl(invert);
    }

    /** Set if the drag is overlapping the sibling cell layout. */
    public void setIsDragOverlapping(boolean isDragOverlapping) {
        if (mIsDragOverlapping != isDragOverlapping) {
            mIsDragOverlapping = isDragOverlapping;
            mBackground.setState(mIsDragOverlapping
                    ? BACKGROUND_STATE_ACTIVE : BACKGROUND_STATE_DEFAULT);
            invalidate();
        }
    }

    public boolean getIsDragOverlapping() {
        return mIsDragOverlapping;
    }

    /**
     * Returns whether dropping an icon on the given View can create (or add to) a folder.
     */
    private boolean canCreateFolder(View child) {
        return child instanceof DraggableView
                && ((DraggableView) child).getViewType() == DRAGGABLE_ICON;
    }

    /**
     * Indicates the progress of the Workspace entering the SpringLoaded state; allows the
     * CellLayout to update various visuals for this state.
     *
     * @param progress
     */
    public void setSpringLoadedProgress(float progress) {
        if (Float.compare(progress, mSpringLoadedProgress) != 0) {
            mSpringLoadedProgress = progress;
            updateBgAlpha();
            // TextLauncher: no grid visualization (mVisualizeCells is for debugging in Launcher3).
        }
    }

    /**
     * See setSpringLoadedProgress
     * @return progress
     */
    public float getSpringLoadedProgress() {
        return mSpringLoadedProgress;
    }

    protected void updateBgAlpha() {
        mBackground.setAlpha((int) (mSpringLoadedProgress * 255));
    }

    protected void visualizeGrid(Canvas canvas) {
        // TextLauncher: no device profile; the icon size and the paddings come from the
        // constructor.
        int paddingX = Math.min((mCellWidth - mIconSizePx) / 2,
                mGridVisualizationPaddingX);
        int paddingY = Math.min((mCellHeight - mIconSizePx) / 2,
                mGridVisualizationPaddingY);

        mVisualizeGridPaint.setStrokeWidth(8);

        if (mVisualizeDropLocation) {
            for (int i = 0; i < mDragOutlines.length; i++) {
                final float alpha = mDragOutlineAlphas[i];
                if (alpha <= 0) continue;
                CellLayoutLayoutParams params = mDragOutlines[i];
                cellToRect(params.getCellX(), params.getCellY(), params.cellHSpan, params.cellVSpan,
                        mTempOnDrawCellToRect);
                mVisualizeGridRect.set(mTempOnDrawCellToRect);
                mVisualizeGridRect.inset(paddingX, paddingY);

                mVisualizeGridPaint.setAlpha(255);
                mVisualizeGridPaint.setStyle(Paint.Style.STROKE);
                mVisualizeGridPaint.setColor(Color.argb((int) (alpha),
                        Color.red(mGridColor), Color.green(mGridColor), Color.blue(mGridColor)));

                canvas.save();
                canvas.translate(getMarginForGivenCellParams(params), 0);
                canvas.drawRoundRect(mVisualizeGridRect, mGridVisualizationRoundingRadius,
                        mGridVisualizationRoundingRadius, mVisualizeGridPaint);
                canvas.restore();
            }
        }
    }

    protected float getMarginForGivenCellParams(CellLayoutLayoutParams params) {
        return 0;
    }

    /**
     * Add Delegated cell drawing
     */
    public void addDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.add(bg);
    }

    /**
     * Remove item from DelegatedCellDrawings
     */
    public void removeDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.remove(bg);
    }

    public int getCountX() {
        return mCountX;
    }

    public int getCountY() {
        return mCountY;
    }

    /**
     * Adds the given view to the CellLayout
     *
     * @param child view to add.
     * @param index index of the CellLayout children where to add the view.
     * @param childId id of the view.
     * @param params represent the logic of the view on the CellLayout.
     * @param markCells if the occupied cells should be marked or not
     * @return if adding the view was successful
     */
    public boolean addViewToCellLayout(View child, int index, int childId,
            CellLayoutLayoutParams params, boolean markCells) {
        final CellLayoutLayoutParams lp = params;

        // TextLauncher: no hotseat, whose icons hide their text.

        child.setScaleX(DEFAULT_SCALE);
        child.setScaleY(DEFAULT_SCALE);

        // Generate an id for each view, this assumes we have at most 256x256 cells
        // per workspace screen
        if (lp.getCellX() >= 0 && lp.getCellX() <= mCountX - 1
                && lp.getCellY() >= 0 && lp.getCellY() <= mCountY - 1) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCountY;

            child.setId(childId);
            mShortcutsAndWidgets.addView(child, index, lp);

            // TextLauncher: no accessibility action on the launcher's NORMAL state.

            if (markCells) markCellsAsOccupiedForView(child);

            return true;
        }
        return false;
    }

    @Override
    public void removeAllViews() {
        mOccupied.clear();
        mShortcutsAndWidgets.removeAllViews();
    }

    @Override
    public void removeView(View view) {
        markCellsAsUnoccupiedForView(view);
        mShortcutsAndWidgets.removeView(view);
    }

    /**
     * Given a cell coordinate, return the point that represents the upper left corner of that cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToPoint(int cellX, int cellY, int[] result) {
        cellToRect(cellX, cellY, 1, 1, mTempRect);
        result[0] = mTempRect.left;
        result[1] = mTempRect.top;
    }

    /**
     * Given a cell coordinate, return the point that represents the center of the cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToCenterPoint(int cellX, int cellY, int[] result) {
        regionToCenterPoint(cellX, cellY, 1, 1, result);
    }

    /**
     * Given a cell coordinate and span return the point that represents the center of the region
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    public void regionToCenterPoint(int cellX, int cellY, int spanX, int spanY, int[] result) {
        cellToRect(cellX, cellY, spanX, spanY, mTempRect);
        result[0] = mTempRect.centerX();
        result[1] = mTempRect.centerY();
    }

    /**
     * Returns the distance between the given coordinate and the visual center of the given cell.
     */
    public float getDistanceFromWorkspaceCellVisualCenter(float x, float y, int[] cell) {
        getWorkspaceCellVisualCenter(cell[0], cell[1], mTmpPoint);
        return (float) Math.hypot(x - mTmpPoint[0], y - mTmpPoint[1]);
    }

    // TextLauncher: icons are centered in their cell, so the visual center is the cell center.
    private void getWorkspaceCellVisualCenter(int cellX, int cellY, int[] outPoint) {
        cellToCenterPoint(cellX, cellY, outPoint);
    }

    /**
     * Returns the max distance from the center of a cell that can accept a drop to create a folder.
     */
    public float getFolderCreationRadius(int[] targetCell) {
        // TextLauncher: the icon size comes from the constructor instead of the device profile.
        float iconVisibleRadius = ICON_VISIBLE_AREA_FACTOR
                * mIconSizePx / 2;
        // Halfway between reorder radius and icon.
        return (getReorderRadius(targetCell, 1, 1) + iconVisibleRadius) / 2;
    }

    /**
     * Returns the max distance from the center of a cell that will start to reorder on drag over.
     */
    public float getReorderRadius(int[] targetCell, int spanX, int spanY) {
        int[] centerPoint = mTmpPoint;
        getWorkspaceCellVisualCenter(targetCell[0], targetCell[1], centerPoint);

        Rect cellBoundsWithSpacing = mTempRect;
        cellToRect(targetCell[0], targetCell[1], spanX, spanY, cellBoundsWithSpacing);
        cellBoundsWithSpacing.inset(-mBorderSpace.x / 2, -mBorderSpace.y / 2);

        if (canCreateFolder(getChildAt(targetCell[0], targetCell[1])) && spanX == 1 && spanY == 1) {
            // Take only the circle in the smaller dimension, to ensure we don't start reordering
            // too soon before accepting a folder drop.
            int minRadius = centerPoint[0] - cellBoundsWithSpacing.left;
            minRadius = Math.min(minRadius, centerPoint[1] - cellBoundsWithSpacing.top);
            minRadius = Math.min(minRadius, cellBoundsWithSpacing.right - centerPoint[0]);
            minRadius = Math.min(minRadius, cellBoundsWithSpacing.bottom - centerPoint[1]);
            return minRadius;
        }
        // Take up the entire cell, including space between this cell and the adjacent ones.
        // Multiply by span to scale radius
        return (float) Math.hypot(spanX * cellBoundsWithSpacing.width() / 2f,
                spanY * cellBoundsWithSpacing.height() / 2f);
    }

    public int getCellWidth() {
        return mCellWidth;
    }

    public int getCellHeight() {
        return mCellHeight;
    }

    public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
        return mShortcutsAndWidgets;
    }

    public View getChildAt(int cellX, int cellY) {
        return mShortcutsAndWidgets.getChildAt(cellX, cellY);
    }

    public boolean animateChildToPosition(final View child, int cellX, int cellY, int duration,
            int delay, boolean permanent, boolean adjustOccupied) {
        ShortcutAndWidgetContainer clc = getShortcutsAndWidgets();

        if (clc.indexOfChild(child) != -1 && (child instanceof Reorderable item)) {
            final CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();

            // We cancel any existing animations
            if (mReorderAnimators.containsKey(lp)) {
                mReorderAnimators.get(lp).cancel();
                mReorderAnimators.remove(lp);
            }


            if (adjustOccupied) {
                GridOccupancy occupied = permanent ? mOccupied : mTmpOccupied;
                occupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, false);
                occupied.markCells(cellX, cellY, lp.cellHSpan, lp.cellVSpan, true);
            }

            // Compute the new x and y position based on the new cellX and cellY
            // We leverage the actual layout logic in the layout params and hence need to modify
            // state and revert that state.
            final int oldX = lp.x;
            final int oldY = lp.y;
            lp.isLockedToGrid = true;
            if (permanent) {
                lp.setCellX(cellX);
                lp.setCellY(cellY);
            } else {
                lp.setTmpCellX(cellX);
                lp.setTmpCellY(cellY);
            }
            clc.setupLp(child);
            final int newX = lp.x;
            final int newY = lp.y;
            lp.x = oldX;
            lp.y = oldY;
            lp.isLockedToGrid = false;
            // End compute new x and y

            MultiTranslateDelegate mtd = item.getTranslateDelegate();
            float initPreviewOffsetX = mtd.getTranslationX(INDEX_REORDER_PREVIEW_OFFSET).getValue();
            float initPreviewOffsetY = mtd.getTranslationY(INDEX_REORDER_PREVIEW_OFFSET).getValue();
            final float finalPreviewOffsetX = newX - oldX;
            final float finalPreviewOffsetY = newY - oldY;

            // Exit early if we're not actually moving the view
            if (finalPreviewOffsetX == 0 && finalPreviewOffsetY == 0
                    && initPreviewOffsetX == 0 && initPreviewOffsetY == 0) {
                lp.isLockedToGrid = true;
                return true;
            }

            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(duration);
            mReorderAnimators.put(lp, va);

            va.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float r = (Float) animation.getAnimatedValue();
                    float x = (1 - r) * initPreviewOffsetX + r * finalPreviewOffsetX;
                    float y = (1 - r) * initPreviewOffsetY + r * finalPreviewOffsetY;
                    item.getTranslateDelegate().setTranslation(INDEX_REORDER_PREVIEW_OFFSET, x, y);
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;
                public void onAnimationEnd(Animator animation) {
                    // If the animation was cancelled, it means that another animation
                    // has interrupted this one, and we don't want to lock the item into
                    // place just yet.
                    if (!cancelled) {
                        lp.isLockedToGrid = true;
                        item.getTranslateDelegate()
                                .setTranslation(INDEX_REORDER_PREVIEW_OFFSET, 0, 0);
                        child.requestLayout();
                    }
                    if (mReorderAnimators.containsKey(lp)) {
                        mReorderAnimators.remove(lp);
                    }
                }
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }
            });
            va.setStartDelay(delay);
            va.start();
            return true;
        }
        return false;
    }

    // TextLauncher: public, called by the home drop target (Workspace in Launcher3).
    public void visualizeDropLocation(int cellX, int cellY, int spanX, int spanY,
            DropTarget.DragObject dragObject) {
        if (mDragCell[0] != cellX || mDragCell[1] != cellY || mDragCellSpan[0] != spanX
                || mDragCellSpan[1] != spanY) {
            determineIfDragHapticsPlay();
            if (mPlayDragHaptics && Flags.msdlFeedback()) {
                mMSDLPlayerWrapper.playToken(MSDLToken.DRAG_INDICATOR_DISCRETE);
            }
            mDragCell[0] = cellX;
            mDragCell[1] = cellY;
            mDragCellSpan[0] = spanX;
            mDragCellSpan[1] = spanY;

            final int oldIndex = mDragOutlineCurrent;
            mDragOutlineAnims[oldIndex].animateOut();
            mDragOutlineCurrent = (oldIndex + 1) % mDragOutlines.length;

            CellLayoutLayoutParams cell = mDragOutlines[mDragOutlineCurrent];
            cell.setCellX(cellX);
            cell.setCellY(cellY);
            cell.cellHSpan = spanX;
            cell.cellVSpan = spanY;

            mDragOutlineAnims[mDragOutlineCurrent].animateIn();
            invalidate();

            // TextLauncher: no accessible drag and drop, so there is nothing to announce.

        }
    }

    private void determineIfDragHapticsPlay() {
        if (mDragCell[0] != -1 || mDragCell[1] != -1
                || mDragCellSpan[0] != -1 || mDragCellSpan[1] != -1) {
            // The nearest cell is known and we can play haptics
            mPlayDragHaptics = true;
        }
    }

    public void clearDragOutlines() {
        final int oldIndex = mDragOutlineCurrent;
        mDragOutlineAnims[oldIndex].animateOut();
        mDragCell[0] = mDragCell[1] = -1;
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param minSpanX The minimum horizontal span required
     * @param minSpanY The minimum vertical span required
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    public int[] findNearestVacantArea(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY, int[] result, int[] resultSpan) {
        return findNearestArea(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, false,
                result, resultSpan);
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     * @param relativeXPos The X location relative to the Cell layout at which you want to search
     *                     for a vacant area.
     * @param relativeYPos The Y location relative to the Cell layout at which you want to search
     *                     for a vacant area.
     * @param minSpanX The minimum horizontal span required
     * @param minSpanY The minimum vertical span required
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param ignoreOccupied If true, the result can be an occupied cell
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    protected int[] findNearestArea(int relativeXPos, int relativeYPos, int minSpanX, int minSpanY,
            int spanX, int spanY, boolean ignoreOccupied, int[] result, int[] resultSpan) {
        // For items with a spanX / spanY > 1, the passed in point (relativeXPos, relativeYPos)
        // corresponds to the center of the item, but we are searching based on the top-left cell,
        // so we translate the point over to correspond to the top-left.
        relativeXPos = (int) (relativeXPos - (mCellWidth + mBorderSpace.x) * (spanX - 1) / 2f);
        relativeYPos = (int) (relativeYPos - (mCellHeight + mBorderSpace.y) * (spanY - 1) / 2f);

        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        double bestDistance = Double.MAX_VALUE;
        final Rect bestRect = new Rect(-1, -1, -1, -1);
        final Stack<Rect> validRegions = new Stack<>();

        final int countX = mCountX;
        final int countY = mCountY;

        if (minSpanX <= 0 || minSpanY <= 0 || spanX <= 0 || spanY <= 0 ||
                spanX < minSpanX || spanY < minSpanY) {
            return bestXY;
        }

        for (int y = 0; y < countY - (minSpanY - 1); y++) {
            inner:
            for (int x = 0; x < countX - (minSpanX - 1); x++) {
                int ySize = -1;
                int xSize = -1;
                if (!ignoreOccupied) {
                    // First, let's see if this thing fits anywhere
                    for (int i = 0; i < minSpanX; i++) {
                        for (int j = 0; j < minSpanY; j++) {
                            if (mOccupied.cells[x + i][y + j]) {
                                continue inner;
                            }
                        }
                    }
                    xSize = minSpanX;
                    ySize = minSpanY;

                    // We know that the item will fit at _some_ acceptable size, now let's see
                    // how big we can make it. We'll alternate between incrementing x and y spans
                    // until we hit a limit.
                    boolean incX = true;
                    boolean hitMaxX = xSize >= spanX;
                    boolean hitMaxY = ySize >= spanY;
                    while (!(hitMaxX && hitMaxY)) {
                        if (incX && !hitMaxX) {
                            for (int j = 0; j < ySize; j++) {
                                if (x + xSize > countX -1 || mOccupied.cells[x + xSize][y + j]) {
                                    // We can't move out horizontally
                                    hitMaxX = true;
                                }
                            }
                            if (!hitMaxX) {
                                xSize++;
                            }
                        } else if (!hitMaxY) {
                            for (int i = 0; i < xSize; i++) {
                                if (y + ySize > countY - 1 || mOccupied.cells[x + i][y + ySize]) {
                                    // We can't move out vertically
                                    hitMaxY = true;
                                }
                            }
                            if (!hitMaxY) {
                                ySize++;
                            }
                        }
                        hitMaxX |= xSize >= spanX;
                        hitMaxY |= ySize >= spanY;
                        incX = !incX;
                    }
                }
                final int[] cellXY = mTmpPoint;
                cellToCenterPoint(x, y, cellXY);

                // We verify that the current rect is not a sub-rect of any of our previous
                // candidates. In this case, the current rect is disqualified in favour of the
                // containing rect.
                Rect currentRect = new Rect(x, y, x + xSize, y + ySize);
                boolean contained = false;
                for (Rect r : validRegions) {
                    if (r.contains(currentRect)) {
                        contained = true;
                        break;
                    }
                }
                validRegions.push(currentRect);
                double distance = Math.hypot(cellXY[0] - relativeXPos,  cellXY[1] - relativeYPos);

                if ((distance <= bestDistance && !contained) ||
                        currentRect.contains(bestRect)) {
                    bestDistance = distance;
                    bestXY[0] = x;
                    bestXY[1] = y;
                    if (resultSpan != null) {
                        resultSpan[0] = xSize;
                        resultSpan[1] = ySize;
                    }
                    bestRect.set(currentRect);
                }
            }
        }

        // Return -1, -1 if no suitable location found
        if (bestDistance == Double.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        return bestXY;
    }

    public GridOccupancy getOccupied() {
        return mOccupied;
    }

    private void copySolutionToTempState(ItemConfiguration solution, View dragView) {
        mTmpOccupied.clear();

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            CellAndSpan c = solution.map.get(child);
            if (c != null) {
                lp.setTmpCellX(c.cellX);
                lp.setTmpCellY(c.cellY);
                lp.cellHSpan = c.spanX;
                lp.cellVSpan = c.spanY;
                mTmpOccupied.markCells(c, true);
            }
        }
        mTmpOccupied.markCells(solution, true);
    }

    private void animateItemsToSolution(ItemConfiguration solution, View dragView, boolean
            commitDragView) {

        GridOccupancy occupied = DESTRUCTIVE_REORDER ? mOccupied : mTmpOccupied;
        occupied.clear();

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellAndSpan c = solution.map.get(child);
            if (c != null) {
                animateChildToPosition(child, c.cellX, c.cellY, REORDER_ANIMATION_DURATION, 0,
                        DESTRUCTIVE_REORDER, false);
                occupied.markCells(c, true);
            }
        }
        if (commitDragView) {
            occupied.markCells(solution, true);
        }
    }


    // This method starts or changes the reorder preview animations
    private void beginOrAdjustReorderPreviewAnimations(ItemConfiguration solution,
            View dragView, int mode) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellAndSpan c = solution.map.get(child);
            boolean skip = mode == ReorderPreviewAnimation.MODE_HINT
                    && !solution.intersectingViews.contains(child);

            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            if (c != null && !skip && (child instanceof Reorderable)) {
                ReorderPreviewAnimation rha = new ReorderPreviewAnimation(child, mode,
                        lp.getCellX(), lp.getCellY(), c.cellX, c.cellY, c.spanX, c.spanY,
                        mReorderPreviewAnimationMagnitude, this, mShakeAnimators);
                rha.animate();
            }
        }
    }

    private void completeAndClearReorderPreviewAnimations() {
        for (ReorderPreviewAnimation a: mShakeAnimators.values()) {
            a.finishAnimation();
        }
        mShakeAnimators.clear();
    }

    private void commitTempPlacement(View dragView) {
        mTmpOccupied.copyTo(mOccupied);

        // TextLauncher: no database writes here; the app saves the items' cells and spans from
        // their views (Launcher.saveItemPlacement), and there is no cell position mapper.

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            ItemInfo info = (ItemInfo) child.getTag();
            // We do a null check here because the item info can be null in the case of the
            // AllApps button in the hotseat.
            if (info != null && child != dragView) {
                lp.setCellX(lp.getTmpCellX());
                lp.setCellY(lp.getTmpCellY());
            }
        }
    }

    private void setUseTempCoords(boolean useTempCoords) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) mShortcutsAndWidgets.getChildAt(
                    i).getLayoutParams();
            lp.useTmpCoords = useTempCoords;
        }
    }

    /**
     * For a given region, return the rectangle of the overlapping cell and span with the given
     * region including the region itself. If there is no overlap the rectangle will be
     * invalid i.e. -1, 0, -1, 0.
     */
    @Nullable
    public Rect getIntersectingRectanglesInRegion(final Rect region, final View dragView) {
        Rect boundingRect = new Rect(region);
        Rect r1 = new Rect();
        boolean isOverlapping = false;
        final int count = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellLayoutLayoutParams
                    lp = (CellLayoutLayoutParams) child.getLayoutParams();
            r1.set(lp.getCellX(), lp.getCellY(), lp.getCellX() + lp.cellHSpan,
                    lp.getCellY() + lp.cellVSpan);
            if (Rect.intersects(region, r1)) {
                isOverlapping = true;
                boundingRect.union(r1);
            }
        }
        return isOverlapping ? boundingRect : null;
    }

    public boolean isNearestDropLocationOccupied(int pixelX, int pixelY, int spanX, int spanY,
            View dragView, int[] result) {
        result = findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, result);
        return getIntersectingRectanglesInRegion(
                new Rect(result[0], result[1], result[0] + spanX, result[1] + spanY),
                dragView
        ) != null;
    }

    /**
     * Clear state variables to be ready for a new reorder.
     */
    // TextLauncher: public, called by the home drop target.
    public void revertTempState() {
        completeAndClearReorderPreviewAnimations();
        if (isItemPlacementDirty() && !DESTRUCTIVE_REORDER) {
            final int count = mShortcutsAndWidgets.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = mShortcutsAndWidgets.getChildAt(i);
                CellLayoutLayoutParams
                        lp = (CellLayoutLayoutParams) child.getLayoutParams();
                if (lp.getTmpCellX() != lp.getCellX() || lp.getTmpCellY() != lp.getCellY()) {
                    lp.setTmpCellX(lp.getCellX());
                    lp.setTmpCellY(lp.getCellY());
                    animateChildToPosition(child, lp.getCellX(), lp.getCellY(),
                            REORDER_ANIMATION_DURATION, 0, false, false);
                }
            }
            setItemPlacementDirty(false);
        }
    }

    /** Returns true if there exists an arrangement to support the given resizing needs. */
    public boolean hasAreaForResize(int cellX, int cellY, int spanX, int spanY,
            View dragView, int[] direction) {
        ItemConfiguration swapSolution = checkAreaForResize(cellX, cellY, spanX, spanY, dragView,
                direction);
        return swapSolution != null && swapSolution.isSolution;
    }

    @Nullable
    private ItemConfiguration checkAreaForResize(int cellX, int cellY, int spanX, int spanY,
            View dragView, int[] direction) {
        int[] pixelXY = new int[2];
        regionToCenterPoint(cellX, cellY, spanX, spanY, pixelXY);

        // First we determine if things have moved enough to cause a different layout
        return findReorderSolution(pixelXY[0], pixelXY[1], spanX, spanY,
                spanX,  spanY, direction, dragView,  true);
    }

    /** Create area for resizing the widget in given the direction. */
    public boolean createAreaForResize(int cellX, int cellY, int spanX, int spanY,
            View dragView, int[] direction, boolean commit) {
        final ItemConfiguration swapSolution = checkAreaForResize(cellX, cellY, spanX, spanY,
                dragView, direction);

        if (swapSolution != null && swapSolution.isSolution) {
            setUseTempCoords(true);
            // If we're just testing for a possible location (MODE_ACCEPT_DROP), we don't bother
            // committing anything or animating anything as we just want to determine if a solution
            // exists
            copySolutionToTempState(swapSolution, dragView);
            setItemPlacementDirty(true);
            animateItemsToSolution(swapSolution, dragView, commit);

            if (commit) {
                commitTempPlacement(null);
                completeAndClearReorderPreviewAnimations();
                setItemPlacementDirty(false);
            } else {
                beginOrAdjustReorderPreviewAnimations(swapSolution, dragView,
                        ReorderPreviewAnimation.MODE_PREVIEW);
            }
            mShortcutsAndWidgets.requestLayout();
        }
        return swapSolution.isSolution;
    }

    public ReorderAlgorithm createReorderAlgorithm() {
        return new ReorderAlgorithm(this);
    }

    protected ItemConfiguration findReorderSolution(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY, int[] direction, View dragView, boolean decX) {
        ItemConfiguration configuration = new ItemConfiguration();
        copyCurrentStateToSolution(configuration);
        ReorderParameters parameters = new ReorderParameters(pixelX, pixelY, spanX, spanY, minSpanX,
                minSpanY, dragView, configuration);
        int[] directionVector = direction != null ? direction : mDirectionVector;
        return createReorderAlgorithm().findReorderSolution(parameters, directionVector, decX);
    }

    public void copyCurrentStateToSolution(ItemConfiguration solution) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            solution.add(child,
                    new CellAndSpan(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan));
        }
    }

    /**
     * When the user drags an Item in the workspace sometimes we need to move the items already in
     * the workspace to make space for the new item, this function return a solution for that
     * reorder.
     *
     * @param pixelX X coordinate in the screen of the dragView in pixels
     * @param pixelY Y coordinate in the screen of the dragView in pixels
     * @param minSpanX minimum horizontal span the item can be shrunk to
     * @param minSpanY minimum vertical span the item can be shrunk to
     * @param spanX occupied horizontal span
     * @param spanY occupied vertical span
     * @param dragView the view of the item being draged
     * @return returns a solution for the given parameters, the solution contains all the icons and
     *         the locations they should be in the given solution.
     */
    public ItemConfiguration calculateReorder(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY, View dragView) {
        ItemConfiguration configuration = new ItemConfiguration();
        copyCurrentStateToSolution(configuration);
        return createReorderAlgorithm().calculateReorder(
                new ReorderParameters(pixelX, pixelY, spanX, spanY,  minSpanX, minSpanY, dragView,
                        configuration)
        );
    }

    // TextLauncher: public, called by the home drop target (Workspace in Launcher3).
    public int[] performReorder(int pixelX, int pixelY, int minSpanX, int minSpanY, int spanX, int spanY,
            View dragView, int[] result, int[] resultSpan, int mode) {
        if (resultSpan == null) {
            resultSpan = new int[]{-1, -1};
        }
        if (result == null) {
            result = new int[]{-1, -1};
        }

        ItemConfiguration finalSolution = null;
        // We want the solution to match the animation of the preview and to match the drop so we
        // only recalculate in mode MODE_SHOW_REORDER_HINT because that the first one to run in the
        // reorder cycle.
        if (mode == MODE_SHOW_REORDER_HINT || mPreviousSolution == null) {
            finalSolution = calculateReorder(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY,
                    dragView);
            mPreviousSolution = finalSolution;
        } else {
            finalSolution = mPreviousSolution;
            // We reset this vector after drop
            if (mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL) {
                mPreviousSolution = null;
            }
        }

        if (finalSolution == null || !finalSolution.isSolution) {
            result[0] = result[1] = resultSpan[0] = resultSpan[1] = -1;
        } else {
            result[0] = finalSolution.cellX;
            result[1] = finalSolution.cellY;
            resultSpan[0] = finalSolution.spanX;
            resultSpan[1] = finalSolution.spanY;
            performReorder(finalSolution, dragView, mode);
        }
        return result;
    }

    /**
     * Animates and submits in the DB the given ItemConfiguration depending of the mode.
     *
     * @param solution represents widgets on the screen which the Workspace will animate to and
     * would be submitted to the database.
     * @param dragView view which is being dragged over the workspace that trigger the reorder
     * @param mode depending on the mode different animations would be played and depending on the
     *             mode the solution would be submitted or not the database.
     *             The possible modes are {@link MODE_SHOW_REORDER_HINT}, {@link MODE_DRAG_OVER},
     *             {@link MODE_ON_DROP}, {@link MODE_ON_DROP_EXTERNAL}, {@link  MODE_ACCEPT_DROP}
     *             defined in {@link CellLayout}.
     */
    public void performReorder(ItemConfiguration solution, View dragView, int mode) {
        if (mode == MODE_SHOW_REORDER_HINT) {
            beginOrAdjustReorderPreviewAnimations(solution, dragView,
                    ReorderPreviewAnimation.MODE_HINT);
            return;
        }
        // If we're just testing for a possible location (MODE_ACCEPT_DROP), we don't bother
        // committing anything or animating anything as we just want to determine if a solution
        // exists
        if (mode == MODE_DRAG_OVER || mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL) {
            if (!DESTRUCTIVE_REORDER) {
                setUseTempCoords(true);
            }

            if (!DESTRUCTIVE_REORDER) {
                copySolutionToTempState(solution, dragView);
            }
            setItemPlacementDirty(true);
            animateItemsToSolution(solution, dragView, mode == MODE_ON_DROP);

            if (!DESTRUCTIVE_REORDER
                    && (mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL)) {
                // Since the temp solution didn't update dragView, don't commit it either
                commitTempPlacement(dragView);
                completeAndClearReorderPreviewAnimations();
                setItemPlacementDirty(false);
            } else {
                beginOrAdjustReorderPreviewAnimations(solution, dragView,
                        ReorderPreviewAnimation.MODE_PREVIEW);
            }
        }

        if (mode == MODE_ON_DROP && !DESTRUCTIVE_REORDER) {
            setUseTempCoords(false);
        }

        mShortcutsAndWidgets.requestLayout();
    }

    void setItemPlacementDirty(boolean dirty) {
        mItemPlacementDirty = dirty;
    }
    boolean isItemPlacementDirty() {
        return mItemPlacementDirty;
    }

    /**
     * Find a starting cell position that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param result Previously returned value to possibly recycle.
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    public int[] findNearestAreaIgnoreOccupied(int pixelX, int pixelY, int spanX, int spanY,
            int[] result) {
        return findNearestArea(pixelX, pixelY, spanX, spanY, spanX, spanY, true, result, null);
    }

    /**
     * Finds the upper-left coordinate of the first rectangle in the grid that can
     * hold a cell of the specified dimensions. If intersectX and intersectY are not -1,
     * then this method will only return coordinates for rectangles that contain the cell
     * (intersectX, intersectY)
     *
     * @param cellXY The array that will contain the position of a vacant cell if such a cell
     *               can be found.
     * @param spanX The horizontal span of the cell we want to find.
     * @param spanY The vertical span of the cell we want to find.
     *
     * @return True if a vacant cell of the specified dimension was found, false otherwise.
     */
    public boolean findCellForSpan(int[] cellXY, int spanX, int spanY) {
        if (cellXY == null) {
            cellXY = new int[2];
        }
        return mOccupied.findVacantCell(cellXY, spanX, spanY);
    }

    /**
     * A drag event has begun over this layout.
     * It may have begun over this layout (in which case onDragChild is called first),
     * or it may have begun on another layout.
     */
    // TextLauncher: public, called by the home drop target.
    public void onDragEnter() {
        mDragging = true;
        mPreviousSolution = null;
    }

    /**
     * Called when drag has left this CellLayout or has been completed (successfully or not)
     */
    // TextLauncher: public, called by the home drop target.
    public void onDragExit() {
        // This can actually be called when we aren't in a drag, e.g. when adding a new
        // item to this layout via the customize drawer.
        // Guard against that case.
        if (mDragging) {
            mDragging = false;
        }

        // Invalidate the drag data
        mPreviousSolution = null;
        mDragCell[0] = mDragCell[1] = -1;
        mDragCellSpan[0] = mDragCellSpan[1] = -1;
        mDragOutlineAnims[mDragOutlineCurrent].animateOut();
        mDragOutlineCurrent = (mDragOutlineCurrent + 1) % mDragOutlineAnims.length;
        revertTempState();
        setIsDragOverlapping(false);
    }

    /**
     * Mark a child as having been dropped.
     * At the beginning of the drag operation, the child may have been on another
     * screen, but it is re-parented before this method is called.
     *
     * @param child The child that is being dropped
     */
    // TextLauncher: public, called by the home drop target.
    public void onDropChild(View child) {
        mPlayDragHaptics = false;
        if (child != null) {
            CellLayoutLayoutParams
                    lp = (CellLayoutLayoutParams) child.getLayoutParams();
            lp.dropped = true;
            child.requestLayout();
            markCellsAsOccupiedForView(child);
        }
    }

    /**
     * Computes a bounding rectangle for a range of cells
     *
     * @param cellX X coordinate of upper left corner expressed as a cell position
     * @param cellY Y coordinate of upper left corner expressed as a cell position
     * @param cellHSpan Width in cells
     * @param cellVSpan Height in cells
     * @param resultRect Rect into which to put the results
     */
    public void cellToRect(int cellX, int cellY, int cellHSpan, int cellVSpan, Rect resultRect) {
        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;

        // TextLauncher: the padding is the grid view's (setCellGeometry); the CellLayout is never
        // measured, so there is no unused horizontal space, and no cell has an X adjustment.
        final int hStartPadding = mPaddingLeft;
        final int vStartPadding = mPaddingTop;

        int x = hStartPadding + (cellX * mBorderSpace.x) + (cellX * cellWidth);
        int y = vStartPadding + (cellY * mBorderSpace.y) + (cellY * cellHeight);

        int width = cellHSpan * cellWidth + ((cellHSpan - 1) * mBorderSpace.x);
        int height = cellVSpan * cellHeight + ((cellVSpan - 1) * mBorderSpace.y);

        resultRect.set(x, y, x + width, y + height);
    }

    public void markCellsAsOccupiedForView(View view) {
        // TextLauncher: no cell position mapper; widgets are marked from their layout params like
        // other items (the model's cells are the presenter's).
        if (view == null || view.getParent() != mShortcutsAndWidgets) return;
        CellLayoutLayoutParams
                lp = (CellLayoutLayoutParams) view.getLayoutParams();
        mOccupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, true);
    }

    public void markCellsAsUnoccupiedForView(View view) {
        // TextLauncher: no cell position mapper; widgets are marked from their layout params like
        // other items (the model's cells are the presenter's).
        if (view == null || view.getParent() != mShortcutsAndWidgets) return;
        CellLayoutLayoutParams
                lp = (CellLayoutLayoutParams) view.getLayoutParams();
        mOccupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, false);
    }

    public int getDesiredWidth() {
        return getPaddingLeft() + getPaddingRight() + (mCountX * mCellWidth)
                + ((mCountX - 1) * mBorderSpace.x);
    }

    public int getDesiredHeight()  {
        return getPaddingTop() + getPaddingBottom() + (mCountY * mCellHeight)
                + ((mCountY - 1) * mBorderSpace.y);
    }

    // TextLauncher: members TextLauncher adds.

    /** The grid view's padding, where the cells start; CellLayout reads its own View padding. */
    private int mPaddingLeft;
    private int mPaddingTop;

    /** The view that draws this layout's content (TextLauncher's grid view). */
    private View mOwnerView;

    /** The icon size; CellLayout reads it from the device profile. */
    private final int mIconSizePx;

    /**
     * WorkspaceProfile.gridVisualizationPaddingX / Y; CellLayout reads them from the device
     * profile.
     */
    private final int mGridVisualizationPaddingX;
    private final int mGridVisualizationPaddingY;

    /** Sets the view that draws this layout's content. */
    public void setOwnerView(View owner) {
        mOwnerView = owner;
    }

    /**
     * Redraws the owner view, which draws this layout's content when this layout is not attached
     * (the home screen's); an attached layout (a folder page) is invalidated as a View.
     */
    @Override
    public void invalidate() {
        if (mOwnerView != null) {
            mOwnerView.invalidate();
        } else {
            super.invalidate();
        }
    }

    /**
     * Sets the cell geometry in pixels, given by the grid view; CellLayout works it out in
     * onMeasure from its own size and the device profile.
     */
    public void setCellGeometry(int cellWidth, int cellHeight, int borderSpaceX, int borderSpaceY,
            int paddingLeft, int paddingTop) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mBorderSpace.set(borderSpaceX, borderSpaceY);
        mPaddingLeft = paddingLeft;
        mPaddingTop = paddingTop;
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
    }

    /**
     * Marks every child, except {@code ignore}, as occupied, after clearing the occupancy. The
     * home drop target calls it as a drag starts.
     */
    public void rebuildOccupied(@Nullable View ignore) {
        mOccupied.clear();
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == ignore) continue;
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            mOccupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, true);
        }
    }

    /** The drop location part of CellLayout.onDraw, called by the owner view. */
    public void drawDropLocation(Canvas canvas) {
        if (mVisualizeDropLocation) {
            visualizeGrid(canvas);
        }
    }

    /** The delegated cell drawings of CellLayout.onDraw (under the items), for the owner view. */
    public void drawDelegatedUnderItems(Canvas canvas) {
        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing cellDrawing = mDelegatedCellDrawings.get(i);
            canvas.save();
            if (cellDrawing.mDelegateCellX >= 0 && cellDrawing.mDelegateCellY >= 0) {
                cellToPoint(cellDrawing.mDelegateCellX, cellDrawing.mDelegateCellY, mTempLocation);
                canvas.translate(mTempLocation[0], mTempLocation[1]);
            }
            cellDrawing.drawUnderItem(canvas);
            canvas.restore();
        }
    }

    /** The delegated cell drawings of CellLayout.dispatchDraw (over the items), for the owner. */
    public void drawDelegatedOverItems(Canvas canvas) {
        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing bg = mDelegatedCellDrawings.get(i);
            canvas.save();
            if (bg.mDelegateCellX >= 0 && bg.mDelegateCellY >= 0) {
                cellToPoint(bg.mDelegateCellX, bg.mDelegateCellY, mTempLocation);
                canvas.translate(mTempLocation[0], mTempLocation[1]);
            }
            bg.drawOverItem(canvas);
            canvas.restore();
        }
    }

    /**
     * The page's background while editing (mBackground, bg_celllayout). CellLayout draws it in
     * onDraw; here the view that shows the visible page (the home screen's workspace) draws it.
     */
    public Drawable getPageBackground() {
        return mBackground;
    }
}
