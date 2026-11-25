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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.model.tasks

import android.os.UserHandle
import android.util.Log
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.ItemInfoMatcher

/**
 * Factory for creating model tasks to handle various package events
 *
 * Stand-in for Launcher3's PackageTaskFactory (android-17.0.0_r1), made from PackageTaskFactory.kt
 * by deleting the shortcuts with a web UI; changes are marked with "CaretLauncher:".
 */
object PackageTaskFactory {
    private const val TAG = "PackageUpdateTaskFactory"
    private const val DEBUG = false

    /** Task to handle apps being removed */
    fun appsRemoved(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            packages.forEach {
                if (DEBUG) Log.i(TAG, "appsRemoved package=$it")
                taskController.iconCache.removeIconsForPkg(it, user)
                apps.removePackage(it, user)
            }
            taskController.bindApplicationsIfNeeded()

            val matcher = ItemInfoMatcher.ofPackages(packages, user)
            // CaretLauncher: no shortcuts with a web UI to keep (forceKeepShortcuts).
            taskController.deleteAndBindComponentsRemoved(
                matcher,
                "removed because the corresponding package is removed. removedPackages=$packages",
            )
            // Remove any queued items from the install queue
            ItemInstallQueue.INSTANCE[taskController.context].removeFromInstallQueue(packages, user)
        }

    /** Task to handle apps being temporarily unavailable */
    fun appsUnavailable(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            packages.forEach {
                if (DEBUG) Log.i(TAG, "appsUnavailable package=$it")
                apps.removePackage(it, user)
            }
            taskController.bindApplicationsIfNeeded()
            updateRuntimeStatus(
                taskController,
                dataModel,
                user,
                packages,
                FlagOp.NO_OP.addFlag(FLAG_DISABLED_NOT_AVAILABLE),
            )
        }

    /** Task to handle apps being suspended */
    fun appsSuspended(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            val flagOp = FlagOp.NO_OP.addFlag(FLAG_DISABLED_SUSPENDED)

            apps.updateRuntimeFlags(ItemInfoMatcher.ofPackages(packages, user), flagOp)
            taskController.bindApplicationsIfNeeded()

            updateRuntimeStatus(taskController, dataModel, user, packages, flagOp)
        }

    /** Task to handle apps being suspended */
    fun appsUnsuspended(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            val flagOp = FlagOp.NO_OP.removeFlag(FLAG_DISABLED_SUSPENDED)

            apps.updateRuntimeFlags(ItemInfoMatcher.ofPackages(packages, user), flagOp)
            taskController.bindApplicationsIfNeeded()

            updateRuntimeStatus(taskController, dataModel, user, packages, flagOp)
        }

    /** Updates matching workspace items with the provided [updateOp] */
    private fun updateRuntimeStatus(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        user: UserHandle,
        packages: Set<String>,
        updateOp: FlagOp,
    ) {

        // Update workspace items, widget suspension is handled by the platform itself
        val updatedItems =
            dataModel.updateAndCollectWorkspaceItemInfos(
                user,
                { itemInfo ->
                    val oldRuntimeFlags = itemInfo.runtimeStatusFlags
                    if (packages.contains(itemInfo.targetPackage))
                        itemInfo.runtimeStatusFlags = updateOp.apply(itemInfo.runtimeStatusFlags)
                    itemInfo.runtimeStatusFlags != oldRuntimeFlags
                },
            )

        taskController.bindUpdatedWorkspaceItems(updatedItems)
    }
}
