package com.malt03.textlauncher.ui.home

import android.content.pm.ShortcutInfo
import android.view.View
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.ShortcutItemInfo

/**
 * ItemInflater and ItemClickHandler of the home screen: makes the views of the home model's items,
 * with their icons (loaded as Launcher3's IconCache does for LoaderTask), and handles their
 * clicks.
 */
internal class HomeItemInflater(private val home: HomeController) {
    private val context = home.context
    private val app = home.app
    private val grid = home.grid

    /** The app whose icon badges an app shortcut (IconCache.getShortcutInfoBadge). */
    fun badgeAppOf(shortcut: ShortcutInfo): AppEntry? =
        app.appRepository.apps?.let { app.shortcutRepository.appOf(shortcut, it) }

    /** The icon of an app or shortcut that was found (LoaderTask); nothing for one that was not. */
    fun loadIcon(info: WorkspaceItemInfo, onLoaded: (BitmapInfo) -> Unit) {
        when (info) {
            is AppItemInfo -> info.entry?.let { app.iconLoader.load(it, onLoaded) }
            is ShortcutItemInfo -> info.shortcut?.let { app.iconLoader.loadShortcut(info.homeKey, it, badgeAppOf(it), onLoaded) }
        }
    }

    /** BubbleTextView.applyFromWorkspaceItem: the icon, now or once it is loaded. */
    fun bindIcon(info: WorkspaceItemInfo, view: AppIconView) {
        loadIcon(info) { bitmap ->
            info.bitmap = bitmap
            view.iconDrawable = app.iconLoader.newDrawable(bitmap)
        }
    }

    /** ItemClickHandler.onClick: starts the app or the app shortcut. */
    fun onClick(info: WorkspaceItemInfo, view: View) {
        val host = home.host
        when (info) {
            is AppItemInfo -> info.entry?.let { host.launch(it, view) }
            is ShortcutItemInfo -> info.shortcut?.let { host.startShortcut(it.`package`, it.id, view, info.user) }
            // A shortcut row of the long-press menu (onClickAppShortcut).
            else -> {
                val id = info.deepShortcutId ?: return
                val packageName = info.targetPackage ?: return
                host.startShortcut(packageName, id, view, info.user)
            }
        }
    }

    /** ItemInflater.inflateItem: the item's view, or null for an item that cannot be shown. */
    fun inflateItem(item: ItemInfo): View? = when (item) {
        is AppItemInfo -> if (item.entry != null) createItemView(item) else null
        is ShortcutItemInfo -> if (item.shortcut != null) createItemView(item) else null
        is FolderInfo -> createFolderView(item)
        else -> null // TODO(Tex-t2ea): widgets
    }

    private fun createItemView(info: WorkspaceItemInfo): AppIconView =
        AppIconView(context, home.gridSpec.iconSizePx).apply {
            bindIcon(info, this)
            // ItemInflater: the item's focus listener is Launcher.getFocusHandler.
            setFocusableAsLauncher3Icon(grid.focusIndicatorHelper)
            setOnClickListener { onClick(info, this) }
            setOnLongClickListener {
                home.itemLongClickListener.onHomeItemLongPress(this)
                true
            }
        }

    /** FolderIcon.inflateIcon */
    private fun createFolderView(folderInfo: FolderInfo): FolderIconView =
        FolderIconView(context, home.activityContext, folderInfo, folderCallbacks).apply {
            HomeFolder(context, home.activityContext, home.launcherDelegate, home.gridSpec.iconSizePx).bindToIcon(this)
            setFocusableAsLauncher3Icon(grid.focusIndicatorHelper)
            for (item in contents) {
                loadIcon(item) { bitmap ->
                    if (item.bitmap === bitmap) return@loadIcon
                    item.bitmap = bitmap
                    updatePreviewItems { it === item }
                }
            }
            // ItemClickHandler.onClickFolderIcon
            setOnClickListener { with(folder) { if (!isOpen && !isDestroyed) animateOpen() } }
            setOnLongClickListener {
                home.itemLongClickListener.onHomeFolderLongPress(this)
                true
            }
        }

    private val folderCallbacks = object : FolderIconView.Callbacks {
        override fun onContentChanged(view: FolderIconView) = home.model.save()
    }
}
