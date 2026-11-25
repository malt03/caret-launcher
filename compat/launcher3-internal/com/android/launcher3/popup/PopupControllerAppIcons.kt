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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.popup

import android.os.Trace
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ShortcutUtil
import com.android.launcher3.views.ActivityContext
import java.util.stream.Collectors

/**
 * Controller for app icons. It handles actions for the popups such as showing and dismissing
 * popups. This is used for icons and shortcuts in the workspace, hotseat, and all apps.
 */
// CaretLauncher: derived from PopupControllerAppIcons.kt (android-17.0.0_r1); T is not bound to a
// Context, as the Launcher stand-in is not one. Changes are marked with "CaretLauncher:".
class PopupControllerForAppIcon<T> : PopupController<T> where T : ActivityContext {

    override fun show(view: View): Popup? {
        val container: PopupContainer<T>
        val icon = view as BubbleTextView
        val activityContext: T = ActivityContext.lookupContext(icon.context) as T
        val item = icon.tag as ItemInfo
        try {
            Trace.beginSection("showPopupMenu")
            if (PopupContainer.getOpen(activityContext) != null) {
                // There is already an items container open, so don't open this one.
                icon.clearFocus()
                return null
            }
            if (!ShortcutUtil.supportsShortcuts(item)) {
                return null
            }
            val popupDataProvider = activityContext.activityComponent.popupDataProvider
            val deepShortcutCount = popupDataProvider.getShortcutCountForItem(item)
            val systemShortcuts =
                activityContext
                    .getSupportedShortcuts(item)
                    .map<SystemShortcut<T>> { s ->
                        s.getShortcut(activityContext, item, icon) as SystemShortcut<T>?
                    }
                    .filter { it != null }
                    .collect(Collectors.toList())

            container =
                PopupContainerWithArrow.create(
                    // CaretLauncher: the Launcher stand-in is not a Context.
                    context = activityContext.asContext(),
                    originalView = icon,
                    itemInfo = item,
                )
            // CaretLauncher: no Compose version (Flags.expandableLongPressMenu is disabled in the
            // release), so no deep shortcut drag handler for it (LauncherDeepShortcutDragHandler).
            container.configureForLauncher(activityContext as Launcher, item)
            container.populateAndShowRows(
                deepShortcutCount,
                if (view.showingMinimalPopup) emptyList() else systemShortcuts,
            )
            // CaretLauncher: no widgets in the menu (refreshAndBindWidgetsForPackageUser).
            container.requestFocus()
        } finally {
            // CaretLauncher: no logging (logEvent).
            Trace.endSection()
        }
        return container
    }

    override fun dismiss() {}
}
