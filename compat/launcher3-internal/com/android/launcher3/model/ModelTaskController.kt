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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.model

import android.content.Context
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.CallbackTask
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.BgDataModel.ModificationSource.ModelTask
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import java.util.function.Predicate

/**
 * Class with utility methods and properties for running a LauncherModel Task
 *
 * Stand-in for Launcher3's ModelTaskController (android-17.0.0_r1), made from
 * ModelTaskController.kt by deleting Dagger, the extra items, the widgets model and the incremental
 * downloads; changes are marked with "CaretLauncher:".
 */
class ModelTaskController(
    val context: Context,
    val iconCache: IconCache,
    val dataModel: BgDataModel,
    val allAppsList: AllAppsList,
    val model: LauncherModel,
    val modelWriterFactory: ModelWriterFactory,
) {

    private val uiExecutor = MAIN_EXECUTOR

    /** Schedules a {@param task} to be executed on the current callbacks. */
    fun scheduleCallbackTask(task: CallbackTask) {
        for (cb in model.callbacks) {
            uiExecutor.execute { task.execute(cb) }
        }
    }

    /**
     * Updates from model task, do not deal with icon position in hotseat. Also no need to verify
     * changes as the ModelTasks always push the changes to callbacks
     */
    fun getModelWriter(): IModelWriter =
        modelWriterFactory.create(
            verifyChanges = false,
            CellPosMapper.DEFAULT,
            modificationSource = ModelTask,
            // TODO: (b/455016031) - Remove owner from ModelWriter
            owner = null,
        )

    fun bindUpdatedWorkspaceItems(allUpdates: Collection<ItemInfo>) {
        // Bind workspace items
        val workspaceUpdates = allUpdates.filter { it.id != ItemInfo.NO_ID }.toSet()
        if (workspaceUpdates.isNotEmpty()) {
            scheduleCallbackTask { it.bindItemsUpdated(workspaceUpdates) }
        }
    }

    // CaretLauncher: no extra items (bindExtraContainerItems) or widgets model
    // (bindUpdatedWidgets).

    fun deleteAndBindComponentsRemoved(matcher: Predicate<ItemInfo?>, reason: String?) {
        getModelWriter().deleteItemsFromDatabase(matcher, reason)

        // Call the components-removed callback
        scheduleCallbackTask { it.bindWorkspaceComponentsRemoved(matcher) }
    }

    fun bindApplicationsIfNeeded() {
        if (allAppsList.getAndResetChangeFlag()) {
            // shallow copy
            val data = allAppsList.immutableData
            scheduleCallbackTask {
                it.bindAllApplications(data.apps, data.flags, data.packageUserKeyToUidMap)
            }
        }
    }

    // CaretLauncher: no incremental downloads (bindIncrementalUpdates).
}
