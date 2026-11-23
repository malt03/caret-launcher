package com.malt03.textlauncher.ui

import android.content.Context
import android.graphics.Rect
import android.view.Display
import android.view.WindowManager
import com.android.launcher3.Utilities.dpiFromPx
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.window.WindowManagerProxy
import com.malt03.textlauncher.R
import kotlin.math.max
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * Grid dimensions, chosen as Launcher3's InvariantDeviceProfile does (android-17.0.0_r1): among the
 * display options of res/xml/device_profiles.xml ([DISPLAY_OPTIONS]) for the device type, the
 * one closest to the device's size gives the grid (columns, folder size), and the icon size, text
 * size and all apps cell height are interpolated from the nearest ones (invDistWeightedInterpolate).
 *
 * Like InvariantDeviceProfile, the grid belongs to the device's displays, not to the window: the
 * size is the smallest the launcher can have on any of them in any rotation (the estimated
 * supportedBounds; a foldable's two panels on its inner screen count as half of it), so turning the
 * device, folding it, the split screen or the keyboard do not change it.
 *
 * TextLauncher: no grid is remembered (Launcher3's GRID_NAME preference and the grid picker); the
 * grid is worked out again each time from the displays. Scalable grids (the tablet's) are not
 * scaled to fit.
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
            // LauncherDisplayInfo.deviceType
            val deviceType = when {
                isMultiDisplay -> TYPE_MULTI_DISPLAY
                supportedBounds.any { isLargeScreen(it.bounds) } -> TYPE_TABLET
                else -> TYPE_PHONE
            }
            val option = invDistWeightedInterpolate(
                dpiFromPx(minWidthPx.toFloat(), densityDpi),
                dpiFromPx(minHeightPx.toFloat(), densityDpi),
                DISPLAY_OPTIONS.filter { it.grid.isEnabled(deviceType) },
            )
            // DeviceProfile: the icon size in pixels (the icons are not scaled down to fit).
            val iconSizePx = ResourceUtils.pxFromDp(option.iconImageSize, metrics)
            val paddingPx = (8 * metrics.density).toInt()
            val minCellHeightPx = iconSizePx + (12 * metrics.density).toInt() * 2
            // TextLauncher: the page is the space above the search field (the hotseat).
            val res = context.resources
            val bar = res.getDimensionPixelSize(R.dimen.search_bar_height) +
                res.getDimensionPixelSize(R.dimen.search_bar_margin_vertical) * 2
            val page = minHeightPx - bar - paddingPx * 2
            return GridSpec(
                columns = option.grid.numColumns,
                rows = max(page / minCellHeightPx, 1),
                iconSizePx = iconSizePx,
                iconTextSizeSp = option.iconTextSize,
                allAppsCellHeightPx = ResourceUtils.pxFromDp(option.allAppsCellHeight, metrics),
                folderColumns = option.grid.numFolderColumns,
                folderRows = option.grid.numFolderRows,
                homePaddingPx = paddingPx,
                homeMinCellHeightPx = minCellHeightPx,
                isTwoPanels = isLargeScreen(windowBounds) && isMultiDisplay,
            )
        }

        /**
         * InvariantDeviceProfile.invDistWeightedInterpolate: the display option closest to the
         * device's size in dp, or, when no option matches it exactly, the closest option's grid
         * with the sizes of the [KNEARESTNEIGHBOR] nearest options weighted by their inverse
         * distance (to the power [WEIGHT_POWER]). The icon size is no larger than the closest
         * option's.
         */
        private fun invDistWeightedInterpolate(width: Float, height: Float, options: List<DisplayOption>): DisplayOption {
            // Sort the profiles based on the closeness to the device size
            val points = options.sortedBy { dist(width, height, it.minWidthDps, it.minHeightDps) }

            val closestPoint = points[0]
            if (dist(width, height, closestPoint.minWidthDps, closestPoint.minHeightDps) == 0f) {
                return closestPoint
            }

            // Calculate the weighted average as
            // out = (w1 * p1 + w2 * p2 + w3 * p3...) / (w1 + w2 + w3...)
            var weights = 0f
            var iconImageSize = 0f
            var iconTextSize = 0f
            var allAppsCellHeight = 0f
            for (p in points.take(KNEARESTNEIGHBOR)) {
                val w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER)
                weights += w
                iconImageSize += w * p.iconImageSize
                iconTextSize += w * p.iconTextSize
                allAppsCellHeight += w * p.allAppsCellHeight
            }
            return DisplayOption(
                closestPoint.grid,
                width,
                height,
                // Since the bitmaps are persisted, ensure that all bitmap sizes are not larger than
                // predefined size to avoid cache invalidation
                iconImageSize = min(iconImageSize / weights, closestPoint.iconImageSize),
                iconTextSize = iconTextSize / weights,
                allAppsCellHeight = allAppsCellHeight / weights,
            )
        }

        private fun dist(x0: Float, y0: Float, x1: Float, y1: Float): Float = hypot(x1 - x0, y1 - y0)

        private fun weight(x0: Float, y0: Float, x1: Float, y1: Float, pow: Float): Float {
            val d = dist(x0, y0, x1, y1)
            if (d == 0f) return Float.POSITIVE_INFINITY
            return (WEIGHT_EFFICIENT / d.toDouble().pow(pow.toDouble())).toFloat()
        }

        // InvariantDeviceProfile
        private const val KNEARESTNEIGHBOR = 3
        private const val WEIGHT_POWER = 5f
        private const val WEIGHT_EFFICIENT = 100000f

        // InvariantDeviceProfile's device types and GridOption's device categories.
        private const val TYPE_PHONE = 0
        private const val TYPE_MULTI_DISPLAY = 1
        private const val TYPE_TABLET = 2
        private const val DEVICE_CATEGORY_PHONE = 1 shl 0
        private const val DEVICE_CATEGORY_TABLET = 1 shl 1
        private const val DEVICE_CATEGORY_MULTI_DISPLAY = 1 shl 2

        /**
         * The grid options of Launcher3's res/xml/device_profiles.xml (android-17.0.0_r1) that
         * TextLauncher uses: phones, foldables and tablets (not desktops, and not the fixed
         * landscape mode, whose setting it does not have). All their display options can be the
         * default.
         */
        private val GRID_3_BY_3 = GridOption("3_by_3", 3, 2, 3, DEVICE_CATEGORY_PHONE)
        private val GRID_4_BY_4 = GridOption("4_by_4", 4, 3, 4, DEVICE_CATEGORY_PHONE or DEVICE_CATEGORY_MULTI_DISPLAY)
        private val GRID_5_BY_5 = GridOption("5_by_5", 5, 4, 4, DEVICE_CATEGORY_PHONE or DEVICE_CATEGORY_MULTI_DISPLAY)
        private val GRID_6_BY_5 = GridOption("6_by_5", 6, 3, 3, DEVICE_CATEGORY_TABLET)

        private val DISPLAY_OPTIONS = listOf(
            DisplayOption(GRID_3_BY_3, 255f, 300f, 48f, 13f, 104f), // Super Short Stubby
            DisplayOption(GRID_3_BY_3, 255f, 400f, 48f, 13f, 104f), // Shorter Stubby
            DisplayOption(GRID_4_BY_4, 275f, 420f, 48f, 13f, 104f), // Short Stubby
            DisplayOption(GRID_4_BY_4, 255f, 450f, 48f, 13f, 104f), // Stubby
            DisplayOption(GRID_4_BY_4, 296f, 491.33f, 48f, 13f, 104f), // Nexus S
            DisplayOption(GRID_4_BY_4, 359f, 567f, 54f, 13f, 104f), // Nexus 4
            DisplayOption(GRID_4_BY_4, 335f, 567f, 54f, 13f, 104f), // Nexus 5
            DisplayOption(GRID_5_BY_5, 406f, 694f, 56f, 14.4f, 104f), // Large Phone
            DisplayOption(GRID_5_BY_5, 406f, 694f, 56f, 14.4f, 104f), // Large Phone Split Display
            DisplayOption(GRID_5_BY_5, 255f, 400f, 48f, 13f, 104f), // Shorter Stubby
            DisplayOption(GRID_6_BY_5, 900f, 820f, 60f, 14f, 142f), // Tablet
        )

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

    /** A grid-option of device_profiles.xml, with what TextLauncher reads of it. */
    private class GridOption(
        val name: String,
        val numColumns: Int,
        val numFolderRows: Int,
        val numFolderColumns: Int,
        val deviceCategory: Int,
    ) {
        /** GridOption.isEnabled */
        fun isEnabled(deviceType: Int): Boolean = when (deviceType) {
            TYPE_PHONE -> deviceCategory and DEVICE_CATEGORY_PHONE == DEVICE_CATEGORY_PHONE
            TYPE_TABLET -> deviceCategory and DEVICE_CATEGORY_TABLET == DEVICE_CATEGORY_TABLET
            TYPE_MULTI_DISPLAY -> deviceCategory and DEVICE_CATEGORY_MULTI_DISPLAY == DEVICE_CATEGORY_MULTI_DISPLAY
            else -> false
        }
    }

    /** A display-option of device_profiles.xml, with what TextLauncher reads of it. */
    private class DisplayOption(
        val grid: GridOption,
        val minWidthDps: Float,
        val minHeightDps: Float,
        val iconImageSize: Float,
        val iconTextSize: Float,
        val allAppsCellHeight: Float,
    )
}
