/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.icons

import android.content.Context
import com.android.launcher3.Flags
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Wrapper class to provide access to [BaseIconFactory] and also to provide pool of this class that
 * are threadsafe.
 *
 * Stand-in for Launcher3's LauncherIcons (android-17.0.0_r1). CaretLauncher: no Dagger; the pool
 * takes the icon density, size and theme controller from the app (InvariantDeviceProfile and
 * ThemeManager in Launcher3), and the user info is the default one (personal profile only).
 */
class LauncherIcons
internal constructor(
    context: Context,
    fillResIconDpi: Int,
    iconBitmapSize: Int,
    themeController: IconThemeController?,
    private val pool: ConcurrentLinkedQueue<LauncherIcons>,
) :
    BaseIconFactory(
        context,
        fillResIconDpi,
        iconBitmapSize,
        /* drawFullBleedIcons */ Flags.enableLauncherIconShapes(),
        themeController,
    ),
    AutoCloseable {

    /** Recycles a LauncherIcons that may be in-use. */
    fun recycle() {
        clear()
        pool.add(this)
    }

    override fun close() {
        recycle()
    }

    /** CaretLauncher: [params] gives the density, size and theme of the icons to make. */
    class IconPool(private val context: Context, private val params: () -> Params) {
        private var pool = ConcurrentLinkedQueue<LauncherIcons>()

        fun obtain(): LauncherIcons =
            pool.let {
                it.poll()
                    ?: params().let { p ->
                        LauncherIcons(context, p.fillResIconDpi, p.iconBitmapSize, p.themeController, it)
                    }
            }

        fun clear() {
            pool = ConcurrentLinkedQueue()
        }
    }

    companion object {
        /**
         * Return a new LauncherIcons instance from the global pool. Allows us to avoid allocating
         * new objects in many cases.
         *
         * CaretLauncher: the pool is the icon cache's (the app's), not the Dagger component's.
         */
        @JvmStatic
        fun obtain(context: Context): LauncherIcons =
            com.android.launcher3.LauncherAppState.getInstance(context).iconCache.iconFactory
                as LauncherIcons
    }

    /** CaretLauncher: InvariantDeviceProfile.fillResIconDpi / iconBitmapSize and the theme. */
    data class Params(
        val fillResIconDpi: Int,
        val iconBitmapSize: Int,
        val themeController: IconThemeController?,
    )
}
