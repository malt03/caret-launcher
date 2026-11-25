package com.malt03.caretlauncher.ui.allapps

import android.view.View
import com.android.launcher3.allapps.AllAppsTransitionController
import com.malt03.caretlauncher.ui.LauncherRootLayout

/**
 * The views Launcher3's AllAppsTransitionController (the derived copy in compat) moves: the search
 * field and the all apps sheet right under it (docs/spec.md 6.4). The progress is Launcher3's (0
 * shows all apps and 1 the workspace): the field goes from [LauncherRootLayout.closedBarTop] (it
 * follows the keyboard) to [LauncherRootLayout.openBarTop], and the sheet, laid out open, comes out
 * from behind the keyboard under it. The field's closed position follows the keyboard, so the
 * layout has the views placed again as the keyboard moves ([place]).
 */
class AllAppsTransitionHost(private val root: LauncherRootLayout) : AllAppsTransitionController.Host {
    private var progress = 1f

    init {
        root.onAllAppsLayoutChanged = ::place
    }

    override fun getClosedBarTop(): Float = root.closedBarTop

    override fun getOpenBarTop(): Float = root.openBarTop

    override fun onAllAppsProgressChanged(progress: Float) {
        this.progress = progress
        place()
    }

    override fun getAppsView(): View = root.allAppsSheet

    private fun place() {
        val shown = progress < 1f
        // The closed position depends on whether the list is shown (the field leaves the keyboard).
        root.isAllAppsShown = shown
        val barTop = root.openBarTop + (root.closedBarTop - root.openBarTop) * progress
        root.searchBarArea.translationY = barTop - root.searchBarArea.top
        root.allAppsSheet.visibility = if (shown) View.VISIBLE else View.GONE
        root.allAppsSheet.translationY = barTop - root.openBarTop
    }
}
