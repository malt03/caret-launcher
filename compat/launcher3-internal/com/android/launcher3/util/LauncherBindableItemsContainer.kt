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

package com.android.launcher3.util

import android.view.View
import com.android.launcher3.model.data.ItemInfo

/**
 * Interface representing a container which can bind Launcher items with some utility methods
 *
 * Stand-in for Launcher3's LauncherBindableItemsContainer (android-17.0.0_r1): only mapping over
 * the items (PopupDataProvider's notification dots). TextLauncher: no updateContainerItems, which
 * the model's bind callbacks use.
 */
fun interface LauncherBindableItemsContainer {

    /**
     * Map the [op] over the shortcuts and widgets. Once we found the first view which matches, we
     * will stop the iteration and return that view.
     */
    fun mapOverItems(op: ItemOperator): View?

    fun interface ItemOperator {

        /**
         * Process the next itemInfo, possibly with side-effect on the next item.
         *
         * @param info info for the shortcut
         * @param view view for the shortcut
         * @return true if done, false to continue the map
         */
        fun evaluate(info: ItemInfo?, view: View): Boolean
    }
}
