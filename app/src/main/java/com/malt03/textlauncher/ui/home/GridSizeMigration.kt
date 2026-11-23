package com.malt03.textlauncher.ui.home

import android.content.Context
import android.graphics.Point
import android.util.Log
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.CellAndSpan
import com.android.launcher3.util.GridOccupancy
import com.android.launcher3.widget.WidgetManagerHelper
import com.malt03.textlauncher.data.WidgetItemInfo
import com.malt03.textlauncher.ui.GridSpec

/**
 * GridSizeMigrationDBController.migrateGridIfNeeded with GridSizeMigrationLogic.migrateGrid for the
 * home items: when the grid is not the one the items were placed in (DeviceGridState in the prefs),
 * they are moved into it before loading.
 *
 * TextLauncher: there is one home database rather than one per grid, so the destination grid starts
 * empty, and there is one page, so the items that fit on no page are deleted rather than put on new
 * pages. There is no hotseat to migrate.
 */
internal object GridSizeMigration {
    /** LauncherFiles.SHARED_PREFERENCES_KEY */
    private const val SHARED_PREFERENCES_KEY = "com.android.launcher3.prefs"

    /** DeviceGridState.KEY_WORKSPACE_SIZE */
    private const val KEY_WORKSPACE_SIZE = "migration_src_workspace_size"

    private const val TAG = "GridSizeMigrationLogic"

    /**
     * DeviceGridState(LauncherPrefs).getColumns: the columns of the grid the items were placed
     * in, or -1 before any grid was saved. InvariantDeviceProfile.initGrid keeps them if it can
     * (filterByColumnCount).
     */
    fun getColumns(context: Context): Int {
        val gridSizeString = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            .getString(KEY_WORKSPACE_SIZE, null)
        if (gridSizeString.isNullOrEmpty()) {
            return -1
        }
        // TextLauncher: a size that does not parse counts as none, rather than crashing.
        return gridSizeString.split(",")[0].toIntOrNull() ?: -1
    }

    /**
     * Moves [items] (the items on the home page, not in folders) into [grid] if it is not the
     * grid they were placed in. Returns null when they were not moved, or else the items that fit
     * on no page, which are to be deleted.
     */
    fun migrateGridIfNeeded(context: Context, grid: GridSpec, items: List<ItemInfo>): List<ItemInfo>? {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        // DeviceGridState: the grid size string, "columns,rows".
        val dest = "${grid.columns},${grid.rows}"
        val src = prefs.getString(KEY_WORKSPACE_SIZE, null)
        // TextLauncher: before any grid was saved, the items are where they were put.
        val srcSize = src?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
        val leftOut = if (src == dest || srcSize == null) {
            null
        } else {
            Log.d(TAG, "Begin grid migration. srcDeviceState: $src, destDeviceState: $dest")
            migrateGrid(context, srcSize[0], srcSize[1], grid.columns, grid.rows, items)
        }
        // Save current configuration, so that the migration does not run again.
        prefs.edit().putString(KEY_WORKSPACE_SIZE, dest).apply()
        return leftOut
    }

    private fun migrateGrid(context: Context, srcColumns: Int, srcRows: Int, columns: Int, rows: Int, items: List<ItemInfo>): List<ItemInfo> {
        loadAllEntries(context, items)
        // shouldMigrateToStrictlyTallerGrid: "We want to add the extra row(s) to the top of the
        // screen, so we shift the grid down."
        if (srcColumns == columns && srcRows < rows) {
            Log.d(TAG, "Migrating to strictly taller grid")
            for (item in items) item.cellY += rows - srcRows
            return emptyList()
        }
        // migrateWorkspace into an empty destination: every item is placed again, in order (DbEntry
        // .compareTo; there is one screen).
        val toPlace = items.sortedWith(compareBy({ it.cellY }, { it.cellX })).toMutableList()
        val placed = solveGridPlacement(columns, rows, toPlace)
        // TextLauncher: the items that are left would go on new pages. A widget's id is given
        // back once the home screen is loaded (LoaderTask's removeGhostWidgets).
        for (item in toPlace) Log.d(TAG, "No room for $item in the ${columns}x$rows grid")
        val placedSet = placed.toSet()
        return items.filter { it !in placedSet }
    }

    /**
     * DbReader.loadAllEntries: the min spans of the items, which they are placed with. A widget's
     * are its provider's (LauncherAppWidgetProviderInfo.getMinSpans, for the cells of the
     * destination grid), or its span in a direction it does not resize in; when its provider is
     * not known, it is assumed that it can be resized down to 2x2. The other items' are 1x1
     * (ItemInfo's).
     */
    private fun loadAllEntries(context: Context, items: List<ItemInfo>) {
        val widgetManagerHelper = WidgetManagerHelper(context)
        for (entry in items) {
            if (entry !is WidgetItemInfo) continue
            val cn = entry.providerName
            val spans: Point? = widgetManagerHelper.getLauncherAppWidgetInfo(entry.appWidgetId, cn)?.minSpans
            if (spans != null) {
                entry.minSpanX = if (spans.x > 0) spans.x else entry.spanX
                entry.minSpanY = if (spans.y > 0) spans.y else entry.spanY
            } else {
                // Assume that the widget be resized down to 2x2
                entry.minSpanY = 2
                entry.minSpanX = entry.minSpanY
            }
        }
    }

    /** GridSizeMigrationLogic.solveGridPlacement for the one page. */
    private fun solveGridPlacement(trgX: Int, trgY: Int, sortedItemsToPlace: MutableList<ItemInfo>): List<ItemInfo> {
        val placementSolution = mutableListOf<ItemInfo>()
        val occupied = GridOccupancy(trgX, trgY)
        var nextX = 0
        var nextY = 0
        val iterator = sortedItemsToPlace.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.minSpanX > trgX || entry.minSpanY > trgY) {
                iterator.remove()
                continue
            }
            val cell = findPlacementForEntry(entry, nextX, nextY, trgX, trgY, occupied) ?: continue
            entry.cellX = cell.cellX
            entry.cellY = cell.cellY
            entry.spanX = cell.spanX
            entry.spanY = cell.spanY
            occupied.markCells(entry, true)
            nextX = entry.cellX + entry.spanX
            nextY = entry.cellY
            placementSolution += entry
            iterator.remove()
        }
        return placementSolution
    }

    /** GridSizeMigrationLogic.findPlacementForEntry */
    private fun findPlacementForEntry(
        entry: ItemInfo,
        startPosX: Int,
        startPosY: Int,
        trgX: Int,
        trgY: Int,
        occupied: GridOccupancy,
    ): CellAndSpan? {
        var newStartPosX = startPosX
        for (y in startPosY until trgY) {
            for (x in newStartPosX until trgX) {
                if (occupied.isRegionVacant(x, y, entry.minSpanX, entry.minSpanY)) {
                    return (CellAndSpan(x, y, entry.minSpanX, entry.minSpanY))
                }
            }
            newStartPosX = 0
        }
        return null
    }
}
