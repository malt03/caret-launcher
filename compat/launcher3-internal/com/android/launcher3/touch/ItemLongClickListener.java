/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.touch;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherState.NORMAL;

import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.BubbleTextHolder;

/**
 * Class to handle long-clicks on workspace items and start drag as a result.
 */
/*
 * TextLauncher: derived from ItemLongClickListener.java (android-17.0.0_r1); changes are marked
 * with "TextLauncher:".
 */
public class ItemLongClickListener {

    public static final OnLongClickListener INSTANCE_WORKSPACE =
            ItemLongClickListener::onWorkspaceItemLongClick;

    public static final OnLongClickListener INSTANCE_ALL_APPS =
            ItemLongClickListener::onAllAppsItemLongClick;

    private static boolean onWorkspaceItemLongClick(View v) {
        // TextLauncher: no test logging (TestLogging).
        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!canStartDrag(launcher)) return false;
        // TextLauncher: no overview or edit mode state (OVERVIEW, EDIT_MODE).
        if (!launcher.isInState(NORMAL)) {
            return false;
        }
        if (!(v.getTag() instanceof ItemInfo)) return false;

        // TextLauncher: no pending activity requests to clear (setWaitingForResult).
        beginDrag(v, launcher, (ItemInfo) v.getTag(), new DragOptions());
        return true;
    }

    public static void beginDrag(View v, Launcher launcher, ItemInfo info,
            DragOptions dragOptions) {
        if (info.container >= 0) {
            Folder folder = Folder.getOpen(launcher);
            if (folder != null) {
                if (!folder.getIconsInReadingOrder().contains(v)) {
                    folder.close(true);
                } else {
                    folder.startDrag(v, dragOptions);
                    return;
                }
            }
        }

        CellInfo longClickCellInfo = new CellInfo(v, info,
                launcher.getCellPosMapper().mapModelToPresenter(info));
        launcher.getWorkspace().startDrag(longClickCellInfo, dragOptions);
    }

    private static boolean onAllAppsItemLongClick(View view) {
        // TextLauncher: no test logging (TestLogging).
        view.cancelLongPress();
        View v = (view instanceof BubbleTextHolder)
                ? ((BubbleTextHolder) view).getBubbleText()
                : view;
        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!canStartAllAppsItemDrag(launcher)) return false;

        // TextLauncher: no private space (PrivateSpaceInstallAppButtonInfo) and no logging.

        // Start the drag
        final DragController dragController = launcher.getDragController();
        dragController.addDragSessionListener(new DragController.DragSessionListener() {
            @Override
            public void onDragSessionStart(DropTarget.DragObject dragObject, DragOptions options) {
                v.setVisibility(INVISIBLE);
            }

            @Override
            public void onDragSessionEnd() {
                v.setVisibility(VISIBLE);
                dragController.removeDragSessionListener(this);
            }
        });

        // TextLauncher: the home screen's own item for the app (AppInfo.makeWorkspaceItem, which
        // Launcher3's ItemInflater calls on the drop), and the all apps list is not a drag source
        // here (getAppsView); nothing is done when the drop completes.
        ItemInfo info = v.getTag() instanceof AppInfo app
                ? launcher.makeWorkspaceItem(app) : (ItemInfo) v.getTag();
        launcher.getWorkspace().beginDragShared(v, null, (target, d, success) -> { }, info,
                new DragPreviewProvider(v), new DragOptions());
        return false;
    }

    /**
     * Returns true if an item drag can be started from All Apps.
     */
    public static boolean canStartAllAppsItemDrag(Launcher launcher) {
        // TextLauncher: the list shows in NORMAL too (with two panels, and the search results),
        // and there is no overview.
        return canStartDrag(launcher) && !launcher.getWorkspace().isSwitchingState();
    }

    public static boolean canStartDrag(Launcher launcher) {
        if (launcher == null) {
            return false;
        }
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        if (launcher.isWorkspaceLocked()) return false;
        // Return early if an item is already being dragged (e.g. when long-pressing two shortcuts)
        if (launcher.getDragController().isDragging()) return false;
        // TextLauncher: no split-screen selection (isSplitSelectionActive).

        return true;
    }
}
