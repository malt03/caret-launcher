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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.util

import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.views.ActivityContext
import java.util.function.Predicate

/**
 * Interface representing a container which can bind Launcher items with some utility methods
 *
 * Stand-in for Launcher3's LauncherBindableItemsContainer (android-17.0.0_r1). CaretLauncher: no app
 * pairs and no pending widgets; the model's updates never move an item, so there is nothing to
 * rebind ([updateContainerItems] returns an empty set; no getCellInfoForView).
 */
fun interface LauncherBindableItemsContainer {

    /**
     * Called to update items in a specific container as a result of
     * [com.android.launcher3.model.BgDataModel.Callbacks.bindItemsUpdated]. Returns a set of items
     * which were potentially moved and need to be rebound to the container.
     */
    fun updateContainerItems(updates: Set<ItemInfo>, context: ActivityContext): Set<ItemInfo> {
        val op = ItemOperator { info, v ->
            when {
                v is BubbleTextView && info is WorkspaceItemInfo && updates.contains(info) ->
                    v.applyFromWorkspaceItem(info)
                v is FolderIcon && info is FolderInfo -> {
                    v.updatePreviewItems(updates::contains)
                    if (updates.contains(info)) {
                        v.onItemsChanged(false)
                        v.folder.apply {
                            // CaretLauncher: no folder titles to apply again (reapplyItemInfo).
                            if (isOpen()) close(false)
                        }
                    }
                }
            }

            // Iterate all items
            false
        }

        mapOverItems(op)
        Folder.getOpen(context)?.mapOverItems(op)

        // CaretLauncher: no moved items to check.
        return emptySet()
    }

    /** Finds the first icon to match one of the given matchers, from highest to lowest priority. */
    fun getFirstMatch(vararg matchers: Predicate<ItemInfo>): View? =
        matchers.firstNotNullOfOrNull { mapOverItems { info, _ -> info != null && it.test(info) } }

    fun getViewByItemId(id: Int): View? = mapOverItems { info, _ -> info != null && info.id == id }

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
