package com.malt03.textlauncher.data

import android.content.ComponentName
import com.android.launcher3.LauncherSettings
import com.android.launcher3.icons.BitmapInfo
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
 * An app (WorkspaceItemInfo of ITEM_TYPE_APPLICATION), saved by [homeKey] ([AppEntry.key]).
 * Loaded from its key; [entry] is set once the installed app is found (LoaderTask's lookup of the
 * activity).
 */
class AppItemInfo(homeKey: String) : WorkspaceItemInfo() {
    /** Changes when the app's saved activity is gone and another of the package takes its place. */
    var homeKey: String = homeKey
        private set
    var entry: AppEntry? = null
        private set

    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
        // AppInfo.makeLaunchIntent: the saved intent (LauncherSettings.Favorites.INTENT).
        AppEntry.componentOf(homeKey)?.let { intent = AppInfo.makeLaunchIntent(it) }
    }

    constructor(entry: AppEntry, bitmap: BitmapInfo?) : this(entry.key) {
        bind(entry)
        if (bitmap != null) this.bitmap = bitmap
    }

    /**
     * The installed app, and its runtime flags (WorkspaceItemProcessor and PackageUpdatedTask:
     * AppInfo.updateRuntimeFlagsForActivityTarget).
     */
    fun bind(entry: AppEntry) {
        this.entry = entry
        user = entry.user
        title = entry.label
        AppInfo.updateRuntimeFlagsForActivityTarget(this, entry.info)
        // PackageUpdatedTask (OP_ADD, OP_UPDATE): the package is available again.
        runtimeStatusFlags = runtimeStatusFlags and FLAG_DISABLED_NOT_AVAILABLE.inv()
    }

    /**
     * WorkspaceItemProcessor and PackageUpdatedTask: the saved activity is gone but its package has
     * another one to launch (PackageManagerHelper.getAppLaunchIntent), which the item now opens.
     */
    fun retarget(entry: AppEntry) {
        homeKey = entry.key
        intent = AppInfo.makeLaunchIntent(entry.component)
        bind(entry)
    }

    /**
     * WorkspaceItemProcessor (isOnSdCard) and PackageUpdatedTask (OP_UNAVAILABLE): the package is
     * present but not available, so the item stays, disabled, with the icon it had.
     */
    fun markUnavailable() {
        entry = null
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
