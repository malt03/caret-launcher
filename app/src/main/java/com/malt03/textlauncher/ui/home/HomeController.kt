package com.malt03.textlauncher.ui.home

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import android.os.UserHandle
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.dagger.ActivityComponent
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupDataProvider
import com.android.launcher3.DeviceProfile
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.popup.SystemShortcut
import com.malt03.textlauncher.R
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.Workspace
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.HomeItem
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.LauncherRootLayout
import com.malt03.textlauncher.ui.NoPressDelayScrollView
import com.malt03.textlauncher.ui.drag.DropTargetBar
import com.malt03.textlauncher.ui.drag.HomeDragController

/**
 * The home layout shown while the query is empty (docs/spec.md 5): builds views from
 * [com.malt03.textlauncher.data.HomeModel], opens folders, and keeps the model in step with
 * Launcher3's drag and drop ([HomeDragController], [HomeWorkspace], [DropTargetBar]).
 */
class HomeController(
    private val app: TextLauncherApplication,
    private val root: LauncherRootLayout,
    private val host: Host,
) {

    interface Host {
        fun launch(entry: AppEntry, view: View)
        /** An app shortcut from the long-press menu (ItemClickHandler.onClickAppShortcut). */
        fun startShortcut(packageName: String, id: String, view: View, user: UserHandle)
        /** The long-press menu's "Edit search terms" (docs/spec.md 4). */
        fun editSearchTerms(entry: AppEntry)
        /** Edit mode (docs/spec.md 5.4) starts with a drag and ends after it. */
        fun onEditModeChanged(editing: Boolean)
    }

    private val context = root.context
    private val model = app.homeModel
    val grid = HomeGridView(context, GridSpec.from(context))
    private val gridSpec = grid.grid

    /** Launcher3's ActivityContext for the copied folder and drag code. */
    val activityContext = object : ActivityContext {
        private val deviceProfile = DeviceProfile(
            DeviceProfile.FolderProfile(gridSpec.iconSizePx, 0, gridSpec.folderColumns, gridSpec.folderRows, gridSpec.iconSizePx),
            DeviceProfile.WorkspaceProfile(gridSpec.iconSizePx),
            DeviceProfile.DeviceProperties(context.resources.displayMetrics.heightPixels),
        )
        private val activityComponent = ActivityComponent().apply {
            setPopupDataProvider(PopupDataProvider(context))
        }
        override fun getDeviceProfile(): DeviceProfile = deviceProfile
        override fun getActivityComponent(): ActivityComponent = activityComponent

        /**
         * ItemClickHandler for the items the long-press menu shows: app shortcuts
         * (onClickAppShortcut).
         */
        override fun getItemOnClickListener(): View.OnClickListener = View.OnClickListener { v ->
            val info = v.tag as? WorkspaceItemInfo ?: return@OnClickListener
            val id = info.deepShortcutId ?: return@OnClickListener
            val packageName = info.targetPackage ?: return@OnClickListener
            host.startShortcut(packageName, id, v, info.user)
        }
        override fun getDragLayer(): BaseDragLayer<*> = root.overlay
        override fun asContext() = context

        /** Edit mode hides the keyboard once the drag starts (docs/spec.md 5.4), not at the long press. */
        override fun hideKeyboard() = Unit

        override fun getDragController(): DragController = this@HomeController.dragController
    }

    val scrollView: NestedScrollView = NoPressDelayScrollView(context).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ -> grid.viewportHeight = bottom - top }
    }

    /** Launcher3's Workspace stand-in: holds the home grid and shrinks while a folder is open. */
    val workspace = Workspace<Any>(context).apply {
        addView(scrollView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
    private val launcher = Launcher(workspace, root.hotseat, root.scrim)

    private val dragController: HomeDragController = HomeDragController(activityContext) { root.isTouchInProgress }
    private val homeWorkspace = HomeWorkspace(activityContext, grid, scrollView, dragController, WorkspaceHost())
    private val dropTargetBar = DropTargetBar(context, { activityContext }, { dragController }, ::canRemove)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var loaded = false
    private var folderPopup: OpenFolder? = null
    private var folderDragSource: FolderDragSource? = null

    /** ActivityAllAppsContainerView as a DragSource: nothing to do when an app from search drops. */
    private val searchDragSource = DragSource { _, _, _ -> }

    init {
        val padding = (8 * context.resources.displayMetrics.density).toInt()
        grid.setPadding(padding, padding, padding, padding)

        // Launcher.setupViews
        root.overlay.setDragController(dragController)
        dragController.defaultDropTarget = homeWorkspace
        dragController.addDragListener(homeWorkspace)
        dragController.addDragListener(SpringLoadedListener())
        root.attachDropTargetBar(dropTargetBar)
        dropTargetBar.setup(dragController)
        // DragLayer.recreateControllers: the drag controller is the touch controller; open
        // floating views (the long-press menu) are asked first.
        root.overlay.setControllers(dragController)
        root.touchInterceptor = root.overlay::dispatchTouchEventFromParent

        app.ioExecutor.execute {
            val items = model.loadBlocking()
            mainHandler.post {
                model.setLoaded(items)
                loaded = true
                pruneMissingApps()
                rebuild()
            }
        }
    }

    /**
     * Re-creates every view, e.g. after the icon theme changed. Like Launcher3's binding, the views
     * are built once the icons are loaded.
     */
    fun rebuild() {
        val generation = ++rebuildGeneration
        val keys = model.items.flatMap {
            when (it) {
                is HomeItem.App -> listOf(it.appKey)
                is HomeItem.Folder -> it.appKeys
                is HomeItem.Widget -> emptyList()
            }
        }
        app.iconLoader.loadAll(keys.mapNotNull { app.appRepository.find(it) }) {
            if (generation != rebuildGeneration) return@loadAll
            grid.removeAllViews()
            for (item in model.items) addViewFor(item)
        }
    }

    private var rebuildGeneration = 0

    fun onAppsChanged() {
        if (!loaded) return
        if (pruneMissingApps()) rebuild()
    }

    /** Launcher.onResume: runs the uninstall drop target's deferred result. */
    fun onResume() {
        dropTargetBar.uninstall.onLauncherResume()
    }

    fun cancelDrag() {
        dragController.cancelDrag()
    }

    fun closeFolder() {
        folderPopup?.dismiss()
        folderPopup = null
    }

    val isFolderOpen: Boolean get() = folderPopup != null

    // ---- starting drags (ItemLongClickListener) ----

    /** A long press on an app in the search results: its menu, and a drag once it moves. */
    fun onSearchResultLongPress(entry: AppEntry, iconView: AppIconView) {
        val info = iconView.itemInfo ?: return
        homeWorkspace.beginDragShared(
            iconView, searchDragSource, info, DragPreviewProvider(iconView), DragOptions(),
            startLongPressAction(entry, iconView, info),
        )
    }

    private fun onHomeAppLongPress(entry: AppEntry, view: AppIconView) {
        val info = view.itemInfo ?: return
        homeWorkspace.startDrag(view, info, DragOptions(), startLongPressAction(entry, view, info))
    }

    private fun onHomeFolderLongPress(view: FolderIconView) {
        homeWorkspace.startDrag(view, view.folderInfo, DragOptions(), null)
    }

    /** Folder.startDrag for an app in the open folder. */
    private fun onFolderItemLongPress(folderIcon: FolderIconView, item: AppItemInfo, view: AppIconView) {
        val source = FolderDragSource(folderIcon, dragController, ::replaceFolderWithFinalItem)
        folderDragSource = source
        source.startDrag()
        homeWorkspace.beginDragShared(
            view, source, item, DragPreviewProvider(view), DragOptions(), startLongPressAction(item.entry, view, item),
        )
    }

    /**
     * BubbleTextView.startLongPressAction with PopupControllerForAppIcon.show: shows the app's
     * long-press menu (docs/spec.md 5.3) and returns the menu's pre-drag condition. Moving more
     * than deep_shortcuts_start_drag_threshold closes the menu and starts the drag.
     */
    private fun startLongPressAction(entry: AppEntry, originalView: View, item: AppItemInfo): () -> DragOptions.PreDragCondition? = {
        if (PopupContainer.getOpen(activityContext) != null) {
            // There is already an items container open, so don't open this one.
            originalView.clearFocus()
            null
        } else {
            // TextLauncher: the launcher's own options (docs/spec.md 5.3) instead of
            // ActivityContext.getSupportedShortcuts, and no app shortcuts.
            val systemShortcuts = listOf(
                EditSearchTerms(entry, item, originalView),
                SystemShortcut.AppInfo(activityContext, item, originalView),
            )
            val container = PopupContainerWithArrow.create<ActivityContext>(
                context = context,
                originalView = originalView,
                itemInfo = item,
            )
            // PopupContainerWithArrow.configureForLauncher
            dragController.addDragListener(container)
            val deepShortcutCount = activityContext.activityComponent.popupDataProvider.getShortcutCountForItem(item)
            container.populateAndShowRows(deepShortcutCount, systemShortcuts)
            container.requestFocus()
            container.createPreDragCondition()
        }
    }

    /** The long-press menu's "Edit search terms" (docs/spec.md 4), a TextLauncher option. */
    private inner class EditSearchTerms(
        private val entry: AppEntry,
        item: AppItemInfo,
        originalView: View,
    ) : SystemShortcut<ActivityContext>(
        com.android.launcher3.R.drawable.gm_edit_24, R.string.menu_search_terms, activityContext, item, originalView,
    ) {
        override fun onClick(view: View) {
            dismissTaskMenuView()
            host.editSearchTerms(entry)
        }
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

    /**
     * Spring-loaded mode while dragging (docs/spec.md 5.4 edit mode): the keyboard goes down, the
     * grid makes room for the drop targets and for drops below its last item.
     */
    private inner class SpringLoadedListener : DragController.DragListener {
        private var editing = false

        override fun onDragStart(dragObject: DragObject, options: DragOptions) {
            editing = true
            closeFolder()
            root.animateWallpaperScrim(true)
            host.onEditModeChanged(true)
            grid.headroomRows = dragObject.dragInfo.spanY + 1
            scrollView.animate().translationY(root.dropTargetBarHeight.toFloat()).setDuration(EDIT_ANIMATION_MS)
                .withEndAction { if (dragController.isDragging) dragController.forceTouchMove() }.start()
        }

        override fun onDragEnd() {
            if (!editing) return
            editing = false
            root.animateWallpaperScrim(false)
            grid.headroomRows = 0
            scrollView.animate().translationY(0f).setDuration(EDIT_ANIMATION_MS).start()
            host.onEditModeChanged(false)
        }
    }

    /** DeleteDropTarget.canRemove: items on the home screen or in a folder. */
    private fun canRemove(d: DragObject): Boolean = d.dragSource !== searchDragSource

    // ---- views ----

    private fun addViewFor(item: HomeItem, itemInfo: AppItemInfo? = null): View? {
        val iconSize = gridSpec.iconSizePx
        val view: View = when (item) {
            is HomeItem.App -> {
                val entry = app.appRepository.find(item.appKey) ?: return null
                AppIconView(context, iconSize).apply {
                    val info = itemInfo ?: AppItemInfo(entry, app.iconLoader.cached(entry))
                    this.itemInfo = info
                    app.iconLoader.load(entry) { bitmap ->
                        info.bitmap = bitmap
                        iconDrawable = app.iconLoader.newDrawable(bitmap)
                    }
                    setOnClickListener { host.launch(entry, this) }
                    setOnLongClickListener {
                        onHomeAppLongPress(entry, this)
                        true
                    }
                }
            }
            is HomeItem.Folder -> createFolderView(item)
            is HomeItem.Widget -> return null // TODO(Tex-t2ea): widgets
        }
        grid.addItemView(view, item)
        return view
    }

    private fun createFolderView(folder: HomeItem.Folder): FolderIconView =
        FolderIconView(context, gridSpec.iconSizePx, activityContext, folderCallbacks).apply {
            val items = folder.appKeys.mapNotNull { app.appRepository.find(it) }
                .map { AppItemInfo(it, app.iconLoader.cached(it)) }
            setContents(items)
            for (item in items) {
                app.iconLoader.load(item.entry) { bitmap ->
                    if (item.bitmap === bitmap) return@load
                    item.bitmap = bitmap
                    updatePreviewItems { it === item }
                }
            }
            setOnClickListener { openFolder(this) }
            setOnLongClickListener {
                onHomeFolderLongPress(this)
                true
            }
        }

    private val folderCallbacks = object : FolderIconView.Callbacks {
        override fun onContentChanged(view: FolderIconView) {
            val folder = view.tag as? HomeItem.Folder ?: return
            folder.appKeys.clear()
            folder.appKeys += view.contents.map { it.entry.key }
            model.save()
        }

        override fun onNotifyDrop(view: FolderIconView) {
            folderDragSource?.takeIf { it.folderIcon === view }?.notifyDrop()
        }
    }

    private fun openFolder(folderView: FolderIconView) {
        closeFolder()
        folderView.folder.setOpen(true)
        folderPopup = OpenFolder(
            root.overlay, folderView, folderView.contents, gridSpec, app.iconLoader, activityContext, launcher,
            onClick = { entry, view -> host.launch(entry, view) },
            onLongPress = { item, view -> onFolderItemLongPress(folderView, item, view) },
            onDismiss = {
                folderView.folder.setOpen(false)
                folderPopup = null
            },
        )
    }

    // ---- model changes ----

    /** LauncherDelegate.replaceFolderWithFinalItem */
    private fun replaceFolderWithFinalItem(folderView: FolderIconView) {
        // Add the last remaining child to the workspace in place of the folder
        val onCompleteRunnable = Runnable {
            val folder = folderView.tag as? HomeItem.Folder ?: return@Runnable
            val itemCount = folderView.folderInfo.contents.size
            if (itemCount <= 1) {
                var newItem: HomeItem.App? = null
                var finalItem: AppItemInfo? = null
                if (itemCount == 1) {
                    // Move the item from the folder to the workspace, in the position of the folder
                    finalItem = folderView.folderInfo.contents.removeAt(0) as AppItemInfo
                    newItem = HomeItem.App(model.newId(), finalItem.entry.key).also {
                        it.cellX = folder.cellX
                        it.cellY = folder.cellY
                    }
                }
                // Remove the folder
                grid.removeView(folderView)
                model.items.remove(folder)
                if (newItem != null) {
                    // We add the child after removing the folder to prevent both from existing at
                    // the same time in the CellLayout.
                    model.items += newItem
                    addViewFor(newItem, finalItem)
                }
                model.save()
            }
        }
        if (folderView.folderInfo.contents.isNotEmpty()) {
            folderView.performDestroyAnimation(onCompleteRunnable)
        } else {
            onCompleteRunnable.run()
        }
    }

    /** Removes apps that are no longer installed. Returns true when something changed. */
    private fun pruneMissingApps(): Boolean {
        val installed = app.appRepository.apps?.map { it.key }?.toSet() ?: return false
        var changed = false
        for (item in model.items.toList()) {
            when (item) {
                is HomeItem.App -> if (item.appKey !in installed) {
                    model.items.remove(item); changed = true
                }
                is HomeItem.Folder -> {
                    if (item.appKeys.removeAll { it !in installed }) changed = true
                    when (item.appKeys.size) {
                        0 -> model.items.remove(item)
                        1 -> {
                            model.items.remove(item)
                            model.items += HomeItem.App(model.newId(), item.appKeys.first()).also { it.cellX = item.cellX; it.cellY = item.cellY }
                        }
                    }
                }
                is HomeItem.Widget -> Unit
            }
        }
        if (changed) model.save()
        return changed
    }

    /** HomeWorkspace's model side (Launcher, ModelWriter). */
    private inner class WorkspaceHost : HomeWorkspace.Host {
        override fun addFolder(cellX: Int, cellY: Int): FolderIconView {
            val folder = HomeItem.Folder(model.newId(), mutableListOf())
            folder.cellX = cellX
            folder.cellY = cellY
            model.items += folder
            return addViewFor(folder) as FolderIconView
        }

        override fun addApp(info: AppItemInfo, cellX: Int, cellY: Int): View {
            val item = HomeItem.App(model.newId(), info.entry.key)
            item.cellX = cellX
            item.cellY = cellY
            model.items += item
            return addViewFor(item, info)!!
        }

        override fun removeWorkspaceItem(view: View) {
            if (view.parent === grid) grid.removeView(view)
            (view.tag as? HomeItem)?.let { model.items.remove(it) }
            model.save()
        }

        override fun onItemsMoved() {
            for (child in grid.itemViews()) {
                val item = child.tag as? HomeItem ?: continue
                val lp = child.layoutParams as CellLayoutLayoutParams
                item.cellX = lp.cellX
                item.cellY = lp.cellY
                item.spanX = lp.cellHSpan
                item.spanY = lp.cellVSpan
            }
            model.save()
        }

        override fun itemInfoOf(view: View): ItemInfo? = when (view) {
            is AppIconView -> view.itemInfo
            is FolderIconView -> view.folderInfo
            else -> null
        }
    }

    private companion object {
        const val EDIT_ANIMATION_MS = 150L
    }
}
