package com.malt03.textlauncher.ui

import android.content.Context
import com.android.launcher3.R

/**
 * LauncherState (android-17.0.0_r1), with the states TextLauncher has: the home screen (NORMAL),
 * edit mode (SPRING_LOADED, docs/spec.md 5.4) and the all apps list (ALL_APPS, docs/spec.md 6).
 *
 * TextLauncher: SPRING_LOADED is also the state while a widget's resize frame is open, which is
 * edit mode too (docs/spec.md 5.4). Its look (the shrunk home screen, the page background, the
 * wallpaper scrim and depth) is animated by SpringLoadedListener with the drag, not by the state
 * manager's handlers.
 */
enum class LauncherState {
    NORMAL,
    SPRING_LOADED,
    ALL_APPS,
    ;

    /**
     * LauncherState.getTransitionDuration: config_allAppsOpenDuration and
     * config_allAppsCloseDuration for ALL_APPS (AllAppsState); the other states change at once here.
     */
    fun getTransitionDuration(isToState: Boolean): Long = when (this) {
        ALL_APPS -> if (isToState) ALL_APPS_OPEN_DURATION_MS else ALL_APPS_CLOSE_DURATION_MS
        else -> 0L
    }

    /**
     * LauncherState.getVerticalProgress: where the all apps list is. TextLauncher: 1 is open and 0
     * closed, the other way round from Launcher3's.
     */
    fun getVerticalProgress(): Float = if (this == ALL_APPS) 1f else 0f

    /**
     * LauncherState.getDepth: BottomSheetProfile.getBottomSheetDepth for ALL_APPS, which zooms the
     * wallpaper to config_wallpaperMaxScale times workspace_content_scale (1.1 and 0.97 on phones).
     * TextLauncher: SPRING_LOADED's (SpringLoadedState.DEPTH_15_PERCENT) is SpringLoadedListener's
     * (DepthController.springLoadedDepth).
     */
    fun getDepth(context: Context): Float {
        if (this != ALL_APPS) return 0f
        val maxWallpaperScale = 1.1f
        val contentScale = context.resources.getFloat(R.dimen.workspace_content_scale)
        // Utilities.mapToRange(maxWallpaperScale * contentScale, maxWallpaperScale, 1f, 0f, 1f, LINEAR)
        return (maxWallpaperScale * contentScale - maxWallpaperScale) / (1f - maxWallpaperScale)
    }

    /**
     * The scale of LauncherState.getWorkspaceScaleAndTranslation: workspace_content_scale for
     * ALL_APPS on a bottom sheet (AllAppsState). TextLauncher: SPRING_LOADED's is
     * SpringLoadedListener's.
     */
    fun getWorkspaceScale(context: Context): Float =
        if (this == ALL_APPS) context.resources.getFloat(R.dimen.workspace_content_scale) else 1f

    private companion object {
        /** config_allAppsOpenDuration */
        const val ALL_APPS_OPEN_DURATION_MS = 600L

        /** config_allAppsCloseDuration */
        const val ALL_APPS_CLOSE_DURATION_MS = 300L
    }
}
