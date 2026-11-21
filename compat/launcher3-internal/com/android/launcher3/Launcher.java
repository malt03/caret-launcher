/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.views.ScrimView;

/**
 * Stand-in for Launcher3's Launcher: the parts of the home screen that the folder animation
 * changes (FolderSpringAnimatorSet.addWorkspaceAnimators).
 */
public class Launcher {
    private final DragLayer mDragLayer;
    private final Workspace<?> mWorkspace;
    private final Hotseat mHotseat;
    private final ScrimView mScrimView;

    public Launcher(DragLayer dragLayer, Workspace<?> workspace, Hotseat hotseat,
            ScrimView scrimView) {
        mDragLayer = dragLayer;
        mWorkspace = workspace;
        mHotseat = hotseat;
        mScrimView = scrimView;
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    public Workspace<?> getWorkspace() {
        return mWorkspace;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public ScrimView getScrimView() {
        return mScrimView;
    }

    /** No wallpaper zoom: it needs a system permission. */
    public DepthController getDepthController() {
        return null;
    }

    /** Stand-in for statehandlers.DepthController. */
    public static class DepthController {
        public MultiPropertyFactory<?>.MultiProperty getFolderZoom() {
            return null;
        }
    }
}
