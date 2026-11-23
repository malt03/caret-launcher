package com.malt03.textlauncher.ui.home

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.GridOccupancy
import com.malt03.textlauncher.data.WidgetItemInfo
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.HomeShortcutKey
import com.malt03.textlauncher.data.isDeepShortcut

/**
 * LoaderTask with PackageUpdatedTask and ShortcutsChangedTask for the home screen: loads the home
 * model, finds its apps and shortcuts and their icons, binds the views, and drops the apps and
 * shortcuts that are gone.
 */
internal class HomeLoader(private val home: HomeController) {
    private val context = home.context
    private val app = home.app
    private val model = home.model
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loaded = false

    /**
     * Launcher.isWorkspaceLoading's opposite: the items are bound to the views, so the grid knows
     * which cells are taken.
     */
    var isLoaded = false
        private set

    /** ModelCallbacks.getWorkspaceLoading: from [start] or [rebuild] until the items are bound. */
    var isWorkspaceLoading = true
        private set
    private var rebuildGeneration = 0
    private val shortcutsListener: () -> Unit = ::onShortcutsChanged

    /**
     * The activity was destroyed (LauncherModel.removeCallbacks): what it started on other threads
     * is dropped when it comes back, as an activity created again binds the model itself.
     */
    private var destroyed = false

    /**
     * LauncherModel.addCallbacksAndLoad: the model is read from the database once; an activity
     * created again (docs/spec.md 7.2) binds the model that is already loaded (rebindCallbacks).
     */
    fun start() {
        app.shortcutRepository.addChangeListener(shortcutsListener)
        if (model.isLoaded) {
            onModelLoaded()
            return
        }
        MODEL_EXECUTOR.execute {
            val items = model.loadBlocking()
            mainHandler.post {
                if (destroyed) return@post
                if (!model.isLoaded) model.setLoaded(items)
                onModelLoaded()
            }
        }
    }

    private fun onModelLoaded() {
        loaded = true
        // LoaderTask (ModelDbController): the grid is migrated before the items are read.
        // TextLauncher: also for a loaded model, as a new grid creates the activity again rather
        // than reloading the model (Launcher.onIdpChanged).
        if (GridSizeMigration.migrateGridIfNeeded(context, home.gridSpec, model.items)) model.save()
        pruneMissingApps()
        rebuild()
    }

    fun destroy() {
        app.shortcutRepository.removeChangeListener(shortcutsListener)
        destroyed = true
        // The callbacks of a rebuild in progress see another generation and stop.
        rebuildGeneration++
    }

    /**
     * Re-creates every view, e.g. after the icon theme changed. Like Launcher3's binding (LoaderTask
     * and its pinned shortcut lookup), the views are built once the shortcuts and icons are loaded.
     */
    fun rebuild() {
        if (destroyed) return
        // ModelCallbacks.startBinding cancels the drag; here it is cancelled before the model
        // changes below (pruneMissing, checkItemPlacement), so that a dragged item goes back first.
        home.cancelDrag()
        isWorkspaceLoading = true
        val generation = ++rebuildGeneration
        val shortcutKeys = model.shortcutKeys()
        MODEL_EXECUTOR.execute {
            val found = if (shortcutKeys.isEmpty()) emptyMap() else app.shortcutRepository.loadBlocking(shortcutKeys)
            mainHandler.post {
                if (generation != rebuildGeneration) return@post
                // WorkspaceItemProcessor: a shortcut that was looked up gets its title, state and
                // icon; when the lookup failed, the item stays with what is known of it.
                val details = HashMap<WorkspaceItemInfo, ShortcutInfo>()
                if (found != null) {
                    // LoaderTask: "Pinned shortcut not found"
                    pruneMissing { it in shortcutKeys && it !in found }
                    for (info in model.allItems()) {
                        if (info !is WorkspaceItemInfo || !info.isDeepShortcut) continue
                        val shortcut = found[model.keyOf(info)] ?: continue
                        info.updateFromDeepShortcutInfo(shortcut, context)
                        details[info] = shortcut
                    }
                }
                bindItems(generation, details)
            }
        }
    }

    /**
     * LoaderCursor.checkItemPlacement for the home items: those out of the grid's bounds, or on
     * cells an earlier item has, are deleted (markDeleted and commitDeleted).
     */
    private fun checkItemPlacement() {
        val countX = home.gridSpec.columns
        val countY = home.gridSpec.rows
        val occupancy = GridOccupancy(countX + 1, countY + 1)
        val deleted = model.items.filter { item ->
            if (item.cellX < 0 || item.cellY < 0 || item.cellX + item.spanX > countX || item.cellY + item.spanY > countY) {
                Log.e(TAG, "Error loading shortcut $item into cell (${item.cellX},${item.cellY}) out of screen bounds ($countX x $countY)")
                true
            } else if (occupancy.isRegionVacant(item.cellX, item.cellY, item.spanX, item.spanY)) {
                occupancy.markCells(item, true)
                false
            } else {
                Log.e(TAG, "Error loading shortcut $item into cell (${item.cellX},${item.cellY},${item.spanX},${item.spanY}) already occupied")
                true
            }
        }
        if (deleted.isNotEmpty()) {
            model.items.removeAll(deleted)
            model.save()
        }
    }

    /** LoaderTask's binding: the apps' activities and the icons, then the views. */
    private fun bindItems(generation: Int, shortcuts: Map<WorkspaceItemInfo, ShortcutInfo>) {
        pruneMissingWidgets()
        checkItemPlacement()
        val items = model.allItems()
        for (info in items) {
            if (info is AppItemInfo) app.appRepository.find(info.homeKey)?.let(info::bind)
        }
        val entries = items.mapNotNull { (it as? AppItemInfo)?.entry }
        // A shortcut that was not looked up keeps the icon saved with it (IconRequestInfo
        // .loadIconFromDb), or else gets the default one.
        val unknown = items.filterIsInstance<WorkspaceItemInfo>()
            .filter { it.isDeepShortcut && it !in shortcuts && it.bitmap.isLowRes }
        // WorkspaceItemProcessor: an app that is not available keeps its cached icon.
        val unavailable = items.filterIsInstance<AppItemInfo>().filter { it.entry == null }
        app.iconLoader.loadAll(entries, DESKTOP_ICON_FLAG) {
            if (generation != rebuildGeneration) return@loadAll
            loadUnavailableIcons(unavailable) {
                if (generation != rebuildGeneration) return@loadUnavailableIcons
                loadShortcutIcons(shortcuts) {
                    if (generation != rebuildGeneration) return@loadShortcutIcons
                    app.iconLoader.loadDefault { default ->
                        if (generation != rebuildGeneration) return@loadDefault
                        for (info in unknown) if (info.bitmap.isLowRes) info.bitmap = default
                        home.bindItems(model.items)
                        isWorkspaceLoading = false
                        if (!isLoaded) {
                            isLoaded = true
                            home.widgetAddFlow.onLoaded()
                        }
                        if (shortcuts.isNotEmpty() && model.hasShortcutsWithoutIcons) {
                            model.hasShortcutsWithoutIcons = false
                            model.save()
                        }
                        // LoaderTask: the icon cache is brought up to date after the first binding.
                        app.appRepository.apps?.let { app.iconLoader.updateIconCacheOnce(it, shortcuts.values) }
                    }
                }
            }
        }
    }

    /** The cached icons and titles of the apps that are not available ([AppItemInfo.markUnavailable]). */
    private fun loadUnavailableIcons(items: List<AppItemInfo>, onLoaded: () -> Unit) {
        var remaining = items.size
        if (remaining == 0) {
            onLoaded()
            return
        }
        for (info in items) {
            val component = AppEntry.componentOf(info.homeKey)
            if (component == null) {
                if (--remaining == 0) onLoaded()
                continue
            }
            app.iconLoader.loadUnavailable(component, info.user) { bitmap, title ->
                info.bitmap = bitmap
                if (!title.isNullOrEmpty()) info.title = title
                if (--remaining == 0) onLoaded()
            }
        }
    }

    /** IconCache.getShortcutIcon for the shortcuts that were looked up. */
    private fun loadShortcutIcons(shortcuts: Map<WorkspaceItemInfo, ShortcutInfo>, onLoaded: () -> Unit) {
        var remaining = shortcuts.size
        if (remaining == 0) {
            onLoaded()
            return
        }
        for ((info, shortcut) in shortcuts) {
            loadShortcutIcon(info, shortcut) {
                remaining--
                if (remaining == 0) onLoaded()
            }
        }
    }

    private fun loadShortcutIcon(info: WorkspaceItemInfo, shortcut: ShortcutInfo, onLoaded: () -> Unit) {
        app.iconLoader.loadShortcut(info, shortcut) { bitmap ->
            info.bitmap = bitmap
            onLoaded()
        }
    }

    /**
     * PackageUpdatedTask: apps that are no longer installed leave the home screen, and the others
     * are bound again (an app may have been suspended or unsuspended).
     */
    fun onAppsChanged() {
        if (!loaded || destroyed) return
        // ModelCallbacks.startBinding: the drag is cancelled before the model changes (rebuild).
        home.cancelDrag()
        pruneMissingApps()
        rebuild()
    }

    /**
     * ShortcutsChangedTask: shortcuts that are gone leave the home screen; the others get their
     * new labels, states and icons in place.
     */
    private fun onShortcutsChanged() {
        if (!loaded || destroyed) return
        val keys = model.shortcutKeys()
        if (keys.isEmpty()) return
        MODEL_EXECUTOR.execute {
            val found = app.shortcutRepository.loadBlocking(keys) ?: return@execute
            mainHandler.post {
                if (destroyed) return@post
                if (pruneMissing { it in keys && it !in found }) {
                    rebuild()
                    return@post
                }
                // ShortcutsChangedTask: the items get their new details and are saved with them.
                val changed = mutableListOf<Pair<WorkspaceItemInfo, ShortcutInfo>>()
                for (info in model.allItems()) {
                    if (info !is WorkspaceItemInfo || !info.isDeepShortcut) continue
                    val shortcut = found[model.keyOf(info)] ?: continue
                    info.updateFromDeepShortcutInfo(shortcut, context)
                    changed += info to shortcut
                }
                var remaining = changed.size
                for ((info, shortcut) in changed) {
                    loadShortcutIcon(info, shortcut) {
                        if (!destroyed) home.bindItemChanged(info)
                        if (--remaining == 0) model.save()
                    }
                }
            }
        }
    }

    /**
     * WidgetInflater: a widget whose provider is not installed anymore is deleted, and its id given
     * back.
     */
    private fun pruneMissingWidgets() {
        val widgetManager = AppWidgetManager.getInstance(context)
        removeItems { item ->
            (item is WidgetItemInfo && widgetManager.getAppWidgetInfo(item.appWidgetId) == null).also { missing ->
                if (missing) {
                    Log.d(TAG, "Deleting widget that isn't installed anymore: $item")
                    home.widgetHost.deleteAppWidgetId((item as WidgetItemInfo).appWidgetId)
                }
            }
        }
    }

    /**
     * WorkspaceItemProcessor.processAppOrDeepShortcut and PackageUpdatedTask for the apps whose
     * saved activity is not in the app list (Tex-yh7w):
     * - the package is enabled: the item opens the package's first launchable activity instead
     *   (PackageManagerHelper.getAppLaunchIntent), or is deleted when it has none
     * - the package is present but not available (isOnSdCard): the item stays, disabled
     *   (FLAG_DISABLED_NOT_AVAILABLE)
     * - otherwise the app is not installed, and the item is deleted
     * Returns true when something changed.
     */
    private fun pruneMissingApps(): Boolean {
        val apps = app.appRepository.apps ?: return false
        val installed = apps.associateBy { it.key }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        var retargeted = false
        val unavailable = HashSet<String>()
        for (info in model.allItems()) {
            if (info !is AppItemInfo || info.homeKey in installed) continue
            val pkg = AppEntry.componentOf(info.homeKey)?.packageName ?: continue
            if (launcherApps.isPackageEnabled(pkg, info.user)) {
                // Gracefully try to find a fallback activity.
                val fallback = launcherApps.getActivityList(pkg, info.user).firstNotNullOfOrNull { activity ->
                    apps.firstOrNull { it.component == activity.componentName && it.user == info.user }
                } ?: continue
                Log.d(TAG, "Activity not enabled for ${info.homeKey}, using ${fallback.key}")
                info.retarget(fallback)
                retargeted = true
            } else if (ApplicationInfoWrapper(context, pkg, info.user).isOnSdCard()) {
                // Package is present but not available.
                info.markUnavailable()
                unavailable += info.homeKey
            }
        }
        val removed = pruneMissing { !HomeShortcutKey.isShortcutKey(it) && it !in installed && it !in unavailable }
        if (retargeted && !removed) model.save()
        return removed || retargeted
    }

    /**
     * Removes the apps and shortcuts whose keys [isMissing] says are gone; a folder left with one
     * item turns into that item. Returns true when something changed.
     */
    private fun pruneMissing(isMissing: (String) -> Boolean): Boolean {
        val matcher = { item: ItemInfo -> model.keyOf(item)?.let(isMissing) == true }
        // DragController.onAppsRemoved and ModelCallbacks.startBinding: a drag is cancelled before
        // the model loses items, so that an item dragged out of a folder goes back into it first
        // (Tex-a8os).
        if (home.isDragging && model.allItems().any(matcher)) home.cancelDrag()
        return removeItems(matcher)
    }

    /**
     * Workspace.persistRemoveItemsByMatcher: removes the items that [matcher] matches, also in
     * folders; a folder that lost items and is left with one turns into that item
     * (Folder.removeFolderContent). Other folders are left alone: one that has a single item while
     * an item is dragged out of it is not folded (Tex-a8os), and one loaded with a single item
     * replaces itself once bound (Folder.bind). Returns true when something changed.
     */
    fun removeItems(matcher: (ItemInfo) -> Boolean): Boolean {
        var changed = false
        for (item in model.items.toList()) {
            when (item) {
                is FolderInfo -> {
                    if (!item.contents.removeAll(matcher)) continue
                    changed = true
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
                else -> if (matcher(item)) {
                    model.items.remove(item); changed = true
                }
            }
        }
        if (changed) model.save()
        return changed
    }

    private companion object {
        /** LoaderCursor.TAG */
        const val TAG = "LoaderCursor"
    }
}
