package com.malt03.textlauncher.ui.home

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.android.launcher3.Alarm
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings
import com.android.launcher3.OnAlarmListener
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.Workspace
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.PreviewBackground
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.Poppable
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.PendingAddShortcutInfo
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.views.ActivityContext
import com.google.android.msdl.data.model.MSDLToken
import com.malt03.textlauncher.ui.drag.HomeDragController

/**
 * The home grid as a drop target and drag source: the drag and drop part of Launcher3's Workspace
 * (android-17.0.0_r1) for its one CellLayout. Methods keep Workspace's names and logic. Left out:
 * pages, the hotseat, widgets and state transitions (a system drag, from the widget picker, is
 * taken as in Workspace.onDropExternal); the grid scrolls vertically
 * instead, so dragging near its top or bottom edge scrolls it.
 *
 * Drag coordinates (DragObject.x / y) are in the grid's coordinates: [getDropView] is the grid,
 * and the drag controller maps into it.
 */
class HomeWorkspace(
    private val activity: ActivityContext,
    private val mLauncher: Launcher,
    private val grid: HomeGridView,
    private val scrollView: NestedScrollView,
    private val dragController: DragController,
    private val host: Host,
) : DropTarget, DragSource, DragController.DragListener {

    interface Host {
        /** Launcher.addFolder: an empty folder at the cell, added to the grid. */
        fun addFolder(cellX: Int, cellY: Int): FolderIconView

        /**
         * ItemInflater.inflateItem and Workspace.addInScreen: the app or shortcut at the cell, on
         * the grid.
         */
        fun addItem(info: WorkspaceItemInfo, cellX: Int, cellY: Int): View

        /** Workspace.removeWorkspaceItem: the view leaves the grid; the model is not changed. */
        fun removeWorkspaceItem(view: View)

        /** WidgetSizeHandler.updateSizeRanges for a widget whose spans changed. */
        fun updateWidgetSize(view: AppWidgetHostView, spanX: Int, spanY: Int)

        /**
         * StateManager.goToState(NORMAL) with a success callback: runs [callback] once the home
         * screen is back from the spring-loaded state.
         */
        fun runWhenNormal(callback: Runnable)
    }

    /** CellLayout.CellInfo */
    private class CellInfo(val cell: View, val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int)

    private val context = grid.context
    private val layout: CellLayout get() = grid.cellLayout

    /** The CellInfo of the item being dragged from the grid. */
    private var mDragInfo: CellInfo? = null

    /** Target drop area calculated during last acceptDrop call. */
    private var mTargetCell = IntArray(2)
    private var mDragOverX = -1
    private var mDragOverY = -1

    /** The CellLayout that is currently being dragged over */
    private var mDragTargetLayout: CellLayout? = null

    /** The CellLayout which will be dropped to */
    private var mDropToLayout: CellLayout? = null

    private val mTempXY = IntArray(2)
    private var mDragViewVisualCenter = FloatArray(2)

    // Related to dragging, folder creation and reordering
    private var mDragMode = DRAG_MODE_NONE
    private var mLastReorderX = -1
    private var mLastReorderY = -1

    private val mReorderAlarm = Alarm()
    private var mFolderCreateBg = PreviewBackground(context)
    private var mDragOverFolderIcon: FolderIconView? = null
    private var mDragOverView: View? = null
    private var mCreateUserFolderOnDrop = false
    private var mAddToExistingFolderOnDrop = false

    private val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private var autoScrollStep = 0
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (autoScrollStep == 0 || !dragController.isDragging) return
            scrollView.scrollBy(0, autoScrollStep)
            dragController.forceTouchMove()
            handler.postDelayed(this, AUTO_SCROLL_INTERVAL_MS)
        }
    }

    override fun getDropView(): View = grid

    /** Workspace.startDrag */
    fun startDrag(child: View, dragInfo: ItemInfo, options: DragOptions, startLongPressAction: (() -> DragOptions.PreDragCondition?)?) {
        val lp = child.layoutParams as CellLayoutLayoutParams
        mDragInfo = CellInfo(child, lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan)
        dragInfo.cellX = lp.cellX
        dragInfo.cellY = lp.cellY
        dragInfo.spanX = lp.cellHSpan
        dragInfo.spanY = lp.cellVSpan
        child.visibility = View.INVISIBLE

        beginDragShared(child, this, dragInfo, DragPreviewProvider(child), options, startLongPressAction)
    }

    /**
     * Workspace.beginDragShared: the core of every drag that may drop on the grid, also from search
     * results and folders. [startLongPressAction] is BubbleTextView.startLongPressAction: it shows
     * the long-press menu and returns the menu's pre-drag condition.
     */
    fun beginDragShared(
        child: View,
        source: DragSource,
        dragObject: ItemInfo,
        previewProvider: DragPreviewProvider,
        dragOptions: DragOptions,
        startLongPressAction: (() -> DragOptions.PreDragCondition?)? = null,
    ): DragView = beginDragShared(
        child, child as? DraggableView, source, dragObject, previewProvider, dragOptions, startLongPressAction,
    )

    /**
     * Workspace.beginDragShared with the view whose bounds the drag starts from, as for an app
     * shortcut dragged out of the long-press menu.
     */
    fun beginDragShared(
        child: View,
        draggableView: DraggableView?,
        source: DragSource,
        dragObject: ItemInfo,
        previewProvider: DragPreviewProvider,
        dragOptions: DragOptions,
        startLongPressAction: (() -> DragOptions.PreDragCondition?)? = null,
    ): DragView {
        var iconScale = 1f
        if (child is BubbleTextView) {
            val icon: Drawable? = child.icon
            if (icon is FastBitmapDrawable) {
                iconScale = icon.getAnimatedScale()
            }
        }

        // Clear the pressed state if necessary
        child.clearFocus()
        child.isPressed = false
        if (child is BubbleTextView) {
            child.clearPressedBackground()
        }

        val contentView = previewProvider.contentView
        val scale: Float
        // The draggable drawable follows the touch point around on the screen
        val drawable: Drawable?
        if (contentView == null) {
            drawable = previewProvider.createDrawable()
            scale = previewProvider.getScaleAndPosition(drawable, mTempXY)
        } else {
            drawable = null
            scale = previewProvider.getScaleAndPosition(contentView, mTempXY)
        }

        var dragLayerX = mTempXY[0]
        var dragLayerY = mTempXY[1]

        val dragRect = Rect()

        if (draggableView != null) {
            draggableView.getSourceVisualDragBounds(dragRect)
            dragLayerY += dragRect.top
        }

        if (child is BubbleTextView && !dragOptions.isAccessibleDrag && !dragOptions.isMouseDrag) {
            dragOptions.preDragCondition = startLongPressAction?.invoke()
            // TextLauncher: search results use the home icon size, so no preDragEndScale.
        } else if (child is Poppable && !dragOptions.isAccessibleDrag && !dragOptions.isMouseDrag) {
            // Flags.homeScreenEditImprovements (enabled in the release) and a Poppable child (a
            // folder or a widget): the popup controller for home screen items shows the menu,
            // whose pre-drag condition starts the drag.
            dragOptions.preDragCondition = startLongPressAction?.invoke()
        }

        dragOptions.preDragCondition?.let { condition ->
            val xDragOffSet = condition.dragOffset.x
            val yDragOffSet = condition.dragOffset.y
            if (xDragOffSet != 0 || yDragOffSet != 0) {
                dragLayerX += xDragOffSet
                dragLayerY += yDragOffSet
            }
        }

        // TextLauncher: no system drags.
        return if (contentView != null) {
            // Flags.homeScreenEditImprovements (enabled in the release): a widget stays where it
            // is until the drag starts, then shrinks to its spring-loaded size.
            val homeDragController = dragController as? HomeDragController
            if ((child.tag as? ItemInfo)?.itemType == ITEM_TYPE_APPWIDGET && homeDragController != null) {
                dragOptions.deferDragToPreDragEnd = true
                dragOptions.preDragEndScale = (contentView.measuredWidth +
                    homeDragController.getWidgetDragScalePx(null, contentView, dragObject)) /
                    contentView.measuredWidth
            }
            dragController.startDrag(
                contentView, draggableView, dragLayerX, dragLayerY, source, dragObject, dragRect,
                scale * iconScale, scale, dragOptions,
            )
        } else {
            val homeDragController = dragController as? HomeDragController
            val childItemInfo = child.tag as? ItemInfo
            if (childItemInfo != null && childItemInfo.itemType == ITEM_TYPE_APPWIDGET &&
                homeDragController != null && drawable != null
            ) {
                dragOptions.preDragEndScale = (drawable.intrinsicWidth +
                    homeDragController.getWidgetDragScalePx(drawable, null, dragObject)) /
                    drawable.intrinsicWidth
            }
            dragController.startDrag(
                drawable, draggableView, dragLayerX, dragLayerY, source, dragObject, dragRect,
                scale * iconScale, scale, dragOptions,
            )
        }
    }

    // ---- DragController.DragListener ----

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        // TextLauncher: the occupancy is rebuilt from the grid's views first, as a safeguard;
        // Launcher3 relies on addInScreen and removeView alone to keep it.
        layout.rebuildOccupied(null)
        mDragInfo?.let { layout.markCellsAsUnoccupiedForView(it.cell) }
    }

    override fun onDragEnd() {
        setAutoScroll(0)
        // Workspace.onDragEnd: setCurrentDragOverlappingLayout(null)
        layout.setIsDragOverlapping(false)
        mDragInfo = null
    }

    // ---- DropTarget ----

    override fun isDropEnabled(): Boolean = true

    override fun prepareAccessibilityDrop() = Unit

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        activity.dragLayer.getDescendantRectRelativeToSelf(grid, outRect)
    }

    override fun acceptDrop(d: DragObject): Boolean {
        // If it's an external drop (e.g. from All Apps), check if it should be accepted
        val dropTargetLayout = mDropToLayout
        if (d.dragSource !== this) {
            // Don't accept the drop if we're not over a valid drop target at time of drop
            if (dropTargetLayout == null) {
                return false
            }

            mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter)

            val spanX: Int
            val spanY: Int
            val dragInfo = mDragInfo
            if (dragInfo != null) {
                spanX = dragInfo.spanX
                spanY = dragInfo.spanY
            } else {
                spanX = d.dragInfo.spanX
                spanY = d.dragInfo.spanY
            }

            val minSpanX = spanX
            val minSpanY = spanY

            mTargetCell = findNearestArea(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                dropTargetLayout, mTargetCell,
            )
            val distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell,
            )
            if (mCreateUserFolderOnDrop && willCreateUserFolder(d.dragInfo, dropTargetLayout, mTargetCell, distance, true)) {
                return true
            }

            if (mAddToExistingFolderOnDrop && willAddToExistingUserFolder(d.dragInfo, dropTargetLayout, mTargetCell, distance)) {
                return true
            }

            val resultSpan = IntArray(2)
            mTargetCell = dropTargetLayout.performReorder(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                spanX, spanY, null, mTargetCell, resultSpan, CellLayout.MODE_ACCEPT_DROP,
            )
            val foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0

            // Don't accept the drop if there's no room for the item
            if (!foundCell) {
                onNoCellFound()
                return false
            }
        }
        return true
    }

    /** Workspace.onNoCellFound: there is no hotseat, and nothing is logged. */
    private fun onNoCellFound() {
        Toast.makeText(context, com.android.launcher3.R.string.out_of_space, Toast.LENGTH_SHORT).show()
    }

    private fun willCreateUserFolder(info: ItemInfo, target: CellLayout, targetCell: IntArray, distance: Float, considerTimeout: Boolean): Boolean {
        if (distance > target.getFolderCreationRadius(targetCell)) return false
        val dropOverView = target.getChildAt(targetCell[0], targetCell[1])
        return willCreateUserFolder(info, dropOverView, considerTimeout)
    }

    private fun willCreateUserFolder(info: ItemInfo, dropOverView: View?, considerTimeout: Boolean): Boolean {
        if (dropOverView != null) {
            val lp = dropOverView.layoutParams as CellLayoutLayoutParams
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY)) {
                return false
            }
        }

        var hasntMoved = false
        mDragInfo?.let { hasntMoved = dropOverView === it.cell }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false
        }

        val aboveShortcut = Folder.willAccept(dropOverView.tag)
        val willBecomeShortcut = FolderInfo.willAcceptItemType(info.itemType)

        return aboveShortcut && willBecomeShortcut
    }

    private fun willAddToExistingUserFolder(dragInfo: ItemInfo, target: CellLayout, targetCell: IntArray, distance: Float): Boolean {
        if (distance > target.getFolderCreationRadius(targetCell)) return false
        val dropOverView = target.getChildAt(targetCell[0], targetCell[1])
        return willAddToExistingUserFolder(dragInfo, dropOverView)
    }

    private fun willAddToExistingUserFolder(dragInfo: ItemInfo, dropOverView: View?): Boolean {
        if (dropOverView != null) {
            val lp = dropOverView.layoutParams as CellLayoutLayoutParams
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY)) {
                return false
            }
        }

        if (dropOverView is FolderIconView) {
            if (dropOverView.acceptDrop(dragInfo)) {
                return true
            }
        }
        // TextLauncher: no file system folders.
        return false
    }

    /**
     * Workspace.createUserFolderIfNecessary. TextLauncher: takes the dropped ItemInfo instead of a
     * view inflated for it.
     */
    private fun createUserFolderIfNecessary(
        sourceInfo: ItemInfo, target: CellLayout, targetCell: IntArray, distance: Float, external: Boolean, d: DragObject?,
    ): Boolean {
        if (distance > target.getFolderCreationRadius(targetCell)) return false
        val v = target.getChildAt(targetCell[0], targetCell[1])

        var hasntMoved = false
        mDragInfo?.let { hasntMoved = it.cellX == targetCell[0] && it.cellY == targetCell[1] }

        if (v == null || hasntMoved || !mCreateUserFolderOnDrop) return false
        mCreateUserFolderOnDrop = false

        val aboveShortcut = Folder.willAccept(v.tag)
        val willBecomeShortcut = FolderInfo.willAcceptItemType(sourceInfo.itemType)

        if (aboveShortcut && willBecomeShortcut) {
            val destInfo = v.tag as? ItemInfo ?: return false
            // if the drag started here, we need to remove it from the workspace
            if (!external) {
                mDragInfo?.let { host.removeWorkspaceItem(it.cell) }
            }

            val folderLocation = Rect()
            val scale = mLauncher.dragLayer.getDescendantRectRelativeToSelf(v, folderLocation)
            host.removeWorkspaceItem(v)
            val fi = host.addFolder(targetCell[0], targetCell[1])
            destInfo.cellX = -1
            destInfo.cellY = -1
            sourceInfo.cellX = -1
            sourceInfo.cellY = -1

            // If the dragView is null, we can't animate
            if (d != null) {
                // In order to keep everything continuous, we hand off the currently rendered
                // folder background to the newly created icon. This preserves animation state.
                fi.setFolderBackground(mFolderCreateBg)
                mFolderCreateBg = PreviewBackground(context)
                fi.performCreateAnimation(destInfo, v, sourceInfo, d, folderLocation, scale)
            } else {
                fi.prepareCreateAnimation(v)
                fi.folder.addFolderContent(destInfo)
                fi.folder.addFolderContent(sourceInfo)
            }
            return true
        }
        return false
    }

    private fun addToExistingFolderIfNecessary(target: CellLayout, targetCell: IntArray, distance: Float, d: DragObject, external: Boolean): Boolean {
        if (distance > target.getFolderCreationRadius(targetCell)) return false

        val dropOverView = target.getChildAt(targetCell[0], targetCell[1])
        if (!mAddToExistingFolderOnDrop) return false
        mAddToExistingFolderOnDrop = false

        return addToExistingFolder(dropOverView, d, external)
    }

    private fun addToExistingFolder(dropOverView: View?, d: DragObject, external: Boolean): Boolean {
        if (dropOverView is FolderIconView) {
            if (dropOverView.acceptDrop(d.dragInfo)) {
                dropOverView.onDrop(d, false /* itemReturnedOnFailedDrop */)
                // if the drag started here, we need to remove it from the workspace
                if (!external) {
                    mDragInfo?.let { host.removeWorkspaceItem(it.cell) }
                }
                return true
            }
        }
        return false
    }

    override fun onDrop(d: DragObject, options: DragOptions) {
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter)
        val dropTargetLayout = mDropToLayout

        val dragInfo = mDragInfo
        if (d.dragSource !== this || dragInfo == null) {
            val touchXY = intArrayOf(mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt())
            onDropExternal(touchXY, dropTargetLayout, d)
        } else {
            val cell = dragInfo.cell
            var onCompleteRunnable: Runnable? = null
            if (dropTargetLayout != null && !d.cancelled) {
                // Move internally
                val spanX = dragInfo.spanX
                val spanY = dragInfo.spanY
                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.

                mTargetCell = findNearestArea(
                    mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), spanX, spanY,
                    dropTargetLayout, mTargetCell,
                )
                val distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell,
                )

                // If the item being dropped is a shortcut and the nearest drop
                // cell also contains a shortcut, then create a folder with the two shortcuts.
                if (createUserFolderIfNecessary(d.dragInfo, dropTargetLayout, mTargetCell, distance, false, d) ||
                    addToExistingFolderIfNecessary(dropTargetLayout, mTargetCell, distance, d, false)
                ) {
                    return
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                val item = d.dragInfo
                val minSpanX = item.spanX
                val minSpanY = item.spanY

                // TextLauncher: there is no state transition that could still be running, so the
                // item is never returned to its cell to prevent shuffling.
                val resultSpan = IntArray(2)
                mTargetCell = dropTargetLayout.performReorder(
                    mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                    spanX, spanY, cell, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP,
                )

                val foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0

                // if the widget resizes on drop
                if (foundCell && cell is AppWidgetHostView &&
                    (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY)
                ) {
                    item.spanX = resultSpan[0]
                    item.spanY = resultSpan[1]
                    host.updateWidgetSize(cell, resultSpan[0], resultSpan[1])
                }

                if (foundCell) {
                    // Workspace.onDrop (hasMovedLayouts): a widget is in the DragView while it is
                    // dragged, and goes back on the grid at its new cell (addInScreen).
                    if (cell is LauncherAppWidgetHostView && cell.parent !== grid) {
                        d.dragView.detachContentView(/* reattachToPreviousParent= */ false)
                        grid.addView(cell, cell.layoutParams)
                    }
                    // update the item's position after drop
                    val lp = cell.layoutParams as CellLayoutLayoutParams
                    lp.tmpCellX = mTargetCell[0]
                    lp.cellX = mTargetCell[0]
                    lp.tmpCellY = mTargetCell[1]
                    lp.cellY = mTargetCell[1]
                    lp.cellHSpan = item.spanX
                    lp.cellVSpan = item.spanY
                    lp.isLockedToGrid = true

                    if (cell is LauncherAppWidgetHostView) {
                        // We post this call so that the widget has a chance to be placed
                        // in its final location
                        onCompleteRunnable = getWidgetResizeFrameRunnable(cell)
                    }
                    mLauncher.modelWriter.modifyItemInDatabase(
                        item, LauncherSettings.Favorites.CONTAINER_DESKTOP, 0, lp.cellX, lp.cellY, item.spanX, item.spanY,
                    )
                } else {
                    if (cell is LauncherAppWidgetHostView) {
                        d.dragView.detachContentView(/* reattachToPreviousParent= */ true)
                    }
                    // If we can't find a drop location, we return the item to its original position
                    val lp = cell.layoutParams as CellLayoutLayoutParams
                    mTargetCell[0] = lp.cellX
                    mTargetCell[1] = lp.cellY
                    layout.markCellsAsOccupiedForView(cell)
                }
            } else if (cell is LauncherAppWidgetHostView) {
                // When drag is cancelled, reattach content view back to its original parent.
                d.dragView.detachContentView(/* reattachToPreviousParent= */ true)
                // TextLauncher: the one page is always visible.
                onCompleteRunnable = getWidgetResizeFrameRunnable(cell)
            }

            val parent = layout
            if (d.dragView.hasDrawn()) {
                mLauncher.dragLayer.animateViewIntoPosition(d.dragView, cell, -1, grid)
            } else {
                d.deferDragViewCleanupPostAnimation = false
                cell.visibility = View.VISIBLE
            }
            parent.onDropChild(cell)

            // Workspace.onDrop: goToState(NORMAL, SPRING_LOADED_EXIT_DELAY, onCompleteRunnable).
            onCompleteRunnable?.let { host.runWhenNormal(it) }
        }
    }

    /**
     * Workspace.getWidgetResizeFrameRunnable: the resize frame of a widget that can be resized.
     * TextLauncher: no page transitions (isPageInTransition).
     */
    private fun getWidgetResizeFrameRunnable(hostView: LauncherAppWidgetHostView): Runnable? {
        val pInfo = hostView.appWidgetInfo
        if (pInfo != null && pInfo.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) {
            return Runnable { AppWidgetResizeFrame.showForWidget(hostView, layout) }
        }
        return null
    }

    /**
     * Workspace.onDropExternal, for drops that did not start on the grid: widgets and shortcuts
     * from the widget picker (PendingAddItemInfo), and apps from search results, items from
     * folders and app shortcuts from the long-press menu ("other drag/drop cases, like dragging
     * from All Apps").
     */
    private fun onDropExternal(touchXY: IntArray?, cellLayout: CellLayout?, d: DragObject) {
        cellLayout ?: return
        // TextLauncher: one page and no hotseat.
        val container = LauncherSettings.Favorites.CONTAINER_DESKTOP

        val pendingShortcut = d.dragInfo as? PendingAddShortcutInfo
        if (pendingShortcut != null) {
            val si = pendingShortcut.getActivityInfo(context).createWorkspaceItemInfo()
            if (si != null) {
                d.dragInfo = si
                si.container = container
            }
        }

        val pendingInfo = d.dragInfo as? PendingAddItemInfo
        if (pendingInfo != null) {
            onDropPendingItem(touchXY, cellLayout, d, pendingInfo, container)
            return
        }

        val info = d.dragInfo as? WorkspaceItemInfo ?: return
        var spanX = info.spanX
        var spanY = info.spanY
        mDragInfo?.let {
            spanX = it.spanX
            spanY = it.spanY
        }

        // First we find the cell nearest to point at which the item is
        // dropped, without any consideration to whether there is an item there.
        if (touchXY != null) {
            mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY, cellLayout, mTargetCell)
            val distance = cellLayout.getDistanceFromWorkspaceCellVisualCenter(
                mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell,
            )
            if (createUserFolderIfNecessary(info, cellLayout, mTargetCell, distance, true, d)) {
                return
            }
            if (addToExistingFolderIfNecessary(cellLayout, mTargetCell, distance, d, true)) {
                return
            }
        }

        // when dragging and dropping, just find the closest free spot
        mTargetCell = cellLayout.performReorder(
            mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), 1, 1, 1, 1,
            null, mTargetCell, null, CellLayout.MODE_ON_DROP_EXTERNAL,
        )
        // Add the item to DB before adding to screen ensures that the container and other
        // values of the info is properly updated.
        val view = host.addItem(info, mTargetCell[0], mTargetCell[1])
        cellLayout.onDropChild(view)
        grid.measureChild(view)

        if (d.dragView != null) {
            mLauncher.dragLayer.animateViewIntoPosition(d.dragView, view, -1, grid)
        }
    }

    /** Workspace.onDropExternal for a widget or shortcut from the widget picker. */
    private fun onDropPendingItem(
        touchXY: IntArray?, cellLayout: CellLayout, d: DragObject, pendingInfo: PendingAddItemInfo, container: Int,
    ) {
        var spanX = pendingInfo.spanX
        var spanY = pendingInfo.spanY
        mDragInfo?.let {
            spanX = it.spanX
            spanY = it.spanY
        }

        var findNearestVacantCell = true
        if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT && touchXY != null) {
            mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY, cellLayout, mTargetCell)
            val distance = cellLayout.getDistanceFromWorkspaceCellVisualCenter(
                mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell,
            )
            if (willCreateUserFolder(d.dragInfo, cellLayout, mTargetCell, distance, true) ||
                willAddToExistingUserFolder(d.dragInfo, cellLayout, mTargetCell, distance)
            ) {
                findNearestVacantCell = false
            }
        }

        val item = d.dragInfo
        if (findNearestVacantCell) {
            var minSpanX = item.spanX
            var minSpanY = item.spanY
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX
                minSpanY = item.minSpanY
            }
            val resultSpan = IntArray(2)
            mTargetCell = cellLayout.performReorder(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                pendingInfo.spanX, pendingInfo.spanY, null, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP_EXTERNAL,
            )
            // TextLauncher: no widget is bound while it is dragged (WidgetHostViewLoader), so
            // there is no bound widget whose size to update; it gets its size once it is added.
            item.spanX = resultSpan[0]
            item.spanY = resultSpan[1]
        }

        val onAnimationCompleteRunnable = Runnable {
            // When dragging and dropping from customization tray, we deal with creating
            // widgets/shortcuts/folders in a slightly different way
            mLauncher.addPendingItem(pendingInfo, container, 0, mTargetCell, item.spanX, item.spanY)
        }
        // TextLauncher: the drag view always disappears (Launcher3 keeps it for a widget's
        // configuration activity, ANIMATE_INTO_POSITION_AND_REMAIN, until the result comes).
        animateWidgetDrop(pendingInfo, cellLayout, d.dragView, onAnimationCompleteRunnable)
    }

    /**
     * Workspace.getFinalPositionForDropAnimation. TextLauncher: no bound widget (finalView), no
     * widget scale and no state transition to account for, and the grid has no unused space.
     */
    private fun getFinalPositionForDropAnimation(
        loc: IntArray, scaleXY: FloatArray, dragView: DragView, layout: CellLayout, info: ItemInfo,
        targetCell: IntArray, scale: Boolean,
    ) {
        // Now we animate the dragView, (ie. the widget or shortcut preview) into its final
        // location and size on the home screen.
        val spanX = info.spanX
        val spanY = info.spanY

        val r = mLauncher.workspace.estimateItemPosition(layout, targetCell[0], targetCell[1], spanX, spanY)

        val tempFXY = floatArrayOf(r.left.toFloat(), r.top.toFloat())
        // TextLauncher: the CellLayout's coordinates are the grid's.
        val cellLayoutScale = mLauncher.dragLayer.getDescendantCoordRelativeToSelf(grid, tempFXY)
        loc[0] = Math.round(tempFXY[0])
        loc[1] = Math.round(tempFXY[1])

        if (scale) {
            val dragViewScaleX = (1.0f * r.width()) / dragView.measuredWidth
            val dragViewScaleY = (1.0f * r.height()) / dragView.measuredHeight

            // The animation will scale the dragView about its center, so we need to center about
            // the final location.
            loc[0] -= ((dragView.measuredWidth - cellLayoutScale * r.width()) / 2).toInt()
            loc[1] -= ((dragView.measuredHeight - cellLayoutScale * r.height()) / 2).toInt()
            scaleXY[0] = dragViewScaleX * cellLayoutScale
            scaleXY[1] = dragViewScaleY * cellLayoutScale
        } else {
            // Since we are not cross-fading the dragView, align the drag view to the
            // final cell position.
            val dragScale = dragView.initialScale * cellLayoutScale
            loc[0] += ((dragScale - 1) * dragView.width / 2).toInt()
            loc[1] += ((dragScale - 1) * dragView.height / 2).toInt()
            scaleXY[0] = dragScale
            scaleXY[1] = dragScale

            // If a dragRegion was provided, offset the final position accordingly.
            val dragRegion = dragView.dragRegion
            if (dragRegion != null) {
                loc[0] += (cellLayoutScale * dragRegion.left).toInt()
                loc[1] += (cellLayoutScale * dragRegion.top).toInt()
            }
        }
    }

    /**
     * Workspace.animateWidgetDrop for an item from the widget picker (external). TextLauncher: no
     * bound widget to cross-fade to, and the drag view disappears at the end.
     */
    private fun animateWidgetDrop(info: ItemInfo, cellLayout: CellLayout, dragView: DragView, onCompleteRunnable: Runnable) {
        val finalPos = IntArray(2)
        val scaleXY = FloatArray(2)
        val scalePreview = info !is PendingAddShortcutInfo
        getFinalPositionForDropAnimation(finalPos, scaleXY, dragView, cellLayout, info, mTargetCell, scalePreview)

        val duration = context.resources.getInteger(com.android.launcher3.R.integer.config_dropAnimMaxDuration) - 200

        val isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET ||
            info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET
        if (isWidget) {
            val minScale = Math.min(scaleXY[0], scaleXY[1])
            scaleXY[0] = minScale
            scaleXY[1] = minScale
        }

        mLauncher.dragLayer.animateViewIntoPosition(
            dragView, finalPos[0], finalPos[1], 1f, scaleXY[0], scaleXY[1], onCompleteRunnable,
            DragLayer.ANIMATION_END_DISAPPEAR, duration, grid,
        )
    }

    override fun onDragEnter(d: DragObject) {
        mCreateUserFolderOnDrop = false
        mAddToExistingFolderOnDrop = false

        mDropToLayout = null
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter)
        setDropLayoutForDragObject(d)
    }

    override fun onDragExit(d: DragObject) {
        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        mDropToLayout = mDragTargetLayout
        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true
        }

        // Reset the previous drag target
        setCurrentDropLayout(null)
        setAutoScroll(0)
    }

    private fun setCurrentDropLayout(layout: CellLayout?) {
        mDragTargetLayout?.let {
            it.revertTempState()
            it.onDragExit()
        }
        mDragTargetLayout = layout
        mDragTargetLayout?.onDragEnter()
        // Workspace.setCurrentDragOverlappingLayout
        this.layout.setIsDragOverlapping(layout != null)
        cleanupReorder(true)
        cleanupFolderCreation()
        setCurrentDropOverCell(-1, -1)
    }

    private fun setCurrentDropOverCell(x: Int, y: Int) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x
            mDragOverY = y
            setDragMode(DRAG_MODE_NONE)
        }
    }

    private fun setDragMode(dragMode: Int) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                cleanupAddToFolder()
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false)
                cleanupFolderCreation()
            } else if (dragMode == DRAG_MODE_ADD_TO_FOLDER) {
                cleanupReorder(true)
                cleanupFolderCreation()
            } else if (dragMode == DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder()
                cleanupReorder(true)
            } else if (dragMode == DRAG_MODE_REORDER) {
                cleanupAddToFolder()
                cleanupFolderCreation()
            }
            mDragMode = dragMode
        }
    }

    private fun cleanupFolderCreation() {
        mFolderCreateBg.animateToRest()
    }

    private fun cleanupAddToFolder() {
        mDragOverFolderIcon?.let {
            it.onDragExit()
            mDragOverFolderIcon = null
        }
    }

    private fun cleanupReorder(cancelAlarm: Boolean) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm()
        }
        mLastReorderX = -1
        mLastReorderY = -1
    }

    override fun onDragOver(d: DragObject) {
        val item = d.dragInfo ?: return

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw RuntimeException("Improper spans found")
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter)

        val child = mDragInfo?.cell
        setDropLayoutForDragObject(d)

        // Handle the drag over
        val target = mDragTargetLayout
        if (target != null) {
            // TextLauncher: the point is already in the grid's coordinates.
            val minSpanX = item.spanX
            val minSpanY = item.spanY

            mTargetCell = findNearestArea(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), item.spanX, item.spanY,
                target, mTargetCell,
            )
            val reorderX = mTargetCell[0]
            val reorderY = mTargetCell[1]

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1])

            val targetCellDistance = target.getDistanceFromWorkspaceCellVisualCenter(
                mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell,
            )

            manageFolderFeedback(targetCellDistance, d)

            val nearestDropOccupied = target.isNearestDropLocationOccupied(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), item.spanX,
                item.spanY, child, mTargetCell,
            )

            manageReorderOnDragOver(d, targetCellDistance, nearestDropOccupied, minSpanX, minSpanY, reorderX, reorderY)

            if (mDragMode == DRAG_MODE_CREATE_FOLDER || mDragMode == DRAG_MODE_ADD_TO_FOLDER || !nearestDropOccupied) {
                mDragTargetLayout?.revertTempState()
            }
        }
        updateAutoScroll(d)
    }

    private fun manageReorderOnDragOver(
        d: DragObject, targetCellDistance: Float, nearestDropOccupied: Boolean, minSpanX: Int, minSpanY: Int,
        reorderX: Int, reorderY: Int,
    ) {
        val target = mDragTargetLayout ?: return
        val item = d.dragInfo
        val child = mDragInfo?.cell
        if (!nearestDropOccupied) {
            val span = IntArray(2)
            target.performReorder(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                item.spanX, item.spanY, child, mTargetCell, span, CellLayout.MODE_SHOW_REORDER_HINT,
            )
            target.visualizeDropLocation(mTargetCell[0], mTargetCell[1], span[0], span[1], d)
        } else if ((mDragMode == DRAG_MODE_NONE || mDragMode == DRAG_MODE_REORDER) &&
            (mLastReorderX != reorderX || mLastReorderY != reorderY) &&
            targetCellDistance < target.getReorderRadius(mTargetCell, item.spanX, item.spanY)
        ) {
            mReorderAlarm.cancelAlarm()
            mLastReorderX = reorderX
            mLastReorderY = reorderY
            target.performReorder(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                item.spanX, item.spanY, child, mTargetCell, IntArray(2), CellLayout.MODE_SHOW_REORDER_HINT,
            )
            // Otherwise, if we aren't adding to or creating a folder and there's no pending
            // reorder, then we schedule a reorder
            val listener = ReorderAlarmListener(minSpanX, minSpanY, item.spanX, item.spanY, d, child)
            mReorderAlarm.setOnAlarmListener(listener)
            mReorderAlarm.setAlarm(Workspace.REORDER_TIMEOUT.toLong())
        }
    }

    /**
     * Workspace.setDropLayoutForDragObject. TextLauncher: there is one layout; the drag is over it
     * while it is over the visible part of the grid.
     */
    private fun setDropLayoutForDragObject(d: DragObject): Boolean {
        val visibleTop = scrollView.scrollY
        val visibleBottom = visibleTop + scrollView.height
        val inside = d.x >= 0 && d.x <= grid.width && d.y >= visibleTop && d.y <= visibleBottom
        val layout = if (inside) this.layout else null

        // Update the current drop layout if the target changed
        if (layout !== mDragTargetLayout) {
            setCurrentDropLayout(layout)
            return true
        }
        return false
    }

    private fun manageFolderFeedback(distance: Float, dragObject: DragObject) {
        val target = mDragTargetLayout ?: return
        if (distance > target.getFolderCreationRadius(mTargetCell)) {
            if (mDragMode == DRAG_MODE_ADD_TO_FOLDER || mDragMode == DRAG_MODE_CREATE_FOLDER) {
                setDragMode(DRAG_MODE_NONE)
            }
            return
        }

        val dragOverView = target.getChildAt(mTargetCell[0], mTargetCell[1])
        mDragOverView = dragOverView
        val info = dragObject.dragInfo
        val userFolderPending = willCreateUserFolder(info, dragOverView, false)
        if (mDragMode == DRAG_MODE_NONE && userFolderPending && dragOverView != null) {
            if (Flags.msdlFeedback()) {
                mMSDLPlayerWrapper.playToken(MSDLToken.DRAG_INDICATOR_DISCRETE)
            }
            mFolderCreateBg = PreviewBackground(context)
            mFolderCreateBg.setup(context, activity, null, dragOverView.measuredWidth, dragOverView.paddingTop)

            // The full preview background should appear behind the icon
            mFolderCreateBg.isClipping = false

            mFolderCreateBg.animateToAccept(target, mTargetCell[0], mTargetCell[1])
            target.clearDragOutlines()
            setDragMode(DRAG_MODE_CREATE_FOLDER)
            return
        }

        val willAddToFolder = willAddToExistingUserFolder(info, dragOverView)
        if (willAddToFolder && mDragMode == DRAG_MODE_NONE) {
            if (dragOverView is FolderIconView) {
                mDragOverFolderIcon = dragOverView
                dragOverView.onDragEnter(info, target)
            }

            target.clearDragOutlines()
            setDragMode(DRAG_MODE_ADD_TO_FOLDER)
            return
        }

        if (mDragMode == DRAG_MODE_ADD_TO_FOLDER && !willAddToFolder) {
            setDragMode(DRAG_MODE_NONE)
        }
        if (mDragMode == DRAG_MODE_CREATE_FOLDER && !userFolderPending) {
            setDragMode(DRAG_MODE_NONE)
        }
    }

    private inner class ReorderAlarmListener(
        val minSpanX: Int,
        val minSpanY: Int,
        val spanX: Int,
        val spanY: Int,
        val dragObject: DragObject,
        val child: View?,
    ) : OnAlarmListener {
        override fun onAlarm(alarm: Alarm) {
            val target = mDragTargetLayout ?: return
            val resultSpan = IntArray(2)
            mTargetCell = findNearestArea(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                target, mTargetCell,
            )

            mTargetCell = target.performReorder(
                mDragViewVisualCenter[0].toInt(), mDragViewVisualCenter[1].toInt(), minSpanX, minSpanY,
                spanX, spanY, child, mTargetCell, resultSpan, CellLayout.MODE_DRAG_OVER,
            )

            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                target.revertTempState()
            } else {
                setDragMode(DRAG_MODE_REORDER)
            }

            target.visualizeDropLocation(mTargetCell[0], mTargetCell[1], resultSpan[0], resultSpan[1], dragObject)
        }
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     *
     * pixelX and pixelY should be in the coordinate system of layout
     */
    private fun findNearestArea(pixelX: Int, pixelY: Int, spanX: Int, spanY: Int, layout: CellLayout, recycle: IntArray): IntArray =
        layout.findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, recycle)

    // ---- auto scroll (TextLauncher) ----

    private fun updateAutoScroll(d: DragObject) {
        val zone = AUTO_SCROLL_ZONE_DP * density
        val step = (AUTO_SCROLL_STEP_DP * density).toInt()
        val visibleY = d.y - scrollView.scrollY
        setAutoScroll(
            when {
                visibleY < zone && scrollView.canScrollVertically(-1) -> -step
                visibleY > scrollView.height - zone && scrollView.canScrollVertically(1) -> step
                else -> 0
            },
        )
    }

    private fun setAutoScroll(step: Int) {
        val wasScrolling = autoScrollStep != 0
        autoScrollStep = step
        if (step == 0) {
            handler.removeCallbacks(autoScrollRunnable)
        } else if (!wasScrolling) {
            handler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL_MS)
        }
    }

    // ---- DragSource ----

    /** Called at the end of a drag which originated on the workspace. */
    override fun onDropCompleted(target: View?, d: DragObject, success: Boolean) {
        val dragInfo = mDragInfo
        if (success) {
            if (target !== grid && dragInfo != null) {
                host.removeWorkspaceItem(dragInfo.cell)
            }
        } else if (dragInfo != null) {
            // When drag is cancelled, reattach content view back to its original parent.
            if (dragInfo.cell is LauncherAppWidgetHostView && dragInfo.cell.parent !== grid) {
                d.dragView?.detachContentView(/* reattachToPreviousParent= */ true)
            }
            if (dragInfo.cell.parent === grid) {
                layout.onDropChild(dragInfo.cell)
            }
        }
        val cell = getViewByItemId(d.originalDragInfo.id)
        if (d.cancelled && cell != null) {
            cell.visibility = View.VISIBLE
            // TextLauncher: a result that comes after the drag ended (the uninstall drop target's,
            // on resume) marks the item's cells as occupied again; Launcher3 leaves them free
            // until the next drag or binding.
            if (dragInfo == null) layout.markCellsAsOccupiedForView(cell)
        }
        mDragInfo = null
    }

    /** LauncherBindableItemsContainer.getViewByItemId: the grid's view of the item. */
    private fun getViewByItemId(id: Int): View? =
        grid.itemViews().firstOrNull { (it.tag as? ItemInfo)?.id == id }

    private companion object {
        const val DRAG_MODE_NONE = 0
        const val DRAG_MODE_CREATE_FOLDER = 1
        const val DRAG_MODE_ADD_TO_FOLDER = 2
        const val DRAG_MODE_REORDER = 3


        const val AUTO_SCROLL_ZONE_DP = 56
        const val AUTO_SCROLL_STEP_DP = 12
        const val AUTO_SCROLL_INTERVAL_MS = 16L
    }
}
