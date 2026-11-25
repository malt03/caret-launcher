/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.popup

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BuildConfig
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.popup.PopupCategory.SYSTEM_SHORTCUT
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import com.android.launcher3.views.ActivityContext

/**
 * Class to create default set of long-press options.
 *
 * Stand-in for Launcher3's WorkspaceLongPressOptions (android-17.0.0_r1), made from
 * WorkspaceLongPressOptions.kt by deleting what CaretLauncher does not use (edit mode, the condo
 * planner, home screen files, logging); changes are marked with "CaretLauncher:".
 */
object WorkspaceLongPressOptions {

    @JvmStatic
    fun getAll(ctx: Context): List<PopupData> = buildList {
        add(
            PopupData(
                R.drawable.ic_palette,
                R.string.styles_wallpaper_button_text,
                SYSTEM_SHORTCUT,
            ) { ac, _, v ->
                startWallpaperPicker(ac, v)
            }
        )
        if (BuildConfig.WIDGETS_ENABLED) {
            add(
                PopupData(
                    R.drawable.ic_widget,
                    R.string.widget_button_text,
                    SYSTEM_SHORTCUT,
                ) { ac, _, _ ->
                    openWidgetPicker(ac.asContext())
                }
            )
        }

        // CaretLauncher: no "Apps list" with two panels, where the list is always shown
        // (docs/spec.md 5.5).
        if ((ActivityContext.lookupContext(ctx) as? Launcher)?.canOpenAllApps() == true) {
        add(
            PopupData(
                R.drawable.ic_apps,
                R.string.all_apps_button_label,
                SYSTEM_SHORTCUT,
            ) { ac, _, _ ->
                (ac as? Launcher)?.apply {
                    // CaretLauncher: no keyboard state manager (launchedFromA11y).
                    openAllApps() // CaretLauncher: stateManager.goToState(LauncherState.ALL_APPS)
                }
            }
        )
        }

        // CaretLauncher: in place of "Home settings", the only setting CaretLauncher has: the share
        // buttons next to the search field (docs/spec.md 2.7).
        (ActivityContext.lookupContext(ctx) as? Launcher)?.let { add(it.shareTextOption) }

        // CaretLauncher: no "Home settings" (docs/spec.md 8). In place of the settings'
        // "Notification dots" (NotificationDotsPreference), "Notification dots" is shown while the
        // dots are on but the launcher may not read the notifications (docs/spec.md 7.5).
        if (isNotificationAccessMissing(ctx)) {
            add(
                PopupData(
                    R.drawable.ic_setting,
                    R.string.notification_dots_title,
                    SYSTEM_SHORTCUT,
                ) { ac, _, _ ->
                    // Launcher3 shows the dialog in its settings activity, so the menu has closed
                    // (Launcher.onStop); here it is shown over the home screen.
                    AbstractFloatingView.closeAllOpenViews(ac)
                    (ac as? Launcher)?.showNotificationAccessConfirmation()
                }
            )
        }
    }

    /**
     * Event handler for the wallpaper picker button that appears after a long press on the home
     * screen.
     */
    private fun startWallpaperPicker(ac: ActivityContext, v: View) {
        val launcher = ac as? Launcher ?: return
        // CaretLauncher: the launcher is not a Context; its context is (asContext).
        if (!Utilities.isWallpaperAllowed(launcher.asContext())) {
            // CaretLauncher: no string cache (enterprise strings).
            val message = launcher.asContext().getString(R.string.msg_disabled_by_admin)
            Toast.makeText(launcher.asContext(), message, Toast.LENGTH_SHORT).show()
            return
        }
        val intent =
            Intent(Intent.ACTION_SET_WALLPAPER)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_WALLPAPER_OFFSET, launcher.workspace.wallpaperOffsetForCenterPage)
                .putExtra(EXTRA_WALLPAPER_LAUNCH_SOURCE, "app_launched_launcher")
                .putExtra(EXTRA_WALLPAPER_FLAVOR, "focus_wallpaper")
        val pickerPackage = launcher.asContext().getString(R.string.wallpaper_picker_package)
        if (!TextUtils.isEmpty(pickerPackage)) {
            intent.setPackage(pickerPackage)
        }
        // CaretLauncher: Launcher.startActivitySafely starts the home screen's items here; the
        // picker is started as ActivityContext.startActivitySafely does, without its options.
        try {
            launcher.asContext().startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Unable to launch. intent=$intent", e)
            Toast.makeText(launcher.asContext(), R.string.activity_not_available, Toast.LENGTH_SHORT)
                .show()
        }
    }

    /** Opens the widget picker UI. Returns true if opened. */
    @JvmStatic
    fun openWidgetPicker(ctx: Context): Boolean {
        if (ctx.packageManager.isSafeMode) {
            Toast.makeText(ctx, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show()
            return false
        } else {
            val intent = Intent(Intent.ACTION_PICK)
            intent.setPackage(ctx.packageName)
            if (ctx !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true
        }
    }

    // An intent extra to indicate the horizontal scroll of the wallpaper.
    private const val EXTRA_WALLPAPER_OFFSET = "com.android.launcher3.WALLPAPER_OFFSET"
    private const val EXTRA_WALLPAPER_FLAVOR = "com.android.launcher3.WALLPAPER_FLAVOR"
    // An intent extra to indicate the launch source by launcher.
    private const val EXTRA_WALLPAPER_LAUNCH_SOURCE = "com.android.wallpaper.LAUNCH_SOURCE"

    // CaretLauncher: members CaretLauncher adds.

    /** WorkspaceTouchListener's tag, for the wallpaper picker's error log. */
    private const val TAG = "WorkspaceTouchListener"

    /**
     * NotificationDotsPreference.onSettingsChanged: the system's notification dots are on, but the
     * launcher's NotificationListener is not an enabled notification listener. Asks
     * NotificationManager.isNotificationListenerAccessGranted instead of reading
     * Settings.Secure.ENABLED_NOTIFICATION_LISTENERS, which apps may not read.
     */
    private fun isNotificationAccessMissing(context: Context): Boolean {
        if (!SettingsCache.INSTANCE.get(context).getValue(NOTIFICATION_BADGING_URI)) return false
        val listener = ComponentName(context, NotificationListener::class.java)
        return !context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listener)
    }
}
