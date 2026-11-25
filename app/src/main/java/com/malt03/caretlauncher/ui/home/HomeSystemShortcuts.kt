package com.malt03.caretlauncher.ui.home

import android.view.View
import android.widget.TextView
import androidx.core.view.doOnLayout
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
 * Launcher3's PopupControllerForAppIcon shows: CaretLauncher's "Search keywords" and Launcher3's
 * App info. CaretLauncher has no Widgets, Install or Remove options there (the drop target bar
 * removes and uninstalls).
 */
internal class HomeSystemShortcuts(private val home: HomeController) {

    fun getSupportedShortcuts(): Stream<SystemShortcut.Factory<*>> =
        Stream.of(editSearchTerms, SystemShortcut.APP_INFO)

    /**
     * "Search keywords" for an app (on the home screen, in a folder or in the all apps list); an
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

    /** The long-press menu's "Search keywords" (docs/spec.md 4), a CaretLauncher option. */
    private inner class EditSearchTerms(
        context: ActivityContext,
        private val app: WorkspaceItemInfo,
        originalView: View,
    ) : SystemShortcut<ActivityContext>(
        com.android.launcher3.R.drawable.gm_edit_24, R.string.menu_search_terms, context, app, originalView,
    ) {
        /**
         * DeepShortcutView.applyShortcutInfo: the long label as long as it fits, else the short one
         * (Launcher3's menu items are one line and cut off). The accessibility action and the content
         * description keep the long label.
         */
        override fun setIconAndLabelFor(iconView: View, labelView: TextView) {
            super.setIconAndLabelFor(iconView, labelView)
            labelView.doOnLayout {
                val availableWidth = labelView.width - labelView.totalPaddingLeft - labelView.totalPaddingRight
                val longLabel = labelView.text.toString()
                if (labelView.paint.measureText(longLabel) > availableWidth) {
                    labelView.setText(R.string.menu_search_terms_short)
                }
            }
        }

        override fun onClick(view: View) {
            dismissTaskMenuView()
            home.host.editSearchTerms(app)
        }
    }
}
