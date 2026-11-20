package com.android.launcher3.model.data;

/**
 * Stand-in for Launcher3's ItemInfo. Only the cell position and span, which
 * util/GridOccupancy.markCells(ItemInfo, boolean) reads, are kept.
 */
public class ItemInfo {
    public int cellX = -1;
    public int cellY = -1;
    public int spanX = 1;
    public int spanY = 1;
}
