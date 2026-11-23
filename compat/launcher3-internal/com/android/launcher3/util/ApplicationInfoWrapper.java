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

package com.android.launcher3.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;

/** Stand-in for Launcher3's ApplicationInfoWrapper: the app info of a package for a user. */
public class ApplicationInfoWrapper {
    private final ApplicationInfo mInfo;

    public ApplicationInfoWrapper(ApplicationInfo appInfo) {
        mInfo = appInfo;
    }

    public ApplicationInfoWrapper(Context context, String packageName, UserHandle user) {
        ApplicationInfo info = null;
        try {
            info = context.getSystemService(LauncherApps.class)
                    .getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES, user);
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException e) {
            // Not installed for the user.
        }
        mInfo = info;
    }

    public ApplicationInfo getInfo() {
        return mInfo;
    }

    private boolean hasFlag(int flag) {
        return mInfo != null && (mInfo.flags & flag) != 0;
    }

    /**
     * Returns true if the app can possibly be on the SDCard. This is just a workaround and doesn't
     * guarantee that the app is on SD card.
     */
    public boolean isOnSdCard() {
        return hasFlag(ApplicationInfo.FLAG_EXTERNAL_STORAGE);
    }
}
