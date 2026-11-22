package com.malt03.textlauncher.ui.home

import android.appwidget.AppWidgetManager
import android.view.View
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.touch.ItemClickHandler
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.WidgetItemInfo
import com.malt03.textlauncher.data.isDeepShortcut

/**
 * ItemInflater of the home screen: makes the views of the home model's items, with their icons
 * (loaded as Launcher3's IconCache does for LoaderTask). Clicks go to Launcher3's ItemClickHandler.
 */
internal class HomeItemInflater(private val home: HomeController) {
    private val context = home.context
    private val app = home.app
    private val grid = home.grid

    /**
     * The icon of an app that was found (LoaderTask). An app shortcut's icon is loaded with the
     * shortcut ([HomeLoader]), so its item already has it.
     */
    fun loadIcon(info: WorkspaceItemInfo, onLoaded: (BitmapInfo) -> Unit) {
        if (info is AppItemInfo) info.entry?.let { app.iconLoader.load(it, DESKTOP_ICON_FLAG, onLoaded) }
    }

    /**
     * BubbleTextView.applyFromWorkspaceItem: the icon, now or once it is loaded, drawn disabled
     * when the item is.
     */
    fun bindIcon(info: WorkspaceItemInfo, view: AppIconView) {
        view.applyFromWorkspaceItem(info)
        loadIcon(info) { bitmap ->
            info.bitmap = bitmap
            view.applyFromWorkspaceItem(info)
        }
    }

    /** ItemInflater.inflateItem: the item's view, or null for an item that cannot be shown. */
    fun inflateItem(item: ItemInfo): View? = when {
        item is AppItemInfo -> if (item.entry != null) createItemView(item) else null
        item is WorkspaceItemInfo && item.isDeepShortcut -> createItemView(item)
        item is FolderInfo -> createFolderView(item)
        item is WidgetItemInfo -> createWidgetView(item)
        else -> null
    }

    /**
     * WidgetInflater.inflateAppWidget and ItemInflater.prepareAppWidget: the widget's view from
     * the host, which starts a drag on a long press. A widget whose provider is gone has no view
     * (the loader deletes it).
     */
    private fun createWidgetView(info: WidgetItemInfo): View? {
        val providerInfo = AppWidgetManager.getInstance(context).getAppWidgetInfo(info.appWidgetId) ?: return null
        return home.widgetHost.createView(context, info.appWidgetId, providerInfo).apply {
            setOnLongClickListener {
                home.itemLongClickListener.onHomeWidgetLongPress(this)
                true
            }
        }
    }

    private fun createItemView(info: WorkspaceItemInfo): AppIconView =
        AppIconView(context, home.gridSpec.iconSizePx).apply {
            bindIcon(info, this)
            // ItemInflater: the item's focus listener is Launcher.getFocusHandler.
            setFocusableAsLauncher3Icon(grid.focusIndicatorHelper)
            setOnClickListener(ItemClickHandler.INSTANCE)
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
            setOnClickListener(ItemClickHandler.INSTANCE)
            setOnLongClickListener {
                home.itemLongClickListener.onHomeFolderLongPress(this)
                true
            }
        }

    private val folderCallbacks = object : FolderIconView.Callbacks {
        override fun onContentChanged(view: FolderIconView) = home.model.save()
    }
}
