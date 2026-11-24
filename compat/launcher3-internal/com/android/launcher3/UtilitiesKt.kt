/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3

import com.android.launcher3.model.data.ItemInfo

/**
 * Stand-in for Launcher3's UtilitiesKt (android-17.0.0_r1), made from UtilitiesKt.kt by deleting
 * what TextLauncher does not use; changes are marked with "TextLauncher:".
 */
object UtilitiesKt {

    // TextLauncher: no view attribute modifiers (ViewGroupAttrModifier, CLIP_CHILDREN_FALSE_MODIFIER,
    // CLIP_TO_PADDING_FALSE_MODIFIER, modifyAttributesOnViewTree, restoreAttributesOnViewTree).

    /**
     * Checks if an item is persisted in model. It excludes items whose ID corresponds to an AAPT
     * generated id which always has a non-zero package identifier first-byte.
     *
     * @see android.view.View.generateViewId
     */
    @JvmStatic fun ItemInfo.isPersistedModelItem() = id > ItemInfo.NO_ID && (id ushr 24) == 0

    // TextLauncher: no selection highlight (drawWorkspaceItemSelectionHighlight).
}
