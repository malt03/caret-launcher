package com.android.launcher3.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;

/** Stand-in for Launcher3's ApplicationInfoWrapper: the app info of a package for a user. */
public class ApplicationInfoWrapper {
    private final ApplicationInfo mInfo;

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
}
