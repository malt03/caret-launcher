package com.malt03.textlauncher.ui.home

import android.content.Context
import android.util.Log
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.GridOccupancy
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
     * Moves [items] (the items on the home page, not in folders) into [grid] if it is not the
     * grid they were placed in. Returns true when they changed.
     */
    fun migrateGridIfNeeded(context: Context, grid: GridSpec, items: MutableList<ItemInfo>): Boolean {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        // DeviceGridState: the grid size string, "columns,rows".
        val dest = "${grid.columns},${grid.rows}"
        val src = prefs.getString(KEY_WORKSPACE_SIZE, null)
        // TextLauncher: before any grid was saved, the items are where they were put.
        val srcSize = src?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
        val changed = if (src == dest || srcSize == null) {
            false
        } else {
            Log.d(TAG, "Begin grid migration. srcDeviceState: $src, destDeviceState: $dest")
            migrateGrid(srcSize[0], srcSize[1], grid.columns, grid.rows, items)
            true
        }
        // Save current configuration, so that the migration does not run again.
        prefs.edit().putString(KEY_WORKSPACE_SIZE, dest).apply()
        return changed
    }

    private fun migrateGrid(srcColumns: Int, srcRows: Int, columns: Int, rows: Int, items: MutableList<ItemInfo>) {
        // shouldMigrateToStrictlyTallerGrid: "We want to add the extra row(s) to the top of the
        // screen, so we shift the grid down."
        if (srcColumns == columns && srcRows < rows) {
            Log.d(TAG, "Migrating to strictly taller grid")
            for (item in items) item.cellY += rows - srcRows
            return
        }
        // migrateWorkspace into an empty destination: every item is placed again, in order (DbEntry
        // .compareTo; there is one screen).
        val toPlace = items.sortedWith(compareBy({ it.cellY }, { it.cellX })).toMutableList()
        val placed = solveGridPlacement(columns, rows, toPlace)
        // TextLauncher: the items that are left would go on new pages.
        for (item in toPlace) Log.d(TAG, "No room for $item in the ${columns}x$rows grid")
        items.retainAll(placed.toSet())
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
            // DbEntry's minSpanX and minSpanY: the item's span (there are no widgets).
            if (entry.spanX > trgX || entry.spanY > trgY) {
                iterator.remove()
                continue
            }
            val cell = findPlacementForEntry(entry, nextX, nextY, trgX, trgY, occupied) ?: continue
            entry.cellX = cell.first
            entry.cellY = cell.second
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
    ): Pair<Int, Int>? {
        var newStartPosX = startPosX
        for (y in startPosY until trgY) {
            for (x in newStartPosX until trgX) {
                if (occupied.isRegionVacant(x, y, entry.spanX, entry.spanY)) return x to y
            }
            newStartPosX = 0
        }
        return null
    }
}
