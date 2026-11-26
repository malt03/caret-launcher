package com.malt03.caretlauncher.ui.home

import android.annotation.SuppressLint
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
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.touch.WorkspaceTouchListener
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.util.WidgetSizeHandler.Companion.updateSizeRanges
import com.malt03.caretlauncher.CaretLauncherApplication
import com.malt03.caretlauncher.ui.GridSpec
import com.malt03.caretlauncher.ui.LauncherRootLayout
import com.android.launcher3.dragndrop.LauncherDragController
import com.malt03.caretlauncher.ui.statehandlers.DepthController

/**
 * The home layout shown while the query is empty (docs/spec.md 5), in the role of Launcher3's
 * Launcher: it sets up the views and the drag and drop (Launcher.setupViews), adds and removes the
 * items' views (Workspace.addInScreen, ModelWriter) and answers what the parts ask of the launcher.
 * The parts are named after their Launcher3 counterparts:
 *
 * - [HomeActivityContext]: Launcher itself (the stand-in), the ActivityContext of the copied code
 * - [modelCallbacks]: ModelCallbacks, the BgDataModel.Callbacks Launcher3's LauncherModel binds
 *   the model with (the model is loaded and changed on the model thread, as in Launcher3)
 * - [HomeItemInflater]: ItemInflater and ItemClickHandler
 * - [HomeSystemShortcuts]: Launcher.getSupportedShortcuts, the options of the long-press menu
 * - Workspace (WorkspaceStateTransitionAnimation): the SPRING_LOADED state while dragging
 * - [HomeWorkspace], [LauncherDragController], [DropTargetBar]: Workspace's drag and drop,
 *   LauncherDragController and the drop targets
 * - [HomeWidgetHost], [HomeWidgetAddFlow]: LauncherWidgetHolder and Launcher's widget and
 *   shortcut adding (the widget sizes are Launcher3's WidgetSizes and WidgetSizeHandler, with the
 *   device profile's cells)
 * - [NotificationAccessConfirmation]: the dialog of the empty space's long-press menu (Launcher3's
 *   WorkspaceTouchListener and WorkspaceLongPressOptions)
 */
class HomeController(
    internal val app: CaretLauncherApplication,
    internal val root: LauncherRootLayout,
    internal val host: Host,
) {

    interface Host {
        /** Launcher.collectStateHandlers */
        fun collectStateHandlers(out: MutableList<StateHandler<LauncherState>>)
        /**
         * Launcher.onStateSetStart. CaretLauncher: with the state the transition comes from, and
         * whether it is animated (the handlers are not set to the state at once).
         */
        fun onStateSetStart(fromState: LauncherState, toState: LauncherState, animated: Boolean)
        /** Launcher.onStateSetEnd, with the stable state before. */
        fun onStateSetEnd(state: LauncherState, lastStableState: LauncherState)
        fun launch(info: ItemInfo, view: View)
        /** An app shortcut (ItemClickHandler.onClickAppShortcut). */
        fun startShortcut(packageName: String, id: String, view: View, user: UserHandle)
        /** The long-press menu's "Edit search terms" (docs/spec.md 4). */
        fun editSearchTerms(app: WorkspaceItemInfo)
        /** The empty space's menu's "Share text" (docs/spec.md 2.7). */
        fun editShareTargets()
        /**
         * docs/spec.md 5.4: the home screen is edited, with the keyboard down (the
         * SPRING_LOADED state, or a widget's resize frame in NORMAL), or not any more.
         */
        fun onEditLayoutChanged(editing: Boolean)
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
    internal val model = app.model
    internal val gridSpec = GridSpec.from(context)

    /** The home screen's page in its vertical scroll container (docs/spec.md 5.1). */
    val scrollView = HomeScrollView(context, gridSpec).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        // CaretLauncher: Launcher3's home pages have a page indicator instead. The vertical home
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
    /**
     * Launcher3's DropTargetBar (drop_target_bar.xml), inflated in a context that gives the
     * launcher, as the activity holds the home screen only once it is made. The home screen places
     * it (LauncherRootLayout.attachDropTargetBar).
     */
    internal val dropTargetBar = LayoutInflater.from(context).cloneInContext(LauncherContextWrapper(context, launcher))
        .inflate(com.android.launcher3.R.layout.drop_target_bar, null) as DropTargetBar

    internal val widgetHost = HomeWidgetHost(context)

    internal val itemInflater = HomeItemInflater(this)
    internal val systemShortcuts = HomeSystemShortcuts(this)

    /**
     * ModelCallbacks: binds the model that LauncherModel loads and changes on the model thread to
     * the views.
     */
    private val modelCallbacks = object : BgDataModel.Callbacks {
        override fun bindCompleteModel(itemIdMap: WorkspaceData, isBindingSync: Boolean) {
            bindItems(itemIdMap.filter { it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP })
            isWorkspaceLoading = false
            // ModelCallbacks.finishBindingItems: the install queue may add its items.
            ItemInstallQueue.INSTANCE[context].resumeModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING)
            if (!isLoaded) {
                isLoaded = true
                // Launcher.finishBindingItems: Launcher.processActivityResult
                launcher.processActivityResult()
            }
        }

        override fun bindItemsAdded(items: List<ItemInfo>) {
            // ModelCallbacks.bindItemsAdded: Launcher.bindItems with the bounce animation.
            // CaretLauncher: the items on the home page; there is one page and no hotseat.
            val views = items.filter { it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP }
                .mapNotNull { item -> itemInflater.inflateItem(item)?.let { android.util.Pair(item, it) } }
            if (views.isNotEmpty()) launcher.bindInflatedItems(views, android.animation.AnimatorSet())
        }

        override fun bindItemsUpdated(updates: Set<ItemInfo>) = this@HomeController.bindItemsUpdated(updates)

        override fun bindWorkspaceComponentsRemoved(matcher: Predicate<ItemInfo?>) =
            this@HomeController.bindWorkspaceComponentsRemoved { matcher.test(it) }

        override fun bindAllApplications(
            apps: Array<AppInfo>,
            flags: Int,
            packageUserKeytoUidMap: Map<PackageUserKey, Int>,
        ) = app.appRepository.bindAllApplications(apps)
    }

    /** LauncherModel.getWriter: the home screen's writer (Launcher.getModelWriter). */
    internal val modelWriter: IModelWriter by lazy { model.getWriter(true, launcher, modelCallbacks) }

    /** Whether the home screen was loaded and bound once. */
    internal var isLoaded: Boolean = false
        private set

    /**
     * ModelCallbacks.getWorkspaceLoading: from the start of a load or rebind until its items are
     * bound. No drag starts meanwhile (Launcher.isDraggingEnabled).
     */
    internal var isWorkspaceLoading: Boolean = true
        private set

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
        // CaretLauncher: until the grid is measured (loading and the grid migration may come
        // first), the cells it has in the smallest window of the displays.
        launcher.deviceProfile.workspaceProfile.setCellSize(gridSpec.homeCellWidthPx, gridSpec.homeCellHeightPx, widgetPadding)
        scrollView.onCellSizeChanged = { width, height ->
            launcher.deviceProfile.workspaceProfile.setCellSize(width, height, widgetPadding)
        }
        // DeviceProfile's page height, spring-loaded scale and shrunk top, from the home screen's
        // layout (SpringLoadedState.getWorkspaceScaleAndTranslation).
        launcher.deviceProfile.setCellLayoutHeight { editCellLayoutHeight().toInt() }
        launcher.deviceProfile.setWorkspaceSpringLoadScale(::workspaceSpringLoadScale)
        launcher.deviceProfile.setCellLayoutSpringLoadShrunkTop {
            root.statusBarTop + root.dropTargetBarShrunkHeight - root.contentArea.top.toFloat()
        }

        // Launcher.setupViews
        root.overlay.setDragController(dragController)
        workspace.setup(dragController)
        dragController.addDragListener(workspace)
        root.attachDropTargetBar(dropTargetBar)
        dropTargetBar.setup(dragController)
        // DragLayer.recreateControllers: the activity sets them (Launcher.createTouchControllers).
        // Launcher.setupViews: the workspace's touch listener, for the long press on empty space.
        scrollView.setOnTouchListener(WorkspaceTouchListener(launcher, scrollView))

    }

    /**
     * Launcher.onCreate: LauncherModel.addCallbacksAndLoad, after the activity's views are set up
     * (Launcher.setupViews). A loaded model binds right away, so the activity calls this once it
     * holds this controller. CaretLauncher: a new grid (the activity was created again for it)
     * loads the model again, which migrates the items (Launcher.onIdpChanged).
     * LauncherModel.forceReload is marked @VisibleForTesting upstream, where ModelInitializer calls
     * it as well (refreshAndReloadLauncher).
     */
    @SuppressLint("VisibleForTests")
    fun startLoading() {
        // ItemInstallQueue: the queue adds its items while the home screen is resumed.
        ItemInstallQueue.INSTANCE[context].setIconUISurface(launcher)
        ItemInstallQueue.INSTANCE[context].pauseModelPush(ItemInstallQueue.FLAG_ACTIVITY_PAUSED)
        model.addCallbacksAndLoad(modelCallbacks)
        if (GridSizeMigration.needsMigration(context, gridSpec)) model.forceReload("grid changed")
    }

    /**
     * StatusBarTouchController's isEnabledCheck: Launcher3's NORMAL state (QuickstepLauncher
     * .createTouchControllers). CaretLauncher: the home screen scrolls vertically, so the swipe down
     * scrolls it until it is at its top (docs/spec.md 5.6); with two panels, the all apps panel's
     * list scrolls with it.
     */
    internal fun canSwipeDownToStatusBar(ev: MotionEvent): Boolean {
        if (!host.canShowOptions() || scrollView.canScrollVertically(-1)) return false
        val panel = root.allAppsPanel ?: return true
        return !root.overlay.isEventOverView(panel, ev)
    }

    fun destroy() {
        // Launcher.onDestroy: LauncherModel.removeCallbacks
        model.removeCallbacks(modelCallbacks)
        // ActivityContext.closeOnDestroy: PopupDataProvider stops listening for notification dots.
        launcher.activityComponent.popupDataProvider.close()
    }

    /**
     * Workspace.mapOverItems: the home screen's item views (icons, folders and widgets), until [op]
     * returns true. Returns that view.
     */
    internal fun mapOverItems(op: LauncherBindableItemsContainer.ItemOperator): View? = cellLayout.mapOverItems(op)

    /**
     * Launcher.onResume: the resumed callbacks (BaseActivity.EVENT_RESUMED), such as the uninstall
     * drop target's deferred result.
     */
    fun onResume() {
        isResumed = true
        launcher.onResumed()
        // Launcher.onDeferredResumed: process any items that were added while Launcher was away.
        ItemInstallQueue.INSTANCE[context].resumeModelPush(ItemInstallQueue.FLAG_ACTIVITY_PAUSED)
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
        // Launcher.onPause
        ItemInstallQueue.INSTANCE[context].pauseModelPush(ItemInstallQueue.FLAG_ACTIVITY_PAUSED)
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
    fun restoreState(savedState: android.os.Bundle?) = launcher.restoreAddFlowState(savedState)

    /** Launcher.onSaveInstanceState: the widget add flow waiting for an activity's result. */
    fun onSaveInstanceState(outState: android.os.Bundle) = launcher.saveAddFlowState(outState)

    /** Launcher.onActivityResult */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) =
        launcher.onActivityResult(requestCode, resultCode, data)

    /** Launcher.runWhenResumed (CaretLauncher): the widget picker's add waits for the home screen. */
    internal fun runWhenResumed(runnable: Runnable) {
        if (isResumed) runnable.run() else runOnResume = { runnable.run() }
    }

    fun cancelDrag() {
        dragController.cancelDrag()
    }

    val isDragging: Boolean get() = dragController.isDragging

    /**
     * Whether a drag from another window (the widget picker) is in progress (DragDriver).
     * DragController.mOptions is marked @VisibleForTesting upstream.
     */
    @get:SuppressLint("VisibleForTests")
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

    /**
     * Launcher.bindItems: the views of the home model's items, from scratch. The floating views to
     * close are TYPE_ALL without TYPE_REBIND_SAFE, as upstream passes them: a combination of types
     * that @FloatingViewType does not list, so lint takes it for a wrong constant.
     */
    @SuppressLint("WrongConstant")
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
        modelWriter.addItemToDatabase(folderInfo, container, screenId, cellX, cellY)

        // Create the view
        val newFolder = itemInflater.inflateItem(folderInfo) as FolderIconView
        workspace.addInScreen(newFolder, folderInfo)
        // Force measure the new folder icon
        val parent = workspace.getParentCellLayoutForView(newFolder)
        parent.shortcutsAndWidgets.measureChild(newFolder)
        return newFolder
    }

    /** Whether a widget's resize frame is open. */
    internal var isWidgetResizeFrameOpen = false
        private set

    /**
     * docs/spec.md 5.4: resizing a widget is edit mode, so the keyboard goes down while its frame
     * is open, and comes back when it closes. CaretLauncher: the launcher stays in NORMAL, as in
     * Launcher3. A frame that closes as its widget's drag starts leaves edit mode to the drag
     * (SPRING_LOADED).
     */
    internal fun onWidgetResizeFrameOpenChanged(open: Boolean) {
        isWidgetResizeFrameOpen = open
        if (open) {
            setEditLayout(true)
        } else if (!dragController.isDragging && launcher.stateManager.state != LauncherState.SPRING_LOADED) {
            setEditLayout(false)
        }
    }

    /**
     * Launcher.onStateSetStart. CaretLauncher: edit mode (SPRING_LOADED) lays the home screen out
     * with the keyboard down at once (docs/spec.md 5.4).
     */
    internal fun onStateSetStart(toState: LauncherState) {
        if (toState == LauncherState.SPRING_LOADED) setEditLayout(true)
    }

    /**
     * Launcher.onStateSetEnd. CaretLauncher: back in NORMAL, edit mode ends unless a widget's resize
     * frame keeps it; the frame opens as the transition ends (Workspace.onDrop's success
     * callback), so this looks after the callbacks. In SPRING_LOADED, the drag sees what is under
     * it now that the page has shrunk.
     */
    internal fun onStateSetEnd(state: LauncherState) {
        when (state) {
            LauncherState.SPRING_LOADED -> if (dragController.isDragging) dragController.forceTouchMove()
            LauncherState.NORMAL -> root.post {
                if (!isWidgetResizeFrameOpen && !dragController.isDragging &&
                    launcher.stateManager.state == LauncherState.NORMAL
                ) {
                    setEditLayout(false)
                }
            }
            else -> Unit
        }
    }

    private fun setEditLayout(editing: Boolean) {
        if (root.isEditLayout == editing) return
        root.isEditLayout = editing
        host.onEditLayoutChanged(editing)
    }

    /**
     * DeviceProfile.getCellLayoutHeight: the page's height with the keyboard down, which it takes
     * while editing (docs/spec.md 5.4); a drag works it out before the layout changes.
     */
    private fun editCellLayoutHeight(): Float = root.keyboardHiddenBarTop - root.statusBarTop - workspace.top

    /**
     * DeviceProfile.getWorkspaceSpringLoadScale, which SpringLoadedState and the widget drag scale
     * (WidgetDragScaleUtils) use: the page shrinks to fit between the drop target bar and the
     * hotseat (the search field with the keyboard down), at most so that 48dp of the next pages
     * would show on each side.
     */
    private fun workspaceSpringLoadScale(): Float {
        val cellLayoutHeight = editCellLayoutHeight()
        if (cellLayoutHeight <= 0f) return 1f
        val res = context.resources
        // getCellLayoutSpringLoadShrunkTop: the drop target bar with its margins, in root coordinates.
        val shrunkTop = root.statusBarTop + root.dropTargetBarShrunkHeight.toFloat()
        // getCellLayoutSpringLoadShrunkBottom: above the hotseat and its spring-loaded top margin.
        val shrunkBottom = root.keyboardHiddenBarTop -
            res.getDimensionPixelSize(com.android.launcher3.R.dimen.spring_loaded_hotseat_top_margin)
        var scale = ((shrunkBottom - shrunkTop) / cellLayoutHeight).coerceAtMost(1f)
        // Reduce scale if next pages would not be visible after scaling the workspace.
        val workspaceWidth = workspace.width.toFloat()
        val scaledWorkspaceWidth = workspaceWidth * scale
        val maxAvailableWidth = workspaceWidth -
            2 * res.getDimensionPixelSize(com.android.launcher3.R.dimen.dynamic_grid_spring_loaded_min_next_space_visible)
        if (scaledWorkspaceWidth > maxAvailableWidth) scale *= maxAvailableWidth / scaledWorkspaceWidth
        return scale
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
        // CaretLauncher: a drag of an affected item is cancelled first, so that an item dragged
        // out of a folder goes back before the folder's views change (Tex-a8os).
        cancelDragIfAffected(matcher)
        workspace.removeItemsByMatcher(matcher, true)
        dragController.onAppsRemoved(matcher)
        PopupContainer.dismissInvalidPopup(launcher)
    }

    /**
     * DragController.onAppsRemoved, before the views change (CaretLauncher): a drag of an item that
     * [matcher] matches, or out of a folder whose contents it matches, is cancelled first, so that
     * the dragged item goes back where it came from while it is still in the model (Tex-a8os).
     * DragController.mDragObject is @VisibleForTesting upstream, where onAppsRemoved reads it.
     */
    @SuppressLint("VisibleForTests")
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

    // ---- model changes ----

    /**
     * LauncherDelegateImpl.replaceFolderWithFinalItem. Returns whether the folder was destroyed.
     * CaretLauncher: the new icon does not take the focus (the search field keeps it, docs/spec.md
     * 1).
     */
    private fun replaceFolderWithFinalItem(folderView: FolderIconView): Boolean {
        // CaretLauncher: the folder may report again after it was already replaced, or after the
        // model dropped it and its view left the home screen (Tex-a8os).
        if (folderView.parent == null) return true
        // Add the last remaining child to the workspace in place of the folder
        val onCompleteRunnable = Runnable {
            val info = folderView.folderInfo
            val itemCount = info.contents.size
            // CaretLauncher: unless the model dropped the folder during the animation, and its view
            // left the home screen (Tex-a8os).
            if (itemCount <= 1 && folderView.parent != null) {
                var newIcon: View? = null
                var finalItem: ItemInfo? = null
                if (itemCount == 1) {
                    // Move the item from the folder to the workspace, in the position of the
                    // folder
                    finalItem = info.contents.removeAt(0)
                    newIcon = itemInflater.inflateItem(finalItem)
                    modelWriter.addOrMoveItemInDatabase(finalItem, info.container, info.screenId, info.cellX, info.cellY)
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
