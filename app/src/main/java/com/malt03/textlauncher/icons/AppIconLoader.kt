package com.malt03.textlauncher.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.PathParser
import com.android.launcher3.Flags
import com.android.launcher3.LauncherAppState
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.IconShape
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.graphics.ThemeManager
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AppEntry
import java.util.concurrent.Executors

/**
 * Builds app icons with iconloaderlib (third_party/systemui) the way Launcher3 does
 * (LauncherIcons, ThemeManager, MonoIconThemeFactory at android-17.0.0_r1), and caches them.
 *
 * - Icons are drawn full bleed and clipped to the selected [IconShape].
 * - With themed icons on, MonoIconThemeController(shouldForceThemeIcon = true) themes the
 *   monochrome layer, generating one for apps that do not provide it.
 */
class AppIconLoader(private val context: Context, private val settings: IconSettings) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.icon_bitmap_size)
    private val cache = LruCache<String, BitmapInfo>(512)
    private val pending = HashMap<String, MutableList<(BitmapInfo) -> Unit>>()
    private val changeListeners = mutableListOf<() -> Unit>()

    /** Bumped on every settings change; results computed for an older generation are dropped. */
    private var generation = 0

    @Volatile private var isThemed = settings.isThemed
    private var iconShape: IconShape = createIconShape(settings.shape.pathString)

    // Used only on [executor]: BaseIconFactory is not thread safe.
    private var factory: BaseIconFactory = createFactory(isThemed)

    init {
        settings.addListener(::onSettingsChanged)
        updateThemeManager()
        LauncherAppState.getInstance(context).iconCache.setShortcutIconFactory(ShortcutIconFactory())
    }

    /**
     * CacheableShortcutCachingLogic.loadIcon for the app shortcuts in the long-press menu. Runs on
     * Launcher3's model thread, so it makes its own factory (LauncherIcons come from a pool).
     */
    private inner class ShortcutIconFactory : IconCache.ShortcutIconFactory {
        private val appContext = this@AppIconLoader.context

        override fun getContext(): Context = appContext

        override fun getFullResIconDpi(): Int = appContext.resources.configuration.densityDpi

        override fun createShortcutIconBitmap(icon: Drawable): BitmapInfo =
            createFactory(isThemed).createBadgedIconBitmap(
                icon,
                BaseIconFactory.IconOptions()
                    .setExtractedColor(GraphicsUtils.getAttrColor(appContext, android.R.attr.colorAccent)),
            )
    }

    /**
     * ThemeManager.parseIconState: the icon state that ItemInfoWithIcon.newIcon reads, and the
     * folder shape, which follows the icon shape's folderRadiusRatio.
     */
    private fun updateThemeManager() {
        val themeManager = ThemeManager.INSTANCE.get(context)
        themeManager.setIconState(isThemed, iconShape)
        themeManager.setFolderRadiusRatio(settings.shape.folderRadiusRatio)
    }

    /** Called on the main thread when every icon has to be rebound. */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners -= listener
    }

    fun cached(entry: AppEntry): BitmapInfo? = cache.get(entry.key)

    /** The cached icon of the app shortcut loaded with [loadShortcut] under [key]. */
    fun cachedShortcut(key: String): BitmapInfo? = cache.get(key)

    /** Drops the cached icons of app shortcuts, which their apps may have changed. */
    fun forgetShortcuts(keys: Collection<String>) {
        keys.forEach { cache.remove(it) }
    }

    /**
     * The icon of an app shortcut on the home screen (IconCache.getShortcutIcon): the shortcut's
     * icon, badged with its app's icon. Calls [onLoaded] on the main thread; cached by [key] like
     * app icons.
     */
    fun loadShortcut(key: String, shortcut: ShortcutInfo, app: AppEntry?, onLoaded: (BitmapInfo) -> Unit) {
        cache.get(key)?.let { onLoaded(it); return }
        val waiting = pending[key]
        if (waiting != null) {
            waiting += onLoaded
            return
        }
        pending[key] = mutableListOf(onLoaded)
        val requestedGeneration = generation
        val appBitmap = app?.let { cache.get(it.key) }
        executor.execute {
            val dpi = context.resources.configuration.densityDpi
            val badge = appBitmap ?: app?.let {
                factory.createBadgedIconBitmap(it.info.getIcon(dpi), BaseIconFactory.IconOptions().setUser(it.user))
            }
            val drawable = try {
                context.getSystemService(LauncherApps::class.java).getShortcutIconDrawable(shortcut, dpi)
            } catch (e: Exception) {
                null
            }
            val icon = drawable?.let {
                factory.createBadgedIconBitmap(
                    it,
                    BaseIconFactory.IconOptions()
                        .setExtractedColor(GraphicsUtils.getAttrColor(context, android.R.attr.colorAccent)),
                )
            }
            // Without its own icon, a shortcut shows its app's icon, or the default one.
            val info = icon?.withBadgeInfo(badge) ?: badge
                ?: factory.createBadgedIconBitmap(context.packageManager.defaultActivityIcon, BaseIconFactory.IconOptions())
            mainHandler.post {
                if (requestedGeneration != generation) return@post
                cache.put(key, info)
                pending.remove(key)?.forEach { it(info) }
            }
        }
    }

    /**
     * IconCache.getDefaultIcon: the icon of an item whose own icon is not known, such as an app
     * shortcut that could not be looked up. Calls [onLoaded] on the main thread.
     */
    fun loadDefault(onLoaded: (BitmapInfo) -> Unit) {
        cache.get(DEFAULT_KEY)?.let { onLoaded(it); return }
        val waiting = pending[DEFAULT_KEY]
        if (waiting != null) {
            waiting += onLoaded
            return
        }
        pending[DEFAULT_KEY] = mutableListOf(onLoaded)
        val requestedGeneration = generation
        executor.execute {
            val info = factory.createBadgedIconBitmap(context.packageManager.defaultActivityIcon, BaseIconFactory.IconOptions())
            mainHandler.post {
                if (requestedGeneration != generation) return@post
                cache.put(DEFAULT_KEY, info)
                pending.remove(DEFAULT_KEY)?.forEach { it(info) }
            }
        }
    }

    /** The cached icon of an app, the badge of its shortcuts (IconCache.getShortcutInfoBadge). */
    fun badgeFor(app: AppEntry?): BitmapInfo? = app?.let { cache.get(it.key) }

    /** Calls [onLoaded] on the main thread, immediately when the icon is cached. */
    fun load(entry: AppEntry, onLoaded: (BitmapInfo) -> Unit) {
        cache.get(entry.key)?.let { onLoaded(it); return }
        val waiting = pending[entry.key]
        if (waiting != null) {
            waiting += onLoaded
            return
        }
        pending[entry.key] = mutableListOf(onLoaded)
        val requestedGeneration = generation
        executor.execute {
            val drawable = entry.info.getIcon(context.resources.configuration.densityDpi)
            val info = factory.createBadgedIconBitmap(
                drawable,
                BaseIconFactory.IconOptions().setUser(entry.user),
            )
            mainHandler.post {
                if (requestedGeneration != generation) return@post
                cache.put(entry.key, info)
                pending.remove(entry.key)?.forEach { it(info) }
            }
        }
    }

    /**
     * Calls [onLoaded] on the main thread once every icon is cached. Launcher3 binds the home
     * screen with its icons already looked up; the folder preview sizes itself from them.
     */
    fun loadAll(entries: Collection<AppEntry>, onLoaded: () -> Unit) {
        var remaining = entries.size
        if (remaining == 0) {
            onLoaded()
            return
        }
        for (entry in entries) {
            load(entry) {
                remaining--
                if (remaining == 0) onLoaded()
            }
        }
    }

    private fun onSettingsChanged() {
        val themed = settings.isThemed
        val shapePath = settings.shape.pathString
        generation++
        cache.evictAll()
        pending.clear()
        isThemed = themed
        iconShape = createIconShape(shapePath)
        updateThemeManager()
        executor.execute { factory = createFactory(themed) }
        changeListeners.toList().forEach { it() }
    }

    private fun createFactory(themed: Boolean) = BaseIconFactory(
        context,
        context.resources.configuration.densityDpi,
        iconSize,
        /* drawFullBleedIcons */ Flags.enableLauncherIconShapes(),
        if (themed) MonoIconThemeController(shouldForceThemeIcon = true) else null,
    )

    /** ThemeManager.createIconShape: the shape path (a 100x100 SVG path) scaled to the icon size. */
    private fun createIconShape(pathString: String): IconShape {
        val path = PathParser.createPathFromPathData(pathString)
        val scale = iconSize / SHAPE_PATH_SIZE
        path.transform(Matrix().apply { setScale(scale, scale) })
        return GraphicsUtils.generateIconShape(iconSize, path)
    }

    private companion object {
        private const val DEFAULT_KEY = "default:"

        const val SHAPE_PATH_SIZE = 100f
    }
}
