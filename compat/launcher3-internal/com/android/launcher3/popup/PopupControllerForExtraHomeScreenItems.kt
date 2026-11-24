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

import android.os.Trace
import com.android.launcher3.Launcher
import android.view.View
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.dragndrop.LauncherDragController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Controller for home screen items: folders, app pairs, widgets, and file system based items. This
 * controller does not handle apps or app shortcuts. This controller handles actions for the popups
 * such as showing and dismissing them.
 */
// TextLauncher: derived from PopupControllerForExtraHomeScreenItems.kt (android-17.0.0_r1); T is not
// bound to a Context, as the Launcher stand-in is not one. Changes are marked with "TextLauncher:".
class PopupControllerForExtraHomeScreenItems<T>(
    private val popupDataRepository: PopupDataRepository,
    private val dragController: LauncherDragController,
) : PopupController<T> where T : ActivityContext {
    override fun show(view: View): Popup {
        val container: PopupContainer<T>
        val activityContext: T = ActivityContext.lookupContext(view.context) as T
        val itemInfo = view.tag as ItemInfo
        try {
            Trace.beginSection("showPopupMenu")
            container =
                PopupContainer.create(
                    context = view.context,
                    originalView = view,
                    itemInfo = itemInfo,
                )
            dragController.addDragListener(container)
            popupDataRepository.getAllSupportedPopupActions(itemInfo)?.let {
                container.showForSystemShortcuts(it, activityContext, view)
            }
            showResizeFrameIfNeeded(activityContext, itemInfo, view)
        } finally {
            // TextLauncher: no logging (logEvent).
            Trace.endSection()
        }
        return container
    }

    private fun showResizeFrameIfNeeded(
        activityContext: ActivityContext,
        itemInfo: ItemInfo,
        view: View,
    ) {
        // TextLauncher: the home screen's Launcher has the cell layouts (ActivityContext.getCellLayout).
        val cellLayout =
            (activityContext as Launcher).getCellLayout(itemInfo.container, itemInfo.screenId)
        val resizeStrategy = DefaultPopupResizeStrategy()
        if (resizeStrategy.shouldShowResizeFrame(itemInfo, view, cellLayout)) {
            AppWidgetResizeFrame.showForWidget(view as LauncherAppWidgetHostView?, cellLayout)
        }
    }

    override fun dismiss() {}
}
