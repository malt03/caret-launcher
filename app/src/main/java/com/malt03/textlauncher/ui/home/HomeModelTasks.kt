package com.malt03.textlauncher.ui.home

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.ItemInfoMatcher
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.PackageEvent
import java.util.function.Predicate

/**
 * Launcher3's model tasks that follow the apps, their icons and their shortcuts on the home screen,
 * changing and binding only the items they concern (no full rebind): PackageUpdatedTask and
 * PackageTaskFactory (the package events of ModelLauncherCallbacks), CacheDataUpdatedTask (icons
 * updated in the cache) and ShortcutsChangedTask, with ModelTaskController's
 * bindUpdatedWorkspaceItems and deleteAndBindComponentsRemoved.
 *
 * TextLauncher: the model is on the main thread, where these run; the app list (AllAppsList) is
 * [com.malt03.textlauncher.data.AppRepository], whose list and icon cache already reflect the
 * package event, and the icons are looked up with AppIconLoader (asynchronously, then written and
 * bound). Only the personal profile (docs/spec.md 2.1).
 */
internal class HomeModelTasks(private val home: HomeController, private val isDestroyed: () -> Boolean) {
    private val context = home.context
    private val app = home.app
    private val model = home.model

    /** ModelLauncherCallbacks: the task for [event]. */
    fun execute(event: PackageEvent) {
        when (event.op) {
            PackageEvent.OP_ADD -> packageUpdated(false, event.user, event.packages)
            PackageEvent.OP_UPDATE -> packageUpdated(true, event.user, event.packages)
            PackageEvent.OP_REMOVE -> appsRemoved(event.user, event.packages)
            PackageEvent.OP_UNAVAILABLE -> appsUnavailable(event.user, event.packages)
            PackageEvent.OP_SUSPEND -> updateRuntimeStatus(event.user, event.packages) { it or FLAG_DISABLED_SUSPENDED }
            PackageEvent.OP_UNSUSPEND -> updateRuntimeStatus(event.user, event.packages) { it and FLAG_DISABLED_SUSPENDED.inv() }
        }
    }

    /** PackageUpdatedTask.execute */
    private fun packageUpdated(isUpdate: Boolean, user: UserHandle, packageNames: Set<String>) {
        val packages = packageNames.toMutableSet()
        if (isUpdate) {
            // Mark disabled packages in the broadcast to be removed
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val disabledPackages = packages.filter { !launcherApps.isPackageEnabled(it, user) }
            if (disabledPackages.isNotEmpty()) {
                packages.removeAll(disabledPackages.toSet())
                appsRemoved(user, disabledPackages.toSet())
            }
        }

        // TextLauncher: the icon cache (IconCache.updateIconsForPkg) and the app list
        // (AllAppsList.updatePackage) were updated before this task.
        val apps = app.appRepository.apps ?: return

        val removedShortcuts = HashSet<Int>()

        // Update shortcut infos
        val updatedItems = model.bgDataModel.updateAndCollectWorkspaceItemInfos(user, { itemInfo ->
            val cn = itemInfo.targetComponent ?: return@updateAndCollectWorkspaceItemInfos false
            val packageName = cn.packageName
            if (!packages.contains(packageName)) return@updateAndCollectWorkspaceItemInfos false

            if (itemInfo.itemType != ITEM_TYPE_APPLICATION && itemInfo.itemType != ITEM_TYPE_DEEP_SHORTCUT) {
                Log.d(TAG, "Ignoring unknown item-type: $itemInfo")
                // Should it be deleted?
                return@updateAndCollectWorkspaceItemInfos false
            }

            if (itemInfo.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
                // Deep shortcuts are handled via shortcut update task. TextLauncher: no promise
                // shortcuts.
                return@updateAndCollectWorkspaceItemInfos false
            }

            if (itemInfo !is AppItemInfo) return@updateAndCollectWorkspaceItemInfos false

            val activityList = apps.filter { it.component.packageName == packageName && it.user == user }

            var activityInfo = activityList.firstOrNull { it.component == cn }

            if (activityInfo == null) {
                if (activityList.isNotEmpty()) {
                    // First activity is considered the default activity,
                    // similar to PackageManagerHelper.getAppLaunchInfo
                    activityInfo = activityList[0]
                    // TextLauncher: AppItemInfo.retarget sets the intent (AppInfo.makeLaunchIntent).
                    itemInfo.retarget(activityInfo)
                } else {
                    Log.e(TAG, "Removing shortcut with invalid target component.$itemInfo")
                    removedShortcuts.add(itemInfo.id)
                    return@updateAndCollectWorkspaceItemInfos false
                }
            }

            // TextLauncher: AppItemInfo.bind clears FLAG_DISABLED_NOT_AVAILABLE and does
            // AppInfo.updateRuntimeFlagsForActivityTarget; the icon is looked up below
            // (IconCache.getTitleAndIcon) and the item written once it is set.
            itemInfo.bind(activityInfo)
            true
        })

        loadIconsWriteAndBind(updatedItems, write = true)
        if (removedShortcuts.isNotEmpty()) {
            deleteAndBindComponentsRemoved(
                { it.id in removedShortcuts },
                "removing shortcuts with invalid target components. ids=$removedShortcuts",
            )
        }
        // TextLauncher: no widgets model to load for a new package (the widget picker reads the
        // installed providers itself).
    }

    /** PackageTaskFactory.appsRemoved */
    private fun appsRemoved(user: UserHandle, packages: Set<String>) {
        // TextLauncher: the icons and the app list were updated before this task.
        val matcher = ItemInfoMatcher.ofPackages(packages, user)
        // TextLauncher: no shortcuts with a web UI to keep.
        deleteAndBindComponentsRemoved(
            matcher::test,
            "removed because the corresponding package is removed. removedPackages=$packages",
        )
        // TextLauncher: no install queue yet (Tex-iuyb).
    }

    /** PackageTaskFactory.appsUnavailable */
    private fun appsUnavailable(user: UserHandle, packages: Set<String>) {
        // TextLauncher: an app also forgets its activity, which is gone from the app list
        // (AppItemInfo.markUnavailable, as when it is loaded unavailable).
        val updatedItems = model.bgDataModel.updateAndCollectWorkspaceItemInfos(user, { itemInfo ->
            val oldRuntimeFlags = itemInfo.runtimeStatusFlags
            if (packages.contains(itemInfo.targetPackage)) {
                if (itemInfo is AppItemInfo) {
                    itemInfo.markUnavailable()
                } else {
                    itemInfo.runtimeStatusFlags = itemInfo.runtimeStatusFlags or FLAG_DISABLED_NOT_AVAILABLE
                }
            }
            itemInfo.runtimeStatusFlags != oldRuntimeFlags
        })
        bindUpdatedWorkspaceItems(updatedItems)
    }

    /**
     * PackageTaskFactory.updateRuntimeStatus: updates matching workspace items with the provided
     * [updateOp]
     */
    private fun updateRuntimeStatus(user: UserHandle, packages: Set<String>, updateOp: (Int) -> Int) {
        // Update workspace items, widget suspension is handled by the platform itself
        val updatedItems = model.bgDataModel.updateAndCollectWorkspaceItemInfos(user, { itemInfo ->
            val oldRuntimeFlags = itemInfo.runtimeStatusFlags
            if (packages.contains(itemInfo.targetPackage)) {
                itemInfo.runtimeStatusFlags = updateOp(itemInfo.runtimeStatusFlags)
            }
            itemInfo.runtimeStatusFlags != oldRuntimeFlags
        })

        bindUpdatedWorkspaceItems(updatedItems)
    }

    /** CacheDataUpdatedTask (OP_CACHE_UPDATE): the apps of [packages] get their new icons. */
    fun cacheDataUpdated(user: UserHandle, packages: Set<String>) {
        val updatedItems = model.bgDataModel.updateAndCollectWorkspaceItemInfos(user, { si ->
            val cn = si.targetComponent
            si.itemType == ITEM_TYPE_APPLICATION && cn != null && packages.contains(cn.packageName)
        })
        loadIconsWriteAndBind(updatedItems, write = false)
    }

    /** ShortcutsChangedTask.execute */
    fun shortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) {
        val itemFilter: (WorkspaceItemInfo) -> Boolean = {
            it.itemType == ITEM_TYPE_DEEP_SHORTCUT && packageName == it.targetPackage
        }

        // Find WorkspaceItemInfo's that have changed on the workspace.
        val matchingShortcutIds = mutableSetOf<String>()
        model.bgDataModel.updateAndCollectWorkspaceItemInfos(user, {
            if (itemFilter.invoke(it)) it.deepShortcutId?.let(matchingShortcutIds::add)

            // We don't care about the returned list
            false
        })

        if (matchingShortcutIds.isEmpty()) return
        // TextLauncher: the system is asked on the model thread, and the model is changed back on
        // the main thread.
        MODEL_EXECUTOR.execute {
            val infoWrapper = ApplicationInfoWrapper(context, packageName, user)
            if (shortcuts.isEmpty()) {
                // Verify that the app is indeed installed.
                if (!infoWrapper.isInstalled() || infoWrapper.isArchived()) {
                    // App is not installed or is archived, ignoring package events
                    return@execute
                }
            }
            // Update the workspace to reflect the changes to updated shortcuts residing on it.
            val result = ShortcutRequest(context, user)
                .forPackage(packageName, matchingShortcutIds.toList())
                .query(ShortcutRequest.ALL)
            // TextLauncher: when the system could not be asked, nothing is known to be gone
            // (LoaderTask treats a failed query the same).
            if (!result.wasSuccess()) return@execute
            val pinnedShortcuts: Map<String, ShortcutInfo> = result.associateBy { it.id }
            MAIN_EXECUTOR.execute { applyShortcuts(packageName, user, matchingShortcutIds, pinnedShortcuts, itemFilter) }
        }
    }

    private fun applyShortcuts(
        packageName: String,
        user: UserHandle,
        matchingShortcutIds: Set<String>,
        pinnedShortcuts: Map<String, ShortcutInfo>,
        itemFilter: (WorkspaceItemInfo) -> Boolean,
    ) {
        val nonPinnedIds = matchingShortcutIds.toMutableSet()
        val details = HashMap<WorkspaceItemInfo, ShortcutInfo>()
        val updatedWorkspaceItemInfos = model.bgDataModel.updateAndCollectWorkspaceItemInfos(user, {
            if (!itemFilter.invoke(it)) return@updateAndCollectWorkspaceItemInfos false
            val shortcutId = it.deepShortcutId ?: return@updateAndCollectWorkspaceItemInfos false
            val fullDetails = pinnedShortcuts[shortcutId] ?: return@updateAndCollectWorkspaceItemInfos false

            nonPinnedIds.remove(shortcutId)
            it.updateFromDeepShortcutInfo(fullDetails, context)
            // TextLauncher: the icon is looked up below (IconCache.getShortcutIcon).
            details[it] = fullDetails
            true
        })

        // TextLauncher: written and bound once their icons are set (bindUpdatedWorkspaceItems
        // writes them with ModelWriter.updateItemInDatabase).
        var remaining = details.size
        for ((info, shortcut) in details) {
            app.iconLoader.loadShortcut(info, shortcut) { bitmap ->
                info.bitmap = bitmap
                if (--remaining == 0) {
                    for (item in updatedWorkspaceItemInfos) if (model.contains(item)) model.writer.updateItemInDatabase(item)
                    bindUpdatedWorkspaceItems(updatedWorkspaceItemInfos)
                }
            }
        }
        if (nonPinnedIds.isNotEmpty()) {
            val keys = nonPinnedIds.mapTo(mutableSetOf()) { ShortcutKey(packageName, user, it) }
            deleteAndBindComponentsRemoved(
                ItemInfoMatcher.ofShortcutKeys(keys)::test,
                "removed because the shortcut is no longer available in shortcut service",
            )
        }
        // TextLauncher: no deep shortcut counts (the long-press menu asks the system itself).
    }

    /**
     * IconCache.getTitleAndIcon for the apps among [items] (asynchronously in TextLauncher), then
     * the items are written ([write]) and bound.
     */
    private fun loadIconsWriteAndBind(items: List<ItemInfo>, write: Boolean) {
        if (items.isEmpty()) return
        val apps = items.filterIsInstance<AppItemInfo>().filter { it.entry != null }
        app.iconLoader.loadAll(apps.mapNotNull { it.entry }, DESKTOP_ICON_FLAG) {
            for (info in apps) info.entry?.let(app.iconLoader::cached)?.let { info.bitmap = it }
            if (write) {
                for (item in items) if (model.contains(item)) model.writer.updateItemInDatabase(item)
            }
            bindUpdatedWorkspaceItems(items)
        }
    }

    /** ModelTaskController.bindUpdatedWorkspaceItems */
    private fun bindUpdatedWorkspaceItems(items: List<ItemInfo>) {
        // Bind workspace items
        if (items.isEmpty() || isDestroyed()) return
        home.bindItemsUpdated(items.toSet())
    }

    /** ModelTaskController.deleteAndBindComponentsRemoved */
    private fun deleteAndBindComponentsRemoved(matcher: (ItemInfo) -> Boolean, reason: String) {
        val predicate = Predicate<ItemInfo> { it != null && matcher(it) }
        // TextLauncher: a drag of an affected item is cancelled before the model changes
        // (DragController.onAppsRemoved comes after in Launcher3), so that an item dragged out of
        // a folder goes back first (Tex-a8os).
        if (!isDestroyed()) home.cancelDragIfAffected(predicate)
        model.writer.deleteItemsFromDatabase({ it != null && matcher(it) }, reason)
        if (!isDestroyed()) home.bindWorkspaceComponentsRemoved(predicate)
    }

    private companion object {
        const val TAG = "PackageUpdatedTask"
    }
}
