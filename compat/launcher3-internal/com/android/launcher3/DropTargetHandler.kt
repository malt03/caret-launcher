/*
 * The upstream file has no license header; Launcher3 is licensed as below.
 *
 * Copyright (C) The Android Open Source Project
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

package com.android.launcher3

import android.content.ComponentName
import android.view.View
import com.android.launcher3.BaseActivity.EVENT_RESUMED
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.SecondaryDropTarget.DeferredOnComplete
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.model.data.ItemInfo

/**
 * Handler class for drop target actions that require modifying or interacting with launcher.
 *
 * This class is created by Launcher and provided the instance of launcher when created, which
 * allows us to decouple drop target controllers from Launcher to enable easier testing.
 *
 * Stand-in for Launcher3's DropTargetHandler (android-17.0.0_r1), made from DropTargetHandler.kt
 * by deleting what CaretLauncher does not use (undo, home screen files, widget reconfiguration);
 * changes are marked with "CaretLauncher:".
 */
class DropTargetHandler(
    private val launcher: Launcher,
    // CaretLauncher: no undo, home screen files or main executor.
) {
    fun onDropAnimationComplete() {
        launcher.stateManager.goToState(LauncherState.NORMAL)
    }

    fun onSecondaryTargetCompleteDrop(target: ComponentName?, d: DragObject) {
        when (val dragSource = d.dragSource) {
            is DeferredOnComplete -> {
                val deferred: DeferredOnComplete = dragSource
                if (d.dragSource is SecondaryDropTarget.DeferredOnComplete) {
                    target?.let {
                        deferred.mPackageName = it.packageName
                        launcher.addEventCallback(EVENT_RESUMED) { deferred.onLauncherResume() }
                    } ?: deferred.sendFailure()
                }
            }
        }
    }

    fun getViewUnderDrag(info: ItemInfo): View? {
        // CaretLauncher: SecondaryDropTarget has no RECONFIGURE for the widget under the drag.
        return null
    }

    fun onDeleteComplete(item: ItemInfo, view: View?) {
        // CaretLauncher: no home screen files.
        removeItemAndStripEmptyScreens(view, item)
        AbstractFloatingView.closeOpenViews(
            launcher,
            false,
            AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME or AbstractFloatingView.TYPE_FOLDER,
        )
        // CaretLauncher: no undo, so no snackbar: the item is removed at once.
    }

    fun getDragLayer(): DragLayer {
        return launcher.dragLayer
    }

    fun onClick(buttonDropTarget: ButtonDropTarget) {
        launcher.accessibilityDelegate.handleAccessibleDrop(buttonDropTarget, null)
    }

    private fun removeItemAndStripEmptyScreens(view: View?, item: ItemInfo) {
        // Remove the item from launcher ONLY (not the db). The DB deletion is handled by
        // resuming the UndoDeleteController.
        launcher.removeItem(view, item, true /* deleteFromDb */, "removed by accessibility drop")
        // CaretLauncher: one page, so no empty screens to strip.
    }
}
