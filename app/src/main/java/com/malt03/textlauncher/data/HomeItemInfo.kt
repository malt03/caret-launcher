package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ShortcutInfo
import com.android.launcher3.LauncherSettings
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * The home model is made of Launcher3's ItemInfos, as BgDataModel's is: [AppItemInfo] and
 * [ShortcutItemInfo] (WorkspaceItemInfo), Launcher3's FolderInfo, whose contents are the items in
 * the folder, and [WidgetItemInfo]. Each keeps its id, container, cell, span and rank in the
 * ItemInfo fields. [homeKey] is the key an app or shortcut is saved by ([AppEntry.key] or
 * [HomeShortcutKey.key]).
 */
interface HomeItemInfo {
    val homeKey: String
}

/**
 * An app (WorkspaceItemInfo of ITEM_TYPE_APPLICATION). Loaded from its key; [entry] is set once the
 * installed app is found (LoaderTask's lookup of the activity).
 */
class AppItemInfo(override val homeKey: String) : WorkspaceItemInfo(), HomeItemInfo {
    var entry: AppEntry? = null
        private set

    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
    }

    constructor(entry: AppEntry, bitmap: BitmapInfo?) : this(entry.key) {
        bind(entry)
        if (bitmap != null) this.bitmap = bitmap
    }

    fun bind(entry: AppEntry) {
        this.entry = entry
        user = entry.user
        title = entry.label
    }

    /** ItemInfo.mComponentName */
    override fun getComponent(): ComponentName? = entry?.component ?: AppEntry.componentOf(homeKey)
}

/**
 * An app shortcut placed on the home screen (WorkspaceItemInfo of ITEM_TYPE_DEEP_SHORTCUT,
 * docs/spec.md 5.3). Loaded from its key; [shortcut] is set once the app's shortcut is found
 * (LoaderTask's pinned shortcut lookup).
 */
class ShortcutItemInfo(override val homeKey: String) : WorkspaceItemInfo(), HomeItemInfo {
    var shortcut: ShortcutInfo? = null
        private set

    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
    }

    constructor(homeKey: String, shortcut: ShortcutInfo, context: Context, bitmap: BitmapInfo?) : this(homeKey) {
        update(shortcut, context)
        if (bitmap != null) this.bitmap = bitmap
    }

    /** WorkspaceItemInfo(ShortcutInfo, Context) and ShortcutsChangedTask: the app's shortcut. */
    fun update(shortcut: ShortcutInfo, context: Context) {
        this.shortcut = shortcut
        user = shortcut.userHandle
        updateFromDeepShortcutInfo(shortcut, context)
    }
}

/** A widget (LauncherAppWidgetInfo). Kept in the model; not shown yet (Tex-t2ea). */
class WidgetItemInfo(val appWidgetId: Int, val provider: String) : ItemInfo() {
    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
    }
}
