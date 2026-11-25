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
package com.android.launcher3.model.tasks;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE;

import static java.util.Collections.emptyList;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.ModelTaskController;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles model changes due to installation or update of an app
 *
 * <p>Stand-in for Launcher3's PackageUpdatedTask (android-17.0.0_r1), made from
 * PackageUpdatedTask.java by deleting the promise and archived shortcuts, the shortcuts with a web
 * UI, the widget restore and the widgets model; changes are marked with "TextLauncher:".
 */
@SuppressWarnings("NewApi")
public class PackageUpdatedTask implements ModelUpdateTask {

    private static final String TAG = "PackageUpdatedTask";

    public static final boolean OP_ADD = false;
    public static final boolean OP_UPDATE = true;

    private final boolean mIsUpdate;

    @NonNull
    private final UserHandle mUser;

    @NonNull
    private final Set<String> mPackages;

    public PackageUpdatedTask(boolean isUpdate, @NonNull final UserHandle user,
            @NonNull final String... packages) {
        mIsUpdate = isUpdate;
        mUser = user;
        mPackages = new HashSet<>(Arrays.asList(packages));
    }

    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList appsList) {
        final Context context = taskController.getContext();
        final IconCache iconCache = taskController.getIconCache();

        if (mIsUpdate) {
            // Mark disabled packages in the broadcast to be removed
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            List<String> disabledPackages = mPackages.stream()
                    .filter(pkg -> !launcherApps.isPackageEnabled(pkg, mUser)).toList();
            if (!disabledPackages.isEmpty()) {
                disabledPackages.forEach(mPackages::remove);
                PackageTaskFactory.INSTANCE.appsRemoved(mUser, new HashSet<>(disabledPackages))
                        .execute(taskController, dataModel, appsList);
            }
        }

        final HashMap<String, List<LauncherActivityInfo>> activitiesLists = new HashMap<>();
        for (String packageName : mPackages) {
            iconCache.updateIconsForPkg(packageName, mUser);
            activitiesLists.put(packageName, appsList.updatePackage(context, packageName, mUser));
        }

        taskController.bindApplicationsIfNeeded();

        final IntSet removedShortcuts = new IntSet();
        // TextLauncher: no shortcuts with a web UI to keep (forceKeepShortcuts).

        // Update shortcut infos
        List<ItemInfo> updatedItems = dataModel.updateAndCollectWorkspaceItemInfos(
                mUser, itemInfo -> {
                    ComponentName cn = itemInfo.getTargetComponent();
                    if (cn == null) return false;
                    String packageName = cn.getPackageName();
                    if (!mPackages.contains(packageName)) return false;

                    if (itemInfo.itemType != ITEM_TYPE_APPLICATION
                            && itemInfo.itemType != ITEM_TYPE_DEEP_SHORTCUT) {
                        FileLog.d(TAG, "Ignoring unknown item-type: " + itemInfo);
                        // Should it be deleted?
                        return false;
                    }

                    if (itemInfo.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
                        // Deep shortcuts are handled via shortcut update task. TextLauncher: no
                        // promise shortcuts.
                        return false;
                    }

                    // TextLauncher: no shortcuts with a web UI (FLAG_SUPPORTS_WEB_UI).

                    List<LauncherActivityInfo> activityList =
                            activitiesLists.getOrDefault(packageName, emptyList());

                    LauncherActivityInfo activityInfo = activityList.stream()
                            .filter(it -> it.getComponentName().equals(cn))
                            .findFirst()
                            .orElse(null);

                    if (activityInfo == null) {
                        if (!activityList.isEmpty()) {
                            // First activity is considered the default activity,
                            // similar to PackageManagerHelper.getAppLaunchInfo
                            activityInfo = activityList.get(0);
                            itemInfo.intent = AppInfo.makeLaunchIntent(activityInfo);
                        } else {
                            FileLog.e(TAG, "Removing shortcut with invalid target component."
                                    + itemInfo);
                            removedShortcuts.add(itemInfo.id);
                            return false;
                        }
                    }

                    // Restore if it's a promise icon. TextLauncher: no promise status.
                    itemInfo.runtimeStatusFlags &= ~FLAG_DISABLED_NOT_AVAILABLE;
                    // TextLauncher: without the user info, API wrapper, package manager helper and
                    // automation repository.
                    AppInfo.updateRuntimeFlagsForActivityTarget(itemInfo, activityInfo);
                    iconCache.getTitleAndIcon(itemInfo, itemInfo.getMatchingLookupFlag());

                    if (itemInfo.id != ItemInfo.NO_ID) {
                        taskController.getModelWriter().updateItemInDatabase(itemInfo);
                    }
                    return true;
                    // TextLauncher: no widget restore (FLAG_PROVIDER_NOT_READY).
                });

        taskController.bindUpdatedWorkspaceItems(updatedItems);
        if (!removedShortcuts.isEmpty()) {
            taskController.deleteAndBindComponentsRemoved(
                    ItemInfoMatcher.ofItemIds(removedShortcuts),
                    "removing shortcuts with invalid target components."
                            + " ids=" + removedShortcuts);
        }

        // TextLauncher: no widgets model to load for a new package.
    }
}
