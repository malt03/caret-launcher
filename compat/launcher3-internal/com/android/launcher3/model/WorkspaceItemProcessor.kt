/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.database.SQLException
import android.util.Log
import android.util.LongSparseArray
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.IconRequestInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache.UserManagerState
import com.android.launcher3.provider.LauncherDbUtils.asSequence
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.IntSparseArrayMap
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.WidgetManagerHelper

/**
 * This items is used by LoaderTask to process items that have been loaded from the Launcher's DB.
 * This data, stored in the Favorites table, needs to be processed in order to be shown on the Home
 * Page.
 *
 * This class processes each of those items: App Shortcuts, Widgets, Folders, etc., one at a time.
 */
/**
 * Stand-in for Launcher3's WorkspaceItemProcessor (android-17.0.0_r1), made from
 * WorkspaceItemProcessor.kt by deleting the restore (restored, archived and installing items and
 * widgets), the install sessions, the app pairs, the home screen files, the custom and pending
 * widgets, the automation indicator, the safe mode and the bulk icon requests' preloaded items;
 * changes are marked with "CaretLauncher:".
 */
class WorkspaceItemProcessor(
    private val c: LoaderCursor,
    private val memoryLogger: LoaderMemoryLogger?,
    // CaretLauncher: the compat UserCache's UserManagerState.
    private val userManagerState: UserManagerState,
    private val launcherApps: LauncherApps,
    private val pendingPackages: MutableSet<PackageUserKey>,
    private val shortcutKeyToPinnedShortcuts: Map<ShortcutKey, ShortcutInfo>,
    private val context: Context,
    private val idp: InvariantDeviceProfile,
    private val iconCache: IconCache,
    private val isSdCardReady: Boolean,
    private val iconRequestInfos: MutableList<IconRequestInfo<WorkspaceItemInfo>>,
    private val unlockedUsers: LongSparseArray<Boolean>,
    // CaretLauncher: no safe mode, install sessions, widget inflater, package manager helper, deep
    // shortcuts to cache, widget size handler, space finder, home screen files or automation.
) {

    private val loadedItems = IntSparseArrayMap<ItemInfo>()

    private val tempPackageKey = PackageUserKey(null, null)

    /**
     * This is the entry point for processing 1 workspace item. This method is like the midfielder
     * that delegates the actual processing to either processAppShortcut, processFolder, or
     * processWidget depending on what type of item is being processed.
     *
     * All the parameters are expected to be shared between many repeated calls of this method, one
     * for each workspace item.
     */
    fun processItem() {
        try {
            when (c.itemType) {
                Favorites.ITEM_TYPE_APPLICATION,
                Favorites.ITEM_TYPE_DEEP_SHORTCUT -> processAppOrDeepShortcut()
                // CaretLauncher: no app pairs, custom widgets or home screen files.
                Favorites.ITEM_TYPE_FOLDER -> processFolderOrAppPair()
                Favorites.ITEM_TYPE_APPWIDGET -> processWidget()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Desktop items loading interrupted", e)
        }
    }

    /**
     * This method verifies that an app shortcut should be shown on the home screen, updates the
     * database accordingly, formats the data in such a way that it is ready to be added to the data
     * model, and then adds it to the launcher’s data model.
     *
     * In this method, verification means that an an app shortcut database entry is required to:
     * Have a Launch Intent. This is how the app component symbolized by the shortcut is launched.
     * Have a Package Name. Not be in a funky “Restoring, but never actually restored” state. Not
     * have null or missing ShortcutInfos or ItemInfos in other data models.
     *
     * If any of the above are found to be true, the database entry is deleted, and not shown on the
     * user’s home screen. When an app is verified, it is marked as restored, meaning that the app
     * is viable to show on the home screen.
     *
     * In order to accommodate different types and versions of App Shortcuts, different properties
     * and flags are set on the ItemInfo objects that are added to the data model. For example,
     * icons that are not a part of the workspace or hotseat are marked as using low resolution icon
     * bitmaps. Currently suspended app icons are marked as such. Installing packages are also
     * marked as such. Lastly, after applying common properties to the ItemInfo, it is added to the
     * data model to be bound to the launcher’s data model.
     */
    @SuppressLint("NewApi")
    private fun processAppOrDeepShortcut() {
        var allowMissingTarget = false
        var intent = c.parseIntent()
        if (intent == null) {
            c.markDeleted("Null intent from db for item id=${c.id}", "app_no_db_intent")
            return
        }
        var disabledState =
            // CaretLauncher: the compat UserManagerState asks by user.
            if (userManagerState.isUserQuiet(c.user))
                WorkspaceItemInfo.FLAG_DISABLED_QUIET_USER
            else 0
        val cn = intent.component
        val targetPkg = cn?.packageName ?: intent.getPackage()
        if (targetPkg.isNullOrEmpty()) {
            c.markDeleted("No target package for item id=${c.id}", "app_no_target_package")
            return
        }
        val appInfoWrapper = ApplicationInfoWrapper(context, targetPkg, c.user)
        var validTarget = launcherApps.isPackageEnabled(targetPkg, c.user)

        // If it's a deep shortcut, we'll use pinned shortcuts to restore it
        if (cn != null && validTarget && (c.itemType != Favorites.ITEM_TYPE_DEEP_SHORTCUT)) {
            // If the apk is present and the shortcut points to a specific component.

            // If the component is already present
            val isActivityEnabled =
                try {
                    launcherApps.isActivityEnabled(cn, c.user)
                } catch (exception: Exception) {
                    FileLog.w(TAG, "Error checking activity enabled for component:$cn", exception)
                    false
                }
            if (isActivityEnabled) {
                // no special handling necessary for this item
                c.markRestored()
            } else {
                // Gracefully try to find a fallback activity.
                FileLog.d(
                    TAG,
                    "Activity not enabled for id=${c.id}, component=$cn, user=${c.user}." +
                        " Will attempt to find fallback Activity for targetPkg=$targetPkg.",
                )
                tempPackageKey.update(targetPkg, c.user)
                // CaretLauncher: PackageManagerHelper.getAppLaunchIntent, the package's first
                // launchable activity.
                val newIntent =
                    launcherApps.getActivityList(targetPkg, c.user).firstOrNull()?.let {
                        AppInfo.makeLaunchIntent(it)
                    }
                if (newIntent != null) {
                    intent = newIntent
                    c.restoreFlag = 0
                    c.updater().put(Favorites.INTENT, intent.toUri(0)).commit()
                } else {
                    // CaretLauncher: no install sessions to keep a promise icon for.
                    c.markDeleted(
                        "No Activities or install sessions found for id=${c.id}," +
                            " targetPkg=$targetPkg, component=$cn." +
                            " Unable to create launch Intent.",
                        "app_no_launch_intent",
                    )
                    return
                }
            }
        }
        if (intent.`package` == null) {
            intent.`package` = targetPkg
        }

        // CaretLauncher: no archived apps (isPreArchivedShortcut).

        // else if cn == null => can't infer much, leave it
        // else if !validPkg => could be restored icon or missing sd-card
        when {
            !targetPkg.isNullOrEmpty() && !validTarget -> {
                // Points to a valid app (superset of cn != null) but the apk
                // is not available.
                when {
                    // CaretLauncher: no restored items (restoreFlag), which wait for their package.
                    appInfoWrapper.isOnSdCard() -> {
                        // Package is present but not available.
                        disabledState =
                            disabledState or WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE
                        // Add the icon on the workspace anyway.
                        allowMissingTarget = true
                    }
                    !isSdCardReady -> {
                        // SdCard is not ready yet. Package might get available,
                        // once it is ready.
                        Log.d(TAG, "Missing package, will check later: $targetPkg")
                        pendingPackages.add(PackageUserKey(targetPkg, c.user))
                        // Add the icon on the workspace anyway.
                        allowMissingTarget = true
                    }
                    else -> {
                        // Do not wait for external media load anymore.
                        c.markDeleted("Invalid package removed: $targetPkg", "app_not_installed_external_media")
                        return
                    }
                }
            }
        }
        // CaretLauncher: no shortcuts with a web UI (FLAG_SUPPORTS_WEB_UI).
        if (validTarget) {
            FileLog.d(
                TAG,
                "valid target true, marking restored: $targetPkg," +
                    " itemType=${c.itemType}, restoreFlag=${c.restoreFlag}",
            )
            // The shortcut points to a valid target (either no target
            // or something which is ready to be used)
            c.markRestored()
        }
        val useLowResIcon = !c.isOnWorkspaceOrHotseat
        val info: WorkspaceItemInfo?
        when {
            // CaretLauncher: no restored items (getRestoredItemInfo).
            c.itemType == Favorites.ITEM_TYPE_APPLICATION ->
                info = c.getAppShortcutInfo(intent, allowMissingTarget, useLowResIcon, false)
            c.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT -> {
                val key = ShortcutKey.fromIntent(intent, c.user)
                if (unlockedUsers[c.serialNumber]) {
                    val pinnedShortcut =
                        shortcutKeyToPinnedShortcuts[key] ?: retryDeepShortcutById(key)
                    if (pinnedShortcut == null) {
                        // The shortcut is no longer valid.
                        c.markDeleted(
                            "Pinned shortcut not found from request. package=${key.packageName}, user=${c.user}",
                            "shortcut_not_found",
                        )
                        return
                    }
                    info = WorkspaceItemInfo(pinnedShortcut, context)
                    // If the pinned deep shortcut is no longer published,
                    // use the last saved icon instead of the default.
                    // CaretLauncher: IconCache.getShortcutIcon with the ShortcutInfo (no
                    // CacheableShortcutInfo), and the saved icon when it gives the default one.
                    iconCache.getShortcutIcon(info, pinnedShortcut, appInfoWrapper, DEFAULT_LOOKUP_FLAG.withThemeIcon())
                    if (iconCache.isDefaultIcon(info.bitmap, info.user)) {
                        c.loadIconFromDb(info)
                    }
                    if (appInfoWrapper.isSuspended()) {
                        info.runtimeStatusFlags =
                            info.runtimeStatusFlags or ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
                    }
                    intent = info.getIntent()
                    // CaretLauncher: no deep shortcuts to cache (allDeepShortcuts).
                } else {
                    // Create a shortcut info in disabled mode for now.
                    info = c.loadSimpleWorkspaceItem()
                    info.runtimeStatusFlags =
                        info.runtimeStatusFlags or ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER
                }
            }
            else -> { // item type == ITEM_TYPE_SHORTCUT
                info = c.loadSimpleWorkspaceItem()

                // Shortcuts are only available on the primary profile
                if (appInfoWrapper.isSuspended()) {
                    disabledState = disabledState or ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
                }
                // CaretLauncher: WorkspaceItemInfo has no options.

                // App shortcuts that used to be automatically added to Launcher
                // didn't always have the correct intent flags set, so do that here
                if (
                    intent.action != null &&
                        intent.categories != null &&
                        intent.action == Intent.ACTION_MAIN &&
                        intent.categories.contains(Intent.CATEGORY_LAUNCHER)
                ) {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                }
            }
        }
        if (info != null) {
            if (info.itemType != Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                // Skip deep shortcuts; their title and icons have already been
                // loaded above.
                iconRequestInfos.add(c.createIconRequestInfo(info, useLowResIcon))
            }
            c.applyCommonProperties(info)
            info.intent = intent
            info.rank = c.rank
            info.spanX = 1
            info.spanY = 1
            info.runtimeStatusFlags = info.runtimeStatusFlags or disabledState
            // CaretLauncher: no safe mode or automation indicator.
            val activityInfo = c.launcherActivityInfo
            if (activityInfo != null) {
                // CaretLauncher: without the user info, API wrapper, package manager helper and
                // automation repository.
                AppInfo.updateRuntimeFlagsForActivityTarget(info, activityInfo)
            }
            // CaretLauncher: no install sessions (FLAG_INSTALL_SESSION_ACTIVE, progress).
            c.checkAndAddItem(info, loadedItems, memoryLogger)
        } else {
            throw RuntimeException("Unexpected null WorkspaceItemInfo")
        }
    }

    /**
     * It is possible that the data was cleared from ShortcutManager after it was restored. In that
     * instance, the Launcher would have a valid Shortcut id, but ShortcutManager wouldn't recognize
     * it as valid. Here we retry by querying ShortcutManager by package name and shortcut id.
     */
    private fun retryDeepShortcutById(key: ShortcutKey): ShortcutInfo? {
        FileLog.d(TAG, "retryDeepShortcutById: package=${key.packageName}, shortcutId=${key.id}")
        return launcherApps
            .getShortcuts(
                ShortcutQuery().apply {
                    setPackage(key.packageName)
                    setShortcutIds(listOf(key.id))
                    setQueryFlags(ShortcutRequest.ALL)
                },
                key.user,
            )
            ?.firstOrNull()
    }

    /**
     * Loads CollectionInfo information from the database and formats it. This function runs while
     * LoaderTask is still active; some of the processing for folder content items is done after all
     * the items in the workspace have been loaded. The loaded and formatted CollectionInfo is then
     * stored in the BgDataModel.
     */
    private fun processFolderOrAppPair() {
        val collection = c.findOrMakeFolder(c.id, loadedItems)
        // CaretLauncher: no app pairs.

        c.applyCommonProperties(collection)
        // Do not trim the folder label, as is was set by the user.
        collection.title = c.title
        collection.spanX = 1
        collection.spanY = 1
        // CaretLauncher: no app pairs, which keep their rank.
        collection.options = c.options

        c.markRestored()
        c.checkAndAddItem(collection, loadedItems, memoryLogger)
    }

    /**
     * This method, similar to processAppShortcut above, verifies that a widget should be shown on
     * the home screen, updates the database accordingly, formats the data in such a way that it is
     * ready to be added to the data model, and then adds it to the launcher’s data model.
     *
     * It verifies that: Widgets are not disabled due to the Launcher variety being of the `Go`
     * type. Search Widgets have a package name. The app behind the widget is still installed on the
     * device. The app behind the widget is not in a funky “Restoring, but never actually restored”
     * state. The widget has a valid size. The widget is in the workspace or the hotseat. If any of
     * the above are found to be true, the database entry is deleted, and the widget is not shown on
     * the user’s home screen. When a widget is verified, it is marked as restored, meaning that the
     * widget is viable to show on the home screen.
     *
     * Common properties are applied to the Widget’s Info object, and other information as well
     * depending on the type of widget. Custom widgets are treated differently than non-custom
     * widgets, installing / restoring widgets are treated differently, etc.
     */
    private fun processWidget() {
        val component = ComponentName.unflattenFromString(c.appWidgetProvider!!)!!
        val appWidgetInfo = LauncherAppWidgetInfo(c.appWidgetId, component)
        c.applyCommonProperties(appWidgetInfo)
        appWidgetInfo.spanX = c.spanX
        appWidgetInfo.spanY = c.spanY
        appWidgetInfo.options = c.options
        appWidgetInfo.user = c.user
        appWidgetInfo.sourceContainer = c.appWidgetSource
        appWidgetInfo.restoreStatus = c.restoreFlag
        if (appWidgetInfo.spanX <= 0 || appWidgetInfo.spanY <= 0) {
            c.markDeleted(
                "processWidget: Widget has invalid size: ${appWidgetInfo.spanX}x${appWidgetInfo.spanY}" +
                    ", id=${c.id}," +
                    ", appWidgetId=${c.appWidgetId}," +
                    ", component=${component}",
                "invalid_widget_size",
            )
            return
        }
        if (!c.isOnWorkspaceOrHotseat) {
            c.markDeleted(
                "processWidget: invalid Widget container != CONTAINER_DESKTOP nor CONTAINER_HOTSEAT." +
                    " id=${c.id}," +
                    ", appWidgetId=${c.appWidgetId}," +
                    ", component=${component}," +
                    ", container=${c.container}",
                "invalid_widget_container",
            )
            return
        }
        // CaretLauncher: no restored widgets (FLAG_DIRECT_CONFIG) or WidgetInflater; a widget whose
        // provider is not found is deleted (WidgetInflater.TYPE_DELETE), and its size ranges are
        // updated when its view is made.
        val lapi = WidgetManagerHelper(context).getLauncherAppWidgetInfo(appWidgetInfo.appWidgetId, component)
        appWidgetInfo.contentDescription = lapi?.loadLabel(context.packageManager)
        if (lapi != null) {
            appWidgetInfo.updateWidgetFeatures(lapi)
        }

        FileLog.d(
            TAG,
            "processWidget: id=${c.id}" + ", appWidgetId=${c.appWidgetId}" + ", provider=$lapi",
        )
        if (lapi == null) {
            c.markDeleted(
                "processWidget: Removing widget with invalid provider: id=${c.id}" +
                    ", appWidgetId=${c.appWidgetId}" +
                    ", component=${component}",
                "missing_widget_provider",
            )
            return
        }
        if (appWidgetInfo.spanX < lapi.minSpanX || appWidgetInfo.spanY < lapi.minSpanY) {
            FileLog.d(
                TAG,
                " processWidget: Widget ${lapi.component} minSizes not met: span=${appWidgetInfo.spanX}x${appWidgetInfo.spanY} minSpan=${lapi.minSpanX}x${lapi.minSpanY}," +
                    " id: ${c.id}," +
                    " appWidgetId: ${c.appWidgetId}," +
                    " component=${component}",
            )
        }
        c.checkAndAddItem(appWidgetInfo, loadedItems, memoryLogger)
    }

    // CaretLauncher: no home screen files (processFileSystemItem).

    private fun processFolderItems() {
        // Sort the folder items, update ranks, and make sure all preview items are high res.
        val verifiers = idp.supportedProfiles.map { createFolderGridOrganizer(it) }
        for (itemInfo in loadedItems) {
            if (itemInfo !is FolderInfo) {
                continue
            }

            itemInfo.getContents().sortWith(Folder.ITEM_POS_COMPARATOR)
            verifiers.forEach { it.setFolderInfo(itemInfo) }

            // Update ranks here to ensure there are no gaps caused by removed folder items.
            // Ranks are the source of truth for folder items, so cellX and cellY can be
            // ignored for now. Database will be updated once user manually modifies folder.
            itemInfo.getContents().forEachIndexed { rank, info ->
                info.rank = rank
                if (
                    info is WorkspaceItemInfo &&
                        info.matchingLookupFlag.isVisuallyLessThan(Favorites.DESKTOP_ICON_FLAG) &&
                        info.itemType == Favorites.ITEM_TYPE_APPLICATION &&
                        verifiers.any { it.isItemInPreview(info.rank) }
                ) {
                    iconCache.getTitleAndIcon(info, Favorites.DESKTOP_ICON_FLAG)
                }
            }
        }
    }

    /**
     * Applies any queued update data update tasks and data sanity checks and returns the final set
     * of workspace data. This includes:
     * 1) Loading any additional model data, not coming from the DB (including file system items)
     * 2) Sanity checks on folder and app pair: removing empty and single item folders, and sorting
     *    contents
     * 3) Committing any persistent modifications and deletions to the storage
     */
    fun finalizeData(
        // CaretLauncher: no model delegate.
        modelDbController: ModelDbController,
    ): IntSparseArrayMap<ItemInfo> {

        // Remove dead items
        val itemsDeleted = c.commitDeleted()

        processFolderItems()
        // CaretLauncher: no app pairs or restored items (commitRestoredItems).
        if (itemsDeleted) {
            // Remove any empty folder
            modelDbController.deleteItemsBasedOnItemIdQuery(EMPTY_FOLDER_QUERY)
        }
        // CaretLauncher: no app pairs (BAD_APP_PAIR_QUERY).

        // Deletes any app with a container id that doesn't exist.
        modelDbController.deleteItemsBasedOnItemIdQuery(ORPHAN_APPS_QUERY)

        return loadedItems
    }

    private fun ModelDbController.deleteItemsBasedOnItemIdQuery(selection: String) {
        try {
            val table = getTable()
            table.newTransaction().use { t ->
                val itemIds =
                    table.query(arrayOf(_ID), selection) { asSequence().map { getInt(0) }.toList() }
                if (itemIds.isNotEmpty()) table.delete("$_ID IN ( ${itemIds.joinToString()} )")
                t.commit()
                itemIds.forEach { loadedItems.remove(it) }
            }
        } catch (ex: SQLException) {
            Log.e(TAG, ex.message, ex)
            return
        }
    }

    // CaretLauncher: no preloaded items (processPreloadedItems).

    companion object {
        private const val TAG = "WorkspaceItemProcessor"

        // language=sql
        private const val EMPTY_FOLDER_QUERY =
            "$ITEM_TYPE = $ITEM_TYPE_FOLDER AND $_ID NOT IN (SELECT $CONTAINER FROM $TABLE_NAME)"
        private const val ORPHAN_APPS_QUERY =
            "$CONTAINER >= 0 AND $CONTAINER NOT IN (SELECT $_ID FROM $TABLE_NAME )"
        // CaretLauncher: no app pairs (BAD_APP_PAIR_QUERY).

        // CaretLauncher: no widget size logging (logWidgetInfo).
    }
}
