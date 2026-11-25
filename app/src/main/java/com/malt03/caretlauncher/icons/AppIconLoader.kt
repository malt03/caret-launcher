package com.malt03.caretlauncher.icons

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.core.graphics.PathParser
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.CacheableShortcutCachingLogic
import com.android.launcher3.icons.CacheableShortcutInfo
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.IconShape
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.malt03.caretlauncher.R

/**
 * Looks up app and shortcut icons in Launcher3's [IconCache] (android-17.0.0_r1), which keeps them
 * in memory and on disk. The all apps list's icons are in its AppInfos (AllAppsList, which
 * [com.malt03.caretlauncher.data.AppRepository] keeps), and the home screen's in its items.
 *
 * - The icons are made by [LauncherIcons]: drawn full bleed and clipped to the selected
 *   [IconShape] when drawn.
 * - With themed icons on, MonoIconThemeController(shouldForceThemeIcon = true) themes the
 *   monochrome layer, generating one for apps that do not provide it (ThemeManager.iconState).
 */
class AppIconLoader(private val context: Context, private val settings: IconSettings) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.icon_bitmap_size)

    /** InvariantDeviceProfile.fillResIconDpi */
    private val iconDpi get() = context.resources.configuration.densityDpi

    @Volatile private var isThemed = settings.isThemed
    private var iconShape: IconShape = createIconShape(settings.shape.pathString)

    /** LauncherIcons.IconPool, with the theme controller of ThemeManager.iconState. */
    private val iconPool = LauncherIcons.IconPool(context) {
        LauncherIcons.Params(iconDpi, iconSize, if (isThemed) MonoIconThemeController(shouldForceThemeIcon = true) else null)
    }

    /** Launcher3's icon cache, used on Launcher3's model thread (Executors.MODEL_EXECUTOR). */
    val iconCache = IconCache(
        context,
        DB_NAME,
        MODEL_EXECUTOR.looper,
        iconDpi,
        iconSize,
        IconProvider(context),
        iconPool,
    ).also { LauncherAppState.getInstance(context).setIconCache(it) }

    private val changeListeners = mutableListOf<() -> Unit>()

    /** Bumped when the icons are made again; results looked up for an older one are dropped. */
    private var generation = 0

    init {
        settings.addListener(::onSettingsChanged)
        updateThemeManager()
        // InvariantDeviceProfile's locale receiver: a new locale is a new model state
        // (InvariantDeviceProfile.toModelState has it), so ModelInitializer.refreshAndReloadLauncher
        // looks the icons and the app names up again.
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = refreshIcons()
            },
            IntentFilter(Intent.ACTION_LOCALE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * ThemeManager.parseIconState: the icon state that ItemInfoWithIcon.newIcon reads, and the
     * icon and folder shapes (the folder's follows the icon shape's folderRadiusRatio).
     */
    private fun updateThemeManager() {
        val themeManager = ThemeManager.INSTANCE.get(context)
        themeManager.setIconState(isThemed, iconShape)
        themeManager.setShapes(settings.shape.pathString, settings.shape.folderRadiusRatio)
    }

    /**
     * Called on the main thread when every icon has to be made again: the icon theme or shape
     * changed (ModelInitializer.refreshAndReloadLauncher).
     */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    /** [getTitlesAndIcons] for one item. */
    fun getTitleAndIcon(info: WorkspaceItemInfo, lookupFlag: CacheLookupFlag, onLoaded: () -> Unit) {
        val user = info.user
        val launchIntent = Intent(info.intent)
        val requestedGeneration = generation
        iconCache.workerHandler.post {
            // CaretLauncher: looked up into a copy, since the item belongs to the main thread.
            val result = WorkspaceItemInfo().apply {
                itemType = ITEM_TYPE_APPLICATION
                this.user = user
                intent = launchIntent
            }
            iconCache.getTitleAndIcon(result, lookupFlag)
            mainHandler.post {
                if (requestedGeneration != generation) return@post
                info.bitmap = result.bitmap
                if (!result.title.isNullOrEmpty()) {
                    info.title = result.title
                    info.contentDescription = result.contentDescription
                }
                onLoaded()
            }
        }
    }

    private fun onSettingsChanged() {
        val themed = settings.isThemed
        val themeChanged = themed != isThemed
        isThemed = themed
        iconShape = createIconShape(settings.shape.pathString)
        updateThemeManager()
        if (themeChanged) {
            // ModelInitializer.refreshAndReloadLauncher for a theme change: the icons are made
            // again. A shape change only rebinds, since the shape is applied when drawn.
            refreshIcons()
        } else {
            changeListeners.toList().forEach { it() }
        }
    }

    /** ModelInitializer.refreshAndReloadLauncher: the icons and names are made again and reloaded. */
    private fun refreshIcons() {
        generation++
        iconPool.clear()
        iconCache.updateIconParams(iconDpi, iconSize)
        changeListeners.toList().forEach { it() }
    }

    /** ThemeManager.createIconShape: the shape path (a 100x100 SVG path) scaled to the icon size. */
    private fun createIconShape(pathString: String): IconShape {
        val path = PathParser.createPathFromPathData(pathString)
        val scale = iconSize / SHAPE_PATH_SIZE
        path.transform(Matrix().apply { setScale(scale, scale) })
        return GraphicsUtils.generateIconShape(iconSize, path)
    }

    private companion object {
        /** LauncherFiles.APP_ICONS_DB */
        const val DB_NAME = "app_icons.db"

        const val SHAPE_PATH_SIZE = 100f
    }
}
