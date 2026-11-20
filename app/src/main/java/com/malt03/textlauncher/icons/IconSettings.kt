package com.malt03.textlauncher.icons

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.shapes.IconShapeModel
import com.android.launcher3.shapes.ShapesProvider

/**
 * Themed icons on/off and the icon shape (docs/spec.md 7.3). Changed from the system
 * "Wallpaper & style" app through [com.malt03.textlauncher.provider.CustomizationProvider].
 */
class IconSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("icon_settings", Context.MODE_PRIVATE)
    private val listeners = mutableListOf<() -> Unit>()
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        listeners.toList().forEach { it() }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    var isThemed: Boolean
        get() = prefs.getBoolean(KEY_THEMED, true)
        set(value) = prefs.edit().putBoolean(KEY_THEMED, value).apply()

    /** The selected shape, or the first one (circle) when nothing valid is stored. */
    val shape: IconShapeModel
        get() {
            val stored = prefs.getString(KEY_SHAPE, null)
            return ShapesProvider.iconShapes.firstOrNull { it.key == stored } ?: ShapesProvider.iconShapes.first()
        }

    /** Returns false when [key] is not a known shape. */
    fun setShape(key: String): Boolean {
        if (ShapesProvider.iconShapes.none { it.key == key }) return false
        prefs.edit().putString(KEY_SHAPE, key).apply()
        return true
    }

    /** Called on the main thread after any change. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    companion object {
        private const val KEY_THEMED = "themed_icons"
        private const val KEY_SHAPE = "icon_shape"
    }
}
