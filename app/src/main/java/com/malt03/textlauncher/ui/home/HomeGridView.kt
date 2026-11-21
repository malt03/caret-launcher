package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.android.launcher3.CellLayout
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.keyboard.ViewGroupFocusHelper
import com.android.launcher3.model.data.ItemInfo
import com.malt03.textlauncher.ui.GridSpec
import kotlin.math.ceil
import kotlin.math.max

/**
 * The home layout (docs/spec.md 5.1): one grid with the Launcher3 column count, icons without
 * labels, and no row limit. It sits in a vertical scroll container.
 *
 * It is the [CellLayout] stand-in's ShortcutAndWidgetContainer; the CellLayout holds the copied
 * reorder, drop location and cell drawing code, and the drag and drop logic itself is in
 * [HomeWorkspace].
 */
@SuppressLint("ViewConstructor")
class HomeGridView(context: Context, val grid: GridSpec) : ShortcutAndWidgetContainer(context) {
    val cellLayout = CellLayout(context, this, grid.iconSizePx).also { it.setOwnerView(this) }

    /**
     * DragLayer's focus outline for the home items (Launcher.getFocusHandler, which ItemInflater
     * sets as each item's focus listener). It is drawn here, in the scrolled grid, so that it
     * scrolls with the items.
     */
    val focusIndicatorHelper = ViewGroupFocusHelper(this)

    private val cellPaddingV = (12 * resources.displayMetrics.density).toInt()
    val cellHeight: Int get() = grid.iconSizePx + cellPaddingV * 2
    var cellWidth = 0
        private set

    /** Extra empty rows below the last item, so that items can be dropped there while editing. */
    var headroomRows = 0
        set(value) {
            field = value
            requestLayout()
        }

    /** Minimum height, so that the grid fills the visible area. */
    var viewportHeight = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    /** CellLayout.addViewToCellLayout: the cells are marked as occupied. */
    fun addItemView(view: View, item: ItemInfo) {
        view.tag = item
        val lp = CellLayoutLayoutParams(item.cellX, item.cellY, item.spanX, item.spanY)
        // The grid grows with its items; make room before marking the cells.
        cellLayout.setGridSize(grid.columns, max(cellLayout.countY, item.cellY + item.spanY))
        addView(view, lp)
        cellLayout.markCellsAsOccupiedForView(view)
        if (cellWidth > 0) measureChild(view)
    }

    /** CellLayout.removeView: the cells are marked as free. */
    override fun removeView(view: View) {
        cellLayout.markCellsAsUnoccupiedForView(view)
        super.removeView(view)
    }

    override fun removeAllViews() {
        for (child in itemViews()) cellLayout.markCellsAsUnoccupiedForView(child)
        super.removeAllViews()
    }

    fun itemViews(): List<View> = (0 until childCount).map { getChildAt(it) }

    /** Rows in use, counting where the reorder preview has moved items to. */
    private fun usedRows(): Int = itemViews().maxOfOrNull {
        val lp = it.layoutParams as CellLayoutLayoutParams
        max(lp.cellY, if (lp.useTmpCoords) lp.tmpCellY else 0) + lp.cellVSpan
    } ?: 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cellWidth = (width - paddingLeft - paddingRight) / grid.columns
        val viewportRows = ceil((viewportHeight - paddingTop - paddingBottom).toFloat() / cellHeight).toInt()
        val contentRows = usedRows() + headroomRows
        cellLayout.setGridSize(grid.columns, max(contentRows, max(viewportRows, 1)))
        cellLayout.setCellGeometry(cellWidth, cellHeight, 0, 0, paddingLeft, paddingTop)
        for (child in itemViews()) measureChild(child)
        // Only as tall as the items need, but at least the visible area: the grid scrolls only when
        // items are placed below it. The last cell row may still be partly visible for dropping.
        setMeasuredDimension(width, max(contentRows * cellHeight + paddingTop + paddingBottom, viewportHeight))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // CellLayout.onDraw: the drop location, then delegated cell drawings (folder circles).
        cellLayout.drawDropLocation(canvas)
        cellLayout.drawDelegatedUnderItems(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // DragLayer.dispatchDraw: the focus outline goes below the items.
        focusIndicatorHelper.draw(canvas)
        super.dispatchDraw(canvas)
        // CellLayout.dispatchDraw: delegated drawings over the items.
        cellLayout.drawDelegatedOverItems(canvas)
    }

    /**
     * ShortcutAndWidgetContainer.layoutChild. TextLauncher: this view's padding is CellLayout's
     * padding, so the cells start inside it.
     */
    override fun layoutChild(child: View) {
        val lp = child.layoutParams as CellLayoutLayoutParams
        child.layout(paddingLeft + lp.x, paddingTop + lp.y, paddingLeft + lp.x + lp.width, paddingTop + lp.y + lp.height)
    }
}
