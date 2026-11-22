package com.malt03.textlauncher.ui

import android.content.Context
import android.graphics.Rect
import android.view.Display
import android.view.WindowManager
import com.android.launcher3.Utilities.dpiFromPx
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.WindowManagerProxy
import com.malt03.textlauncher.R
import kotlin.math.max
import kotlin.math.min

/**
 * Grid dimensions, following Launcher3 res/xml/device_profiles.xml (android-17.0.0_r1):
 * phones narrower than the "Large Phone" profile (406dp) use the 4_by_4 grid with 54dp icons
 * (folders 4x3), wider ones the 5_by_5 grid with 56dp icons (folders 4x4).
 *
 * Like InvariantDeviceProfile, the grid belongs to the device's displays, not to the window: it is
 * worked out from the smallest size the launcher can have on any of them in any rotation (the
 * estimated supportedBounds; a foldable's two panels on its inner screen count as half of it), so
 * turning the device, folding it, the split screen or the keyboard do not change it.
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
    /**
     * DeviceProfile.isTwoPanels: the window is a large screen on a device that also has a phone
     * sized display (a foldable's inner screen). Launcher3 shows two pages side by side;
     * TextLauncher shows the all apps list on the left and the home page on the right
     * (docs/spec.md 5.5).
     */
    val isTwoPanels: Boolean,
) {
    companion object {
        fun from(context: Context): GridSpec {
            val metrics = context.resources.displayMetrics
            val proxy = WindowManagerProxy.INSTANCE
            val densityDpi = context.resources.configuration.densityDpi
            val isDefaultDisplay = proxy.getDisplay(context).displayId == Display.DEFAULT_DISPLAY
            // LauncherDisplayInfo.isLargeScreen
            fun isLargeScreen(bounds: Rect): Boolean =
                dpiFromPx(min(bounds.width(), bounds.height()).toFloat(), densityDpi) >= WindowManagerProxy.MIN_TABLET_WIDTH ||
                    !isDefaultDisplay
            // LauncherDisplayInfo.supportedBounds and deviceType: TYPE_MULTI_DISPLAY when there
            // are both phone and large screen bounds.
            val supportedBounds = proxy.estimateInternalDisplayBounds(context).values.flatten()
            val isMultiDisplay = supportedBounds.any { isLargeScreen(it.bounds) } &&
                supportedBounds.any { !isLargeScreen(it.bounds) }
            // DeviceProfile: isTwoPanels = info.isLargeScreen(windowBounds) && isMultiDisplay
            val windowBounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            val (minWidthPx, minHeightPx) = minAvailableSize(supportedBounds, isMultiDisplay) { isLargeScreen(it.bounds) }
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
                isTwoPanels = isLargeScreen(windowBounds) && isMultiDisplay,
            )
        }

        /**
         * InvariantDeviceProfile.invDistWeightedInterpolate: the smallest available width and
         * height over the window bounds of the displays in each rotation. A large screen of a
         * device with several displays shows two panels, so a panel has half its width; a phone in
         * landscape uses the transposed layout, so its sizes are swapped.
         */
        private fun minAvailableSize(
            supportedBounds: List<WindowBounds>,
            isMultiDisplay: Boolean,
            isLargeScreen: (WindowBounds) -> Boolean,
        ): Pair<Int, Int> {
            var minWidthPx = Int.MAX_VALUE
            var minHeightPx = Int.MAX_VALUE
            for (bounds in supportedBounds) {
                val isLargeScreen = isLargeScreen(bounds)
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
