package com.android.launcher3.logging;

/** Stand-in for Launcher3's logging InstanceId. TextLauncher does not log. */
public final class InstanceId {
    private final int mId;

    InstanceId(int id) {
        mId = id;
    }

    public int getId() {
        return mId;
    }
}
