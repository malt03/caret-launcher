package com.android.launcher3.logging;

/** Stand-in for Launcher3's InstanceIdSequence. */
public class InstanceIdSequence {
    private static int sNext = 1;

    public InstanceId newInstanceId() {
        return new InstanceId(sNext++);
    }
}
