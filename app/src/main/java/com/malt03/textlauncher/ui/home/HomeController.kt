package com.malt03.textlauncher.ui.home

import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.graphics.Rect
import android.os.UserHandle
import android.util.Log
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import java.util.function.Predicate
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.CellLayout
import com.android.launcher3.LauncherState
import com.android.launcher3.statemanager.StateManager.StateHandler
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.DropTargetBar
import com.android.launcher3.LauncherSettings
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.util.LauncherBindableItemsContainer
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.PendingAddShortcutInfo
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.Workspace
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.LauncherDelegate
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.touch.WorkspaceTouchListener
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.util.WidgetSizeHandler.Companion.updateSizeRanges
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.WidgetItemInfo
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.LauncherRootLayout
import com.android.launcher3.dragndrop.LauncherDragController
import com.malt03.textlauncher.ui.statehandlers.DepthController

/**
 * The home layout shown while the query is empty (docs/spec.md 5), in the role of Launcher3's
 * Launcher: it sets up the views and the drag and drop (Launcher.setupViews), adds and removes the
 * items' views (Workspace.addInScreen, ModelWriter) and answers what the parts ask of the launcher.
 * The parts are named after their Launcher3 counterparts:
 *
 * - [HomeActivityContext]: Launcher itself (the stand-in), the ActivityContext of the copied code
 * - [HomeLoader]: LoaderTask, PackageUpdatedTask and ShortcutsChangedTask
 * - [HomeItemInflater]: ItemInflater and ItemClickHandler
 * - [ItemLongClickListener]: ItemLongClickListener, PopupControllerForAppIcon and
 *   LauncherPopupItemDragHandler
 * - [SpringLoadedListener]: the SPRING_LOADED state while dragging
 * - [HomeWorkspace], [LauncherDragController], [DropTargetBar]: Workspace's drag and drop,
 *   LauncherDragController and the drop targets
 * - [HomeWidgetHost], [HomeWidgetAddFlow]: LauncherWidgetHolder and Launcher's widget and
 *   shortcut adding (the widget sizes are Launcher3's WidgetSizes and WidgetSizeHandler, with the
 *   device profile's cells)
 * - [NotificationAccessConfirmation]: the dialog of the empty space's long-press menu (Launcher3's
 *   WorkspaceTouchListener and WorkspaceLongPressOptions)
 */
class HomeController(
    internal val app: TextLauncherApplication,
    internal val root: LauncherRootLayout,
    internal val host: Host,
) {

    interface Host {
        /** Launcher.collectStateHandlers */
        fun collectStateHandlers(out: MutableList<StateHandler<LauncherState>>)
        /**
         * Launcher.onStateSetStart. TextLauncher: with the state the transition comes from, and
         * whether it is animated (the handlers are not set to the state at once).
         */
        fun onStateSetStart(fromState: LauncherState, toState: LauncherState, animated: Boolean)
        /** Launcher.onStateSetEnd, with the stable state before. */
        fun onStateSetEnd(state: LauncherState, lastStableState: LauncherState)
        fun launch(info: ItemInfo, view: View)
        /** An app shortcut (ItemClickHandler.onClickAppShortcut). */
        fun startShortcut(packageName: String, id: String, view: View, user: UserHandle)
        /** The long-press menu's "Edit search terms" (docs/spec.md 4). */
        fun editSearchTerms(app: AppItemInfo)
        /** Edit mode (docs/spec.md 5.4) starts with a drag and ends after it. */
        fun onEditModeChanged(editing: Boolean)
        /**
         * Shows the home screen, leaving the search results or the all apps list (Launcher3's
         * goToState(NORMAL, true, forSuccessCallback(onDone))); [onDone] runs once it is shown.
         */
        fun showHome(onDone: () -> Unit = {})
        /** The empty space's menu's "Apps list" (docs/spec.md 5.3), or null when it has none. */
        val openAllApps: (() -> Unit)?
        /** Whether the empty space's long press may show its menu (Launcher3's NORMAL state). */
        fun canShowOptions(): Boolean
        /** Launcher.getRotationHelper, for a drag from the widget picker (BaseItemDragListener). */
        val rotationHelper: RotationHelper
        /** Launcher.getDepthController */
        val depthController: DepthController
    }

    internal val context = root.context
    internal val model = app.homeModel
    internal val gridSpec = GridSpec.from(context)

    /** The home screen's page in its vertical scroll container (docs/spec.md 5.1). */
    val scrollView = HomeScrollView(context, gridSpec).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        // TextLauncher: Launcher3's home pages have a page indicator instead. The vertical home
        // screen shows where it is with a scrollbar in the all apps scrollbar's colors and width
        // (RecyclerViewFastScroller: the accent thumb on a textColorPrimary track at alpha 30,
        // fastscroll_track_min_width), shown whenever it can scroll.
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        val trackWidth = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.fastscroll_track_min_width)
        scrollBarSize = trackWidth
        verticalScrollbarThumbDrawable = GradientDrawable().apply {
            cornerRadius = trackWidth / 2f
            setColor(Themes.getColorAccent(context))
        }
        verticalScrollbarTrackDrawable = GradientDrawable().apply {
            cornerRadius = trackWidth / 2f
            setColor(ColorUtils.setAlphaComponent(Themes.getAttrColor(context, android.R.attr.textColorPrimary), SCROLLBAR_TRACK_ALPHA))
        }
    }

    /**
     * Launcher3's Launcher stand-in, which is the ActivityContext of the copied folder, drag and
     * long-press menu code.
     */
    internal val launcher = HomeActivityContext(this)
    val activityContext: ActivityContext get() = launcher

    /**
     * Launcher3's Workspace: the drag and drop on the home screen's page, which it holds in its
     * scroll container and shrinks while editing. Made in a context that gives the launcher, as
     * the activity holds the home screen only once it is made.
     */
    val workspace = HomeWorkspace(LauncherContextWrapper(context, launcher), this)

    /** The home screen's page (Workspace's CellLayout). */
    val cellLayout: CellLayout = CellLayout(LauncherContextWrapper(context, launcher), workspace).also {
        scrollView.cellLayout = it
        workspace.addPage(it, scrollView)
    }

    /** Launcher.mDragController */
    internal val dragController = LauncherDragController(launcher)
    private val springLoadedListener = SpringLoadedListener(this)
    /**
     * Launcher3's DropTargetBar (drop_target_bar.xml), inflated in a context that gives the
     * launcher, as the activity holds the home screen only once it is made. The home screen places
     * it (LauncherRootLayout.attachDropTargetBar).
     */
    internal val dropTargetBar = LayoutInflater.from(context).cloneInContext(LauncherContextWrapper(context, launcher))
        .inflate(com.android.launcher3.R.layout.drop_target_bar, null) as DropTargetBar

    internal val widgetHost = HomeWidgetHost(context)
    internal val widgetAddFlow = HomeWidgetAddFlow(this)

    internal val itemInflater = HomeItemInflater(this)
    internal val itemLongClickListener = ItemLongClickListener(this)
    private val loader = HomeLoader(this)

    /** Whether the home screen was loaded and bound once. */
    internal val isLoaded: Boolean get() = loader.isLoaded

    /**
     * ModelCallbacks.getWorkspaceLoading: from the start of a load or rebind until its items are
     * bound. No drag starts meanwhile (Launcher.isDraggingEnabled).
     */
    internal val isWorkspaceLoading: Boolean get() = loader.isWorkspaceLoading

    /** Folder's LauncherDelegate: what the folder asks of the workspace and the model. */
    internal val launcherDelegate = object : LauncherDelegate(launcher) {
        override fun beginDragShared(child: View, source: DragSource, options: DragOptions) {
            workspace.beginDragShared(child, source, options)
        }

        override fun replaceFolderWithFinalItem(folder: Folder): Boolean =
            replaceFolderWithFinalItem(folder.folderIcon as FolderIconView)
    }

    init {
        val padding = gridSpec.homePaddingPx
        cellLayout.setPadding(padding, padding, padding, padding)
        root.onPageHeightChanged = { scrollView.pageHeight = it }
        // DeviceProfile's cell size and widget padding (WorkspaceProfile), which the widget sizes
        // and the widget picker read.
        val widgetPadding = ShortcutAndWidgetContainer.getWidgetPadding(context, android.graphics.Point(0, 0))
        // TextLauncher: until the grid is measured (loading and the grid migration may come
        // first), the cells it has in the smallest window of the displays.
        launcher.deviceProfile.workspaceProfile.setCellSize(gridSpec.homeCellWidthPx, gridSpec.homeCellHeightPx, widgetPadding)
        scrollView.onCellSizeChanged = { width, height ->
            launcher.deviceProfile.workspaceProfile.setCellSize(width, height, widgetPadding)
        }
        // DeviceProfile.getWorkspaceSpringLoadScale, from the home screen's layout.
        launcher.deviceProfile.setWorkspaceSpringLoadScale(springLoadedListener::workspaceSpringLoadScale)

        // Launcher.setupViews
        root.overlay.setDragController(dragController)
        workspace.setup(dragController)
        dragController.addDragListener(workspace)
        dragController.addDragListener(springLoadedListener)
        root.attachDropTargetBar(dropTargetBar)
        dropTargetBar.setup(dragController)
        // Workspace.setState at the start (onEndStateTransition): the page's accessibility flags.
        workspace.updateAccessibilityFlags()
        // DragLayer.recreateControllers: the activity sets them (Launcher.createTouchControllers).
        // Launcher.setupViews: the workspace's touch listener, for the long press on empty space.
        scrollView.setOnTouchListener(WorkspaceTouchListener(launcher, scrollView))

        loader.start()
    }

    /**
     * StatusBarTouchController's isEnabledCheck: Launcher3's NORMAL state (QuickstepLauncher
     * .createTouchControllers). TextLauncher: the home screen scrolls vertically, so the swipe down
     * scrolls it until it is at its top (docs/spec.md 5.6); with two panels, the all apps panel's
     * list scrolls with it.
     */
    internal fun canSwipeDownToStatusBar(ev: MotionEvent): Boolean {
        if (!host.canShowOptions() || scrollView.canScrollVertically(-1)) return false
        val panel = root.allAppsPanel ?: return true
        return !root.overlay.isEventOverView(panel, ev)
    }

    fun destroy() {
        loader.destroy()
        // ActivityContext.closeOnDestroy: PopupDataProvider stops listening for notification dots.
        launcher.activityComponent.popupDataProvider.close()
    }

    /**
     * Workspace.mapOverItems: the home screen's item views (icons, folders and widgets), until [op]
     * returns true. Returns that view.
     */
    internal fun mapOverItems(op: LauncherBindableItemsContainer.ItemOperator): View? = cellLayout.mapOverItems(op)

    /** Re-creates every view, e.g. after the icon theme changed. */
    fun rebuild() = loader.rebuild()

    /**
     * Launcher.onResume: the resumed callbacks (BaseActivity.EVENT_RESUMED), such as the uninstall
     * drop target's deferred result.
     */
    fun onResume() {
        isResumed = true
        launcher.onResumed()
        runOnResume?.let {
            runOnResume = null
            it()
        }
    }

    /**
     * Launcher.onPause: a drag does not outlast the pause, so the uninstall drop target's deferred
     * result (onResume) never comes while another drag is in progress.
     */
    fun onPause() {
        isResumed = false
        dragController.cancelDrag()
    }

    private var isResumed = false

    /** The widget picker's add, waiting for the home screen to come to the front. */
    private var runOnResume: (() -> Unit)? = null

    /** BaseActivity.isStarted, for Launcher.ACTIVITY_TRACKER. */
    internal var isStarted = false
        private set

    /** LauncherWidgetHolder.setActivityStarted: the widgets are updated while it is started. */
    fun onStart() {
        isStarted = true
        widgetHost.startListening()
    }

    fun onStop() {
        isStarted = false
        widgetHost.stopListening()
    }

    /** Launcher.restoreState: the widget add flow that was waiting for an activity's result. */
    fun restoreState(savedState: android.os.Bundle?) = widgetAddFlow.restoreState(savedState)

    /** Launcher.onSaveInstanceState: the widget add flow waiting for an activity's result. */
    fun onSaveInstanceState(outState: android.os.Bundle) = widgetAddFlow.onSaveInstanceState(outState)

    /** Launcher.onActivityResult for the widget add flow. Returns whether it was for it. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean =
        widgetAddFlow.onActivityResult(requestCode, resultCode, data)

    fun cancelDrag() {
        dragController.cancelDrag()
    }

    val isDragging: Boolean get() = dragController.isDragging

    /** Whether a drag from another window (the widget picker) is in progress (DragDriver). */
    val isSystemDragInProgress: Boolean
        get() = dragController.isDragging && dragController.mOptions?.simulatedDndStartPoint != null

    /**
     * Closes the open folder (typing in the search field, dragging a shortcut out of the menu),
     * and a widget's resize frame, which belongs to the home screen the search results cover.
     */
    fun closeFolders() {
        AbstractFloatingView.closeOpenViews(
            activityContext,
            true,
            AbstractFloatingView.TYPE_FOLDER or AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME,
        )
    }

    /** Closes the long-press menu and other floating views (Launcher.onNewIntent and onStop). */
    fun closeFloatingViews(animate: Boolean) {
        AbstractFloatingView.closeAllOpenViews(activityContext, animate)
    }

    /** The back gesture closes the long-press menu first, like Launcher3's floating views. */
    fun closeTopFloatingView(): Boolean {
        val top = AbstractFloatingView.getTopOpenView(activityContext) ?: return false
        top.close(true)
        return true
    }

    // ---- views ----

    /** Launcher.bindItems: the views of the home model's items, from scratch. */
    internal fun bindItems(items: List<ItemInfo>) {
        // ModelCallbacks.startBinding: the floating views (but the rebind-safe ones) close and the
        // drag is cancelled, as the views they belong to are going away.
        AbstractFloatingView.closeOpenViews(
            activityContext,
            true,
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv(),
        )
        dragController.cancelDrag()
        cellLayout.removeAllViews()
        for (item in items) addInScreen(item)
    }

    /** ItemInflater.inflateItem and Workspace.addInScreenFromBind: the item's view on the page. */
    private fun addInScreen(item: ItemInfo): View? {
        val view = itemInflater.inflateItem(item) ?: return null
        workspace.addInScreenFromBind(view, item)
        return view
    }

    /** Launcher.addFolder: an empty folder at the cell, written to the model and added to the page. */
    internal fun addFolder(container: Int, screenId: Int, cellX: Int, cellY: Int): FolderIconView {
        val folderInfo = FolderInfo()

        // Update the model
        model.writer.addItemToDatabase(folderInfo, container, screenId, cellX, cellY)

        // Create the view
        val newFolder = itemInflater.inflateItem(folderInfo) as FolderIconView
        workspace.addInScreen(newFolder, folderInfo)
        // Force measure the new folder icon
        val parent = workspace.getParentCellLayoutForView(newFolder)
        parent.shortcutsAndWidgets.measureChild(newFolder)
        return newFolder
    }

    /** Workspace.persistRemoveItemsByMatcher: the items leave the model, which is bound again. */
    internal fun persistRemoveItemsByMatcher(matcher: Predicate<ItemInfo>) {
        if (loader.removeItems(matcher::test)) loader.rebuild()
    }

    /** StateManager.goToState(NORMAL) with a success callback (SpringLoadedListener). */
    internal fun runWhenNormal(callback: Runnable) = springLoadedListener.runWhenNormal(callback)

    /**
     * Launcher.addPendingItem for a widget or shortcut from the widget picker, added with its add
     * button or dropped (Workspace.onDropExternal), at [cell] with the spans it takes there.
     */
    internal fun addPendingItem(info: PendingAddItemInfo, container: Int, cell: IntArray?, spanX: Int, spanY: Int) {
        if (cell != null) {
            info.cellX = cell[0]
            info.cellY = cell[1]
        }
        info.container = container
        info.spanX = spanX
        info.spanY = spanY

        // TextLauncher: Launcher3 binds the widget itself (BIND_APPWIDGET, a privileged
        // permission), so it starts no activity while the picker is still in front. TextLauncher
        // may need the system's bind dialog or the widget's configuration activity, and a
        // shortcut needs its app's activity, which the home intent the picker starts as it closes
        // would clear (the launcher is singleTask), so the add waits until the home screen is
        // resumed.
        val add = if (info is PendingAddWidgetInfo) {
            // TextLauncher: WidgetHostViewLoader.preloadWidget's size options, as the widget is
            // not preloaded while it is dragged.
            if (info.bindOptions == null) {
                info.bindOptions = info.getDefaultSizeOptions(context)
            }
            { widgetAddFlow.addAppWidgetFromDrop(info) }
        } else { // info can only be PendingAddShortcutInfo
            { widgetAddFlow.processShortcutFromDrop(info as PendingAddShortcutInfo) }
        }
        if (isResumed) add() else runOnResume = add
    }

    /**
     * Launcher.completeAddShortcut's workspace part: the shortcut an app's activity made, in the
     * cell Launcher.addPendingItem chose ([cellX], [cellY]), bound with
     * Launcher.bindInflatedItems' bounce. TextLauncher: when that cell is no longer free (an
     * item is there, or the home screen changed while the activity was in front), the first free
     * cell, as no folder is made or added to (Workspace.createUserFolderIfNecessary); and when
     * there is no room, it says so (Workspace.onNoCellFound) and returns false.
     */
    internal fun completeAddShortcut(info: WorkspaceItemInfo, cellX: Int, cellY: Int): Boolean {
        host.showHome()
        val cellXY = intArrayOf(cellX, cellY)
        val foundCellSpan = (cellX >= 0 && cellY >= 0 && cellLayout.occupied.isRegionVacant(cellX, cellY, 1, 1)) ||
            cellLayout.findCellForSpan(cellXY, 1, 1)
        if (!foundCellSpan) {
            Toast.makeText(context, com.android.launcher3.R.string.out_of_space, Toast.LENGTH_SHORT).show()
            return false
        }
        model.writer.addItemToDatabase(info, LauncherSettings.Favorites.CONTAINER_DESKTOP, 0, cellXY[0], cellXY[1])
        launcher.inflateAndBindItemWithAnimation(info)
        return true
    }

    /**
     * Launcher.completeAddAppWidget's model and view part: the widget in the cell
     * Launcher.addPendingItem chose ([cellX], [cellY]), told its size. TextLauncher: when that
     * cell is no longer free (the home screen may have changed or been loaded again while an
     * activity was in front), the first free cell (LauncherAccessibilityDelegate
     * .findSpaceOnWorkspace); returns false when there is no room, where Launcher3 would add a
     * page.
     */
    internal fun addWidget(item: WidgetItemInfo, cellX: Int, cellY: Int): Boolean {
        host.showHome()
        val cell = intArrayOf(cellX, cellY)
        if (!cellLayout.occupied.isRegionVacant(cellX, cellY, item.spanX, item.spanY) &&
            !cellLayout.findCellForSpan(cell, item.spanX, item.spanY)
        ) {
            return false
        }
        model.writer.addItemToDatabase(item, LauncherSettings.Favorites.CONTAINER_DESKTOP, 0, cell[0], cell[1])
        val view = addInScreen(item)
        if (view is AppWidgetHostView) {
            view.updateSizeRanges(item.spanX, item.spanY)
            view.post {
                view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
                // Launcher.showWidgetResizeFrame
                if (view is LauncherAppWidgetHostView) AppWidgetResizeFrame.showForWidget(view, cellLayout)
            }
        }
        return true
    }

    /** The widget menu's "Remove" (PopupDataSource.removePopupData, DropTargetHandler.onDeleteComplete). */
    internal fun removeHomeItem(view: View) {
        val info = view.tag as? ItemInfo ?: return
        launcher.removeItem(view, info, true /* deleteFromDb */, "removed from its menu")
    }

    /** Whether a widget's resize frame is open. */
    internal var isWidgetResizeFrameOpen = false
        private set

    /**
     * docs/spec.md 5.4: resizing a widget is edit mode, so the keyboard goes down while its frame
     * is open, and comes back when it closes. A frame that closes as its widget's drag starts
     * leaves edit mode to the drag (SpringLoadedListener).
     */
    internal fun onWidgetResizeFrameOpenChanged(open: Boolean) {
        isWidgetResizeFrameOpen = open
        if (open) {
            root.isEditLayout = true
            host.onEditModeChanged(true)
        } else if (!dragController.isDragging) {
            root.isEditLayout = false
            host.onEditModeChanged(false)
        }
    }

    /**
     * ModelCallbacks.bindItemsUpdated (bindWorkspaceItemsUpdated): the views of [updates], on the
     * grid, in folders' previews and in the open folder, show their new details
     * (LauncherBindableItemsContainer.updateContainerItems).
     */
    internal fun bindItemsUpdated(updates: Set<ItemInfo>) {
        if (updates.isEmpty()) return
        launcher.content.updateContainerItems(updates, launcher)
        PopupContainer.dismissInvalidPopup(launcher)
    }

    /**
     * ModelCallbacks.bindWorkspaceComponentsRemoved (bindWorkspaceItemsRemoved): the views of the
     * items that [matcher] matches leave the grid and the folders.
     */
    internal fun bindWorkspaceComponentsRemoved(matcher: Predicate<ItemInfo>) {
        workspace.removeItemsByMatcher(matcher, true)
        dragController.onAppsRemoved(matcher)
        PopupContainer.dismissInvalidPopup(launcher)
    }

    /**
     * DragController.onAppsRemoved, before the model changes (TextLauncher): a drag of an item that
     * [matcher] matches, or out of a folder whose contents it matches, is cancelled first, so that
     * the dragged item goes back where it came from while it is still in the model (Tex-a8os).
     */
    internal fun cancelDragIfAffected(matcher: Predicate<ItemInfo>) {
        val dragObject = dragController.mDragObject ?: return
        if (!dragController.isDragging) return
        val dragInfo = dragObject.dragInfo
        val sourceFolder = dragObject.dragSource as? Folder
        if ((dragInfo is WorkspaceItemInfo && matcher.test(dragInfo)) ||
            sourceFolder?.info?.contents?.any(matcher::test) == true
        ) {
            dragController.cancelDrag()
        }
    }

    /** LauncherModel.onPackageIconsUpdated: CacheDataUpdatedTask for the home items. */
    fun onPackageIconsUpdated(packages: Set<String>) = loader.onPackageIconsUpdated(packages)

    // ---- model changes ----

    /**
     * LauncherDelegateImpl.replaceFolderWithFinalItem. Returns whether the folder was destroyed.
     * TextLauncher: the new icon does not take the focus (the search field keeps it, docs/spec.md
     * 1).
     */
    private fun replaceFolderWithFinalItem(folderView: FolderIconView): Boolean {
        // TextLauncher: the folder may report again after it was already replaced, or after the
        // model dropped it (Tex-a8os).
        if (folderView.parent == null || !model.contains(folderView.folderInfo)) return true
        // Add the last remaining child to the workspace in place of the folder
        val onCompleteRunnable = Runnable {
            val info = folderView.folderInfo
            val itemCount = info.contents.size
            // TextLauncher: unless the model dropped the folder during the animation (Tex-a8os).
            if (itemCount <= 1 && model.contains(info)) {
                var newIcon: View? = null
                var finalItem: ItemInfo? = null
                if (itemCount == 1) {
                    // Move the item from the folder to the workspace, in the position of the
                    // folder
                    finalItem = info.contents.removeAt(0)
                    newIcon = itemInflater.inflateItem(finalItem)
                    model.writer.addOrMoveItemInDatabase(finalItem, info.container, info.screenId, info.cellX, info.cellY)
                }

                // Remove the folder
                launcher.removeItem(folderView, info, true /* deleteFromDb */, "folder removed because there's only 1 item in it")

                if (newIcon != null && finalItem != null) {
                    // We add the child after removing the folder to prevent both from existing
                    // at the same time in the CellLayout.
                    workspace.addInScreenFromBind(newIcon, finalItem)
                }
            }
        }
        if (folderView.folderInfo.contents.isNotEmpty()) {
            folderView.performDestroyAnimation(onCompleteRunnable)
        } else {
            onCompleteRunnable.run()
        }
        return true
    }

    private companion object {
        /** RecyclerViewFastScroller.MAX_TRACK_ALPHA */
        const val SCROLLBAR_TRACK_ALPHA = 30
        const val TAG = "HomeController"
    }
}
