package com.malt03.textlauncher.ui.home

import android.content.pm.ShortcutInfo
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.Workspace
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dagger.ActivityComponent
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.LauncherDelegate
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.popup.PopupDataProvider
import com.android.launcher3.popup.PopupItemDragHandler
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.malt03.textlauncher.R
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.HomeItemInfo
import com.malt03.textlauncher.data.ShortcutItemInfo
import com.malt03.textlauncher.data.HomeShortcutKey
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
        /** An app shortcut (ItemClickHandler.onClickAppShortcut). */
        fun startShortcut(packageName: String, id: String, view: View, user: UserHandle)
        /** The long-press menu's "Edit search terms" (docs/spec.md 4). */
        fun editSearchTerms(entry: AppEntry)
        /** Edit mode (docs/spec.md 5.4) starts with a drag and ends after it. */
        fun onEditModeChanged(editing: Boolean)
        /** Shows the home screen, leaving the search results (Launcher3's NORMAL state). */
        fun showHome()
    }

    private val context = root.context
    private val model = app.homeModel
    val grid = HomeGridView(context, GridSpec.from(context))
    private val gridSpec = grid.grid

    /** Launcher3's ActivityContext for the copied folder, drag and long-press menu code. */
    val activityContext: ActivityContext = HomeActivityContext()

    private inner class HomeActivityContext : ActivityContext, DeepShortcutView.WorkspaceHost {
        private val deviceProfile = DeviceProfile(
            DeviceProfile.FolderProfile(
                gridSpec.iconSizePx, 0, gridSpec.folderColumns, gridSpec.folderRows, gridSpec.iconSizePx,
                folderCellSizePx, folderCellSizePx,
            ),
            // No labels (docs/spec.md 5.2): the icons are centered in their cells.
            DeviceProfile.WorkspaceProfile(gridSpec.iconSizePx, /* iconCenterVertically= */ true),
            DeviceProfile.DeviceProperties(context.resources.displayMetrics.heightPixels),
        )
        private val activityComponent = ActivityComponent().apply {
            setPopupDataProvider(PopupDataProvider(context))
        }
        override fun getDeviceProfile(): DeviceProfile = deviceProfile
        override fun getActivityComponent(): ActivityComponent = activityComponent

        /**
         * ItemClickHandler for the items in folders and the long-press menu's app shortcuts
         * (onClickAppShortcut).
         */
        override fun getItemOnClickListener(): View.OnClickListener = View.OnClickListener { v ->
            val info = v.tag as? WorkspaceItemInfo ?: return@OnClickListener
            onItemClick(info, v)
        }
        override fun getDragLayer(): BaseDragLayer<*> = root.overlay
        override fun asContext() = context

        /** Edit mode hides the keyboard once the drag starts (docs/spec.md 5.4), not at the long press. */
        override fun hideKeyboard() = Unit

        override fun getDragController(): DragController = this@HomeController.dragController

        /**
         * DeepShortcutView.getFinalInfo: the shortcut as it goes on the home screen, badged with
         * its app's icon (IconCache.getShortcutInfoBadge).
         */
        override fun makeWorkspaceItem(info: WorkspaceItemInfo, detail: ShortcutInfo): WorkspaceItemInfo {
            val key = app.shortcutRepository.keyOf(detail)
            val bitmap = app.iconLoader.cachedShortcut(key)
                ?: info.bitmap.withBadgeInfo(app.iconLoader.badgeFor(badgeAppOf(detail)))
            return ShortcutItemInfo(key, detail, context, bitmap)
        }

        /**
         * LauncherAccessibilityDelegate.addToWorkspace for the menu's add button: the first free
         * cell, bound with Launcher.bindInflatedItems' bounce.
         */
        override fun addToWorkspace(item: ItemInfo): Boolean {
            val info = item as? WorkspaceItemInfo ?: return false
            // TextLauncher: Launcher3 goes to the NORMAL state first.
            host.showHome()
            val cell = findSpaceOnHome()
            val view = workspaceHost.addItem(info, cell[0], cell[1])
            model.save()
            animateNewItem(view)
            return true
        }
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
    private val launcher = Launcher(root.overlay, workspace, root.hotseat, root.scrim)

    private val dragController: HomeDragController = HomeDragController(activityContext) { root.isTouchInProgress }
    private val workspaceHost = WorkspaceHost()
    private val homeWorkspace = HomeWorkspace(activityContext, launcher, grid, scrollView, dragController, workspaceHost)
    private val dropTargetBar = DropTargetBar(context, { activityContext }, launcher, { dragController }, ::canRemove)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var loaded = false
    /** The open folder's cells: the icon with Launcher3's folder cell padding around it. */
    private val folderCellSizePx get() = gridSpec.iconSizePx + (24 * context.resources.displayMetrics.density).toInt()

    /** Folder's LauncherDelegate: what the folder asks of the workspace and the model. */
    private val launcherDelegate = object : LauncherDelegate(launcher) {
        override fun beginDragShared(child: View, source: DragSource, options: DragOptions) {
            val item = child.tag as? WorkspaceItemInfo ?: return
            homeWorkspace.beginDragShared(
                child, source, item, DragPreviewProvider(child), options, startLongPressAction(child, item),
            )
        }

        override fun replaceFolderWithFinalItem(folder: Folder): Boolean =
            replaceFolderWithFinalItem(folder.folderIcon as FolderIconView)
    }

    private val shortcutsListener: () -> Unit = ::onShortcutsChanged

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

        app.shortcutRepository.addChangeListener(shortcutsListener)
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

    fun destroy() {
        app.shortcutRepository.removeChangeListener(shortcutsListener)
    }

    /**
     * Re-creates every view, e.g. after the icon theme changed. Like Launcher3's binding (LoaderTask
     * and its pinned shortcut lookup), the views are built once the shortcuts and icons are loaded.
     */
    fun rebuild() {
        val generation = ++rebuildGeneration
        val shortcutKeys = model.shortcutKeys()
        app.ioExecutor.execute {
            val found = if (shortcutKeys.isEmpty()) emptyMap() else app.shortcutRepository.loadBlocking(shortcutKeys)
            mainHandler.post {
                if (generation != rebuildGeneration) return@post
                if (found != null) {
                    // LoaderTask: "Pinned shortcut not found"
                    pruneMissing { it in shortcutKeys && it !in found }
                    for (info in model.allItems()) {
                        if (info is ShortcutItemInfo) found[info.homeKey]?.let { info.update(it, context) }
                    }
                }
                bindItems(generation)
            }
        }
    }

    private var rebuildGeneration = 0

    /** LoaderTask's binding: the apps' activities and the icons, then the views. */
    private fun bindItems(generation: Int) {
        val items = model.allItems()
        for (info in items) {
            if (info is AppItemInfo) app.appRepository.find(info.homeKey)?.let(info::bind)
        }
        val entries = items.mapNotNull { (it as? AppItemInfo)?.entry }
        val shortcutsToLoad = items.filterIsInstance<ShortcutItemInfo>().filter { it.shortcut != null }
        // The shortcuts' badges are their apps' icons.
        val badgeApps = shortcutsToLoad.mapNotNull { badgeAppOf(it.shortcut!!) }
        app.iconLoader.loadAll(entries + badgeApps) {
            if (generation != rebuildGeneration) return@loadAll
            loadShortcutIcons(shortcutsToLoad) {
                if (generation != rebuildGeneration) return@loadShortcutIcons
                grid.removeAllViews()
                for (item in model.items) addViewFor(item)
            }
        }
    }

    private fun loadShortcutIcons(shortcuts: List<ShortcutItemInfo>, onLoaded: () -> Unit) {
        var remaining = shortcuts.size
        if (remaining == 0) {
            onLoaded()
            return
        }
        for (info in shortcuts) {
            loadIcon(info) {
                remaining--
                if (remaining == 0) onLoaded()
            }
        }
    }

    fun onAppsChanged() {
        if (!loaded) return
        if (pruneMissingApps()) rebuild()
    }

    /**
     * ShortcutsChangedTask: shortcuts that are gone leave the home screen; the others get their
     * new labels and icons in place.
     */
    private fun onShortcutsChanged() {
        if (!loaded) return
        val keys = model.shortcutKeys()
        if (keys.isEmpty()) return
        app.ioExecutor.execute {
            val found = app.shortcutRepository.loadBlocking(keys) ?: return@execute
            mainHandler.post {
                if (pruneMissing { it in keys && it !in found }) {
                    rebuild()
                    return@post
                }
                app.iconLoader.forgetShortcuts(found.keys)
                for (view in grid.itemViews()) {
                    when (view) {
                        is AppIconView -> {
                            val info = view.tag as? ShortcutItemInfo ?: continue
                            info.update(found[info.homeKey] ?: continue, context)
                            bindIcon(info, view)
                        }
                        is FolderIconView -> for (info in view.contents) {
                            if (info !is ShortcutItemInfo) continue
                            info.update(found[info.homeKey] ?: continue, context)
                            loadIcon(info) { bitmap ->
                                info.bitmap = bitmap
                                view.updatePreviewItems { it === info }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Launcher.onResume: runs the uninstall drop target's deferred result. */
    fun onResume() {
        dropTargetBar.uninstall.onLauncherResume()
    }

    fun cancelDrag() {
        dragController.cancelDrag()
    }

    val isDragging: Boolean get() = dragController.isDragging

    /** Closes the open folder (typing in the search field, adding an item from the menu). */
    fun closeFolders() {
        AbstractFloatingView.closeOpenViews(activityContext, true, AbstractFloatingView.TYPE_FOLDER)
    }

    // ---- starting drags (ItemLongClickListener) ----

    /** A long press on an app in the search results: its menu, and a drag once it moves. */
    fun onSearchResultLongPress(entry: AppEntry, iconView: AppIconView) {
        // AppInfo.makeWorkspaceItem: the home screen gets its own item, not the search result's.
        val info = AppItemInfo(entry, (iconView.tag as? AppItemInfo)?.bitmap)
        homeWorkspace.beginDragShared(
            iconView, searchDragSource, info, DragPreviewProvider(iconView), DragOptions(),
            startLongPressAction(iconView, info),
        )
    }

    private fun onHomeItemLongPress(view: AppIconView) {
        val info = view.tag as? WorkspaceItemInfo ?: return
        homeWorkspace.startDrag(view, info, DragOptions(), startLongPressAction(view, info))
    }

    private fun onHomeFolderLongPress(view: FolderIconView) {
        homeWorkspace.startDrag(view, view.folderInfo, DragOptions(), null)
    }

    /**
     * BubbleTextView.startLongPressAction with PopupControllerForAppIcon.show: shows the item's
     * long-press menu (docs/spec.md 5.3) and returns the menu's pre-drag condition. Moving more
     * than deep_shortcuts_start_drag_threshold closes the menu and starts the drag.
     */
    private fun startLongPressAction(originalView: View, item: WorkspaceItemInfo): () -> DragOptions.PreDragCondition? = {
        if (PopupContainer.getOpen(activityContext) != null) {
            // There is already an items container open, so don't open this one.
            originalView.clearFocus()
            null
        } else {
            // TextLauncher: the launcher's own options (docs/spec.md 5.3) instead of
            // ActivityContext.getSupportedShortcuts.
            val systemShortcuts = buildList<SystemShortcut<*>> {
                if (item is AppItemInfo) item.entry?.let { add(EditSearchTerms(it, item, originalView)) }
                add(SystemShortcut.AppInfo(activityContext, item, originalView))
            }
            val container = PopupContainerWithArrow.create<ActivityContext>(
                context = context,
                originalView = originalView,
                itemInfo = item,
            )
            // PopupContainerWithArrow.configureForLauncher
            container.setPopupItemDragHandler(LauncherPopupItemDragHandler(container))
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
        item: ItemInfo,
        originalView: View,
    ) : SystemShortcut<ActivityContext>(
        com.android.launcher3.R.drawable.gm_edit_24, R.string.menu_search_terms, activityContext, item, originalView,
    ) {
        override fun onClick(view: View) {
            dismissTaskMenuView()
            host.editSearchTerms(entry)
        }
    }

    /**
     * LauncherPopupItemDragHandler (popup/PopupItemDragHandler.kt): drags an app shortcut out of
     * the long-press menu onto the home screen.
     */
    private inner class LauncherPopupItemDragHandler(
        private val mContainer: PopupContainerWithArrow<*>,
    ) : PopupItemDragHandler {
        private val mIconLastTouchPos: Point = Point()

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            // Touched a shortcut, update where it was touched so we can drag from there on
            // long click.
            when (ev.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> mIconLastTouchPos[ev.x.toInt()] = ev.y.toInt()
            }
            return false
        }

        override fun onLongClick(v: View): Boolean {
            // TextLauncher: no ItemLongClickListener.canStartDrag; nothing locks the home screen.
            // Return early if not the correct view
            if (v.parent !is DeepShortcutView) return false

            // Long clicked on a shortcut.
            val sv = v.parent as DeepShortcutView
            sv.setWillDrawIcon(false)

            // Move the icon to align with the center-top of the touch point
            val iconShift = Point()
            iconShift.x = mIconLastTouchPos.x - sv.iconCenter.x
            iconShift.y = mIconLastTouchPos.y - activityContext.deviceProfile.workspaceProfile.iconSizePx

            val draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON)
            val itemInfo = sv.finalInfo
            itemInfo.container = LauncherSettings.Favorites.CONTAINER_SHORTCUTS
            val dv =
                homeWorkspace.beginDragShared(
                    sv.iconView,
                    draggableView,
                    mContainer,
                    itemInfo,
                    ShortcutDragPreviewProvider(sv.iconView, iconShift),
                    DragOptions(),
                )
            dv.animateShift(-iconShift.x, -iconShift.y)

            // TODO: support dragging from within folder without having to close it
            closeFolders()
            return false
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
            AbstractFloatingView.closeOpenViews(activityContext, true, AbstractFloatingView.TYPE_FOLDER)
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

    /**
     * DeleteDropTarget.canRemove: items on the home screen or in a folder (Launcher3: items with
     * an id). Apps from search results and shortcuts from the menu are not there yet, so the
     * button cancels the drag.
     */
    private fun canRemove(d: DragObject): Boolean = d.dragSource === homeWorkspace || d.dragSource is Folder

    // ---- items ----

    private fun badgeAppOf(shortcut: ShortcutInfo): AppEntry? =
        app.appRepository.apps?.let { app.shortcutRepository.appOf(shortcut, it) }

    /** The icon of an app or shortcut that was found (LoaderTask); nothing for one that was not. */
    private fun loadIcon(info: WorkspaceItemInfo, onLoaded: (BitmapInfo) -> Unit) {
        when (info) {
            is AppItemInfo -> info.entry?.let { app.iconLoader.load(it, onLoaded) }
            is ShortcutItemInfo -> info.shortcut?.let { app.iconLoader.loadShortcut(info.homeKey, it, badgeAppOf(it), onLoaded) }
        }
    }

    /** BubbleTextView.applyFromWorkspaceItem: the icon, now or once it is loaded. */
    private fun bindIcon(info: WorkspaceItemInfo, view: AppIconView) {
        loadIcon(info) { bitmap ->
            info.bitmap = bitmap
            view.iconDrawable = app.iconLoader.newDrawable(bitmap)
        }
    }

    /** ItemClickHandler.onClick: starts the app or the app shortcut. */
    private fun onItemClick(info: WorkspaceItemInfo, view: View) {
        when (info) {
            is AppItemInfo -> info.entry?.let { host.launch(it, view) }
            is ShortcutItemInfo -> info.shortcut?.let { host.startShortcut(it.`package`, it.id, view, info.user) }
            // A shortcut row of the long-press menu (onClickAppShortcut).
            else -> {
                val id = info.deepShortcutId ?: return
                val packageName = info.targetPackage ?: return
                host.startShortcut(packageName, id, view, info.user)
            }
        }
    }

    // ---- views ----

    /** ItemInflater.inflateItem and Workspace.addInScreen: the item's view on the grid. */
    private fun addViewFor(item: ItemInfo): View? {
        val view: View = when (item) {
            is AppItemInfo -> if (item.entry != null) createItemView(item) else return null
            is ShortcutItemInfo -> if (item.shortcut != null) createItemView(item) else return null
            is FolderInfo -> createFolderView(item)
            else -> return null // TODO(Tex-t2ea): widgets
        }
        grid.addItemView(view, item)
        return view
    }

    private fun createItemView(info: WorkspaceItemInfo): AppIconView =
        AppIconView(context, gridSpec.iconSizePx).apply {
            bindIcon(info, this)
            // ItemInflater: the item's focus listener is Launcher.getFocusHandler.
            setFocusableAsLauncher3Icon(grid.focusIndicatorHelper)
            setOnClickListener { onItemClick(info, this) }
            setOnLongClickListener {
                onHomeItemLongPress(this)
                true
            }
        }

    /** FolderIcon.inflateIcon */
    private fun createFolderView(folderInfo: FolderInfo): FolderIconView =
        FolderIconView(context, activityContext, folderInfo, folderCallbacks).apply {
            HomeFolder(context, activityContext, launcherDelegate, gridSpec.iconSizePx).bindToIcon(this)
            setFocusableAsLauncher3Icon(grid.focusIndicatorHelper)
            for (item in contents) {
                loadIcon(item) { bitmap ->
                    if (item.bitmap === bitmap) return@loadIcon
                    item.bitmap = bitmap
                    updatePreviewItems { it === item }
                }
            }
            // ItemClickHandler.onClickFolderIcon
            setOnClickListener { with(folder) { if (!isOpen && !isDestroyed) animateOpen() } }
            setOnLongClickListener {
                onHomeFolderLongPress(this)
                true
            }
        }

    private val folderCallbacks = object : FolderIconView.Callbacks {
        override fun onContentChanged(view: FolderIconView) = model.save()
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
     * LauncherAccessibilityDelegate.findSpaceOnWorkspace: the first free cell, row by row. The
     * grid grows downward, so there always is one.
     */
    private fun findSpaceOnHome(): IntArray {
        val occupied = HashSet<Long>()
        for (item in model.items) {
            for (x in item.cellX until item.cellX + item.spanX) {
                for (y in item.cellY until item.cellY + item.spanY) occupied += y.toLong() * gridSpec.columns + x
            }
        }
        var y = 0
        while (true) {
            for (x in 0 until gridSpec.columns) {
                if (y.toLong() * gridSpec.columns + x !in occupied) return intArrayOf(x, y)
            }
            y++
        }
    }

    // ---- model changes ----

    /** LauncherDelegate.replaceFolderWithFinalItem. Returns whether the folder was destroyed. */
    private fun replaceFolderWithFinalItem(folderView: FolderIconView): Boolean {
        // TextLauncher: the folder may report again after it was already replaced.
        if (folderView.parent == null) return true
        // Add the last remaining child to the workspace in place of the folder
        val onCompleteRunnable = Runnable {
            val info = folderView.folderInfo
            val itemCount = info.contents.size
            if (itemCount <= 1) {
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
                    addViewFor(finalItem)
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

    /** Removes apps that are no longer installed. Returns true when something changed. */
    private fun pruneMissingApps(): Boolean {
        val installed = app.appRepository.apps?.map { it.key }?.toSet() ?: return false
        return pruneMissing { !HomeShortcutKey.isShortcutKey(it) && it !in installed }
    }

    /**
     * Removes the apps and shortcuts whose keys [isMissing] says are gone; a folder left with one
     * item turns into that item. Returns true when something changed.
     */
    private fun pruneMissing(isMissing: (String) -> Boolean): Boolean {
        var changed = false
        for (item in model.items.toList()) {
            when (item) {
                is HomeItemInfo -> if (isMissing(item.homeKey)) {
                    model.items.remove(item); changed = true
                }
                is FolderInfo -> {
                    if (item.contents.removeAll { isMissing((it as HomeItemInfo).homeKey) }) changed = true
                    when (item.contents.size) {
                        0 -> {
                            model.items.remove(item); changed = true
                        }
                        1 -> {
                            model.items.remove(item)
                            val last = item.contents.removeAt(0)
                            last.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
                            last.cellX = item.cellX
                            last.cellY = item.cellY
                            model.items += last
                            changed = true
                        }
                    }
                }
            }
        }
        if (changed) model.save()
        return changed
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
            return addViewFor(folder) as FolderIconView
        }

        override fun addItem(info: WorkspaceItemInfo, cellX: Int, cellY: Int): View {
            info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            info.cellX = cellX
            info.cellY = cellY
            model.items += info
            return addViewFor(info)!!
        }

        override fun removeWorkspaceItem(view: View) {
            if (view.parent === grid) grid.removeView(view)
            (view.tag as? ItemInfo)?.let { model.items.remove(it) }
            model.save()
        }

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
        const val EDIT_ANIMATION_MS = 150L

        /** ItemInstallQueue.NEW_SHORTCUT_BOUNCE_DURATION */
        const val NEW_SHORTCUT_BOUNCE_DURATION = 450L

        /** Launcher.BOUNCE_ANIMATION_TENSION */
        const val BOUNCE_ANIMATION_TENSION = 1.3f
    }
}
