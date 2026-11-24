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

package com.android.launcher3.popup

import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DropTargetHandler
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView

// TextLauncher: derived from PopupDataSource.kt (android-17.0.0_r1), keeping the remove action and
// the folders' and widgets' mappers; no app pairs (AppPairSystemShortcuts), home screen files
// (FileSystemShortcuts), custom widgets, or the app shortcuts' actions. Changes are marked with
// "TextLauncher:".

object PopupDataSource {

    // Popup data for remove shortcut.
    val removePopupData =
        PopupData(
            iconResId = R.drawable.ic_remove_no_shadow,
            labelResId = R.string.remove_system_shortcut_label,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        ) { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            AbstractFloatingView.closeAllOpenViews(activityContext)
            val dropTargetHandler: DropTargetHandler = activityContext.dropTargetHandler
            // TextLauncher: no undo (prepareToUndoDelete).
            dropTargetHandler.onDeleteComplete(itemInfo, view)
        }
}

object FolderSystemShortcuts : PopupDataMapper {

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? =
        if (itemInfo.itemType == ITEM_TYPE_FOLDER) listOf(PopupDataSource.removePopupData) else null
}

// TextLauncher: no app pairs (AppPairSystemShortcuts).

object AppWidgetSystemShortcuts : PopupDataMapper {
    // TextLauncher: no TAG, as nothing is logged here.

    // Popup data for widget settings shortcut.
    private val widgetSettingsPopupData =
        PopupData(
            iconResId = R.drawable.ic_setting,
            labelResId = R.string.widget_settings,
            category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
        ) { activityContext: ActivityContext, itemInfo: ItemInfo, view: View ->
            if (view is LauncherAppWidgetHostView) {
                // TextLauncher: the home screen starts the widget's configuration and waits for
                // its result (Launcher.setWaitingForResult, LauncherWidgetHolder
                // .startConfigActivity with REQUEST_RECONFIGURE_APPWIDGET).
                (activityContext as Launcher).startWidgetReconfigure(view)
            }
        }

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? {
        return if (itemInfo.itemType != ITEM_TYPE_APPWIDGET) null
        else if (itemInfo is LauncherAppWidgetInfo && itemInfo.isReconfigurable) {
            listOf(PopupDataSource.removePopupData, widgetSettingsPopupData)
        } else {
            listOf(PopupDataSource.removePopupData)
        }
    }
}

// TextLauncher: no home screen files, custom widgets or the app shortcuts' actions (the rest of
// the file).
