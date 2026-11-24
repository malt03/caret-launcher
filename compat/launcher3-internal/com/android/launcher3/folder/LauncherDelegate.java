/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.folder;

import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.DragSource;
import com.android.launcher3.Launcher;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.views.BaseDragLayer;

/**
 * Stand-in for Launcher3's folder.LauncherDelegate (android-17.0.0_r1): what a folder asks of the
 * launcher. TextLauncher: the workspace and model work is the app's, which overrides
 * {@link #beginDragShared} and {@link #replaceFolderWithFinalItem} (package-private in Launcher3).
 */
public class LauncherDelegate {
    private final Launcher mLauncher;

    public LauncherDelegate(Launcher launcher) {
        mLauncher = launcher;
    }

    boolean isDraggingEnabled() {
        return mLauncher.isDraggingEnabled();
    }

    /** Workspace.beginDragShared for an item dragged out of the folder. */
    public void beginDragShared(View child, DragSource source, DragOptions options) { }

    public Launcher getLauncher() {
        return mLauncher;
    }

    /**
     * Turns a folder left with one item (or none) back into that item. Returns whether the folder
     * was destroyed.
     */
    public boolean replaceFolderWithFinalItem(Folder folder) {
        return false;
    }

    boolean interceptOutsideTouch(MotionEvent ev, BaseDragLayer dl, Folder folder) {
        if (mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            // Do not close the container if in drag and drop.
            if (!dl.isEventOverView(mLauncher.getDropTargetBar(), ev)) {
                return true;
            }
        } else {
            // TODO: add ww log if need to gather tap outside to close folder
            folder.close(true);
            return true;
        }
        return false;
    }
}
