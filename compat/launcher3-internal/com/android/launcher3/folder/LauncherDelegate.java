package com.android.launcher3.folder;

import com.android.launcher3.Launcher;

/** Stand-in for Launcher3's folder.LauncherDelegate. Only the launcher accessor. */
public class LauncherDelegate {
    private final Launcher mLauncher;

    public LauncherDelegate(Launcher launcher) {
        mLauncher = launcher;
    }

    public Launcher getLauncher() {
        return mLauncher;
    }
}
