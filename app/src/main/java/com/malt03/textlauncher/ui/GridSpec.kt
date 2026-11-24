package com.malt03.textlauncher.ui

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.view.Display
import android.view.WindowManager
import com.android.launcher3.Flags
import com.android.launcher3.Utilities.dpiFromPx
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.WindowBounds
import com.android.launcher3.util.XmlElement
import com.android.launcher3.util.XmlElement.Companion.getRootElement
import com.android.launcher3.util.window.WindowManagerProxy
import com.malt03.textlauncher.R
import com.malt03.textlauncher.ui.home.GridSizeMigration
import kotlin.math.max
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import com.android.launcher3.R as L3R

/**
 * Grid dimensions, chosen as Launcher3's InvariantDeviceProfile does (android-17.0.0_r1): among the
 * display options of res/xml/device_profiles.xml (copied as is) for the device type, those with
 * the columns of the grid the items were placed in (DeviceGridState) if there are any, the one
 * closest to the device's size gives the grid (columns, folder size), and the icon size, text size
 * and all apps cell height are interpolated from the nearest ones (invDistWeightedInterpolate).
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
     * The home cells in the smallest window the displays have (the size the grid is chosen for),
     * as HomeScrollView measures them there: the cell size of DeviceProfile before the grid is
     * measured.
     */
    val homeCellWidthPx: Int,
    val homeCellHeightPx: Int,
    /**
     * DeviceProfile.isTwoPanels: the window is a large screen on a device that also has a phone
     * sized display (a foldable's inner screen). Launcher3 shows two pages side by side;
     * TextLauncher shows the all apps list on the left and the home page on the right
     * (docs/spec.md 5.5).
     */
    val isTwoPanels: Boolean,
    /** LauncherDisplayInfo.isLargeScreen for the window (DeviceProperties.isLargeScreen). */
    val isLargeScreen: Boolean,
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
            // InvariantDeviceProfile.initGrid
            val allOptions = getPredefinedDeviceProfiles(context, deviceType)

            // Filter out options that don't have the same number of columns as the grid
            val allOptionsFilteredByColCount =
                filterByColumnCount(allOptions, GridSizeMigration.getColumns(context))

            val option = invDistWeightedInterpolate(
                dpiFromPx(minWidthPx.toFloat(), densityDpi),
                dpiFromPx(minHeightPx.toFloat(), densityDpi),
                if (allOptionsFilteredByColCount.isEmpty()) allOptions else allOptionsFilteredByColCount,
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
            val rows = max(page / minCellHeightPx, 1)
            return GridSpec(
                columns = option.grid.numColumns,
                rows = rows,
                iconSizePx = iconSizePx,
                iconTextSizeSp = option.iconTextSize,
                allAppsCellHeightPx = ResourceUtils.pxFromDp(option.allAppsCellHeight, metrics),
                folderColumns = option.grid.numFolderColumns,
                folderRows = option.grid.numFolderRows,
                homePaddingPx = paddingPx,
                homeMinCellHeightPx = minCellHeightPx,
                homeCellWidthPx = (minWidthPx - paddingPx * 2) / option.grid.numColumns,
                homeCellHeightPx = max(page / rows, minCellHeightPx),
                isTwoPanels = isLargeScreen(windowBounds) && isMultiDisplay,
                isLargeScreen = isLargeScreen(windowBounds),
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
                closestPoint.canBeDefault,
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

        // InvariantDeviceProfile's device types.
        private const val TYPE_PHONE = 0
        private const val TYPE_MULTI_DISPLAY = 1
        private const val TYPE_TABLET = 2

        /**
         * InvariantDeviceProfile.getPredefinedDeviceProfiles with an empty grid name: the display
         * options that can be the default, of the grids for the device type. TextLauncher: no grid
         * is remembered (GRID_NAME), so the grid name is always empty, and there is no fixed
         * landscape mode (FIXED_LANDSCAPE_MODE), which has no setting here.
         */
        private fun getPredefinedDeviceProfiles(context: Context, deviceType: Int): List<DisplayOption> {
            val profiles = DisplayOption.getPredefinedDisplayOptions(context, deviceType, false)

            val filteredProfiles = ArrayList<DisplayOption>()
            // Use the default options since gridName is empty and there's no valid grids.
            for (option in profiles) {
                if (option.canBeDefault) {
                    filteredProfiles.add(option)
                }
            }
            if (filteredProfiles.isEmpty()) {
                throw RuntimeException("No display option with canBeDefault=true")
            }
            return filteredProfiles
        }

        /** InvariantDeviceProfile.filterByColumnCount */
        private fun filterByColumnCount(allOptions: List<DisplayOption>, numColumns: Int): List<DisplayOption> =
            allOptions.filter { option -> option.grid.numColumns == numColumns }

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

    /**
     * GridOption (deviceprofile/parser/GridOption.kt): a grid-option of device_profiles.xml, with
     * what TextLauncher reads of it.
     */
    private class GridOption(ta: TypedArray) {
        val name: String = requireNotNull(ta.getString(L3R.styleable.GridDisplayOption_name))

        // getDefinedGridSizeSpecOrDefault. TextLauncher: the grid size specs (gridSizeSpecsId,
        // which only the fixed landscape mode has) are not read.
        val numRows: Int = ta.getInt(L3R.styleable.GridDisplayOption_numRows, 0)
        val numColumns: Int = ta.getInt(L3R.styleable.GridDisplayOption_numColumns, 0)

        val deviceCategory: Int = ta.getInt(L3R.styleable.GridDisplayOption_deviceCategory, DEVICE_CATEGORY_ANY)

        // numFolderRows and numFolderColumns. TextLauncher: their defaults (INDEX_DEFAULT) only,
        // as the folders do not change with the rotation.
        val numFolderRows: Int = ta.getInt(L3R.styleable.GridDisplayOption_numFolderRows, numRows)
        val numFolderColumns: Int = ta.getInt(L3R.styleable.GridDisplayOption_numFolderColumns, numColumns)

        val mIsDualGrid: Boolean = ta.getBoolean(L3R.styleable.GridDisplayOption_isDualGrid, false)

        val isFixedLandscape: Boolean = ta.getBoolean(L3R.styleable.GridDisplayOption_isFixedLandscape, false)

        fun isEnabled(deviceType: Int): Boolean = when (deviceType) {
            TYPE_PHONE -> (deviceCategory and DEVICE_CATEGORY_PHONE) == DEVICE_CATEGORY_PHONE
            TYPE_TABLET -> (deviceCategory and DEVICE_CATEGORY_TABLET) == DEVICE_CATEGORY_TABLET
            TYPE_MULTI_DISPLAY -> (deviceCategory and DEVICE_CATEGORY_MULTI_DISPLAY) == DEVICE_CATEGORY_MULTI_DISPLAY
            // TextLauncher: no desktop devices (TYPE_DESKTOP).
            else -> false
        }

        /** Returns true if the grid option should be used given the flags that are toggled on/off. */
        fun filterByFlag(deviceType: Int, isFixedLandscape: Boolean): Boolean {
            // TextLauncher: no desktop devices (TYPE_DESKTOP), whose grids go by gridType.

            if (deviceType == TYPE_TABLET) {
                return Flags.oneGridRotationHandling() == mIsDualGrid
            }

            // Here we return true if fixed landscape mode should be on.
            if (this.isFixedLandscape || isFixedLandscape) {
                return this.isFixedLandscape && isFixedLandscape
            }

            return true
        }

        companion object {
            const val TAG_NAME: String = "grid-option"

            private const val DEVICE_CATEGORY_PHONE = 1 shl 0
            private const val DEVICE_CATEGORY_TABLET = 1 shl 1
            private const val DEVICE_CATEGORY_MULTI_DISPLAY = 1 shl 2
            private const val DEVICE_CATEGORY_DESKTOP = 1 shl 3
            private const val DEVICE_CATEGORY_ANY =
                (DEVICE_CATEGORY_PHONE or
                    DEVICE_CATEGORY_TABLET or
                    DEVICE_CATEGORY_MULTI_DISPLAY or
                    DEVICE_CATEGORY_DESKTOP)

            /** @return all the grid options that can be shown on the device */
            fun <T> parseAllDefined(
                context: Context,
                filter: (GridOption) -> Boolean = { true },
                mapper: (GridOption, XmlElement) -> T,
            ): List<T> {
                return context.resources.getXml(L3R.xml.device_profiles).use { xml ->
                    xml.getRootElement()
                        .children(TAG_NAME)
                        .mapNotNull { el ->
                            val ta = el.obtainAttrs(context, L3R.styleable.GridDisplayOption)
                            val option = GridOption(ta)
                            ta.recycle()

                            if (filter.invoke(option)) mapper.invoke(option, el) else null
                        }
                        .toList()
                }
            }
        }
    }

    /**
     * DisplayOption (deviceprofile/parser/DisplayOption.kt): a display-option of
     * device_profiles.xml, with what TextLauncher reads of it. TextLauncher: of the sizes that
     * change with the rotation, the defaults (INDEX_DEFAULT).
     */
    private class DisplayOption(
        val grid: GridOption,
        val minWidthDps: Float,
        val minHeightDps: Float,
        val canBeDefault: Boolean,
        val iconImageSize: Float,
        val iconTextSize: Float,
        val allAppsCellHeight: Float,
    ) {
        constructor(grid: GridOption, ta: TypedArray) : this(
            grid,
            minWidthDps = ta.getFloat(L3R.styleable.ProfileDisplayOption_minWidthDps, 0f),
            minHeightDps = ta.getFloat(L3R.styleable.ProfileDisplayOption_minHeightDps, 0f),
            canBeDefault = ta.getBoolean(L3R.styleable.ProfileDisplayOption_canBeDefault, false),
            // iconSizes, textSizes and allAppsCellSize
            iconImageSize = ta.getFloat(L3R.styleable.ProfileDisplayOption_iconImageSize, 0f),
            iconTextSize = ta.getFloat(L3R.styleable.ProfileDisplayOption_iconTextSize, 0f),
            allAppsCellHeight = ta.getFloat(L3R.styleable.ProfileDisplayOption_allAppsCellHeight, 0f),
        )

        companion object {
            fun getPredefinedDisplayOptions(
                context: Context,
                deviceType: Int,
                isFixedLandscapeMode: Boolean,
            ): List<DisplayOption> {
                return GridOption.parseAllDefined(
                    context,
                    filter = {
                        it.isEnabled(deviceType) &&
                            it.filterByFlag(deviceType, isFixedLandscapeMode)
                    },
                    mapper = { option, el ->
                        el.children("display-option")
                            .map {
                                val ta = it.obtainAttrs(context, L3R.styleable.ProfileDisplayOption)
                                DisplayOption(option, ta).also { ta.recycle() }
                            }
                            .toList()
                    },
                )
                    .flatten()
            }
        }
    }
}
