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
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.icons.BitmapInfo
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
import com.malt03.textlauncher.data.HomeItem
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

        /**
         * DeepShortcutView.getFinalInfo: the shortcut as it goes on the home screen, badged with
         * its app's icon (IconCache.getShortcutInfoBadge).
         */
        override fun makeWorkspaceItem(info: WorkspaceItemInfo, detail: ShortcutInfo): WorkspaceItemInfo {
            val key = app.shortcutRepository.keyOf(detail)
            shortcuts = shortcuts + (key to detail)
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
            closeFolder()
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
    private val launcher = Launcher(workspace, root.hotseat, root.scrim)

    private val dragController: HomeDragController = HomeDragController(activityContext) { root.isTouchInProgress }
    private val workspaceHost = WorkspaceHost()
    private val homeWorkspace = HomeWorkspace(activityContext, grid, scrollView, dragController, workspaceHost)
    private val dropTargetBar = DropTargetBar(context, { activityContext }, { dragController }, ::canRemove)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var loaded = false
    private var folderPopup: OpenFolder? = null
    private var folderDragSource: FolderDragSource? = null

    /** The app shortcuts on the home screen and in folders, by [HomeShortcutKey] key. */
    private var shortcuts: Map<String, ShortcutInfo> = emptyMap()
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
                    shortcuts = found
                    // LoaderTask: "Pinned shortcut not found"
                    pruneMissing { it in shortcutKeys && it !in found }
                }
                bindItems(generation)
            }
        }
    }

    private var rebuildGeneration = 0

    private fun bindItems(generation: Int) {
        val keys = model.items.flatMap {
            when (it) {
                is HomeItem.App -> listOf(it.appKey)
                is HomeItem.Shortcut -> listOf(it.shortcutKey)
                is HomeItem.Folder -> it.itemKeys
                is HomeItem.Widget -> emptyList()
            }
        }
        val entries = keys.filterNot(HomeShortcutKey::isShortcutKey).mapNotNull { app.appRepository.find(it) }
        val shortcutsToLoad = keys.mapNotNull { key -> shortcuts[key]?.let { key to it } }
        // The shortcuts' badges are their apps' icons.
        val badgeApps = shortcutsToLoad.mapNotNull { badgeAppOf(it.second) }
        app.iconLoader.loadAll(entries + badgeApps) {
            if (generation != rebuildGeneration) return@loadAll
            loadShortcutIcons(shortcutsToLoad) {
                if (generation != rebuildGeneration) return@loadShortcutIcons
                grid.removeAllViews()
                for (item in model.items) addViewFor(item)
            }
        }
    }

    private fun loadShortcutIcons(shortcuts: List<Pair<String, ShortcutInfo>>, onLoaded: () -> Unit) {
        var remaining = shortcuts.size
        if (remaining == 0) {
            onLoaded()
            return
        }
        for ((key, shortcut) in shortcuts) {
            app.iconLoader.loadShortcut(key, shortcut, badgeAppOf(shortcut)) {
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
                shortcuts = shortcuts + found
                if (pruneMissing { it in keys && it !in found }) {
                    rebuild()
                    return@post
                }
                app.iconLoader.forgetShortcuts(found.keys)
                for (view in grid.itemViews()) {
                    when (view) {
                        is AppIconView -> {
                            val info = view.itemInfo as? ShortcutItemInfo ?: continue
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

    fun closeFolder() {
        folderPopup?.dismiss()
        folderPopup = null
    }

    val isFolderOpen: Boolean get() = folderPopup != null

    // ---- starting drags (ItemLongClickListener) ----

    /** A long press on an app in the search results: its menu, and a drag once it moves. */
    fun onSearchResultLongPress(entry: AppEntry, iconView: AppIconView) {
        val info = iconView.itemInfo as? AppItemInfo ?: return
        homeWorkspace.beginDragShared(
            iconView, searchDragSource, info, DragPreviewProvider(iconView), DragOptions(),
            startLongPressAction(iconView, info),
        )
    }

    private fun onHomeItemLongPress(view: AppIconView) {
        val info = view.itemInfo ?: return
        homeWorkspace.startDrag(view, info, DragOptions(), startLongPressAction(view, info))
    }

    private fun onHomeFolderLongPress(view: FolderIconView) {
        homeWorkspace.startDrag(view, view.folderInfo, DragOptions(), null)
    }

    /** Folder.startDrag for an item in the open folder. */
    private fun onFolderItemLongPress(folderIcon: FolderIconView, item: WorkspaceItemInfo, view: AppIconView) {
        val source = FolderDragSource(folderIcon, dragController, ::replaceFolderWithFinalItem)
        folderDragSource = source
        source.startDrag()
        homeWorkspace.beginDragShared(
            view, source, item, DragPreviewProvider(view), DragOptions(), startLongPressAction(view, item),
        )
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
                if (item is AppItemInfo) add(EditSearchTerms(item.entry, item, originalView))
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
            closeFolder()
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

    /**
     * DeleteDropTarget.canRemove: items on the home screen or in a folder (Launcher3: items with
     * an id). Apps from search results and shortcuts from the menu are not there yet, so the
     * button cancels the drag.
     */
    private fun canRemove(d: DragObject): Boolean = d.dragSource === homeWorkspace || d.dragSource is FolderDragSource

    // ---- items ----

    /** The ItemInfo of an app or shortcut key, or null when it is gone. */
    private fun itemInfoFor(key: String): WorkspaceItemInfo? =
        if (HomeShortcutKey.isShortcutKey(key)) {
            shortcuts[key]?.let { ShortcutItemInfo(key, it, context, app.iconLoader.cachedShortcut(key)) }
        } else {
            app.appRepository.find(key)?.let { AppItemInfo(it, app.iconLoader.cached(it)) }
        }

    private fun homeItemFor(key: String): HomeItem =
        if (HomeShortcutKey.isShortcutKey(key)) HomeItem.Shortcut(model.newId(), key) else HomeItem.App(model.newId(), key)

    private fun badgeAppOf(shortcut: ShortcutInfo): AppEntry? =
        app.appRepository.apps?.let { app.shortcutRepository.appOf(shortcut, it) }

    private fun loadIcon(info: WorkspaceItemInfo, onLoaded: (BitmapInfo) -> Unit) {
        when (info) {
            is AppItemInfo -> app.iconLoader.load(info.entry, onLoaded)
            is ShortcutItemInfo -> app.iconLoader.loadShortcut(info.homeKey, info.shortcut, badgeAppOf(info.shortcut), onLoaded)
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
            is AppItemInfo -> host.launch(info.entry, view)
            is ShortcutItemInfo -> host.startShortcut(info.shortcut.`package`, info.shortcut.id, view, info.user)
        }
    }

    // ---- views ----

    private fun addViewFor(item: HomeItem, itemInfo: WorkspaceItemInfo? = null): View? {
        val view: View = when (item) {
            is HomeItem.App -> createItemView(itemInfo ?: itemInfoFor(item.appKey) ?: return null)
            is HomeItem.Shortcut -> createItemView(itemInfo ?: itemInfoFor(item.shortcutKey) ?: return null)
            is HomeItem.Folder -> createFolderView(item)
            is HomeItem.Widget -> return null // TODO(Tex-t2ea): widgets
        }
        grid.addItemView(view, item)
        return view
    }

    private fun createItemView(info: WorkspaceItemInfo): AppIconView =
        AppIconView(context, gridSpec.iconSizePx).apply {
            itemInfo = info
            bindIcon(info, this)
            setOnClickListener { onItemClick(info, this) }
            setOnLongClickListener {
                onHomeItemLongPress(this)
                true
            }
        }

    private fun createFolderView(folder: HomeItem.Folder): FolderIconView =
        FolderIconView(context, gridSpec.iconSizePx, activityContext, folderCallbacks).apply {
            val items = folder.itemKeys.mapNotNull(::itemInfoFor)
            setContents(items)
            for (item in items) {
                loadIcon(item) { bitmap ->
                    if (item.bitmap === bitmap) return@loadIcon
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
            folder.itemKeys.clear()
            folder.itemKeys += view.contents.map { (it as HomeItemInfo).homeKey }
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
            root.overlay, folderView, folderView.contents, gridSpec, activityContext, launcher,
            bindIcon = ::bindIcon,
            onClick = ::onItemClick,
            onLongPress = { item, view -> onFolderItemLongPress(folderView, item, view) },
            onDismiss = {
                folderView.folder.setOpen(false)
                folderPopup = null
            },
        )
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

    /** LauncherDelegate.replaceFolderWithFinalItem */
    private fun replaceFolderWithFinalItem(folderView: FolderIconView) {
        // Add the last remaining child to the workspace in place of the folder
        val onCompleteRunnable = Runnable {
            val folder = folderView.tag as? HomeItem.Folder ?: return@Runnable
            val itemCount = folderView.folderInfo.contents.size
            if (itemCount <= 1) {
                var newItem: HomeItem? = null
                var finalItem: WorkspaceItemInfo? = null
                if (itemCount == 1) {
                    // Move the item from the folder to the workspace, in the position of the folder
                    finalItem = folderView.folderInfo.contents.removeAt(0) as WorkspaceItemInfo
                    newItem = homeItemFor((finalItem as HomeItemInfo).homeKey).also {
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
                is HomeItem.App -> if (isMissing(item.appKey)) {
                    model.items.remove(item); changed = true
                }
                is HomeItem.Shortcut -> if (isMissing(item.shortcutKey)) {
                    model.items.remove(item); changed = true
                }
                is HomeItem.Folder -> {
                    if (item.itemKeys.removeAll { isMissing(it) }) changed = true
                    when (item.itemKeys.size) {
                        0 -> model.items.remove(item)
                        1 -> {
                            model.items.remove(item)
                            model.items += homeItemFor(item.itemKeys.first()).also { it.cellX = item.cellX; it.cellY = item.cellY }
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

        override fun addItem(info: WorkspaceItemInfo, cellX: Int, cellY: Int): View {
            val item = homeItemFor((info as HomeItemInfo).homeKey)
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

        /** ItemInstallQueue.NEW_SHORTCUT_BOUNCE_DURATION */
        const val NEW_SHORTCUT_BOUNCE_DURATION = 450L

        /** Launcher.BOUNCE_ANIMATION_TENSION */
        const val BOUNCE_ANIMATION_TENSION = 1.3f
    }
}
