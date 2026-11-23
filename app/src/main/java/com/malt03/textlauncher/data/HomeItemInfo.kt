package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import com.android.launcher3.LauncherSettings
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
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
class AppItemInfo(val homeKey: String) : WorkspaceItemInfo() {
    var entry: AppEntry? = null
        private set

    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
    }

    constructor(entry: AppEntry, bitmap: BitmapInfo?) : this(entry.key) {
        bind(entry)
        if (bitmap != null) this.bitmap = bitmap
    }

    /**
     * The installed app, and whether it is suspended (AppInfo and WorkspaceItemProcessor:
     * FLAG_DISABLED_SUSPENDED).
     */
    fun bind(entry: AppEntry) {
        this.entry = entry
        user = entry.user
        title = entry.label
        runtimeStatusFlags = if (entry.info.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0) {
            runtimeStatusFlags or FLAG_DISABLED_SUSPENDED
        } else {
            runtimeStatusFlags and FLAG_DISABLED_SUSPENDED.inv()
        }
    }

    /** ItemInfo.mComponentName */
    override fun getComponent(): ComponentName? = entry?.component ?: AppEntry.componentOf(homeKey)
}

/** Whether the item is an app shortcut (ITEM_TYPE_DEEP_SHORTCUT). */
val ItemInfo.isDeepShortcut: Boolean
    get() = itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT

/**
 * A widget (LauncherAppWidgetInfo): its id in the home screen's widget host and its provider
 * (LauncherAppWidgetInfo.providerName, flattened).
 */
class WidgetItemInfo(val appWidgetId: Int, val provider: String) : LauncherAppWidgetInfo() {
    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
    }

    /** LauncherAppWidgetInfo.getTargetComponent: the provider. */
    override fun getTargetComponent(): ComponentName? = ComponentName.unflattenFromString(provider)

    override fun toString(): String = "WidgetItemInfo(id=$id appWidgetId=$appWidgetId provider=$provider cell=($cellX,$cellY) span=($spanX,$spanY))"
}
