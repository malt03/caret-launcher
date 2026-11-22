package com.malt03.textlauncher.ui

import android.content.Context
import android.view.Display
import com.android.launcher3.Utilities.dpiFromPx
import com.android.launcher3.util.window.WindowManagerProxy
import com.malt03.textlauncher.R
import kotlin.math.max
import kotlin.math.min

/**
 * Grid dimensions, following Launcher3 res/xml/device_profiles.xml (android-17.0.0_r1):
 * phones narrower than the "Large Phone" profile (406dp) use the 4_by_4 grid with 54dp icons
 * (folders 4x3), wider ones the 5_by_5 grid with 56dp icons (folders 4x4).
 *
 * Like InvariantDeviceProfile, the grid belongs to the display, not to the window: it is worked out
 * from the smallest size the launcher can have on the display in any rotation (the estimated
 * supportedBounds), so turning the device, the split screen or the keyboard do not change it.
 * Launcher3 picks the closest display option by width and height; this takes the width only.
 */
data class GridSpec(
    val columns: Int,
    /**
     * The home page's rows (InvariantDeviceProfile.numRows). TextLauncher: as many as fit in the
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
) {
    companion object {
        fun from(context: Context): GridSpec {
            val metrics = context.resources.displayMetrics
            val (minWidthPx, minHeightPx) = minAvailableSize(context)
            val widthDp = dpiFromPx(minWidthPx.toFloat(), metrics.densityDpi)
            val large = widthDp >= 406
            val iconSizePx = ((if (large) 56 else 54) * metrics.density).toInt()
            val paddingPx = (8 * metrics.density).toInt()
            val minCellHeightPx = iconSizePx + (12 * metrics.density).toInt() * 2
            // TextLauncher: the page is the space above the search field (the hotseat).
            val res = context.resources
            val bar = res.getDimensionPixelSize(R.dimen.search_bar_height) +
                res.getDimensionPixelSize(R.dimen.search_bar_margin_vertical) * 2
            val page = minHeightPx - bar - paddingPx * 2
            return GridSpec(
                columns = if (large) 5 else 4,
                rows = max(page / minCellHeightPx, 1),
                iconSizePx = iconSizePx,
                iconTextSizeSp = if (large) 14.4f else 13f,
                allAppsCellHeightPx = (104 * metrics.density).toInt(),
                folderColumns = 4,
                folderRows = if (large) 4 else 3,
                homePaddingPx = paddingPx,
                homeMinCellHeightPx = minCellHeightPx,
            )
        }

        /**
         * InvariantDeviceProfile.invDistWeightedInterpolate: the smallest available width and
         * height over the display's window bounds in each rotation; a phone in landscape uses the
         * transposed layout, so its sizes are swapped.
         */
        private fun minAvailableSize(context: Context): Pair<Int, Int> {
            val proxy = WindowManagerProxy.INSTANCE
            val densityDpi = context.resources.configuration.densityDpi
            var minWidthPx = Int.MAX_VALUE
            var minHeightPx = Int.MAX_VALUE
            for (bounds in proxy.estimateInternalDisplayBounds(context).values.flatten()) {
                // LauncherDisplayInfo.isLargeScreen
                val smallestSizeDp = dpiFromPx(min(bounds.bounds.width(), bounds.bounds.height()).toFloat(), densityDpi)
                val isLargeScreen = smallestSizeDp >= WindowManagerProxy.MIN_TABLET_WIDTH ||
                    proxy.getDisplay(context).displayId != Display.DEFAULT_DISPLAY
                if (!isLargeScreen && bounds.isLandscape) {
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
