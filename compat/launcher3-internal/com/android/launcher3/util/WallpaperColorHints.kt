/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.util

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_SYSTEM
import android.app.WallpaperManager.OnColorsChangedListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread

/**
 * This class caches the system's wallpaper color hints for use by other classes as a performance
 * enhancer. It also centralizes all the WallpaperManager color hint code in one location.
 *
 * Stand-in for Launcher3's WallpaperColorHints (android-17.0.0_r1). CaretLauncher: no Dagger; one
 * instance for the app (INSTANCE), listening for as long as the app runs, on the main thread.
 */
class WallpaperColorHints private constructor(private val context: Context) {

    private val wallpaperManager
        get() = context.getSystemService(WallpaperManager::class.java)!!

    var colors: WallpaperColors? = wallpaperManager.getWallpaperColors(FLAG_SYSTEM)
        private set

    val hints: Int
        get() = colors?.colorHints ?: 0

    private val onColorHintsChangedListeners = mutableListOf<OnColorHintListener>()

    init {
        val onColorsChangedListener = OnColorsChangedListener { colors, which ->
            onColorsChanged(colors, which)
        }
        // CaretLauncher: registered right away (UI_HELPER_EXECUTOR in Launcher3).
        wallpaperManager.addOnColorsChangedListener(
            onColorsChangedListener,
            Handler(Looper.getMainLooper()),
        )
    }

    @MainThread
    private fun onColorsChanged(colors: WallpaperColors?, which: Int) {
        if ((which and FLAG_SYSTEM) != 0) {
            val oldHints = hints
            this.colors = colors
            val newHints = hints
            if (oldHints != newHints) {
                onColorHintsChangedListeners.forEach { it.onColorHintsChanged(newHints) }
            }
        }
    }

    fun registerOnColorHintsChangedListener(listener: OnColorHintListener) {
        onColorHintsChangedListeners.add(listener)
    }

    fun unregisterOnColorsChangedListener(listener: OnColorHintListener) {
        onColorHintsChangedListeners.remove(listener)
    }

    companion object {
        /** CaretLauncher: in place of DaggerSingletonObject. */
        @JvmField val INSTANCE = Instance()

        @JvmStatic fun get(context: Context): WallpaperColorHints = INSTANCE.get(context)
    }

    class Instance {
        private var hints: WallpaperColorHints? = null

        @MainThread
        fun get(context: Context): WallpaperColorHints =
            hints ?: WallpaperColorHints(context.applicationContext).also { hints = it }
    }
}

interface OnColorHintListener {
    fun onColorHintsChanged(colorHints: Int)
}
