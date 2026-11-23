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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.accessibility;

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;

import java.util.function.Consumer;

/**
 * Stand-in for Launcher3's LauncherAccessibilityDelegate (android-17.0.0_r1), made from
 * LauncherAccessibilityDelegate.java by deleting what TextLauncher does not use: only
 * {@link #addToWorkspace}, which the long-press menu's add button uses, is left. Changes are
 * marked with "TextLauncher:".
 */
// TextLauncher: an interface that the home screen implements, not a BaseAccessibilityDelegate of
// Launcher; there are no accessibility actions.
public interface LauncherAccessibilityDelegate {

    /**
     * Functionality to add the item {@link ItemInfo} to the workspace
     * @param item item to be added
     * @param accessibility true if the first item to be added to the workspace
     *     should be focused for accessibility.
     * @param finishCallback Callback which will be run after this item has been added
     *                       and the view has been transitioned to the workspace, or on failure.
     *
     * @return true if the item could be successfully added
     */
    public boolean addToWorkspace(ItemInfo item, boolean accessibility,
            @Nullable Consumer<Boolean> finishCallback); // TextLauncher: the home screen adds it.
}
