package com.android.launcher3;

import com.android.launcher3.views.ScrimView;

/**
 * Stand-in for Launcher3's Launcher: the parts of the home screen that the folder animation
 * changes (FolderSpringAnimatorSet.addWorkspaceAnimators).
 */
public class Launcher {
    private final Workspace<?> mWorkspace;
    private final Hotseat mHotseat;
    private final ScrimView mScrimView;

    public Launcher(Workspace<?> workspace, Hotseat hotseat, ScrimView scrimView) {
        mWorkspace = workspace;
        mHotseat = hotseat;
        mScrimView = scrimView;
    }

    public Workspace<?> getWorkspace() {
        return mWorkspace;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public ScrimView getScrimView() {
        return mScrimView;
    }

    /** No wallpaper zoom: it needs a system permission. */
    public DepthController getDepthController() {
        return null;
    }

    /** Stand-in for statehandlers.DepthController. */
    public static class DepthController {
        public com.android.launcher3.util.MultiPropertyFactory<?>.MultiProperty getFolderZoom() {
            return null;
        }
    }
}
