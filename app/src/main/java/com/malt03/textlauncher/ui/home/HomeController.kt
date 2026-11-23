package com.malt03.textlauncher.ui.home

import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.graphics.Rect
import android.os.UserHandle
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import java.util.function.Predicate
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherSettings
import com.android.launcher3.util.LauncherBindableItemsContainer
import com.android.launcher3.widget.PendingAddWidgetInfo
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
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.util.WidgetSizeHandler.Companion.updateSizeRanges
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.WidgetItemInfo
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.LauncherRootLayout
import com.malt03.textlauncher.ui.NoPressDelayScrollView
import com.malt03.textlauncher.ui.drag.DropTargetBar
import com.malt03.textlauncher.ui.drag.HomeDragController

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
 * - [HomeWorkspace], [HomeDragController], [DropTargetBar]: Workspace's drag and drop,
 *   LauncherDragController and the drop targets
 * - [HomeWidgetHost], [HomeWidgetAddFlow]: LauncherWidgetHolder and Launcher's widget adding (the
 *   widget sizes are Launcher3's WidgetSizes and WidgetSizeHandler, with the device profile's
 *   cells)
 * - [HomeOptionsMenu]: WorkspaceTouchListener and WorkspaceLongPressOptions, the empty space's
 *   long-press menu
 */
class HomeController(
    internal val app: TextLauncherApplication,
    internal val root: LauncherRootLayout,
    internal val host: Host,
) {

    interface Host {
        fun launch(entry: AppEntry, view: View)
        /** An app shortcut (ItemClickHandler.onClickAppShortcut). */
        fun startShortcut(packageName: String, id: String, view: View, user: UserHandle)
        /** The long-press menu's "Edit search terms" (docs/spec.md 4). */
        fun editSearchTerms(entry: AppEntry)
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
    }

    internal val context = root.context
    internal val model = app.homeModel
    val grid = HomeGridView(context, GridSpec.from(context))
    internal val gridSpec = grid.grid

    val scrollView: NestedScrollView = NoPressDelayScrollView(context).apply {
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
        addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Launcher3's Workspace stand-in: holds the home grid and shrinks while a folder is open. */
    val workspace = object : Workspace<Any>(context) {
        override fun persistRemoveItemsByMatcher(matcher: Predicate<ItemInfo>, reason: String?) {
            if (loader.removeItems(matcher::test)) loader.rebuild()
        }
    }.apply {
        addView(scrollView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /**
     * Launcher3's Launcher stand-in, which is the ActivityContext of the copied folder, drag and
     * long-press menu code.
     */
    internal val launcher = HomeActivityContext(this)
    val activityContext: ActivityContext get() = launcher

    internal val dragController: HomeDragController = HomeDragController(activityContext) { root.isTouchInProgress }
    private val workspaceHost = WorkspaceHost()
    internal val homeWorkspace = HomeWorkspace(activityContext, launcher, grid, scrollView, dragController, workspaceHost)
    private val springLoadedListener = SpringLoadedListener(this)
    private val dropTargetBar = DropTargetBar(context, { activityContext }, launcher, { dragController })

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
            val item = child.tag as? WorkspaceItemInfo ?: return
            homeWorkspace.beginDragShared(
                child, source, item, DragPreviewProvider(child), options,
                itemLongClickListener.startLongPressAction(child, item),
            )
        }

        override fun replaceFolderWithFinalItem(folder: Folder): Boolean =
            replaceFolderWithFinalItem(folder.folderIcon as FolderIconView)
    }

    init {
        val padding = gridSpec.homePaddingPx
        grid.setPadding(padding, padding, padding, padding)
        root.onPageHeightChanged = { grid.pageHeight = it }
        // DeviceProfile's cell size and widget padding (WorkspaceProfile), which the widget sizes
        // and the widget picker read.
        val widgetPadding = ShortcutAndWidgetContainer.getWidgetPadding(context, android.graphics.Point(0, 0))
        // TextLauncher: until the grid is measured (loading and the grid migration may come
        // first), the cells it has in the smallest window of the displays.
        launcher.deviceProfile.workspaceProfile.setCellSize(gridSpec.homeCellWidthPx, gridSpec.homeCellHeightPx, widgetPadding)
        grid.onCellSizeChanged = { width, height ->
            launcher.deviceProfile.workspaceProfile.setCellSize(width, height, widgetPadding)
        }

        // Launcher.setupViews
        root.overlay.setDragController(dragController)
        dragController.defaultDropTarget = homeWorkspace
        // TextLauncher: the model saves once per drag (HomeModel.deferSaves); this listener comes
        // first, before the drag source takes the item out of the model.
        dragController.addDragListener(object : DragController.DragListener {
            override fun onDragStart(dragObject: DragObject, options: DragOptions) =
                model.deferSaves(true)

            override fun onDragEnd() = model.deferSaves(false)
        })
        dragController.addDragListener(homeWorkspace)
        dragController.addDragListener(springLoadedListener)
        root.attachDropTargetBar(dropTargetBar)
        dropTargetBar.setup(dragController)
        // DragLayer.recreateControllers (QuickstepLauncher.createTouchControllers): the drag
        // controller, then the status bar's swipe down; open floating views (the long-press menu,
        // folders) are asked first. TextLauncher: the all apps swipe is the root's own interceptor.
        root.overlay.setControllers(dragController, StatusBarTouchController(launcher, ::canSwipeDownToStatusBar))
        root.touchInterceptor = root.overlay::dispatchTouchEventFromParent
        // Launcher.setupViews: the workspace's touch listener, for the long press on empty space.
        scrollView.setOnTouchListener(HomeOptionsMenu(this))

        loader.start()
    }

    /**
     * StatusBarTouchController's isEnabledCheck: Launcher3's NORMAL state (QuickstepLauncher
     * .createTouchControllers). TextLauncher: the home screen scrolls vertically, so the swipe down
     * scrolls it until it is at its top (docs/spec.md 5.6); with two panels, the all apps panel's
     * list scrolls with it.
     */
    private fun canSwipeDownToStatusBar(ev: MotionEvent): Boolean {
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
    internal fun mapOverItems(op: LauncherBindableItemsContainer.ItemOperator): View? {
        for (i in 0 until grid.childCount) {
            val view = grid.getChildAt(i)
            if (op.evaluate(view.tag as? ItemInfo, view)) return view
        }
        return null
    }

    /** Re-creates every view, e.g. after the icon theme changed. */
    fun rebuild() = loader.rebuild()

    fun onAppsChanged() = loader.onAppsChanged()

    /** Launcher.onResume: runs the uninstall drop target's deferred result. */
    fun onResume() {
        isResumed = true
        dropTargetBar.uninstall.onLauncherResume()
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

    /**
     * The empty space's menu's "Widgets" (WorkspaceLongPressOptions.openWidgetPicker): the widget
     * picker activity (WidgetPickerActivity), which adds the chosen widget through
     * Launcher.ACTIVITY_TRACKER and [addToWorkspace].
     */
    internal fun openWidgetPicker(): Boolean {
        if (context.packageManager.isSafeMode) {
            Toast.makeText(context, com.android.launcher3.R.string.safemode_widget_error, Toast.LENGTH_SHORT).show()
            return false
        } else {
            val intent = Intent(Intent.ACTION_PICK)
            intent.setPackage(context.packageName)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        }
    }

    fun cancelDrag() {
        dragController.cancelDrag()
    }

    val isDragging: Boolean get() = dragController.isDragging

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

    /** A long press on an app in the search results: its menu, and a drag once it moves. */
    fun onSearchResultLongPress(entry: AppEntry, iconView: BubbleTextView) =
        itemLongClickListener.onSearchResultLongPress(entry, iconView)

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
        grid.removeAllViews()
        for (item in items) addInScreen(item)
    }

    /** ItemInflater.inflateItem and Workspace.addInScreen: the item's view on the grid. */
    private fun addInScreen(item: ItemInfo): View? {
        val view = itemInflater.inflateItem(item) ?: return null
        grid.addItemView(view, item)
        return view
    }

    /**
     * LauncherAccessibilityDelegate.addToWorkspace for the menu's add button and the widget
     * picker's: the first free cell, bound with Launcher.bindInflatedItems' bounce, or for a
     * widget, Launcher.addPendingItem's widget add flow.
     */
    internal fun addToWorkspace(item: ItemInfo): Boolean {
        // TextLauncher: nothing is added while the home screen loads (Launcher3's menu cannot be
        // opened then, as no drag or long press starts); the search results' can (Tex-twu9).
        if (isWorkspaceLoading) return false
        if (item is PendingAddWidgetInfo) return addPendingWidget(item)
        // LauncherAccessibilityDelegate.addToWorkspace: the menu's item is copied.
        val info = (item as? WorkspaceItemInfo ?: return false).clone()
        // Dismiss widget resize frame if it is showing. The frame marks its cells as unoccupied
        // while it is showing, so findSpaceOnWorkspace may try to use those cells.
        AbstractFloatingView.closeOpenViews(activityContext, false, AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME)
        val cell = IntArray(2)
        if (!findSpaceOnWorkspace(info, cell)) {
            // TextLauncher: one page, so where Launcher3 adds a page there is no room; it says so
            // as Workspace.onNoCellFound does for a drop (Tex-8w0r).
            Toast.makeText(context, com.android.launcher3.R.string.out_of_space, Toast.LENGTH_SHORT).show()
            return false
        }
        // Launcher3 goes to the NORMAL state first, and binds the item when it gets there.
        host.showHome {
            val view = workspaceHost.addItem(info, cell[0], cell[1])
            model.save()
            animateNewItem(view)
        }
        return true
    }

    /**
     * LauncherAccessibilityDelegate.addToWorkspace for a widget (PendingAddItemInfo): with room
     * for it, its size options and Launcher.addPendingItem (addAppWidgetFromDrop).
     */
    private fun addPendingWidget(info: PendingAddWidgetInfo): Boolean {
        // Dismiss widget resize frame if it is showing. The frame marks its cells as unoccupied
        // while it is showing, so findSpaceOnWorkspace may try to use those cells.
        AbstractFloatingView.closeOpenViews(activityContext, false, AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME)
        host.showHome()
        val cell = IntArray(2)
        // TextLauncher: one page, so no room means it is not added (Launcher3 would add a page).
        if (!grid.cellLayout.findCellForSpan(cell, info.spanX, info.spanY)) return false
        if (info.bindOptions == null) {
            info.bindOptions = info.getDefaultSizeOptions(context)
        }
        // Launcher.addPendingItem
        info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
        info.cellX = cell[0]
        info.cellY = cell[1]
        // TextLauncher: Launcher3 binds the widget itself (BIND_APPWIDGET, a privileged
        // permission), so it starts no activity while the picker is still in front. TextLauncher
        // may need the system's bind dialog or the widget's configuration activity, which the home
        // intent the picker starts as it closes would clear (the launcher is singleTask), so the
        // add waits until the home screen is resumed.
        if (isResumed) widgetAddFlow.addAppWidgetFromDrop(info)
        else runOnResume = { widgetAddFlow.addAppWidgetFromDrop(info) }
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
        if (!grid.cellLayout.occupied.isRegionVacant(cellX, cellY, item.spanX, item.spanY) &&
            !grid.cellLayout.findCellForSpan(cell, item.spanX, item.spanY)
        ) {
            return false
        }
        item.id = model.newId()
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
        item.cellX = cell[0]
        item.cellY = cell[1]
        model.items += item
        val view = addInScreen(item)
        model.save()
        if (view is AppWidgetHostView) {
            view.updateSizeRanges(item.spanX, item.spanY)
            view.post {
                view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
                // Launcher.showWidgetResizeFrame
                if (view is LauncherAppWidgetHostView) AppWidgetResizeFrame.showForWidget(view, grid.cellLayout)
            }
        }
        return true
    }

    /** The widget menu's "Remove" (PopupDataSource.removePopupData, DropTargetHandler.onDeleteComplete). */
    internal fun removeHomeItem(view: View) = workspaceHost.removeWorkspaceItem(view)

    /** CellLayout.commitTempPlacement's database writes: the items' cells and spans, from their views. */
    internal fun saveItemPlacement() = workspaceHost.onItemsMoved()

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
     * Launcher.bindWorkspaceItemsChanged: the views of [info], on the grid or in a folder's preview,
     * show its new details.
     */
    internal fun bindItemChanged(info: WorkspaceItemInfo) {
        for (view in grid.itemViews()) {
            when (view) {
                is AppIconView -> if (view.tag === info) view.applyFromWorkspaceItem(info)
                is FolderIconView -> if (view.contents.any { it === info }) view.updatePreviewItems { it === info }
            }
        }
    }

    /**
     * Launcher.bindInflatedItems with createNewAppBounceAnimation: an item added from the menu
     * scales in. TextLauncher: the grid scrolls to it instead of snapping to its page.
     */
    private fun animateNewItem(view: View) {
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.post {
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), false)
            view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(NEW_SHORTCUT_BOUNCE_DURATION)
                .setInterpolator(OvershootInterpolator(BOUNCE_ANIMATION_TENSION))
                .start()
        }
    }

    /**
     * LauncherAccessibilityDelegate.findSpaceOnWorkspace: the first free cell of the grid.
     * TextLauncher: there is one page and no extra empty screen to add, so it returns false when
     * the grid is full.
     */
    private fun findSpaceOnWorkspace(info: ItemInfo, outCoordinates: IntArray): Boolean =
        grid.cellLayout.findCellForSpan(outCoordinates, info.spanX, info.spanY)

    // ---- model changes ----

    /** LauncherDelegate.replaceFolderWithFinalItem. Returns whether the folder was destroyed. */
    private fun replaceFolderWithFinalItem(folderView: FolderIconView): Boolean {
        // TextLauncher: the folder may report again after it was already replaced, or after the
        // model dropped it (Tex-a8os).
        if (folderView.parent == null || folderView.folderInfo !in model.items) return true
        // Add the last remaining child to the workspace in place of the folder
        val onCompleteRunnable = Runnable {
            val info = folderView.folderInfo
            val itemCount = info.contents.size
            // TextLauncher: unless the model dropped the folder during the animation (Tex-a8os).
            if (itemCount <= 1 && info in model.items) {
                var finalItem: ItemInfo? = null
                if (itemCount == 1) {
                    // Move the item from the folder to the workspace, in the position of the folder
                    finalItem = info.contents.removeAt(0)
                    finalItem.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                    finalItem.cellX = info.cellX
                    finalItem.cellY = info.cellY
                }
                // Remove the folder
                grid.removeView(folderView)
                model.items.remove(info)
                if (finalItem != null) {
                    // We add the child after removing the folder to prevent both from existing at
                    // the same time in the CellLayout.
                    model.items += finalItem
                    addInScreen(finalItem)
                }
                model.save()
            }
        }
        if (folderView.folderInfo.contents.isNotEmpty()) {
            folderView.performDestroyAnimation(onCompleteRunnable)
        } else {
            onCompleteRunnable.run()
        }
        return true
    }

    /** HomeWorkspace's model side (Launcher, ModelWriter). */
    private inner class WorkspaceHost : HomeWorkspace.Host {
        override fun addFolder(cellX: Int, cellY: Int): FolderIconView {
            val folder = FolderInfo()
            folder.id = model.newId()
            folder.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            folder.cellX = cellX
            folder.cellY = cellY
            model.items += folder
            return addInScreen(folder) as FolderIconView
        }

        override fun addItem(info: WorkspaceItemInfo, cellX: Int, cellY: Int): View {
            info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            info.cellX = cellX
            info.cellY = cellY
            model.items += info
            return addInScreen(info)!!
        }

        override fun removeWorkspaceItem(view: View) {
            if (view.parent === grid) grid.removeView(view)
            (view.tag as? ItemInfo)?.let { model.items.remove(it) }
            // ModelWriter.deleteWidgetInfo: the widget's id is given back.
            (view.tag as? WidgetItemInfo)?.let { widgetHost.deleteAppWidgetId(it.appWidgetId) }
            model.save()
        }

        override fun updateWidgetSize(view: AppWidgetHostView, spanX: Int, spanY: Int) =
            view.updateSizeRanges(spanX, spanY)

        override fun runWhenNormal(callback: Runnable) = springLoadedListener.runWhenNormal(callback)

        override fun onItemsMoved() {
            for (child in grid.itemViews()) {
                val item = child.tag as? ItemInfo ?: continue
                val lp = child.layoutParams as CellLayoutLayoutParams
                item.cellX = lp.cellX
                item.cellY = lp.cellY
                item.spanX = lp.cellHSpan
                item.spanY = lp.cellVSpan
            }
            model.save()
        }
    }

    private companion object {
        /** RecyclerViewFastScroller.MAX_TRACK_ALPHA */
        const val SCROLLBAR_TRACK_ALPHA = 30

        /** ItemInstallQueue.NEW_SHORTCUT_BOUNCE_DURATION */
        const val NEW_SHORTCUT_BOUNCE_DURATION = 450L

        /** Launcher.BOUNCE_ANIMATION_TENSION */
        const val BOUNCE_ANIMATION_TENSION = 1.3f
    }
}
