/*
 * Copyright (C) 2025 The Android Open Source Project
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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.util

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Bundle
import com.android.launcher3.R

/**
 * Utility class to manage activity's theme in case it is wallpaper dependent
 *
 * Stand-in for Launcher3's WallpaperThemeManager (android-17.0.0_r1). TextLauncher: the lifecycle
 * callbacks are written out (no ActivityLifecycleCallbacksAdapter), and the recreate flag is not
 * saved (Launcher3 reads it back to skip the transition).
 */
class WallpaperThemeManager(private val activity: Activity) :
    OnColorHintListener, ActivityLifecycleCallbacks, ComponentCallbacks {

    private var themeRes: Int = R.style.AppTheme

    init {
        // Update theme
        WallpaperColorHints.get(activity).registerOnColorHintsChangedListener(this)
        val expectedTheme = Themes.getActivityThemeRes(activity)
        if (expectedTheme != themeRes) {
            themeRes = expectedTheme
            activity.setTheme(expectedTheme)
        }

        activity.registerActivityLifecycleCallbacks(this)
        activity.registerComponentCallbacks(this)
    }

    override fun onActivityDestroyed(unused: Activity) {
        WallpaperColorHints.get(activity).unregisterOnColorsChangedListener(this)
        activity.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(config: Configuration) = updateTheme()

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {}

    override fun onColorHintsChanged(colorHints: Int) = updateTheme()

    private fun updateTheme() {
        if (themeRes != Themes.getActivityThemeRes(activity)) {
            recreateToUpdateTheme()
        }
    }

    fun recreateToUpdateTheme() {
        activity.recreate()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
