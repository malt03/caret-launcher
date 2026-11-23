package com.malt03.textlauncher.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AppEntry

/**
 * Looks up app and shortcut icons in Launcher3's [IconCache] (android-17.0.0_r1), which keeps them
 * in memory and on disk, and holds the apps' icons for the views as AllAppsList's AppInfos do.
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

    /** The icons of the apps by [AppEntry.key] (AllAppsList's AppInfo.bitmap); main thread only. */
    private val icons = HashMap<String, BitmapInfo>()
    private val pending = HashMap<Pair<String, CacheLookupFlag>, MutableList<(BitmapInfo) -> Unit>>()
    private val changeListeners = mutableListOf<(Set<String>?) -> Unit>()

    /** Bumped when the icons are made again; results looked up for an older one are dropped. */
    private var generation = 0
    private var isCacheUpdated = false

    init {
        settings.addListener(::onSettingsChanged)
        updateThemeManager()
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
     * Called on the main thread when icons have to be rebound: the icons of the packages given
     * (LauncherModel.onPackageIconsUpdated), or every icon (null: the icon theme or shape changed,
     * ModelInitializer.refreshAndReloadLauncher).
     */
    fun addChangeListener(listener: (Set<String>?) -> Unit) {
        changeListeners += listener
    }

    fun removeChangeListener(listener: (Set<String>?) -> Unit) {
        changeListeners -= listener
    }

    fun cached(entry: AppEntry): BitmapInfo? = icons[entry.key]

    /**
     * AllAppsList.add, or WorkspaceItemProcessor for the home screen ([lookupFlag] DESKTOP_ICON_FLAG):
     * the app's icon (IconCache.getTitleAndIcon). An icon held with less than [lookupFlag] asks for
     * is looked up again (BubbleTextView.verifyHighRes). Calls [onLoaded] on the main thread,
     * immediately when the icon is already held.
     */
    fun load(entry: AppEntry, lookupFlag: CacheLookupFlag = DEFAULT_LOOKUP_FLAG, onLoaded: (BitmapInfo) -> Unit) {
        icons[entry.key]?.takeUnless { it.matchingLookupFlag.isVisuallyLessThan(lookupFlag) }?.let {
            onLoaded(it)
            return
        }
        val requestKey = entry.key to lookupFlag
        val waiting = pending[requestKey]
        if (waiting != null) {
            waiting += onLoaded
            return
        }
        pending[requestKey] = mutableListOf(onLoaded)
        val requestedGeneration = generation
        iconCache.workerHandler.post {
            // AppInfo(LauncherActivityInfo, UserHandle, ...)
            val info = AppInfo().apply {
                user = entry.user
                componentName = entry.component
                intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
            }
            iconCache.getTitleAndIcon(info, entry.info, lookupFlag)
            val bitmap = info.bitmap
            mainHandler.post {
                if (requestedGeneration != generation) return@post
                val held = icons[entry.key]
                if (held == null || !bitmap.matchingLookupFlag.isVisuallyLessThan(held.matchingLookupFlag)) {
                    icons[entry.key] = bitmap
                }
                pending.remove(requestKey)?.forEach { it(bitmap) }
            }
        }
    }

    /**
     * Calls [onLoaded] on the main thread once every icon is held. Launcher3 binds the home screen
     * with its icons already looked up; the folder preview sizes itself from them.
     */
    fun loadAll(entries: Collection<AppEntry>, lookupFlag: CacheLookupFlag, onLoaded: () -> Unit) {
        var remaining = entries.size
        if (remaining == 0) {
            onLoaded()
            return
        }
        for (entry in entries) {
            load(entry, lookupFlag) {
                remaining--
                if (remaining == 0) onLoaded()
            }
        }
    }

    /**
     * IconCache.getShortcutIcon for an app shortcut on the home screen: the shortcut's icon, badged
     * with its app's icon. When the shortcut has no icon, [info]'s icon is kept (the one saved with
     * the home screen). Calls [onLoaded] on the main thread with the icon for [info].
     */
    fun loadShortcut(info: WorkspaceItemInfo, shortcut: ShortcutInfo, onLoaded: (BitmapInfo) -> Unit) {
        val oldIcon = info.bitmap
        val requestedGeneration = generation
        iconCache.workerHandler.post {
            // TextLauncher: looked up into a copy, since the item belongs to the main thread.
            val result = WorkspaceItemInfo().apply { bitmap = oldIcon }
            iconCache.getShortcutIcon(result, shortcut)
            val bitmap = result.bitmap
            mainHandler.post {
                if (requestedGeneration == generation) onLoaded(bitmap)
            }
        }
    }

    /**
     * IconCache.getTitleAndIcon for an app that is present but not available (WorkspaceItemProcessor
     * keeps it with FLAG_DISABLED_NOT_AVAILABLE): no activity to look up, so the icon and title saved
     * in the cache, else the package's (withUsePackageIcon). Calls [onLoaded] on the main thread.
     */
    fun loadUnavailable(component: ComponentName, user: UserHandle, onLoaded: (BitmapInfo, CharSequence?) -> Unit) {
        val requestedGeneration = generation
        iconCache.workerHandler.post {
            val info = AppInfo().apply {
                this.user = user
                componentName = component
                intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
            }
            iconCache.getTitleAndIcon(info, DESKTOP_ICON_FLAG.withUsePackageIcon()) { null }
            val bitmap = info.bitmap
            val title = info.title
            mainHandler.post {
                if (requestedGeneration == generation) onLoaded(bitmap, title)
            }
        }
    }

    /**
     * IconCache.getDefaultIcon: the icon of an item whose own icon is not known, such as an app
     * shortcut that could not be looked up. Calls [onLoaded] on the main thread.
     */
    fun loadDefault(onLoaded: (BitmapInfo) -> Unit) {
        val requestedGeneration = generation
        iconCache.workerHandler.post {
            val bitmap = iconCache.getDefaultIcon(Process.myUserHandle())
            mainHandler.post {
                if (requestedGeneration == generation) onLoaded(bitmap)
            }
        }
    }

    /**
     * PackageUpdatedTask: the package's icons are looked up again (OP_ADD / OP_UPDATE) or dropped
     * ([removed], OP_REMOVE) in the cache. Calls [then] on the main thread afterwards.
     */
    fun updatePackage(packageName: String, user: UserHandle, removed: Boolean, then: () -> Unit) {
        iconCache.workerHandler.post {
            if (removed) {
                iconCache.removeIconsForPkg(packageName, user)
            } else {
                iconCache.updateIconsForPkg(packageName, user)
            }
            mainHandler.post {
                forgetPackages(setOf(packageName))
                then()
            }
        }
    }

    /**
     * LoaderTask.loadAllSurfacesOrdered: once per process after the first binding, brings the
     * cache up to date with the installed [apps], the [homeShortcuts] and every other shortcut,
     * and drops the entries of what is gone. Icons that changed are rebound.
     */
    fun updateIconCacheOnce(apps: List<AppEntry>, homeShortcuts: Collection<ShortcutInfo>) {
        if (isCacheUpdated) return
        isCacheUpdated = true
        val activities = apps.map { it.info }
        val user = Process.myUserHandle()
        iconCache.workerHandler.post {
            val updateHandler = iconCache.getUpdateHandler()
            updateHandler.updateIcons(activities, LauncherActivityCachingLogic, ::onPackageIconsUpdated)
            updateHandler.updateIcons(
                CacheableShortcutInfo.convertShortcutsToCacheableShortcuts(homeShortcuts.toList(), activities),
                CacheableShortcutCachingLogic,
                ::onPackageIconsUpdated,
            )
            // loadDeepShortcuts
            val allDeepShortcuts =
                if (context.getSystemService(LauncherApps::class.java).hasShortcutHostPermission()) {
                    ShortcutRequest(context, user).query(ShortcutRequest.ALL)
                } else {
                    emptyList()
                }
            updateHandler.updateIcons(
                CacheableShortcutInfo.convertShortcutsToCacheableShortcuts(allDeepShortcuts, activities),
                CacheableShortcutCachingLogic,
            ) { _, _ -> }
            updateHandler.finish()
        }
    }

    /** LauncherModel.onPackageIconsUpdated: called on the cache's thread. */
    private fun onPackageIconsUpdated(packages: HashSet<String>, user: UserHandle) {
        val updated = HashSet(packages)
        mainHandler.post {
            forgetPackages(updated)
            changeListeners.toList().forEach { it(updated) }
        }
    }

    private fun forgetPackages(packages: Set<String>) {
        icons.keys.removeAll { AppEntry.componentOf(it)?.packageName in packages }
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
            generation++
            icons.clear()
            pending.clear()
            iconPool.clear()
            iconCache.updateIconParams(iconDpi, iconSize)
        }
        changeListeners.toList().forEach { it(null) }
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
