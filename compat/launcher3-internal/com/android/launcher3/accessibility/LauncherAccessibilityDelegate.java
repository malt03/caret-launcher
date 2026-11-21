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
 * Stand-in for Launcher3's LauncherAccessibilityDelegate (android-17.0.0_r1): only
 * {@link #addToWorkspace}, which the long-press menu's add button uses. TextLauncher's home
 * screen implements it; there are no accessibility actions.
 */
public interface LauncherAccessibilityDelegate {
    /**
     * Adds the item to the first free cell of the home screen and binds it.
     *
     * @param accessibility whether the item is added by an accessibility action
     * @param finishCallback called with whether the item was added
     */
    boolean addToWorkspace(ItemInfo item, boolean accessibility,
            @Nullable Consumer<Boolean> finishCallback);
}
