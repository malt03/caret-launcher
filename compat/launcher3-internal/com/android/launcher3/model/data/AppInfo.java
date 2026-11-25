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

package com.android.launcher3.model.data;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.util.ApplicationInfoWrapper;

import java.util.Comparator;

/**
 * Represents an app in AllAppsView.
 *
 * <p>Stand-in for Launcher3's AppInfo (android-17.0.0_r1), made from AppInfo.java by deleting what
 * TextLauncher does not have (copies, the user cache, quiet mode, archived apps,
 * install progress, automation and App Lock); changes are marked with "TextLauncher:".
 */
@SuppressWarnings("NewApi")
// TextLauncher: not a WorkspaceItemFactory.
public class AppInfo extends ItemInfoWithIcon {

    public static final AppInfo[] EMPTY_ARRAY = new AppInfo[0];
    public static final Comparator<AppInfo> COMPONENT_KEY_COMPARATOR = (a, b) -> {
        int uc = a.user.hashCode() - b.user.hashCode();
        return uc != 0 ? uc : a.componentName.compareTo(b.componentName);
    };

    /**
     * The intent used to start the application.
     */
    public Intent intent;

    // componentName for the Private Space Install App button can be null
    @Nullable
    public ComponentName componentName;

    // Section name used for indexing.
    public String sectionName = "";

    /**
     * The uid of the application.
     * The kernel user-ID that has been assigned to this application. Currently this is not a unique
     * ID (multiple applications can have the same uid).
     */
    public int uid = -1;

    public AppInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    }

    @Override
    @Nullable
    public Intent getIntent() {
        return intent;
    }

    /**
     * TextLauncher: AppInfo(LauncherActivityInfo, CachedUserInfo, ...) with the user from the
     * caller (no UserCache, so no quiet mode); see updateRuntimeFlagsForActivityTarget.
     */
    public AppInfo(LauncherActivityInfo info, UserHandle user) {
        this.componentName = info.getComponentName();
        this.container = CONTAINER_ALL_APPS;
        this.user = user;
        intent = makeLaunchIntent(info);

        uid = info.getApplicationInfo().uid;
        updateRuntimeFlagsForActivityTarget(this, info);
    }

    public WorkspaceItemInfo makeWorkspaceItem(Context context) {
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(this);

        // TextLauncher: no install sessions (FLAG_INSTALL_SESSION_ACTIVE) or automation.
        return workspaceItemInfo;
    }

    /**
     * Updates the runtime status flags for the given info based on the state of the specified
     * activity.
     *
     * TextLauncher: the suspended and system states only (no archived apps, automation, private
     * space, App Lock, install progress or activity aliases).
     *
     * @return true if the flags were changed
     */
    public static boolean updateRuntimeFlagsForActivityTarget(
            ItemInfoWithIcon info, LauncherActivityInfo lai) {
        final int oldRuntimeStatusFlags = info.runtimeStatusFlags;
        ApplicationInfoWrapper appInfo = new ApplicationInfoWrapper(lai.getApplicationInfo());
        if (appInfo.isSuspended()) {
            info.runtimeStatusFlags |= FLAG_DISABLED_SUSPENDED;
        } else {
            info.runtimeStatusFlags &= ~FLAG_DISABLED_SUSPENDED;
        }

        info.runtimeStatusFlags |= appInfo.isSystem() ? FLAG_SYSTEM_YES : FLAG_SYSTEM_NO;

        return oldRuntimeStatusFlags != info.runtimeStatusFlags;
    }

    public static Intent makeLaunchIntent(LauncherActivityInfo info) {
        return makeLaunchIntent(info.getComponentName());
    }

    public static Intent makeLaunchIntent(ComponentName cn) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    @NonNull
    @Override
    public ComponentName getTargetComponent() {
        return componentName;
    }
}
