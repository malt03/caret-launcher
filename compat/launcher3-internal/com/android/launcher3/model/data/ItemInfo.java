package com.android.launcher3.model.data;

/**
 * Stand-in for Launcher3's ItemInfo. Only the fields that copied code reads: the cell position and
 * span (util/GridOccupancy) and the rank in a folder (folder/FolderGridOrganizer).
 */
public class ItemInfo {
    public int cellX = -1;
    public int cellY = -1;
    public int spanX = 1;
    public int spanY = 1;
    public int rank = 0;
}
