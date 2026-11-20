package com.android.launcher3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.GridOccupancy;

import java.util.ArrayList;
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
public class CellLayout extends android.widget.FrameLayout {

    /** TextLauncher: replaces CellLayout's ShortcutAndWidgetContainer. */
    public interface Children {
        int getChildCount();

        View getChildAt(int index);
    }

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
    private android.view.View mOwnerView;

    public CellLayout(Context context, Children children) {
        super(context);
        mShortcutsAndWidgets = children;
        setGridSize(1, 1);
    }

    /** TextLauncher: no child container; see {@link ShortcutAndWidgetContainer}. */
    public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
        return null;
    }

    /** TextLauncher: the view that draws this layout's content. */
    public void setOwnerView(android.view.View owner) {
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

    /** TextLauncher: grid size. Occupancy is cleared when the size changes. */
    public void setGridSize(int countX, int countY) {
        if (mOccupied != null && countX == mCountX && countY == mCountY) return;
        mCountX = countX;
        mCountY = countY;
        mOccupied = new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);
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
}
