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
import kotlin.math.max
import kotlin.math.min

/**
 * The home layout (docs/spec.md 5.1): one grid with the Launcher3 column count, the display's rows
 * ([GridSpec.rows]) and icons without labels. Like Launcher3's workspace page, the grid fills the
 * space above the hotseat (the search field) with the keyboard down ([pageHeight]), and the cells
 * share the space (CellLayout's cell height is the page height over the rows). It sits in a
 * vertical scroll container, which scrolls it while the keyboard covers part of it.
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

    private val minCellHeight: Int get() = grid.homeMinCellHeightPx
    var cellHeight = minCellHeight
        private set
    var cellWidth = 0
        private set

    /**
     * The page: the height above the search field with the keyboard down (Launcher3's workspace
     * above the hotseat). Set by the root layout as it measures.
     */
    var pageHeight = 0
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
        cellLayout.setGridSize(grid.columns, grid.rows)
    }

    /** CellLayout.addViewToCellLayout: the cells are marked as occupied. */
    fun addItemView(view: View, item: ItemInfo) {
        view.tag = item
        val lp = CellLayoutLayoutParams(item.cellX, item.cellY, item.spanX, item.spanY)
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

    /** The bottom of the lowest row with an item in it. */
    private fun usedRows(): Int = itemViews().maxOfOrNull {
        val lp = it.layoutParams as CellLayoutLayoutParams
        lp.cellY + lp.cellVSpan
    } ?: 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cellWidth = (width - paddingLeft - paddingRight) / grid.columns
        // The cells share the page's height (DeviceProfile: cellHeight from the workspace height
        // and rows). TextLauncher: in a window shorter than the display's grid (the split screen),
        // the cells keep their smallest height and the page scrolls, as under the keyboard;
        // Launcher3 scales the whole profile down instead (DeviceProfile.getMultiWindowProfile).
        val rows = grid.rows
        cellHeight = max((pageHeight - paddingTop - paddingBottom) / rows, minCellHeight)
        cellLayout.setCellGeometry(cellWidth, cellHeight, 0, 0, paddingLeft, paddingTop)
        for (child in itemViews()) measureChild(child)
        // TextLauncher: while the keyboard covers part of the page, it scrolls only as far as the
        // items go; the empty rows below stay under the keyboard. The scroll container stretches
        // this to the visible height (fillViewport, measured again EXACTLY), which shows the whole
        // page with the keyboard down and while editing.
        val pageContentHeight = rows * cellHeight + paddingTop + paddingBottom
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            min(pageContentHeight, usedRows() * cellHeight + paddingTop + paddingBottom)
        }
        setMeasuredDimension(width, height)
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
