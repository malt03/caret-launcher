package com.malt03.textlauncher.data

import android.content.ComponentName
import com.android.launcher3.LauncherSettings
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * The home model is made of Launcher3's ItemInfos, as BgDataModel's is: apps ([AppItemInfo]), app
 * shortcuts (WorkspaceItemInfo of ITEM_TYPE_DEEP_SHORTCUT, identified by ShortcutKey.fromItemInfo
 * as in Launcher3), Launcher3's FolderInfo, whose contents are the items in the folder, and
 * [WidgetItemInfo]. Each keeps its id, container, cell, span and rank in the ItemInfo fields.
 */

/**
 * An app (WorkspaceItemInfo of ITEM_TYPE_APPLICATION), saved with its launch intent and kept by
 * [homeKey] ([AppKey]). Loaded from the database; once its app is found in the app list, it gets
 * the app's title and runtime flags ([bind]).
 */
class AppItemInfo : WorkspaceItemInfo {
    private val userSerial: Long

    /** Changes when the app's saved activity is gone and another of the package takes its place. */
    val homeKey: String get() = AppKey.of(intent.component!!, userSerial)

    /** Whether the app is not marked unavailable (FLAG_DISABLED_NOT_AVAILABLE, [markUnavailable]). */
    val isAvailable: Boolean get() = runtimeStatusFlags and FLAG_DISABLED_NOT_AVAILABLE == 0

    constructor(component: ComponentName, userSerial: Long) : super() {
        this.userSerial = userSerial
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
        // AppInfo.makeLaunchIntent: the saved intent (LauncherSettings.Favorites.INTENT).
        intent = AppInfo.makeLaunchIntent(component)
    }

    /** AppInfo.makeWorkspaceItem: a new home item for the all apps list's [app]. */
    constructor(app: AppInfo, userSerial: Long) : this(app.componentName!!, userSerial) {
        bind(app)
        bitmap = app.bitmap
    }

    /** WorkspaceItemInfo(WorkspaceItemInfo), for [clone]. */
    private constructor(info: AppItemInfo) : super(info) {
        userSerial = info.userSerial
    }

    /**
     * WorkspaceItemInfo.clone, which LauncherAccessibilityDelegate.addToWorkspace adds to the home
     * screen (an app in a folder added again): still an app of the home model.
     */
    override fun clone(): WorkspaceItemInfo = AppItemInfo(this)

    /**
     * The installed app, from the app list: what WorkspaceItemInfo(AppInfo) copies, its title and
     * runtime flags (AppInfo.updateRuntimeFlagsForActivityTarget's, with the package events'). So
     * the app is available again (PackageUpdatedTask clears FLAG_DISABLED_NOT_AVAILABLE).
     */
    fun bind(app: AppInfo) {
        user = app.user
        title = Utilities.trim(app.title)
        contentDescription = app.contentDescription
        runtimeStatusFlags = app.runtimeStatusFlags
    }

    /**
     * WorkspaceItemProcessor and PackageUpdatedTask: the saved activity is gone but its package has
     * another one to launch (PackageManagerHelper.getAppLaunchIntent), which the item now opens.
     */
    fun retarget(component: ComponentName) {
        intent = AppInfo.makeLaunchIntent(component)
    }

    /**
     * WorkspaceItemProcessor (isOnSdCard) and PackageUpdatedTask (OP_UNAVAILABLE): the package is
     * present but not available, so the item stays, disabled, with the icon it had.
     */
    fun markUnavailable() {
        runtimeStatusFlags = runtimeStatusFlags or FLAG_DISABLED_NOT_AVAILABLE
    }
}

/** Whether the item is an app shortcut (ITEM_TYPE_DEEP_SHORTCUT). */
val ItemInfo.isDeepShortcut: Boolean
    get() = itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT

/**
 * A widget (LauncherAppWidgetInfo): its id in the home screen's widget host and its provider.
 */
class WidgetItemInfo(appWidgetId: Int, providerName: ComponentName) : LauncherAppWidgetInfo(appWidgetId, providerName) {
    override fun toString(): String = "WidgetItemInfo(id=$id appWidgetId=$appWidgetId provider=$providerName cell=($cellX,$cellY) span=($spanX,$spanY))"
}
