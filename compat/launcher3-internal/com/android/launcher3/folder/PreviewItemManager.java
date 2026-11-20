package com.android.launcher3.folder;

/** Stand-in for Launcher3's PreviewItemManager. Only what the folder animation calls. */
public class PreviewItemManager {
    private final Runnable mRecompute;

    public PreviewItemManager(Runnable recompute) {
        mRecompute = recompute;
    }

    public void recomputePreviewDrawingParams() {
        mRecompute.run();
    }
}
