package com.malt03.textlauncher.data

import android.content.Context
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE
import com.android.launcher3.model.data.WorkspaceItemInfo

/*
 * The home model is Launcher3's (LauncherModel, BgDataModel): apps and app shortcuts are
 * WorkspaceItemInfos (ITEM_TYPE_APPLICATION, ITEM_TYPE_DEEP_SHORTCUT), folders FolderInfos, whose
 * contents are the items in the folder, and widgets LauncherAppWidgetInfos. These are what the
 * app asks of them.
 */

/** Whether the item is an app shortcut (ITEM_TYPE_DEEP_SHORTCUT). */
val ItemInfo.isDeepShortcut: Boolean
    get() = itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT

/** Whether the item is an app on the home screen or in a folder (ITEM_TYPE_APPLICATION). */
val ItemInfo.isApp: Boolean
    get() = this is WorkspaceItemInfo && itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION

/**
 * Whether the app is not marked unavailable (FLAG_DISABLED_NOT_AVAILABLE: its package is present
 * but not available, PackageTaskFactory.appsUnavailable and WorkspaceItemProcessor).
 */
val WorkspaceItemInfo.isAvailable: Boolean
    get() = runtimeStatusFlags and FLAG_DISABLED_NOT_AVAILABLE == 0

/** The key the app is kept by in the search (docs/spec.md 3.1, [AppKey]). */
fun WorkspaceItemInfo.homeKey(context: Context): String = AppKey.of(context, targetComponent!!, user)
