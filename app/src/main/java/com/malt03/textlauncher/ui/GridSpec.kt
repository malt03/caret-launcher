package com.malt03.textlauncher.ui

import android.content.Context

/**
 * Grid dimensions, following Launcher3 res/xml/device_profiles.xml (android-17.0.0_r1):
 * phones narrower than the "Large Phone" profile (406dp) use the 4_by_4 grid with 54dp icons
 * (folders 4x3), wider ones the 5_by_5 grid with 56dp icons (folders 4x4).
 *
 * Launcher3 picks the closest display option by width and height (InvariantDeviceProfile); porting
 * that is part of the home grid work. This is the width-only approximation until then.
 */
data class GridSpec(
    val columns: Int,
    val iconSizePx: Int,
    val iconTextSizeSp: Float,
    /** Launcher3 allAppsCellHeight. */
    val allAppsCellHeightPx: Int,
    /** Launcher3 numFolderColumns / numFolderRows. */
    val folderColumns: Int,
    val folderRows: Int,
) {
    companion object {
        fun from(context: Context): GridSpec {
            val metrics = context.resources.displayMetrics
            val widthDp = metrics.widthPixels / metrics.density
            val large = widthDp >= 406
            return GridSpec(
                columns = if (large) 5 else 4,
                iconSizePx = ((if (large) 56 else 54) * metrics.density).toInt(),
                iconTextSizeSp = if (large) 14.4f else 13f,
                allAppsCellHeightPx = (104 * metrics.density).toInt(),
                folderColumns = 4,
                folderRows = if (large) 4 else 3,
            )
        }
    }
}
