package com.malt03.textlauncher.icons

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.PathParser
import com.android.launcher3.Flags
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

    private var isThemed = settings.isThemed
    private var iconShape: IconShape = createIconShape(settings.shape.pathString)

    // Used only on [executor]: BaseIconFactory is not thread safe.
    private var factory: BaseIconFactory = createFactory(isThemed)

    init {
        settings.addListener(::onSettingsChanged)
        updateFolderShape()
    }

    /** ThemeManager.parseIconState: the folder shape follows the icon shape's folderRadiusRatio. */
    private fun updateFolderShape() {
        ThemeManager.INSTANCE.get(context).setFolderRadiusRatio(settings.shape.folderRadiusRatio)
    }

    /** Called on the main thread when every icon has to be rebound. */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        changeListeners -= listener
    }

    fun cached(entry: AppEntry): BitmapInfo? = cache.get(entry.key)

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

    fun newDrawable(info: BitmapInfo): Drawable =
        info.newIcon(context, if (isThemed) BitmapInfo.FLAG_THEMED else 0, iconShape)

    private fun onSettingsChanged() {
        val themed = settings.isThemed
        val shapePath = settings.shape.pathString
        generation++
        cache.evictAll()
        pending.clear()
        isThemed = themed
        iconShape = createIconShape(shapePath)
        updateFolderShape()
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
        const val SHAPE_PATH_SIZE = 100f
    }
}
