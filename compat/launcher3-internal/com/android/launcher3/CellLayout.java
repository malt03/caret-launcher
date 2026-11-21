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

import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_PREVIEW_OFFSET;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.ArrayMap;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.celllayout.ReorderAlgorithm;
import com.android.launcher3.celllayout.ReorderParameters;
import com.android.launcher3.celllayout.ReorderPreviewAnimation;
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
 * Stand-in for Launcher3's CellLayout (android-17.0.0_r1), used by the copied reorder algorithm
 * (celllayout/ReorderAlgorithm.java, celllayout/ViewCluster.kt).
 *
 * Never attached: TextLauncher's grid view owns one, keeps its geometry in sync, lists its
 * children through {@link Children} and draws its delegated cell drawings. It is a View only
 * because copied code reads View members of CellLayout (clipChildren and so on). Method bodies are copied from CellLayout.java; changes are
 * marked with "TextLauncher:".
 */
public class CellLayout extends FrameLayout {

    /** TextLauncher: replaces CellLayout's ShortcutAndWidgetContainer. */
    public interface Children {
        int getChildCount();

        View getChildAt(int index);

        int indexOfChild(View child);

        /** ShortcutAndWidgetContainer.setupLp */
        void setupLp(View child);

        /** ShortcutAndWidgetContainer.measureChild */
        void measureChild(View child);

        /** ShortcutAndWidgetContainer.layoutChild */
        void layoutChild(View child);

        /** ShortcutAndWidgetContainer.getChildAt(cellX, cellY) */
        View getChildAtCell(int cellX, int cellY);

        /** Workspace / CellLayout.canCreateFolder: whether the child can take a folder drop. */
        boolean canCreateFolder(View child);

        void requestLayout();
    }

    private static final boolean DESTRUCTIVE_REORDER = false;

    @Thunk final ArrayMap<CellLayoutLayoutParams, Animator> mReorderAnimators = new ArrayMap<>();
    @Thunk final ArrayMap<Reorderable, ReorderPreviewAnimation> mShakeAnimators = new ArrayMap<>();
    private boolean mItemPlacementDirty = false;
    ItemConfiguration mPreviousSolution = null;
    // TextLauncher: CellLayout computes this from the device profile's icon size.
    private float mReorderPreviewAnimationMagnitude;

    public static final int MODE_SHOW_REORDER_HINT = 0;
    public static final int MODE_DRAG_OVER = 1;
    public static final int MODE_ON_DROP = 2;
    public static final int MODE_ON_DROP_EXTERNAL = 3;
    public static final int MODE_ACCEPT_DROP = 4;
    public static final int REORDER_ANIMATION_DURATION = 150;
    public static final float DEFAULT_SCALE = 1f;
    public static final float REORDER_PREVIEW_MAGNITUDE = 0.12f;

    private final Children mShortcutsAndWidgets;

    private int mCountX;
    private int mCountY;
    private int mCellWidth;
    private int mCellHeight;
    private final Point mBorderSpace = new Point();
    // TextLauncher: CellLayout reads these from View padding.
    private int mPaddingLeft;
    private int mPaddingTop;

    private GridOccupancy mOccupied;
    public GridOccupancy mTmpOccupied;
    public final int[] mDirectionVector = new int[2];

    private final Rect mTempRect = new Rect();
    private final int[] mTmpPoint = new int[2];

    private final ArrayList<DelegatedCellDrawing> mDelegatedCellDrawings = new ArrayList<>();
    private View mOwnerView;

    private final MSDLPlayerWrapper mMSDLPlayerWrapper;

    private final int mIconSizePx;

    public CellLayout(Context context, Children children, int iconSizePx) {
        super(context);
        mIconSizePx = iconSizePx;
        mShortcutsAndWidgets = children;
        mReorderPreviewAnimationMagnitude = (REORDER_PREVIEW_MAGNITUDE * iconSizePx);
        setGridSize(1, 1);
        mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context);

        Resources res = getResources();
        mGridColor = Themes.getAttrColor(context, R.attr.workspaceAccentColor);
        mGridVisualizationRoundingRadius =
                res.getDimensionPixelSize(R.dimen.grid_visualization_rounding_radius);
        // TextLauncher: WorkspaceProfile.gridVisualizationPaddingX / Y.
        mGridVisualizationPaddingX =
                res.getDimensionPixelSize(R.dimen.grid_visualization_horizontal_cell_spacing);
        mGridVisualizationPaddingY =
                res.getDimensionPixelSize(R.dimen.grid_visualization_vertical_cell_spacing);

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
    }

    // These arrays are used to implement the drag visualization on x-large screens.
    // They are used as circular arrays, indexed by mDragOutlineCurrent.
    @Thunk final CellLayoutLayoutParams[] mDragOutlines = new CellLayoutLayoutParams[4];
    @Thunk final float[] mDragOutlineAlphas = new float[mDragOutlines.length];
    private final InterruptibleInOutAnimator[] mDragOutlineAnims =
            new InterruptibleInOutAnimator[mDragOutlines.length];

    // Used as an index into the above 3 arrays; indicates which is the most current value.
    private int mDragOutlineCurrent = 0;

    private TimeInterpolator mEaseOutInterpolator;
    private final int[] mDragCell = new int[2];
    private final int[] mDragCellSpan = new int[2];
    private boolean mDragging = false;
    private boolean mPlayDragHaptics = false;
    private boolean mVisualizeDropLocation = true;
    private final RectF mVisualizeGridRect = new RectF();
    private final Paint mVisualizeGridPaint = new Paint();
    private int mGridVisualizationRoundingRadius;
    private int mGridColor = 0;
    // TextLauncher: from the device profile in CellLayout.visualizeGrid.
    private final int mGridVisualizationPaddingX;
    private final int mGridVisualizationPaddingY;
    private final Rect mTempOnDrawCellToRect = new Rect();

    /**
     * TextLauncher: the drop location part of CellLayout.onDraw / visualizeGrid, called by the
     * owner view.
     */
    public void drawDropLocation(Canvas canvas) {
        if (mVisualizeDropLocation) {
            visualizeGrid(canvas);
        }
    }

    protected void visualizeGrid(Canvas canvas) {
        int paddingX = Math.min((mCellWidth - mIconSizePx) / 2, mGridVisualizationPaddingX);
        int paddingY = Math.min((mCellHeight - mIconSizePx) / 2, mGridVisualizationPaddingY);

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

    // TextLauncher: public, called by the home drop target.
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
    }

    /**
     * Mark a child as having been dropped.
     * At the beginning of the drag operation, the child may have been on another
     * screen, but it is re-parented before this method is called.
     *
     * @param child The child that is being dropped
     */
    public void onDropChild(View child) {
        mPlayDragHaptics = false;
        if (child != null) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            lp.dropped = true;
            child.requestLayout();
            markCellsAsOccupiedForView(child);
        }
    }

    public ReorderAlgorithm createReorderAlgorithm() {
        return new ReorderAlgorithm(this);
    }

    /** TextLauncher: no child container; see {@link ShortcutAndWidgetContainer}. */
    public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
        return null;
    }

    /** TextLauncher: the view that draws this layout's content. */
    public void setOwnerView(View owner) {
        mOwnerView = owner;
    }

    /** TextLauncher: redraw happens in the owner view. */
    @Override
    public void invalidate() {
        if (mOwnerView != null) mOwnerView.invalidate();
    }

    public void addDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.add(bg);
    }

    public void removeDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.remove(bg);
    }

    private final int[] mTempLocation = new int[2];

    /** TextLauncher: CellLayout.onDraw's delegated drawing, called by the owner view. */
    public void drawDelegatedUnderItems(Canvas canvas) {
        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing cellDrawing = mDelegatedCellDrawings.get(i);
            canvas.save();
            cellToPoint(cellDrawing.mDelegateCellX, cellDrawing.mDelegateCellY, mTempLocation);
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            cellDrawing.drawUnderItem(canvas);
            canvas.restore();
        }
    }

    /** TextLauncher: CellLayout.dispatchDraw's delegated drawing, called by the owner view. */
    public void drawDelegatedOverItems(Canvas canvas) {
        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing bg = mDelegatedCellDrawings.get(i);
            canvas.save();
            cellToPoint(bg.mDelegateCellX, bg.mDelegateCellY, mTempLocation);
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            bg.drawOverItem(canvas);
            canvas.restore();
        }
    }

    void cellToPoint(int cellX, int cellY, int[] result) {
        cellToRect(cellX, cellY, 1, 1, mTempRect);
        result[0] = mTempRect.left;
        result[1] = mTempRect.top;
    }

    /**
     * TextLauncher: grid size. The home grid grows and shrinks by rows, so the occupancy of the
     * cells inside both sizes is kept.
     */
    public void setGridSize(int countX, int countY) {
        if (mOccupied != null && countX == mCountX && countY == mCountY) return;
        GridOccupancy oldOccupied = mOccupied;
        GridOccupancy oldTmpOccupied = mTmpOccupied;
        int oldCountX = mCountX;
        int oldCountY = mCountY;
        mCountX = countX;
        mCountY = countY;
        mOccupied = new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);
        if (oldOccupied != null) {
            for (int x = 0; x < Math.min(oldCountX, countX); x++) {
                for (int y = 0; y < Math.min(oldCountY, countY); y++) {
                    mOccupied.cells[x][y] = oldOccupied.cells[x][y];
                    mTmpOccupied.cells[x][y] = oldTmpOccupied.cells[x][y];
                }
            }
        }
    }

    /** TextLauncher: cell geometry in pixels. */
    public void setCellGeometry(int cellWidth, int cellHeight, int borderSpaceX, int borderSpaceY,
            int paddingLeft, int paddingTop) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mBorderSpace.set(borderSpaceX, borderSpaceY);
        mPaddingLeft = paddingLeft;
        mPaddingTop = paddingTop;
    }

    public int getCountX() {
        return mCountX;
    }

    public int getCountY() {
        return mCountY;
    }

    public int getCellWidth() {
        return mCellWidth;
    }

    public int getCellHeight() {
        return mCellHeight;
    }

    public GridOccupancy getOccupied() {
        return mOccupied;
    }

    /** TextLauncher: marks every child, except {@code ignore}, as occupied. */
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

    public View getChildAt(int cellX, int cellY) {
        return mShortcutsAndWidgets.getChildAtCell(cellX, cellY);
    }

    // TextLauncher: icons are centered in their cell, so the visual center is the cell center.
    private void getWorkspaceCellVisualCenter(int cellX, int cellY, int[] outPoint) {
        cellToCenterPoint(cellX, cellY, outPoint);
    }

    /**
     * Returns the distance between the given coordinate and the visual center of the given cell.
     */
    public float getDistanceFromWorkspaceCellVisualCenter(float x, float y, int[] cell) {
        getWorkspaceCellVisualCenter(cell[0], cell[1], mTmpPoint);
        return (float) Math.hypot(x - mTmpPoint[0], y - mTmpPoint[1]);
    }

    public float getFolderCreationRadius(int[] targetCell) {
        // TextLauncher: the icon size comes from the constructor instead of the device profile.
        float iconVisibleRadius = com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR
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

    private boolean canCreateFolder(View child) {
        return child != null && mShortcutsAndWidgets.canCreateFolder(child);
    }

    public boolean isNearestDropLocationOccupied(int pixelX, int pixelY, int spanX, int spanY,
            View dragView, int[] result) {
        result = findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, result);
        return getIntersectingRectanglesInRegion(
                new Rect(result[0], result[1], result[0] + spanX, result[1] + spanY),
                dragView
        ) != null;
    }

    public void markCellsAsOccupiedForView(View view) {
        if (view == null || mShortcutsAndWidgets.indexOfChild(view) < 0) return;
        CellLayoutLayoutParams
                lp = (CellLayoutLayoutParams) view.getLayoutParams();
        mOccupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, true);
    }

    public void markCellsAsUnoccupiedForView(View view) {
        if (view == null || mShortcutsAndWidgets.indexOfChild(view) < 0) return;
        CellLayoutLayoutParams
                lp = (CellLayoutLayoutParams) view.getLayoutParams();
        mOccupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, false);
    }

    void cellToCenterPoint(int cellX, int cellY, int[] result) {
        regionToCenterPoint(cellX, cellY, 1, 1, result);
    }

    public void regionToCenterPoint(int cellX, int cellY, int spanX, int spanY, int[] result) {
        cellToRect(cellX, cellY, spanX, spanY, mTempRect);
        result[0] = mTempRect.centerX();
        result[1] = mTempRect.centerY();
    }

    public int[] findNearestVacantArea(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY, int[] result, int[] resultSpan) {
        return findNearestArea(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, false,
                result, resultSpan);
    }

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

    public void copyCurrentStateToSolution(ItemConfiguration solution) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            solution.add(child,
                    new CellAndSpan(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan));
        }
    }

    public int[] findNearestAreaIgnoreOccupied(int pixelX, int pixelY, int spanX, int spanY,
            int[] result) {
        return findNearestArea(pixelX, pixelY, spanX, spanY, spanX, spanY, true, result, null);
    }

    public void cellToRect(int cellX, int cellY, int cellHSpan, int cellVSpan, Rect resultRect) {
        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;

        // TextLauncher: no unused horizontal space and no per-cell translation.
        final int hStartPadding = mPaddingLeft;
        final int vStartPadding = mPaddingTop;

        int x = hStartPadding + (cellX * mBorderSpace.x) + (cellX * cellWidth);
        int y = vStartPadding + (cellY * mBorderSpace.y) + (cellY * cellHeight);

        int width = cellHSpan * cellWidth + ((cellHSpan - 1) * mBorderSpace.x);
        int height = cellVSpan * cellHeight + ((cellVSpan - 1) * mBorderSpace.y);

        resultRect.set(x, y, x + width, y + height);
    }

    // ---- Copied from CellLayout.java (android-17.0.0_r1) ----

    protected ItemConfiguration findReorderSolution(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY, int[] direction, View dragView, boolean decX) {
        ItemConfiguration configuration = new ItemConfiguration();
        copyCurrentStateToSolution(configuration);
        ReorderParameters parameters = new ReorderParameters(pixelX, pixelY, spanX, spanY, minSpanX,
                minSpanY, dragView, configuration);
        int[] directionVector = direction != null ? direction : mDirectionVector;
        return createReorderAlgorithm().findReorderSolution(parameters, directionVector, decX);
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

        // TextLauncher: no database or cell position mapper; the owner saves the model.
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            if (child != dragView) {
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

    public boolean animateChildToPosition(final View child, int cellX, int cellY, int duration,
            int delay, boolean permanent, boolean adjustOccupied) {
        // TextLauncher: the grid view stands in for ShortcutAndWidgetContainer.
        Children clc = mShortcutsAndWidgets;

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
}
