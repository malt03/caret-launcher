package com.malt03.textlauncher.ui.home

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.CellLayout
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.celllayout.ItemConfiguration
import com.android.launcher3.celllayout.ReorderAlgorithm
import com.android.launcher3.celllayout.ReorderParameters
import com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR
import com.google.android.material.color.MaterialColors
import com.malt03.textlauncher.data.HomeItem
import com.malt03.textlauncher.ui.GridSpec
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The home layout (docs/spec.md 5.1): one grid with the Launcher3 column count, icons without
 * labels, and no row limit. It sits in a vertical scroll container.
 *
 * Reordering uses Launcher3's ReorderAlgorithm through the [CellLayout] stand-in; applying the
 * result follows CellLayout.performReorder (temporary coordinates while dragging, committed on
 * drop).
 */
@SuppressLint("ViewConstructor")
class HomeGridView(context: Context, val grid: GridSpec) : ViewGroup(context), CellLayout.Children {
    val cellLayout = CellLayout(this)
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

    /** The child being dragged; not counted for the row count and not drawn in place. */
    var draggingView: View? = null

    /** Minimum height, so that the grid fills the visible area. */
    var viewportHeight = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.density
        color = MaterialColors.getColor(this@HomeGridView, com.google.android.material.R.attr.colorOnSurface)
        alpha = 160
    }
    private val folderRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@HomeGridView, com.google.android.material.R.attr.colorSurfaceContainerHighest)
        alpha = 200
    }
    private var dropOutline: Rect? = null
    private var folderRingCell: IntArray? = null
    private val tmpRect = Rect()

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    fun addItemView(view: View, item: HomeItem) {
        view.tag = item
        addView(view, CellLayoutLayoutParams(item.cellX, item.cellY, item.spanX, item.spanY))
    }

    fun itemViews(): List<View> = (0 until childCount).map { getChildAt(it) }

    fun viewFor(item: HomeItem): View? = itemViews().firstOrNull { it.tag === item }

    /** The child covering [cellX], [cellY] using the committed coordinates. */
    fun childAtCell(cellX: Int, cellY: Int, ignore: View? = null): View? = itemViews().firstOrNull { child ->
        if (child === ignore) return@firstOrNull false
        val lp = child.layoutParams as CellLayoutLayoutParams
        val x = if (lp.useTmpCoords) lp.tmpCellX else lp.cellX
        val y = if (lp.useTmpCoords) lp.tmpCellY else lp.cellY
        cellX in x until x + lp.cellHSpan && cellY in y until y + lp.cellVSpan
    }

    private fun usedRows(): Int = itemViews().filter { it !== draggingView }.maxOfOrNull {
        val lp = it.layoutParams as CellLayoutLayoutParams
        max(lp.cellY, if (lp.useTmpCoords) lp.tmpCellY else 0) + lp.cellVSpan
    } ?: 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cellWidth = (width - paddingLeft - paddingRight) / grid.columns
        val viewportRows = ceil((viewportHeight - paddingTop - paddingBottom).toFloat() / cellHeight).toInt()
        val rows = max(usedRows() + headroomRows, max(viewportRows, 1))
        cellLayout.setGridSize(grid.columns, rows)
        cellLayout.setCellGeometry(cellWidth, cellHeight, 0, 0, paddingLeft, paddingTop)
        for (child in itemViews()) {
            val lp = child.layoutParams as CellLayoutLayoutParams
            lp.setup(cellWidth, cellHeight, false, grid.columns, rows, NO_BORDER)
            child.measure(
                MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, max(rows * cellHeight + paddingTop + paddingBottom, viewportHeight))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (child in itemViews()) {
            val lp = child.layoutParams as CellLayoutLayoutParams
            child.layout(paddingLeft + lp.x, paddingTop + lp.y, paddingLeft + lp.x + lp.width, paddingTop + lp.y + lp.height)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        folderRingCell?.let { cell ->
            cellLayout.cellToRect(cell[0], cell[1], 1, 1, tmpRect)
            val radius = grid.iconSizePx * 0.65f
            canvas.drawCircle(tmpRect.exactCenterX(), tmpRect.exactCenterY(), radius, folderRingPaint)
        }
        dropOutline?.let { rect ->
            val inset = 6 * resources.displayMetrics.density
            val corner = 16 * resources.displayMetrics.density
            canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, corner, corner, outlinePaint)
        }
    }

    // ---- drag feedback ----

    fun showDropOutline(cellX: Int, cellY: Int, spanX: Int, spanY: Int) {
        val rect = Rect()
        cellLayout.cellToRect(cellX, cellY, spanX, spanY, rect)
        if (rect != dropOutline) {
            dropOutline = rect
            invalidate()
        }
    }

    fun showFolderRing(cell: IntArray?) {
        if (!cell.contentEquals(folderRingCell)) {
            folderRingCell = cell?.copyOf()
            invalidate()
        }
    }

    fun clearDragFeedback() {
        dropOutline = null
        folderRingCell = null
        invalidate()
    }

    /** CellLayout.getFolderCreationRadius: halfway between the reorder radius and the icon. */
    fun folderCreationRadius(): Float {
        val iconVisibleRadius = ICON_VISIBLE_AREA_FACTOR * grid.iconSizePx / 2
        val reorderRadius = min(cellWidth, cellHeight) / 2f
        return (reorderRadius + iconVisibleRadius) / 2
    }

    fun distanceToCellCenter(x: Float, y: Float, cellX: Int, cellY: Int): Float {
        cellLayout.cellToRect(cellX, cellY, 1, 1, tmpRect)
        return hypot(x - tmpRect.exactCenterX(), y - tmpRect.exactCenterY())
    }

    // ---- reorder (CellLayout.calculateReorder / performReorder) ----

    /** Rebuilds the occupancy from the committed coordinates, ignoring [dragView]. */
    fun prepareOccupancy(dragView: View?) {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED),
        )
        cellLayout.rebuildOccupied(dragView)
    }

    fun calculateReorder(pixelX: Int, pixelY: Int, spanX: Int, spanY: Int, dragView: View?): ItemConfiguration {
        val configuration = ItemConfiguration()
        cellLayout.copyCurrentStateToSolution(configuration)
        return ReorderAlgorithm(cellLayout).calculateReorder(
            ReorderParameters(pixelX, pixelY, spanX, spanY, spanX, spanY, dragView, configuration),
        )
    }

    /** CellLayout.performReorder with MODE_DRAG_OVER: move the others to temporary positions. */
    fun applyTemporaryReorder(solution: ItemConfiguration, dragView: View?) {
        for (child in itemViews()) {
            if (child === dragView) continue
            val c = solution.map[child] ?: continue
            val lp = child.layoutParams as CellLayoutLayoutParams
            val currentX = if (lp.useTmpCoords) lp.tmpCellX else lp.cellX
            val currentY = if (lp.useTmpCoords) lp.tmpCellY else lp.cellY
            lp.useTmpCoords = true
            lp.tmpCellX = c.cellX
            lp.tmpCellY = c.cellY
            if (currentX != c.cellX || currentY != c.cellY) animateChild(child)
        }
        requestLayout()
    }

    /** CellLayout.revertTempState. */
    fun revertTemporaryReorder() {
        var changed = false
        for (child in itemViews()) {
            val lp = child.layoutParams as CellLayoutLayoutParams
            if (!lp.useTmpCoords) continue
            val moved = lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY
            lp.useTmpCoords = false
            if (moved) {
                animateChild(child)
                changed = true
            }
        }
        if (changed) requestLayout()
    }

    /** CellLayout.commitTempPlacement: temporary positions become the real ones. */
    fun commitTemporaryReorder() {
        for (child in itemViews()) {
            val lp = child.layoutParams as CellLayoutLayoutParams
            if (lp.useTmpCoords) {
                lp.cellX = lp.tmpCellX
                lp.cellY = lp.tmpCellY
                lp.useTmpCoords = false
            }
            val item = child.tag as HomeItem
            item.cellX = lp.cellX
            item.cellY = lp.cellY
            item.spanX = lp.cellHSpan
            item.spanY = lp.cellVSpan
        }
        requestLayout()
    }

    fun hasTemporaryReorder(): Boolean = itemViews().any { (it.layoutParams as CellLayoutLayoutParams).useTmpCoords }

    /** Animates a child from where it is drawn now to its next layout position (150ms, like CellLayout). */
    private fun animateChild(child: View) {
        val oldLeft = child.left + child.translationX
        val oldTop = child.top + child.translationY
        child.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                v.removeOnLayoutChangeListener(this)
                val dx = oldLeft - l
                val dy = oldTop - t
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = CellLayout.REORDER_ANIMATION_DURATION.toLong()
                    addUpdateListener {
                        val f = it.animatedValue as Float
                        v.translationX = dx * f
                        v.translationY = dy * f
                    }
                    start()
                }
            }
        })
    }

    private companion object {
        val NO_BORDER = Point(0, 0)
    }
}
