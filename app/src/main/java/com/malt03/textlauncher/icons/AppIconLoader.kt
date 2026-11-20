package com.malt03.textlauncher.icons

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AppEntry
import java.util.concurrent.Executors

/**
 * Builds app icons with iconloaderlib (third_party/systemui) and caches them in memory.
 * Theming and icon shapes are wired up separately (docs/spec.md 7.3).
 */
class AppIconLoader(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.icon_bitmap_size)
    private val cache = LruCache<String, BitmapInfo>(512)
    private val pending = HashMap<String, MutableList<(BitmapInfo) -> Unit>>()

    // Used only on [executor]: BaseIconFactory is not thread safe.
    private val factory by lazy {
        BaseIconFactory(context, context.resources.configuration.densityDpi, iconSize)
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
        executor.execute {
            val drawable = entry.info.getIcon(context.resources.configuration.densityDpi)
            val info = factory.createBadgedIconBitmap(
                drawable,
                BaseIconFactory.IconOptions().setUser(entry.user),
            )
            mainHandler.post {
                cache.put(entry.key, info)
                pending.remove(entry.key)?.forEach { it(info) }
            }
        }
    }

    fun newDrawable(info: BitmapInfo): Drawable = info.newIcon(context)

    fun clear() {
        cache.evictAll()
    }
}
