package com.android.launcher3.accessibility;

import android.view.View;

/**
 * Stand-in for Launcher3's DragViewStateAnnouncer. TextLauncher has no drag accessibility
 * announcements yet, so none is created.
 */
public class DragViewStateAnnouncer {
    public static DragViewStateAnnouncer createFor(View v) {
        return null;
    }

    public void announce(CharSequence msg) { }

    public void cancel() { }

    public void completeAction(int actionId) { }
}
