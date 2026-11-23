/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.widget.resize

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.CellLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.util.WidgetSizeHandler.Companion.updateSizeRanges
import kotlin.math.abs

/**
 * Responsible for managing widget's size, evaluating and performing the resize operations.
 *
 * Stand-in for Launcher3's ResizeManager (android-17.0.0_r1); changes are marked with
 * "TextLauncher:". TextLauncher: the two panels are the all apps list and the home screen, so
 * there is no paired CellLayout to dim (handleInvalidResizeForTwoPanelUi); there are no pending
 * widget views and no stats logging. The widget's spans come from the home screen
 * ([Launcher.getLauncherAppWidgetInfo]), and so do its size updates.
 */
class ResizeManager(
    private val widgetView: LauncherAppWidgetHostView,
    private val cellLayout: CellLayout,
    private val dragLayer: DragLayer,
    private val deviceProfile: DeviceProfile,
    private val touchPadding: Int,
) {
    // Constraints derived from widget's metadata to understand if a widget can be resized and
    // how much.
    val resizeConstraints: ResizeConstraints
    var resizeListener: ResizeListener? = null

    private val resizeSession: ResizeSession = ResizeSession()

    // TextLauncher: the launcher, for the widget's spans.
    private val launcher: Launcher = Launcher.getLauncher(cellLayout.context)

    init {
        val itemInfo = widgetView.tag as LauncherAppWidgetInfo

        // TextLauncher: no custom widgets.
        val widgetProviderInfo = launcher.getLauncherAppWidgetInfo(widgetView.appWidgetInfo)

        val resizeMode = widgetProviderInfo?.resizeMode ?: 0
        resizeConstraints =
            ResizeConstraints(
                minHSpan = widgetProviderInfo?.minSpanX ?: itemInfo.spanX,
                minVSpan = widgetProviderInfo?.minSpanY ?: itemInfo.spanY,
                maxHSpan = widgetProviderInfo?.maxSpanX ?: itemInfo.spanX,
                maxVSpan = widgetProviderInfo?.maxSpanY ?: itemInfo.spanY,
                // TextLauncher: InvariantDeviceProfile's numRows and numColumns, as Launcher3
                // passes them.
                cellCountX = cellLayout.countY,
                cellCountY = cellLayout.countX,
                horizontalResizeModeEnabled =
                    resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
                verticalResizeModeEnabled =
                    resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            )

        initializeWidgetViewForResize(itemInfo)
    }

    private fun initializeWidgetViewForResize(widgetInfo: ItemInfo) {
        // TextLauncher: the model's cells are the presenter's (no CellPosMapper).
        (this.widgetView.layoutParams as CellLayoutLayoutParams).apply {
            cellX = widgetInfo.cellX
            tmpCellX = widgetInfo.cellX
            cellY = widgetInfo.cellY
            tmpCellY = widgetInfo.cellY
            cellHSpan = widgetInfo.spanX
            cellVSpan = widgetInfo.spanY
            isLockedToGrid = true
        }

        // When we create the resize frame, we first mark all cells as unoccupied. The appropriate
        // cells (same if not resized, or different) will be marked as occupied when the resize
        // frame is dismissed.
        this.cellLayout.markCellsAsUnoccupiedForView(this.widgetView)
        resizeSession.reset()
    }

    /** Returns the current bounds of the widget. */
    fun getCurrentWidgetBounds(): Rect {
        val bounds = Rect()
        val scale = widgetView.scaleToFit
        dragLayer.getViewRectRelativeToSelf(widgetView, bounds)

        val scaledWidth = Math.round(scale * bounds.width())
        val scaledHeight = Math.round(scale * bounds.height())
        val x = bounds.left + (bounds.width() - scaledWidth) / 2
        val y = bounds.top + (bounds.height() - scaledHeight) / 2

        bounds.left = x
        bounds.top = y
        bounds.right = x + scaledWidth
        bounds.bottom = y + scaledHeight
        return bounds
    }

    /** Resizes the widget immediately for the provided span delta. */
    fun runAtomicResizeSession(edge: Edge, spanDelta: Int, onResizeComplete: () -> Unit) {
        val isHorizontalEdge = edge.isHorizontal()
        val totalDragAmount =
            if (isHorizontalEdge) {
                val widthDelta =
                    cellLayout.cellWidthWithBorderSpace(deviceProfile).toFloat() * spanDelta
                Offset(x = edge.expandDirX * widthDelta, y = 0f)
            } else {
                val heightDelta =
                    cellLayout.cellHeightWithBorderSpace(deviceProfile).toFloat() * spanDelta
                Offset(x = 0f, y = edge.expandDirY * heightDelta)
            }

        // In this resize mode, user taps on a button, so only one of horizontal / vertical will
        // be participating.
        val horizontalEdge = if (isHorizontalEdge) edge else null
        val verticalEdge = if (!isHorizontalEdge) edge else null

        // Match the way a resize session is run across drags.
        beginResizeSession(horizontalEdge, verticalEdge)
        visualizeResizeForDelta(horizontalEdge, verticalEdge, totalDragAmount)
        endResizeSession(horizontalEdge, verticalEdge, totalDragAmount)
        widgetView.post { onResizeComplete() }
    }

    /**
     * Starts a new session of tracking the deltas until [endResizeSession] is called; Also see
     * [visualizeResizeForDelta] which helps update resize frame size during the session.
     */
    fun beginResizeSession(horizontal: Edge?, vertical: Edge?) {
        resizeSession.apply {
            val rect = getCurrentWidgetBounds()
            // Initialize the base bounds on which all the deltas will be applied.
            baselineXRange.set(rect.left, rect.right)
            baselineYRange.set(rect.top, rect.bottom)

            // calculate the relative offset we can move on either sides.
            when (horizontal) {
                Edge.Left ->
                    // We can move the left handle as much as left edge of drag layer; and move it
                    // right as much as there is enough touchable space left between left and right
                    // edge
                    deltaXRange.set(start = -rect.left, end = rect.width() - touchPadding)

                Edge.Right ->
                    // We can move the move it left as much as there is enough space left between
                    // left handle and it; and move it right until the right edge of drag layer.
                    deltaXRange.set(
                        start = touchPadding - rect.width(),
                        end = dragLayer.width - rect.right + touchPadding,
                    )

                else -> deltaXRange.reset()
            }

            when (vertical) {
                Edge.Top ->
                    // We can move the top edge upwards until the top edge of drag layer and
                    // move down as much as there is enough space between it and bottom edge.
                    deltaYRange.set(start = -rect.top, end = rect.height() - touchPadding)

                Edge.Bottom ->
                    // We can move the bottom edge upwards until there is enough space between it
                    // and the top edge; and move it down as much until bottom edge of drag layer.
                    deltaYRange.set(
                        start = touchPadding - rect.height(),
                        end = dragLayer.height - rect.bottom + touchPadding,
                    )

                else -> deltaYRange.reset()
            }
        }
    }

    /** Helps visually the resize the frame and maybe widget during a resize session. */
    fun visualizeResizeForDelta(horizontal: Edge?, vertical: Edge?, totalDragAmount: Offset) {
        // The drag amount is a relative value representing the movement.
        val deltaX = resizeSession.deltaXRange.clamp(totalDragAmount.x.toInt())
        val deltaY = resizeSession.deltaYRange.clamp(totalDragAmount.y.toInt())

        resizeSession.apply {
            baselineXRange.applyDelta(
                moveStart = horizontal == Edge.Left,
                moveEnd = horizontal == Edge.Right,
                delta = deltaX,
                outputRange = tempRange1,
            )
            val newFrameX = tempRange1.start
            val newFrameWidth = tempRange1.size()

            baselineYRange.applyDelta(
                moveStart = vertical == Edge.Top,
                moveEnd = vertical == Edge.Bottom,
                delta = deltaY,
                outputRange = tempRange1,
            )
            val newFrameY = tempRange1.start
            val newFrameHeight = tempRange1.size()

            val newFrameBounds =
                Rect(
                    /*left=*/ newFrameX,
                    /*top=*/ newFrameY,
                    /*right=*/ newFrameX + newFrameWidth,
                    /*bottom=*/ newFrameY + newFrameHeight,
                )
            // TextLauncher: no paired CellLayout (handleInvalidResizeForTwoPanelUi).

            // Publish the resize request to frame UI, so it can adapt itself.
            resizeListener?.onNewResizeRequest(newFrameBounds)

            resizeWidgetIfNeeded(horizontal, vertical, deltaX, deltaY, commit = false)
        }
    }

    /** Concludes a resize session by invalidating temporary states. */
    fun endResizeSession(horizontal: Edge?, vertical: Edge?, totalDragAmount: Offset) {
        val deltaX = totalDragAmount.x.toInt()
        val deltaY = totalDragAmount.y.toInt()
        resizeWidgetIfNeeded(horizontal, vertical, deltaX, deltaY, commit = true)

        val xThreshold = cellLayout.cellWidthWithBorderSpace(deviceProfile).toFloat()
        val yThreshold = cellLayout.cellHeightWithBorderSpace(deviceProfile).toFloat()

        resizeSession.apply {
            deltaXAddOn = runningHInc * xThreshold
            deltaYAddOn = runningVInc * yThreshold
        }

        // User stopped resizing, so snap the frame to widget.
        widgetView.post { resizeListener?.onNewResizeRequest(getCurrentWidgetBounds()) }

        // TextLauncher: no two-panel drag effect to reset.
    }

    /** To be called before destroying the resize manager. */
    fun finish() {
        // TextLauncher: the CellLayout is never attached; the widget is on its grid, unless it is
        // being dragged (in the DragView) or was removed.
        if (widgetView.parent !is ShortcutAndWidgetContainer) return
        if (widgetView.layoutParams !is CellLayoutLayoutParams) return

        val wlp = widgetView.layoutParams as CellLayoutLayoutParams
        cellLayout.createAreaForResize(
            wlp.tmpCellX,
            wlp.tmpCellY,
            wlp.cellHSpan,
            wlp.cellVSpan,
            widgetView,
            resizeSession.lastDirectionVector,
            /* commit= */ true,
        )

        // TextLauncher: no stats logging.
    }

    private fun resizeWidgetIfNeeded(
        horizontal: Edge?,
        vertical: Edge?,
        deltaX: Int,
        deltaY: Int,
        commit: Boolean,
    ) {
        val wlp: ViewGroup.LayoutParams? = widgetView.layoutParams
        if (wlp == null || wlp !is CellLayoutLayoutParams) return

        val xThreshold = cellLayout.cellWidthWithBorderSpace(deviceProfile).toFloat()
        val yThreshold = cellLayout.cellHeightWithBorderSpace(deviceProfile).toFloat()

        val hSpanInc =
            getSpanIncrement(
                (deltaX + resizeSession.deltaXAddOn) / xThreshold - resizeSession.runningHInc
            )
        val vSpanInc =
            getSpanIncrement(
                (deltaY + resizeSession.deltaYAddOn) / yThreshold - resizeSession.runningVInc
            )

        if (!commit && (hSpanInc == 0 && vSpanInc == 0)) return

        resizeSession.directionVector[DIRECTION_HORIZONTAL] = DIRECTION_NONE
        resizeSession.directionVector[DIRECTION_VERTICAL] = DIRECTION_NONE

        var spanX = wlp.cellHSpan
        var spanY = wlp.cellVSpan
        var cellX = if (wlp.useTmpCoords) wlp.tmpCellX else wlp.cellX
        var cellY = if (wlp.useTmpCoords) wlp.tmpCellY else wlp.cellY

        resizeSession.tempRange1.set(cellX, spanX + cellX)
        val hSpanDelta =
            resizeSession.tempRange1.applyDeltaAndBound(
                moveStart = horizontal == Edge.Left,
                moveEnd = horizontal == Edge.Right,
                delta = hSpanInc,
                minSize = resizeConstraints.minHSpan,
                maxSize = resizeConstraints.maxHSpan,
                maxEnd = cellLayout.countX,
                outputRange = resizeSession.tempRange2,
            )
        cellX = resizeSession.tempRange2.start
        spanX = resizeSession.tempRange2.size()
        if (hSpanDelta != 0) {
            resizeSession.directionVector[DIRECTION_HORIZONTAL] =
                if (horizontal == Edge.Left) DIRECTION_X_LEFT else DIRECTION_X_RIGHT
        }

        resizeSession.tempRange1.set(cellY, spanY + cellY)
        val vSpanDelta =
            resizeSession.tempRange1.applyDeltaAndBound(
                moveStart = vertical == Edge.Top,
                moveEnd = vertical == Edge.Bottom,
                delta = vSpanInc,
                minSize = resizeConstraints.minVSpan,
                maxSize = resizeConstraints.maxVSpan,
                maxEnd = cellLayout.countY,
                outputRange = resizeSession.tempRange2,
            )
        cellY = resizeSession.tempRange2.start
        spanY = resizeSession.tempRange2.size()
        if (vSpanDelta != 0) {
            resizeSession.directionVector[DIRECTION_VERTICAL] =
                if (vertical == Edge.Top) DIRECTION_Y_TOP else DIRECTION_Y_BOTTOM
        }

        if (!commit && vSpanDelta == 0 && hSpanDelta == 0) return

        if (commit) { // maintain the direction that we were last resizing.
            resizeSession.directionVector[DIRECTION_HORIZONTAL] =
                resizeSession.lastDirectionVector[DIRECTION_HORIZONTAL]
            resizeSession.directionVector[DIRECTION_VERTICAL] =
                resizeSession.lastDirectionVector[DIRECTION_VERTICAL]
        } else { // Save current direction for until end of session.
            resizeSession.lastDirectionVector[DIRECTION_HORIZONTAL] =
                resizeSession.directionVector[DIRECTION_HORIZONTAL]
            resizeSession.lastDirectionVector[DIRECTION_VERTICAL] =
                resizeSession.directionVector[DIRECTION_VERTICAL]
        }

        // TextLauncher: no pending widget views (PendingAppWidgetHostView).
        if (
            cellLayout.createAreaForResize(
                cellX,
                cellY,
                spanX,
                spanY,
                widgetView,
                resizeSession.directionVector,
                /*commit=*/ commit,
            )
        ) {
            wlp.tmpCellX = cellX
            wlp.tmpCellY = cellY
            wlp.cellHSpan = spanX
            wlp.cellVSpan = spanY
            resizeSession.runningVInc += vSpanDelta
            resizeSession.runningHInc += hSpanDelta

            if (!commit) {
                widgetView.updateSizeRanges(spanX, spanY)
            }
        }

        widgetView.requestLayout()
    }

    /** Returns whether user has moved resize handle enough to snap to the next span. */
    private fun getSpanIncrement(deltaFraction: Float): Int {
        return if (abs(deltaFraction) > RESIZE_THRESHOLD) {
            Math.round(deltaFraction)
        } else 0
    }

    /**
     * Returns if a widget can resized down in horizontal or vertical direction.
     *
     * Unlike the [canExpandInDirection], this just needs to know if widget's min size allows
     * further resizing down in the given direction.
     */
    fun canShrink(inHorizontalDirection: Boolean): Boolean {
        val wlp = widgetView.layoutParams as CellLayoutLayoutParams

        return if (inHorizontalDirection) {
            wlp.cellHSpan > resizeConstraints.minHSpan
        } else {
            wlp.cellVSpan > resizeConstraints.minVSpan
        }
    }

    /**
     * Indicates if a widget can be resized in the provided directions in X / Y axis.
     *
     * This requires us to check the surrounding area to see if we can really expand.
     */
    fun canExpandInDirection(dirX: Int, dirY: Int): Boolean {
        if (dirX == DIRECTION_NONE && dirY == DIRECTION_NONE) return false // no resize
        check(dirX == DIRECTION_NONE || dirY == DIRECTION_NONE) {
            "Can resize in only one direction"
        }

        val lp = widgetView.layoutParams as CellLayoutLayoutParams
        val cellX = if (lp.useTmpCoords) lp.tmpCellX else lp.cellX
        val cellY = if (lp.useTmpCoords) lp.tmpCellY else lp.cellY

        fun canGrowHorizontally(): Boolean {
            if (lp.cellHSpan < resizeConstraints.maxHSpan) {
                return if (dirX == DIRECTION_X_LEFT) {
                    return cellX > 0 &&
                        cellLayout.hasAreaForResize(
                            // when resizing on left, widget's cell decrements.
                            /*cellX=*/ cellX - 1,
                            /*cellY=*/ cellY,
                            /*spanX=*/ lp.cellHSpan + 1,
                            /*spanY=*/ lp.cellVSpan,
                            /*dragView*/ widgetView,
                            /*direction=*/ intArrayOf(dirX, dirY),
                        )
                } else {
                    ((cellX + lp.cellHSpan) < cellLayout.countX &&
                        cellLayout.hasAreaForResize(
                            // when resizing towards right, widget's cell doesn't change, just
                            // span increases
                            /*cellX=*/ cellX,
                            /*cellY=*/ cellY,
                            /*spanX=*/ lp.cellHSpan + 1,
                            /*spanY=*/ lp.cellVSpan,
                            /*dragView*/ widgetView,
                            /*direction=*/ intArrayOf(dirX, dirY),
                        ))
                }
            }
            return false
        }

        fun canGrowVertically(): Boolean {
            if (lp.cellVSpan < resizeConstraints.maxVSpan) {
                return if (dirY == DIRECTION_Y_TOP) {
                    cellY > 0 &&
                        cellLayout.hasAreaForResize(
                            /*cellX=*/ cellX,
                            /*cellY=*/ cellY - 1,
                            /*spanX=*/ lp.cellHSpan,
                            /*spanY=*/ lp.cellVSpan + 1,
                            /*dragView*/ widgetView,
                            /*direction=*/ intArrayOf(dirX, dirY),
                        )
                } else {
                    ((cellY + lp.cellVSpan) < cellLayout.countY &&
                        cellLayout.hasAreaForResize(
                            /*cellX=*/ cellX,
                            /*cellY=*/ cellY,
                            /*spanX=*/ lp.cellHSpan,
                            /*spanY=*/ lp.cellVSpan + 1,
                            /*dragView*/ widgetView,
                            /*direction=*/ intArrayOf(dirX, dirY),
                        ))
                }
            }
            return false
        }

        return when {
            dirY == 0 -> canGrowHorizontally()
            else -> canGrowVertically()
        }
    }

    /**
     * Constraints derived from widget's metadata to understand if a widget can be resized and how
     * much.
     */
    data class ResizeConstraints(
        val minHSpan: Int,
        val minVSpan: Int,
        val maxHSpan: Int,
        val maxVSpan: Int,
        val cellCountX: Int,
        val cellCountY: Int,
        val horizontalResizeModeEnabled: Boolean,
        val verticalResizeModeEnabled: Boolean,
    )

    /** Holding intermediate values during a resize session. */
    class ResizeSession {
        var runningHInc = 0
        var runningVInc = 0
        var deltaXAddOn = 0f
        var deltaYAddOn = 0f
        val directionVector = IntArray(2)
        val lastDirectionVector = IntArray(2)
        val tempRange1 = AppWidgetResizeFrame.IntRange()
        val tempRange2 = AppWidgetResizeFrame.IntRange()

        // Snapshot of frame's position at beginning of a resize. When you drag an edge, the delta
        // is applied with this as baseline point.
        val baselineXRange = AppWidgetResizeFrame.IntRange()
        val baselineYRange = AppWidgetResizeFrame.IntRange()

        // The movement limits. The maximum we can move the frame edges in each direction. This is
        // used to clamp the deltas before we apply them to the baseline position.
        val deltaXRange = AppWidgetResizeFrame.IntRange()
        val deltaYRange = AppWidgetResizeFrame.IntRange()

        fun reset() {
            runningHInc = 0
            runningVInc = 0
            deltaXAddOn = 0f
            deltaYAddOn = 0f
            lastDirectionVector.set(DIRECTION_NONE, DIRECTION_NONE)
            directionVector.set(DIRECTION_NONE, DIRECTION_NONE)
        }
    }

    companion object {
        const val DIRECTION_X_LEFT = -1
        const val DIRECTION_X_RIGHT = 1
        const val DIRECTION_Y_TOP = -1
        const val DIRECTION_Y_BOTTOM = 1
        const val DIRECTION_NONE = 0

        const val DIRECTION_HORIZONTAL = 0
        const val DIRECTION_VERTICAL = 1

        // Threshold at which to snap to the next span and update widget's size.
        const val RESIZE_THRESHOLD = 0.66f

        private fun CellLayout.cellWidthWithBorderSpace(deviceProfile: DeviceProfile) =
            cellWidth + deviceProfile.workspaceProfile.cellLayoutBorderSpacePx.x

        private fun CellLayout.cellHeightWithBorderSpace(deviceProfile: DeviceProfile) =
            cellHeight + deviceProfile.workspaceProfile.cellLayoutBorderSpacePx.y
    }

    /** Listener for either in-progress or committed resize events. */
    interface ResizeListener {
        /** Indicates that bounds were changed for resize frame. */
        fun onNewResizeRequest(bounds: Rect)
    }
}
