package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.core.widget.NestedScrollView
import com.android.launcher3.CellLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.keyboard.ViewGroupFocusHelper
import com.malt03.textlauncher.ui.GridSpec
import kotlin.math.max
import kotlin.math.min

/**
 * The home layout (docs/spec.md 5.1): the home screen's one page, a [CellLayout] with the
 * Launcher3 column count, the display's rows ([GridSpec.rows]) and icons without labels, in a
 * vertical scroll container. TextLauncher: Launcher3's workspace pages page horizontally; this
 * page scrolls vertically while the keyboard covers part of it.
 *
 * Like Launcher3's workspace page, the page fills the space above the hotseat (the search field)
 * with the keyboard down ([pageHeight]), and the cells share it (DeviceProfile's cell height is the
 * page height over the rows). This view gives the CellLayout its cell size and its height, as the
 * workspace gives its pages theirs.
 */
@SuppressLint("ViewConstructor")
class HomeScrollView(context: Context, val grid: GridSpec) : NestedScrollView(context) {
    /** The home screen's page, set once the home screen's Launcher is made. */
    var cellLayout: CellLayout? = null
        set(value) {
            field?.let(::removeView)
            field = value
            value?.let { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
        }

    /**
     * DragLayer's focus outline for the home items (Launcher.getFocusHandler, which ItemInflater
     * sets as each item's focus listener). It is drawn here, under the page, so that it scrolls
     * with the items.
     */
    val focusIndicatorHelper = ViewGroupFocusHelper(this)

    private val minCellHeight: Int get() = grid.homeMinCellHeightPx

    /** Called with the cell size each time it changes (DeviceProfile's cell size). */
    var onCellSizeChanged: ((width: Int, height: Int) -> Unit)? = null

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
        // The page draws the drop location and the folder previews outside an item's cell.
        clipChildren = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val page = cellLayout
        if (page != null) {
            // DeviceProfile: the cell width from the page width and columns, the cell height from
            // the page height and rows. TextLauncher: in a window shorter than the display's grid
            // (the split screen), the cells keep their smallest height and the page scrolls, as
            // under the keyboard; Launcher3 scales the whole profile down instead
            // (DeviceProfile.getMultiWindowProfile).
            val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val cellWidth = DeviceProfile.calculateCellWidth(
                width - page.paddingLeft - page.paddingRight, 0, grid.columns,
            )
            val cellHeight = max((pageHeight - page.paddingTop - page.paddingBottom) / grid.rows, minCellHeight)
            if (cellWidth != page.cellWidth || cellHeight != page.cellHeight) {
                page.setCellDimensions(cellWidth, cellHeight)
                onCellSizeChanged?.invoke(cellWidth, cellHeight)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * NestedScrollView measures its child as tall as it wants; CellLayout needs an exact height.
     * TextLauncher: while the keyboard covers part of the page, it scrolls only as far as the
     * items go and the empty rows below stay under the keyboard; otherwise (the keyboard down,
     * while editing) the page fills the visible height (fillViewport).
     */
    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int,
    ) {
        val page = cellLayout
        if (child !== page) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
            return
        }
        val verticalPadding = page.paddingTop + page.paddingBottom
        val pageContentHeight = grid.rows * page.cellHeight + verticalPadding
        val usedHeight = usedRows(page) * page.cellHeight + verticalPadding
        val visibleHeight = MeasureSpec.getSize(parentHeightMeasureSpec) - paddingTop - paddingBottom
        val height = max(min(pageContentHeight, usedHeight), visibleHeight)
        val width = MeasureSpec.getSize(parentWidthMeasureSpec) - paddingLeft - paddingRight - widthUsed
        page.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    /** The bottom of the lowest row with an item in it. */
    private fun usedRows(page: CellLayout): Int {
        val items = page.shortcutsAndWidgets
        var rows = 0
        for (i in 0 until items.childCount) {
            val lp = items.getChildAt(i).layoutParams as CellLayoutLayoutParams
            rows = max(rows, lp.cellY + lp.cellVSpan)
        }
        return rows
    }

    override fun dispatchDraw(canvas: Canvas) {
        // DragLayer.dispatchDraw: the focus outline goes below the items.
        focusIndicatorHelper.draw(canvas)
        super.dispatchDraw(canvas)
    }

    /** ui/NoPressDelay.kt: the icons show their pressed state at once. */
    override fun shouldDelayChildPressedState(): Boolean = false
}
