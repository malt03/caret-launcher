package com.malt03.caretlauncher.ui.home

import android.view.View
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import com.malt03.caretlauncher.R
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.malt03.caretlauncher.data.isApp
import com.malt03.caretlauncher.data.isAvailable
import java.util.stream.Stream

/**
 * Launcher.getSupportedShortcuts: the options of an app's long-press menu (docs/spec.md 5.3), which
 * Launcher3's PopupControllerForAppIcon shows: CaretLauncher's "Edit search terms" and Launcher3's
 * App info. CaretLauncher has no Widgets, Install or Remove options there (the drop target bar
 * removes and uninstalls).
 */
internal class HomeSystemShortcuts(private val home: HomeController) {

    fun getSupportedShortcuts(): Stream<SystemShortcut.Factory<*>> =
        Stream.of(editSearchTerms, SystemShortcut.APP_INFO)

    /**
     * "Edit search terms" for an app (on the home screen, in a folder or in the all apps list); an
     * app of the list goes by the home screen's item for it (AppInfo.makeWorkspaceItem).
     */
    private val editSearchTerms = SystemShortcut.Factory<ActivityContext> { context, itemInfo, originalView ->
        val app = when {
            itemInfo is WorkspaceItemInfo && itemInfo.isApp -> itemInfo
            itemInfo is AppInfo -> itemInfo.makeWorkspaceItem(home.context)
            else -> null
        }
        if (app != null && app.isAvailable) EditSearchTerms(context, app, originalView) else null
    }

    /** The long-press menu's "Edit search terms" (docs/spec.md 4), a CaretLauncher option. */
    private inner class EditSearchTerms(
        context: ActivityContext,
        private val app: WorkspaceItemInfo,
        originalView: View,
    ) : SystemShortcut<ActivityContext>(
        com.android.launcher3.R.drawable.gm_edit_24, R.string.menu_search_terms, context, app, originalView,
    ) {
        override fun onClick(view: View) {
            dismissTaskMenuView()
            home.host.editSearchTerms(app)
        }
    }
}
