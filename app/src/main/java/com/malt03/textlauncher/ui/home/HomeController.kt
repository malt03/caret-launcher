package com.malt03.textlauncher.ui.home

import android.graphics.Rect
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import java.util.function.Predicate
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherSettings
import com.android.launcher3.Workspace
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.LauncherDelegate
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.views.ActivityContext
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppEntry
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
        /** Shows the home screen, leaving the search results (Launcher3's NORMAL state). */
        fun showHome()
    }

    internal val context = root.context
    internal val model = app.homeModel
    val grid = HomeGridView(context, GridSpec.from(context))
    internal val gridSpec = grid.grid

    val scrollView: NestedScrollView = NoPressDelayScrollView(context).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
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
    private val dropTargetBar = DropTargetBar(context, { activityContext }, launcher, { dragController }, ::canRemove)

    internal val itemInflater = HomeItemInflater(this)
    internal val itemLongClickListener = ItemLongClickListener(this)
    private val loader = HomeLoader(this)

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
        val padding = (8 * context.resources.displayMetrics.density).toInt()
        grid.setPadding(padding, padding, padding, padding)

        // Launcher.setupViews
        root.overlay.setDragController(dragController)
        dragController.defaultDropTarget = homeWorkspace
        dragController.addDragListener(homeWorkspace)
        dragController.addDragListener(SpringLoadedListener(this))
        root.attachDropTargetBar(dropTargetBar)
        dropTargetBar.setup(dragController)
        // DragLayer.recreateControllers: the drag controller is the touch controller; open
        // floating views (the long-press menu) are asked first.
        root.overlay.setControllers(dragController)
        root.touchInterceptor = root.overlay::dispatchTouchEventFromParent

        loader.start()
    }

    fun destroy() {
        loader.destroy()
    }

    /** Re-creates every view, e.g. after the icon theme changed. */
    fun rebuild() = loader.rebuild()

    fun onAppsChanged() = loader.onAppsChanged()

    /** Launcher.onResume: runs the uninstall drop target's deferred result. */
    fun onResume() {
        dropTargetBar.uninstall.onLauncherResume()
    }

    fun cancelDrag() {
        dragController.cancelDrag()
    }

    val isDragging: Boolean get() = dragController.isDragging

    /** Closes the open folder (typing in the search field, dragging a shortcut out of the menu). */
    fun closeFolders() {
        AbstractFloatingView.closeOpenViews(activityContext, true, AbstractFloatingView.TYPE_FOLDER)
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

    /**
     * DeleteDropTarget.canRemove: items on the home screen or in a folder (Launcher3: items with
     * an id). Apps from search results and shortcuts from the menu are not there yet, so the
     * button cancels the drag.
     */
    private fun canRemove(d: DragObject): Boolean = d.dragSource === homeWorkspace || d.dragSource is Folder

    // ---- views ----

    /** Launcher.bindItems: the views of the home model's items, from scratch. */
    internal fun bindItems(items: List<ItemInfo>) {
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
     * LauncherAccessibilityDelegate.addToWorkspace for the menu's add button: the first free
     * cell, bound with Launcher.bindInflatedItems' bounce.
     */
    internal fun addToWorkspace(item: ItemInfo): Boolean {
        // LauncherAccessibilityDelegate.addToWorkspace: the menu's item is copied.
        val info = (item as? WorkspaceItemInfo ?: return false).clone()
        // TextLauncher: Launcher3 goes to the NORMAL state first.
        host.showHome()
        val cell = IntArray(2)
        findSpaceOnWorkspace(info, cell)
        val view = workspaceHost.addItem(info, cell[0], cell[1])
        model.save()
        animateNewItem(view)
        return true
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
     * TextLauncher: one grid that grows downward, so a new row below the last stands in for an
     * extra empty screen.
     */
    private fun findSpaceOnWorkspace(info: ItemInfo, outCoordinates: IntArray) {
        val layout = grid.cellLayout
        val found = layout.findCellForSpan(outCoordinates, info.spanX, info.spanY)
        if (!found) {
            outCoordinates[0] = 0
            outCoordinates[1] = layout.countY
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
        /** ItemInstallQueue.NEW_SHORTCUT_BOUNCE_DURATION */
        const val NEW_SHORTCUT_BOUNCE_DURATION = 450L

        /** Launcher.BOUNCE_ANIMATION_TENSION */
        const val BOUNCE_ANIMATION_TENSION = 1.3f
    }
}
