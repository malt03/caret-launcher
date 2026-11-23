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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.model

import android.appwidget.AppWidgetHost
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import java.util.function.Predicate

/**
 * Interface for handling model updates.
 *
 * TextLauncher: no write suspension and no UI state notifier (one home screen); folders are
 * FolderInfo (no CollectionInfo), and the widget holder is the home screen's AppWidgetHost.
 */
interface IModelWriter {

    /**
     * A log of changes made to the model.
     *
     * @property itemsAdded A list of [ItemInfo] objects representing items added to the model.
     * @property itemsModified A set of [ItemInfo] objects representing items modified in the model.
     * @property itemsRemoved A list of [ItemInfo] objects representing items removed from the
     *   model.
     */
    data class ChangeLog(
        val itemsAdded: MutableList<ItemInfo> = mutableListOf(),
        val itemsModified: MutableSet<ItemInfo> = mutableSetOf(),
        val itemsRemoved: MutableList<ItemInfo> = mutableListOf(),
    )

    /**
     * Schedules a block of code to be executed within a single database transaction on the
     * background model thread. This is the ONLY public way to mutate the model.
     *
     * TextLauncher: the in-memory model is changed on the calling (main) thread, where the block
     * runs; its database writes run in one transaction on the model thread.
     *
     * @param onComplete An optional lambda that will be executed after the transaction has
     *   completed, indicating success or failure and the result of the block.
     * @param block The block of code to execute within the transaction, receiving a
     *   [TransactionContext] handle for performing mutations.
     */
    fun <T> scheduleTransaction(
        onComplete: ((success: Boolean, result: T?) -> Unit)? = null,
        block: (TransactionContext) -> T,
    )

    // The following methods are deprecated and will be removed once all clients are migrated
    // to the new transactional API. They are kept for now to ensure a safe, incremental rollout.
    fun addOrMoveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    )

    fun moveItemInDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int)

    fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int)

    fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    )

    fun updateItemInDatabase(item: ItemInfo)

    fun addItemToDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int)

    fun addItemsToDatabase(items: List<ItemInfo>)

    fun deleteItemFromDatabase(item: ItemInfo, reason: String?)

    fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?)

    fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?)

    fun deleteCollectionAndContentsFromDatabase(info: FolderInfo)

    fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: AppWidgetHost?,
        reason: String?,
    )
}

/**
 * A handle for performing model mutations within a transaction.
 *
 * All methods on this interface are executed synchronously within the transaction's thread.
 */
interface TransactionContext {

    fun addItemToDatabase(item: ItemInfo)

    fun addItemsToDatabase(items: List<ItemInfo>)

    fun moveItemInDatabase(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int)

    fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int)

    fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    )

    fun updateItemInDatabase(item: ItemInfo)

    fun deleteItemFromDatabase(item: ItemInfo, reason: String?) =
        deleteItemsFromDatabase(listOf(item), reason)

    fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?)

    fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?)

    fun deleteCollectionAndContentsFromDatabase(info: FolderInfo)

    fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: AppWidgetHost?,
        reason: String?,
    )

    fun deleteAllItems()
}
