package com.malt03.textlauncher.ui.home

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.widget.NestedScrollView
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.HomeItem
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.LauncherRootLayout
import com.malt03.textlauncher.ui.NoPressDelayScrollView
import com.malt03.textlauncher.ui.drag.DragController
import com.malt03.textlauncher.ui.drag.DropTargetBar

/**
 * The home layout shown while the query is empty (docs/spec.md 5): builds views from
 * [com.malt03.textlauncher.data.HomeModel], opens folders, and applies drag and drop results.
 */
class HomeController(
    private val app: TextLauncherApplication,
    private val root: LauncherRootLayout,
    private val host: Host,
) : DragController.Callbacks {

    interface Host {
        fun launch(entry: AppEntry, view: View)
        /** Shows the long-press menu of an app and returns it. */
        fun showAppMenu(entry: AppEntry, anchor: View): PopupMenu
        /** Edit mode (docs/spec.md 5.4) starts with a drag and ends after it. */
        fun onEditModeChanged(editing: Boolean)
    }

    private val context = root.context
    private val model = app.homeModel
    val grid = HomeGridView(context, GridSpec.from(context))
    val scrollView: NestedScrollView = NoPressDelayScrollView(context).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addOnLayoutChangeListener { v, _, top, _, bottom, _, _, _, _ -> grid.viewportHeight = bottom - top }
    }
    val drag = DragController(root.overlay, scrollView, grid, root.dropTargetBar, this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loaded = false
    private var folderPopup: FolderPopup? = null

    init {
        val padding = (8 * context.resources.displayMetrics.density).toInt()
        grid.setPadding(padding, padding, padding, padding)
        root.touchInterceptor = { drag.onTouchEvent(it) }
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

    /** Re-creates every view, e.g. after the icon theme changed. */
    fun rebuild() {
        grid.removeAllViews()
        for (item in model.items) addViewFor(item)
    }

    fun onAppsChanged() {
        if (!loaded) return
        if (pruneMissingApps()) rebuild()
    }

    fun closeFolder() {
        folderPopup?.dismiss()
        folderPopup = null
    }

    val isFolderOpen: Boolean get() = folderPopup != null

    /**
     * Long press on an app anywhere (home, folder, search results): show its menu and arm the
     * move-to-drag transition (docs/spec.md 5.3).
     */
    fun onAppLongPress(entry: AppEntry, anchor: View, homeItem: HomeItem?, homeView: View?, onDragStart: () -> Unit = {}) {
        anchor.parent?.requestDisallowInterceptTouchEvent(true)
        val menu = host.showAppMenu(entry, anchor)
        val info = app.iconLoader.cached(entry) ?: return
        val payload = DragController.Payload(entry.key, homeItem, homeView, {
            AppIconView(context, grid.grid.iconSizePx).apply { icon = app.iconLoader.newDrawable(info) }
        })
        drag.armPreDrag(payload, root.lastDownRawX, root.lastDownRawY) {
            menu.dismiss()
            onDragStart()
        }
    }

    // ---- views ----

    private fun addViewFor(item: HomeItem) {
        val iconSize = grid.grid.iconSizePx
        val view: View = when (item) {
            is HomeItem.App -> AppIconView(context, iconSize).apply {
                val entry = app.appRepository.find(item.appKey)
                if (entry != null) {
                    app.iconLoader.load(entry) { info -> icon = app.iconLoader.newDrawable(info) }
                    setOnClickListener { host.launch(entry, this) }
                    setOnLongClickListener {
                        onAppLongPress(entry, this, item, this)
                        true
                    }
                }
            }
            is HomeItem.Folder -> FolderIconView(context, iconSize).apply {
                bindFolderIcons(this, item)
                setOnClickListener { openFolder(item, this) }
                setOnLongClickListener {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val icons = this.icons
                    drag.startDrag(
                        DragController.Payload(null, item, this, { FolderIconView(context, iconSize).also { it.icons = icons } }),
                        root.lastDownRawX, root.lastDownRawY,
                    )
                    true
                }
            }
            is HomeItem.Widget -> return // TODO(Tex-t2ea): widgets
        }
        grid.addItemView(view, item)
    }

    private fun bindFolderIcons(view: FolderIconView, folder: HomeItem.Folder) {
        val entries = folder.appKeys.mapNotNull { app.appRepository.find(it) }
        val drawables = arrayOfNulls<Drawable>(entries.size)
        entries.forEachIndexed { index, entry ->
            app.iconLoader.load(entry) { info ->
                drawables[index] = app.iconLoader.newDrawable(info)
                view.icons = drawables.filterNotNull()
            }
        }
    }


    private fun openFolder(folder: HomeItem.Folder, folderView: View) {
        closeFolder()
        val entries = folder.appKeys.mapNotNull { app.appRepository.find(it) }
        folderPopup = FolderPopup(
            root.overlay, folderView, entries, grid.grid, app.iconLoader,
            onClick = { entry, view -> host.launch(entry, view) },
            onLongPress = { entry, view ->
                onAppLongPress(entry, view, null, null) {
                    closeFolder()
                    removeFromFolder(folder, entry.key)
                }
            },
            backgroundViews = listOf(scrollView, root.searchBarArea),
            onDismiss = { folderPopup = null },
        )
    }

    // ---- model changes ----

    private fun replace(old: HomeItem, new: HomeItem?) {
        grid.viewFor(old)?.let { grid.removeView(it) }
        model.items.remove(old)
        if (new != null) {
            model.items += new
            addViewFor(new)
        }
    }

    /** Takes an app out of a folder. A folder left with one app turns back into that app. */
    private fun removeFromFolder(folder: HomeItem.Folder, appKey: String) {
        folder.appKeys.remove(appKey)
        when (folder.appKeys.size) {
            0 -> replace(folder, null)
            1 -> replace(folder, HomeItem.App(model.newId(), folder.appKeys.first()).also { it.cellX = folder.cellX; it.cellY = folder.cellY })
            else -> grid.viewFor(folder)?.let { bindFolderIcons(it as FolderIconView, folder) }
        }
        model.save()
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

    // ---- DragController.Callbacks ----

    override fun onDragStarted(payload: DragController.Payload) {
        closeFolder()
        host.onEditModeChanged(true)
        // Make room for the drop target bar, like Launcher3's spring-loaded workspace.
        scrollView.animate().translationY(root.dropTargetBar.layoutParams.height.toFloat()).setDuration(EDIT_ANIMATION_MS)
            .withEndAction { drag.onGridMoved() }.start()
    }

    override fun onDropOnGrid(payload: DragController.Payload, cellX: Int, cellY: Int) {
        val homeItem = payload.homeItem
        val homeView = payload.homeView
        if (homeItem != null && homeView != null) {
            val lp = homeView.layoutParams as CellLayoutLayoutParams
            lp.cellX = cellX
            lp.cellY = cellY
            homeItem.cellX = cellX
            homeItem.cellY = cellY
            grid.requestLayout()
        } else {
            val key = payload.appKey ?: return
            val item = HomeItem.App(model.newId(), key).also { it.cellX = cellX; it.cellY = cellY }
            model.items += item
            addViewFor(item)
        }
        model.save()
    }

    override fun onDropIntoFolderOrApp(payload: DragController.Payload, target: View) {
        val key = payload.appKey ?: return
        payload.homeItem?.let { replace(it, null) }
        when (val targetItem = target.tag as HomeItem) {
            is HomeItem.Folder -> {
                targetItem.appKeys += key
                bindFolderIcons(target as FolderIconView, targetItem)
            }
            is HomeItem.App -> {
                val folder = HomeItem.Folder(model.newId(), mutableListOf(targetItem.appKey, key))
                folder.cellX = targetItem.cellX
                folder.cellY = targetItem.cellY
                replace(targetItem, folder)
                (grid.viewFor(folder) as? FolderIconView)?.playCreateAnimation()
            }
            is HomeItem.Widget -> Unit
        }
        model.save()
    }

    override fun onDropOnTarget(payload: DragController.Payload, target: DropTargetBar.Target) {
        when (target) {
            DropTargetBar.Target.REMOVE -> payload.homeItem?.let { replace(it, null) }
            DropTargetBar.Target.UNINSTALL -> {
                val component = payload.appKey?.let(AppEntry::componentOf) ?: return
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", component.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        model.save()
    }

    override fun onDragEnded() {
        scrollView.animate().translationY(0f).setDuration(EDIT_ANIMATION_MS).start()
        host.onEditModeChanged(false)
    }

    private companion object {
        const val EDIT_ANIMATION_MS = 150L
    }
}
