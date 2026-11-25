package com.malt03.caretlauncher.ui

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.deviceprofile.parser.DeviceTypedMap.INDEX_DEFAULT
import com.android.launcher3.display.LauncherDisplayInfo
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.WindowManagerProxy
import com.malt03.caretlauncher.R
import com.malt03.caretlauncher.ui.home.GridSizeMigration
import kotlin.math.max
import kotlin.math.min

/**
 * Grid dimensions, chosen by Launcher3's InvariantDeviceProfile (the derived copy in compat,
 * android-17.0.0_r1) from res/xml/device_profiles.xml (copied as is) for the device's displays
 * (LauncherDisplayInfo), with the columns of the grid the items were placed in (DeviceGridState):
 * the grid (columns, folder size), and the icon size, text size and all apps cell height
 * interpolated from the nearest display options.
 *
 * Like InvariantDeviceProfile, the grid belongs to the device's displays, not to the window: the
 * size is the smallest the launcher can have on any of them in any rotation (the supportedBounds;
 * a foldable's two panels on its inner screen count as half of it), so turning the device, folding
 * it, the split screen or the keyboard do not change it.
 *
 * CaretLauncher: no grid is remembered (Launcher3's GRID_NAME preference and the grid picker); the
 * grid is worked out again each time from the displays. Scalable grids (the tablet's) are not
 * scaled to fit. The rows, the padding and the cells are the home screen's own.
 */
data class GridSpec(
    val columns: Int,
    /**
     * The home page's rows (InvariantDeviceProfile.numRows). CaretLauncher: as many as fit in the
     * page above the search field (Launcher3 takes numRows from the display option and fits the
     * cells, which here would be too tall without labels).
     */
    val rows: Int,
    val iconSizePx: Int,
    val iconTextSizeSp: Float,
    /** Launcher3 allAppsCellHeight. */
    val allAppsCellHeightPx: Int,
    /** Launcher3 numFolderColumns / numFolderRows. */
    val folderColumns: Int,
    val folderRows: Int,
    /** The home grid's padding on each side. */
    val homePaddingPx: Int,
    /** The smallest home cell height: the icon with its padding (the cells have no labels). */
    val homeMinCellHeightPx: Int,
    /**
     * The home cells in the smallest window the displays have (the size the grid is chosen for),
     * as HomeScrollView measures them there: the cell size of DeviceProfile before the grid is
     * measured.
     */
    val homeCellWidthPx: Int,
    val homeCellHeightPx: Int,
    /**
     * DeviceProfile.isTwoPanels: the window is a large screen on a device that also has a phone
     * sized display (a foldable's inner screen). Launcher3 shows two pages side by side;
     * CaretLauncher shows the all apps list on the left and the home page on the right
     * (docs/spec.md 5.5).
     */
    val isTwoPanels: Boolean,
    /** LauncherDisplayInfo.isLargeScreen for the window (DeviceProperties.isLargeScreen). */
    val isLargeScreen: Boolean,
) {
    companion object {
        fun from(context: Context): GridSpec {
            val metrics = context.resources.displayMetrics
            // DisplayInfoContainer.getNewInfo: the display info of a window context, with the
            // estimated bounds of the internal displays.
            val windowContext = windowContext(context)
            val wmProxy = WindowManagerProxy.INSTANCE
            val displayInfo = LauncherDisplayInfo(
                windowContext,
                wmProxy,
                perDisplayBoundsCache = wmProxy.estimateInternalDisplayBounds(windowContext),
            )
            val idp = InvariantDeviceProfile()
            idp.initGrid(displayInfo, GridSizeMigration.getColumns(context))
            // DeviceProfile: isTwoPanels = info.isLargeScreen(windowBounds) && isMultiDisplay
            val windowBounds = WindowBounds(
                context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds, Rect(),
            )
            val isLargeScreen = displayInfo.isLargeScreen(windowBounds)
            val isMultiDisplay = idp.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
            val (minWidthPx, minHeightPx) = minAvailableSize(displayInfo, isMultiDisplay)
            // DeviceProfile: the icon size in pixels (the icons are not scaled down to fit).
            val iconSizePx = ResourceUtils.pxFromDp(idp.iconSize[INDEX_DEFAULT], metrics)
            val paddingPx = (8 * metrics.density).toInt()
            val minCellHeightPx = iconSizePx + (12 * metrics.density).toInt() * 2
            // CaretLauncher: the page is the space above the search field (the hotseat).
            val res = context.resources
            val bar = res.getDimensionPixelSize(R.dimen.search_bar_height) +
                res.getDimensionPixelSize(R.dimen.search_bar_margin_vertical) * 2
            val page = minHeightPx - bar - paddingPx * 2
            val rows = max(page / minCellHeightPx, 1)
            return GridSpec(
                columns = idp.numColumns,
                rows = rows,
                iconSizePx = iconSizePx,
                iconTextSizeSp = idp.iconTextSize[INDEX_DEFAULT],
                allAppsCellHeightPx = ResourceUtils.pxFromDp(idp.allAppsCellSize[INDEX_DEFAULT].y, metrics),
                folderColumns = idp.numFolderColumns[INDEX_DEFAULT],
                folderRows = idp.numFolderRows[INDEX_DEFAULT],
                homePaddingPx = paddingPx,
                homeMinCellHeightPx = minCellHeightPx,
                homeCellWidthPx = (minWidthPx - paddingPx * 2) / idp.numColumns,
                homeCellHeightPx = max(page / rows, minCellHeightPx),
                isTwoPanels = isLargeScreen && isMultiDisplay,
                isLargeScreen = isLargeScreen,
            )
        }

        /**
         * DisplayControllerImpl: a window context of the context's display, as the app's context
         * (the widget picker's repositories ask with it) has no display.
         */
        private fun windowContext(context: Context): Context {
            if (context.isUiContext) return context
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            return context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION, null)
        }

        /**
         * InvariantDeviceProfile.invDistWeightedInterpolate: the smallest available width and
         * height over the window bounds of the displays in each rotation. A large screen of a
         * device with several displays shows two panels, so a panel has half its width; a phone in
         * landscape uses the transposed layout, so its sizes are swapped.
         */
        private fun minAvailableSize(displayInfo: LauncherDisplayInfo, isMultiDisplay: Boolean): Pair<Int, Int> {
            var minWidthPx = Int.MAX_VALUE
            var minHeightPx = Int.MAX_VALUE
            for (bounds in displayInfo.supportedBounds) {
                val isLargeScreen = displayInfo.isLargeScreen(bounds)
                if (isLargeScreen && isMultiDisplay) {
                    // For split displays, take half width per page
                    minWidthPx = min(minWidthPx, bounds.availableSize.x / 2)
                    minHeightPx = min(minHeightPx, bounds.availableSize.y)
                } else if (!isLargeScreen && bounds.isLandscape) {
                    minWidthPx = min(minWidthPx, bounds.availableSize.y)
                    minHeightPx = min(minHeightPx, bounds.availableSize.x)
                } else {
                    minWidthPx = min(minWidthPx, bounds.availableSize.x)
                    minHeightPx = min(minHeightPx, bounds.availableSize.y)
                }
            }
            return minWidthPx to minHeightPx
        }
    }
}
