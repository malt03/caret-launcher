/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.model

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.os.UserHandle
import android.util.Log
import android.util.SparseArray
import androidx.annotation.AnyThread
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceData.MutableWorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.Executors
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.views.ActivityContext
import java.util.function.Predicate

/**
 * All the data stored in-memory and managed by the LauncherModel
 *
 * All the static data should be accessed on the background thread, A lock should be acquired on
 * this object when accessing any data from this model.
 *
 * Stand-in for Launcher3's BgDataModel (android-17.0.0_r1), made from BgDataModel.kt by deleting
 * Dagger, the dump, the widgets model, the extra items, the deep shortcut counts, the string cache
 * and the repository; changes are marked with "CaretLauncher:". Used on the model thread
 * (Executors.MODEL_EXECUTOR), as in Launcher3.
 */
class BgDataModel {

    private val mutableWorkspaceData = MutableWorkspaceData()

    /**
     * Map of all the ItemInfos (shortcuts, folders, widgets and predicted items) created by
     * LauncherModel to their ids
     */
    @JvmField val itemsIdMap: WorkspaceData = mutableWorkspaceData

    /** Id when the model was last bound */
    @JvmField var lastBindId: Int = 0

    /** Load id for which the callbacks were successfully bound */
    @JvmField var lastLoadId: Int = -1

    /** Clears all the data */
    @Synchronized
    fun clear() {
        mutableWorkspaceData.replaceDataMap(SparseArray())
    }

    @Synchronized
    fun removeItems(context: Context, items: List<ItemInfo>, owner: ModificationSource) {
        mutableWorkspaceData.modifyItems { items.forEach { remove(it.id) } }

        items
            .asSequence()
            .map { it.user }
            .distinct()
            .forEach { updateShortcutPinnedState(context, it) }
    }

    @Synchronized
    fun addItems(context: Context, items: List<ItemInfo>, owner: ModificationSource) {
        mutableWorkspaceData.modifyItems { items.forEach { put(it.id, it) } }

        items
            .filter { it.itemType == ITEM_TYPE_DEEP_SHORTCUT }
            .map { it.user }
            .distinct()
            .forEach { updateShortcutPinnedState(context, it) }
    }

    @Synchronized
    fun updateAndDispatchItem(item: ItemInfo, owner: ModificationSource) {
        mutableWorkspaceData.modifyItems { put(item.id, item) }
    }

    @Synchronized
    fun updateItems(items: List<ItemInfo>, owner: ModificationSource) {
        mutableWorkspaceData.modifyItems {}
    }

    @Synchronized
    fun dataLoadComplete(allItems: SparseArray<ItemInfo>, reason: String) {
        mutableWorkspaceData.replaceDataMap(allItems)
        dispatchRebind(reason)
    }

    @Synchronized
    fun dispatchRebind(reason: String) {
        // CaretLauncher: no repository (Flags.modelRepository is disabled in the release config).
    }

    /**
     * Updates the deep shortcuts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     *
     * CaretLauncher: the personal profile only (docs/spec.md 2.1).
     */
    fun updateShortcutPinnedState(context: Context) {
        updateShortcutPinnedState(context, android.os.Process.myUserHandle())
    }

    /**
     * Updates the deep shortcuts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     */
    @Synchronized
    fun updateShortcutPinnedState(context: Context, user: UserHandle) {
        // CaretLauncher: BuildConfig.WIDGETS_ENABLED is true.

        // Collect all system shortcuts
        val result =
            ShortcutRequest(context, user)
                .query(ShortcutRequest.PINNED or ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY)
        if (!result.wasSuccess()) {
            return
        }

        // Map of packageName to shortcutIds that are currently in the system
        val systemMap: MutableMap<String, MutableSet<String>> =
            result
                .groupingBy { it.`package` }
                .foldTo(
                    destination = mutableMapOf(),
                    initialValueSelector = { _, element -> mutableSetOf(element.id) },
                    operation = { _, accumulator, element -> accumulator.apply { add(element.id) } },
                )

        // Collect all model shortcuts
        val allWorkspaceItems =
            mutableListOf<ShortcutKey>().apply {
                updateAndCollectWorkspaceItemInfos(
                    user,
                    {
                        if (it.itemType == ITEM_TYPE_DEEP_SHORTCUT)
                            add(ShortcutKey.fromItemInfo(it))
                        // We don't care about the returned list
                        false
                    },
                )
            }
        allWorkspaceItems.addAll(
            ItemInstallQueue.INSTANCE[context].getPendingShortcuts(user).toList()
        )
        // Map of packageName to shortcutIds that are currently in our model
        val modelMap =
            allWorkspaceItems
                .groupingBy { it.packageName }
                .foldTo(
                    destination = mutableMapOf(),
                    initialValueSelector = { _, element -> mutableSetOf(element.id) },
                    operation = { _, accumulator, element -> accumulator.apply { add(element.id) } },
                )

        // Check for diff
        for ((key, modelShortcuts) in modelMap) {
            val systemShortcuts = systemMap.remove(key) ?: emptySet()

            // Do not use .equals as it can vary based on the type of set
            if (
                systemShortcuts.size != modelShortcuts.size ||
                    !systemShortcuts.containsAll(modelShortcuts)
            ) {
                // Update system state for this package
                try {
                    FileLog.d(
                        TAG,
                        "updateShortcutPinnedState: Pinning Shortcuts: $key: $modelShortcuts",
                    )
                    context
                        .getSystemService(LauncherApps::class.java)
                        ?.pinShortcuts(key, ArrayList(modelShortcuts), user)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Failed to pin shortcut", e)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Failed to pin shortcut", e)
                }
            }
        }

        // If there are any extra pinned shortcuts, remove them
        systemMap.keys.forEach { packageName: String ->
            // Update system state
            try {
                FileLog.d(
                    TAG,
                    "updateShortcutPinnedState: Unpinning extra Shortcuts for package: $packageName: ${systemMap[packageName]}",
                )
                context
                    .getSystemService(LauncherApps::class.java)
                    ?.pinShortcuts(packageName, emptyList(), user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to unpin shortcut", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to unpin shortcut", e)
            }
        }
    }

    // CaretLauncher: no deep shortcut counts (updateDeepShortcutCounts).

    /**
     * Calls the [workspaceItemOp] for all workspaceItems [widgetItemOp] for all widget items in the
     * in-memory model (both persisted items and dynamic/predicted items for the [userHandle]) and
     * returns a list of updates to be dispatched to the callbacks. Note the call is not
     * synchronized over the model, that should be handled by the caller.
     */
    @JvmOverloads
    @Synchronized
    fun updateAndCollectWorkspaceItemInfos(
        userHandle: UserHandle,
        workspaceItemOp: (WorkspaceItemInfo) -> Boolean,
        widgetItemOp: ((LauncherAppWidgetInfo) -> Boolean)? = null,
    ): List<ItemInfo> =
        itemsIdMap
            .filter {
                when {
                    it is WorkspaceItemInfo && userHandle == it.user -> workspaceItemOp.invoke(it)
                    widgetItemOp != null && it is LauncherAppWidgetInfo && userHandle == it.user ->
                        widgetItemOp.invoke(it)
                    else -> false
                }
            }
            .apply {
                // Dispatch an update
                if (isNotEmpty()) updateItems(this, ModificationSource.ModelTask)
            }

    interface Callbacks {
        // Migrated to repository
        @AnyThread
        fun bindCompleteModelAsync(itemIdMap: WorkspaceData, isBindingSync: Boolean) {
            Executors.MAIN_EXECUTOR.execute { bindCompleteModel(itemIdMap, isBindingSync) }
        }

        // Migrated to repository
        fun bindCompleteModel(itemIdMap: WorkspaceData, isBindingSync: Boolean) {}

        // Migrated to repository
        fun bindItemsAdded(items: List<@JvmSuppressWildcards ItemInfo>) {}

        // Migrated to repository
        /** Called when a runtime property of the ItemInfo is updated due to some system event */
        fun bindItemsUpdated(updates: Set<@JvmSuppressWildcards ItemInfo>) {}

        // Migrated to repository
        fun bindWorkspaceComponentsRemoved(matcher: Predicate<ItemInfo?>) {}

        // CaretLauncher: no incremental downloads (bindIncrementalDownloadProgressUpdated), widgets
        // model (bindAllWidgets) or extra items (bindExtraContainerItems).

        // Migrated to repository
        fun bindAllApplications(
            apps: Array<AppInfo>,
            flags: Int,
            packageUserKeytoUidMap: Map<PackageUserKey, Int>,
        ) {}

        // CaretLauncher: no string cache (bindStringCache).
    }

    sealed class ModificationSource {

        data class UISurface(val surface: ActivityContext) : ModificationSource()

        data object ModelTask : ModificationSource()
    }

    companion object {
        private const val TAG = "BgDataModel"
    }
}
