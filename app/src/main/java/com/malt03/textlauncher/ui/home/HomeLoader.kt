package com.malt03.textlauncher.ui.home

import android.os.Handler
import android.os.Looper
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.FolderInfo
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.HomeItemInfo
import com.malt03.textlauncher.data.HomeShortcutKey
import com.malt03.textlauncher.data.ShortcutItemInfo

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

    /** LoaderTask's binding: the apps' activities and the icons, then the views. */
    private fun bindItems(generation: Int) {
        val items = model.allItems()
        for (info in items) {
            if (info is AppItemInfo) app.appRepository.find(info.homeKey)?.let(info::bind)
        }
        val entries = items.mapNotNull { (it as? AppItemInfo)?.entry }
        val shortcutsToLoad = items.filterIsInstance<ShortcutItemInfo>().filter { it.shortcut != null }
        // The shortcuts' badges are their apps' icons.
        val badgeApps = shortcutsToLoad.mapNotNull { home.itemInflater.badgeAppOf(it.shortcut!!) }
        app.iconLoader.loadAll(entries + badgeApps) {
            if (generation != rebuildGeneration) return@loadAll
            loadShortcutIcons(shortcutsToLoad) {
                if (generation != rebuildGeneration) return@loadShortcutIcons
                home.bindItems(model.items)
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
            home.itemInflater.loadIcon(info) {
                remaining--
                if (remaining == 0) onLoaded()
            }
        }
    }

    /** PackageUpdatedTask: apps that are no longer installed leave the home screen. */
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
                for (view in home.grid.itemViews()) {
                    when (view) {
                        is AppIconView -> {
                            val info = view.tag as? ShortcutItemInfo ?: continue
                            info.update(found[info.homeKey] ?: continue, context)
                            home.itemInflater.bindIcon(info, view)
                        }
                        is FolderIconView -> for (info in view.contents) {
                            if (info !is ShortcutItemInfo) continue
                            info.update(found[info.homeKey] ?: continue, context)
                            home.itemInflater.loadIcon(info) { bitmap ->
                                info.bitmap = bitmap
                                view.updatePreviewItems { it === info }
                            }
                        }
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
}
