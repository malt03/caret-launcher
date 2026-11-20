package com.malt03.textlauncher.ui.drag

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import com.android.launcher3.CellLayout
import com.android.launcher3.celllayout.ItemConfiguration
import com.malt03.textlauncher.data.HomeItem
import com.malt03.textlauncher.ui.home.AppIconView
import com.malt03.textlauncher.ui.home.HomeGridView
import kotlin.math.hypot

/**
 * Drag and drop on the home screen (docs/spec.md 5.3, 5.4), following Launcher3:
 *
 * - Long-pressing an app shows its menu; moving more than deep_shortcuts_start_drag_threshold
 *   (16dp) while still pressing closes the menu and starts the drag (DragController pre-drag).
 * - While dragging over an occupied cell, the others are pushed aside after REORDER_TIMEOUT
 *   (650ms) using Launcher3's ReorderAlgorithm.
 * - Dropping an app on another app within the folder creation radius makes a folder.
 * - Haptics: LONG_PRESS on drag start (LauncherDragController).
 */
class DragController(
    private val dragLayer: FrameLayout,
    private val scrollView: NestedScrollView,
    private val grid: HomeGridView,
    private val dropTargets: DropTargetBar,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onDragStarted(payload: Payload)
        fun onDropOnGrid(payload: Payload, cellX: Int, cellY: Int)
        fun onDropIntoFolderOrApp(payload: Payload, target: View)
        fun onDropOnTarget(payload: Payload, target: DropTargetBar.Target)
        /** Called after every drag, dropped or not. */
        fun onDragEnded()
    }

    /** What is dragged. [homeView] is set when it comes from the home grid. */
    class Payload(
        val appKey: String?,
        val homeItem: HomeItem?,
        val homeView: View?,
        /** Creates the view that follows the finger. It draws an icon of iconSizePx at its center. */
        val createDragVisual: () -> View,
        val spanX: Int = homeItem?.spanX ?: 1,
        val spanY: Int = homeItem?.spanY ?: 1,
    )

    private val density = dragLayer.resources.displayMetrics.density
    private val startDragThreshold = 16 * density
    private val autoScrollZone = 56 * density
    private val handler = Handler(Looper.getMainLooper())

    private var pending: Payload? = null
    private var pendingDownX = 0f
    private var pendingDownY = 0f
    private var onPendingStart: (() -> Unit)? = null

    private var payload: Payload? = null
    private var dragView: View? = null
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private var targetCell = intArrayOf(-1, -1)
    private var folderTarget: View? = null
    private var reorderSolution: ItemConfiguration? = null
    private var hoveredTarget: DropTargetBar.Target? = null
    private val reorderRunnable = Runnable { performReorder() }
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            val step = autoScrollStep()
            if (step != 0 && payload != null) {
                scrollView.scrollBy(0, step)
                onDragMove(lastX, lastY)
                handler.postDelayed(this, 16)
            }
        }
    }

    val isDragging: Boolean get() = payload != null

    /**
     * Arms the move-to-drag transition after a long press (the menu is shown by the caller).
     * [onStart] runs right before the drag starts, e.g. to close the menu.
     */
    fun armPreDrag(payload: Payload, downX: Float, downY: Float, onStart: () -> Unit) {
        pending = payload
        pendingDownX = downX
        pendingDownY = downY
        onPendingStart = onStart
    }

    /**
     * Sees every touch event of the root before its children. Returns true when the event is
     * consumed by the drag.
     */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.rawX
        val y = ev.rawY
        if (payload == null) {
            val armed = pending ?: return false
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (hypot(x - pendingDownX, y - pendingDownY) > startDragThreshold) {
                    pending = null
                    onPendingStart?.invoke()
                    onPendingStart = null
                    // The icon keeps its offset from the long-pressed point (DragController registration).
                    startDrag(armed, pendingDownX, pendingDownY)
                    onDragMove(x, y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pending = null
                    onPendingStart = null
                }
            }
            return false
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> onDragMove(x, y)
            MotionEvent.ACTION_UP -> onDrop(x, y)
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    /** Starts a drag right away (folders and widgets have no menu). */
    fun startDrag(payload: Payload, rawX: Float, rawY: Float) {
        this.payload = payload
        val iconSize = grid.grid.iconSizePx
        val view = payload.createDragVisual()
        dragLayer.addView(view, FrameLayout.LayoutParams(iconSize, iconSize))
        val layerLocation = IntArray(2).also { dragLayer.getLocationOnScreen(it) }
        touchOffsetX = iconSize / 2f
        touchOffsetY = iconSize / 2f
        payload.homeView?.let { source ->
            val sourceLocation = IntArray(2).also { source.getLocationOnScreen(it) }
            val iconLeft = sourceLocation[0] + ((source as? AppIconView)?.iconLeft() ?: ((source.width - iconSize) / 2))
            val iconTop = sourceLocation[1] + ((source as? AppIconView)?.iconTop() ?: ((source.height - iconSize) / 2))
            touchOffsetX = (rawX - iconLeft).coerceIn(0f, iconSize.toFloat())
            touchOffsetY = (rawY - iconTop).coerceIn(0f, iconSize.toFloat())
            source.visibility = View.INVISIBLE
            grid.draggingView = source
        }
        view.x = rawX - layerLocation[0] - touchOffsetX
        view.y = rawY - layerLocation[1] - touchOffsetY
        view.animate().scaleX(DRAG_SCALE).scaleY(DRAG_SCALE).setDuration(150).start()
        dragView = view
        // After measuring the source: this moves the home layout to make room for the drop targets.
        callbacks.onDragStarted(payload)
        dragLayer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        dropTargets.show(canUninstall = payload.appKey != null && payload.homeItem !is HomeItem.Folder)
        grid.headroomRows = payload.spanY + 1
        grid.prepareOccupancy(payload.homeView)
        lastX = rawX
        lastY = rawY
        onDragMove(rawX, rawY)
    }

    private fun onDragMove(rawX: Float, rawY: Float) {
        val payload = payload ?: return
        lastX = rawX
        lastY = rawY
        val layerLocation = IntArray(2).also { dragLayer.getLocationOnScreen(it) }
        dragView?.x = rawX - layerLocation[0] - touchOffsetX
        dragView?.y = rawY - layerLocation[1] - touchOffsetY

        val target = dropTargets.targetAt(rawX, rawY)
        if (target != hoveredTarget) {
            hoveredTarget = target
            dropTargets.setHovered(target)
            if (target != null) dragLayer.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
        if (target != null) {
            cancelReorder()
            grid.clearDragFeedback()
            folderTarget = null
            return
        }

        handler.removeCallbacks(autoScrollRunnable)
        if (autoScrollStep() != 0) handler.postDelayed(autoScrollRunnable, 16)

        // Position in grid coordinates, using the center of the dragged icon like Workspace.
        val gridLocation = IntArray(2).also { grid.getLocationOnScreen(it) }
        val iconSize = grid.grid.iconSizePx
        val centerX = rawX - touchOffsetX + iconSize / 2f - gridLocation[0]
        val centerY = rawY - touchOffsetY + iconSize / 2f - gridLocation[1]
        val cell = grid.cellLayout.findNearestAreaIgnoreOccupied(centerX.toInt(), centerY.toInt(), payload.spanX, payload.spanY, IntArray(2))
        if (cell[0] < 0) return

        // Folder creation (Workspace.manageFolderFeedback): an app over an app or a folder.
        val canMakeFolder = payload.appKey != null && payload.homeItem !is HomeItem.Folder && payload.spanX == 1 && payload.spanY == 1
        val over = grid.childAtCell(cell[0], cell[1], ignore = payload.homeView)
        val overItem = over?.tag as? HomeItem
        if (canMakeFolder && (overItem is HomeItem.App || overItem is HomeItem.Folder) &&
            grid.distanceToCellCenter(centerX, centerY, cell[0], cell[1]) < grid.folderCreationRadius()
        ) {
            if (folderTarget !== over) {
                folderTarget = over
                cancelReorder()
                grid.revertTemporaryReorder()
                grid.showFolderRing(cell)
                dragLayer.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }
            return
        }
        if (folderTarget != null) {
            folderTarget = null
            grid.showFolderRing(null)
        }

        if (!cell.contentEquals(targetCell)) {
            targetCell = cell
            cancelReorder()
            val occupied = grid.cellLayout.getIntersectingRectanglesInRegion(
                Rect(cell[0], cell[1], cell[0] + payload.spanX, cell[1] + payload.spanY), payload.homeView,
            ) != null
            if (occupied) {
                handler.postDelayed(reorderRunnable, REORDER_TIMEOUT)
            } else {
                grid.revertTemporaryReorder()
            }
            grid.showDropOutline(cell[0], cell[1], payload.spanX, payload.spanY)
        }
    }

    private fun performReorder() {
        val payload = payload ?: return
        val gridLocation = IntArray(2).also { grid.getLocationOnScreen(it) }
        val iconSize = grid.grid.iconSizePx
        val centerX = (lastX - touchOffsetX + iconSize / 2f - gridLocation[0]).toInt()
        val centerY = (lastY - touchOffsetY + iconSize / 2f - gridLocation[1]).toInt()
        grid.prepareOccupancy(payload.homeView)
        val solution = grid.calculateReorder(centerX, centerY, payload.spanX, payload.spanY, payload.homeView)
        if (solution.isSolution) {
            reorderSolution = solution
            grid.applyTemporaryReorder(solution, payload.homeView)
            grid.showDropOutline(solution.cellX, solution.cellY, solution.spanX, solution.spanY)
            dragLayer.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
    }

    private fun cancelReorder() {
        handler.removeCallbacks(reorderRunnable)
        reorderSolution = null
    }

    private fun onDrop(rawX: Float, rawY: Float) {
        val payload = payload ?: return
        onDragMove(rawX, rawY)
        handler.removeCallbacks(autoScrollRunnable)
        handler.removeCallbacks(reorderRunnable)
        val target = hoveredTarget
        val folder = folderTarget
        when {
            target != null -> {
                grid.revertTemporaryReorder()
                callbacks.onDropOnTarget(payload, target)
                finish(animateTo = null)
            }
            folder != null -> {
                grid.revertTemporaryReorder()
                callbacks.onDropIntoFolderOrApp(payload, folder)
                finish(animateTo = folder)
            }
            else -> {
                val solution = reorderSolution
                val cell: IntArray
                if (solution != null && solution.isSolution) {
                    grid.commitTemporaryReorder()
                    cell = intArrayOf(solution.cellX, solution.cellY)
                } else {
                    grid.revertTemporaryReorder()
                    grid.commitTemporaryReorder()
                    grid.prepareOccupancy(payload.homeView)
                    val gridLocation = IntArray(2).also { grid.getLocationOnScreen(it) }
                    val iconSize = grid.grid.iconSizePx
                    val centerX = (rawX - touchOffsetX + iconSize / 2f - gridLocation[0]).toInt()
                    val centerY = (rawY - touchOffsetY + iconSize / 2f - gridLocation[1]).toInt()
                    cell = grid.cellLayout.findNearestVacantArea(centerX, centerY, payload.spanX, payload.spanY, payload.spanX, payload.spanY, IntArray(2), null)
                }
                if (cell[0] >= 0) {
                    callbacks.onDropOnGrid(payload, cell[0], cell[1])
                }
                finish(animateTo = null, cell = cell.takeIf { it[0] >= 0 })
            }
        }
    }

    fun cancel() {
        if (payload == null) return
        handler.removeCallbacks(autoScrollRunnable)
        cancelReorder()
        grid.revertTemporaryReorder()
        finish(animateTo = null)
    }

    private fun finish(animateTo: View?, cell: IntArray? = null) {
        val view = dragView
        val source = payload?.homeView
        payload = null
        pending = null
        targetCell = intArrayOf(-1, -1)
        folderTarget = null
        reorderSolution = null
        hoveredTarget = null
        grid.clearDragFeedback()
        grid.draggingView = null
        grid.headroomRows = 0
        dropTargets.hide()

        val done = {
            if (view != null) dragLayer.removeView(view)
            source?.visibility = View.VISIBLE
            callbacks.onDragEnded()
        }
        if (view == null) {
            done()
            return
        }
        // Drop animation (DragView.animateTo): settle into the target, scaling back down.
        val layerLocation = IntArray(2).also { dragLayer.getLocationOnScreen(it) }
        val iconSize = grid.grid.iconSizePx
        val destination: Pair<Float, Float>? = when {
            animateTo != null -> {
                val l = IntArray(2).also { animateTo.getLocationOnScreen(it) }
                (l[0] + (animateTo.width - iconSize) / 2f - layerLocation[0]) to (l[1] + (animateTo.height - iconSize) / 2f - layerLocation[1])
            }
            cell != null -> {
                val rect = Rect()
                grid.cellLayout.cellToRect(cell[0], cell[1], 1, 1, rect)
                val l = IntArray(2).also { grid.getLocationOnScreen(it) }
                (l[0] + rect.left + (rect.width() - iconSize) / 2f - layerLocation[0]) to (l[1] + rect.top + (rect.height() - iconSize) / 2f - layerLocation[1])
            }
            else -> null
        }
        val animator = view.animate().scaleX(1f).scaleY(1f).setDuration(DROP_DURATION)
        if (destination != null) animator.x(destination.first).y(destination.second) else animator.alpha(0f)
        animator.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = done()
        }).start()
    }

    private fun autoScrollStep(): Int {
        val location = IntArray(2).also { scrollView.getLocationOnScreen(it) }
        val top = location[1]
        val bottom = top + scrollView.height
        return when {
            lastY < top + autoScrollZone -> -(12 * density).toInt()
            lastY > bottom - autoScrollZone -> (12 * density).toInt()
            else -> 0
        }
    }

    private companion object {
        /** Workspace.REORDER_TIMEOUT */
        const val REORDER_TIMEOUT = 650L
        const val DRAG_SCALE = 1.15f
        const val DROP_DURATION = CellLayout.REORDER_ANIMATION_DURATION.toLong() + 100
    }
}
