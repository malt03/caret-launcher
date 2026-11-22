package com.malt03.textlauncher.ui.home

import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.GridOccupancy
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
    private var rebuildGeneration = 0
    private val shortcutsListener: () -> Unit = ::onShortcutsChanged

    fun start() {
        app.shortcutRepository.addChangeListener(shortcutsListener)
        app.ioExecutor.execute {
            val items = model.loadBlocking()
            mainHandler.post {
                model.setLoaded(items)
                loaded = true
                // LoaderTask (ModelDbController): the grid is migrated before the items are read.
                if (GridSizeMigration.migrateGridIfNeeded(context, home.gridSpec, model.items)) model.save()
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
        app.iconLoader.loadAll(entries, DESKTOP_ICON_FLAG) {
            if (generation != rebuildGeneration) return@loadAll
            loadShortcutIcons(shortcuts) {
                if (generation != rebuildGeneration) return@loadShortcutIcons
                app.iconLoader.loadDefault { default ->
                    if (generation != rebuildGeneration) return@loadDefault
                    for (info in unknown) if (info.bitmap.isLowRes) info.bitmap = default
                    home.bindItems(model.items)
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
        if (!loaded) return
        pruneMissingApps()
        rebuild()
    }

    /**
     * ShortcutsChangedTask: shortcuts that are gone leave the home screen; the others get their
     * new labels, states and icons in place.
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
                        home.bindItemChanged(info)
                        if (--remaining == 0) model.save()
                    }
                }
            }
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
    private fun pruneMissing(isMissing: (String) -> Boolean): Boolean =
        removeItems { item -> model.keyOf(item)?.let(isMissing) == true }

    /**
     * Workspace.persistRemoveItemsByMatcher: removes the items that [matcher] matches, also in
     * folders; a folder left with one item turns into that item. Returns true when something
     * changed.
     */
    fun removeItems(matcher: (ItemInfo) -> Boolean): Boolean {
        var changed = false
        for (item in model.items.toList()) {
            when (item) {
                is FolderInfo -> {
                    if (item.contents.removeAll(matcher)) changed = true
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
