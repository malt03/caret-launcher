package com.android.launcher3.logging;

import android.util.Log;

/** Stand-in for Launcher3's FileLog: logs to logcat only. */
public final class FileLog {
    private FileLog() {}

    public static void e(String tag, String msg, Exception e) {
        Log.e(tag, msg, e);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }
}
