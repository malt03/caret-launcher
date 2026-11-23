/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * Modified for TextLauncher: no LauncherApplication or safe mode.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.dagger

import android.content.Context
import android.view.LayoutInflater

/**
 * Utility class to extract LauncherAppComponent from a context.
 *
 * If the context doesn't provide LauncherAppComponent by default, it creates a new one and
 * associate it with that context
 */
object LauncherComponentProvider {

    @JvmStatic
    fun get(c: Context): LauncherAppComponent {
        val app = c.applicationContext
        // TextLauncher: no LauncherApplication, which holds the component in Launcher3.

        val inflater = LayoutInflater.from(app)
        val existingFilter = inflater.filter
        if (existingFilter is Holder) return existingFilter.component

        // Create a new component
        return Holder(
                // TextLauncher: no safe mode.
                DaggerLauncherAppComponent.builder().appContext(app).build()
                    as LauncherAppComponent,
                existingFilter,
            )
            .apply { inflater.filter = this }
            .component
    }

    /** Extension method easily access LauncherAppComponent */
    val Context.appComponent: LauncherAppComponent
        get() = get(this)

    private data class Holder(
        val component: LauncherAppComponent,
        private val filter: LayoutInflater.Filter?,
    ) : LayoutInflater.Filter {

        override fun onLoadClass(clazz: Class<*>?) = filter?.onLoadClass(clazz) ?: true
    }
}
