package com.android.launcher3.util;

import android.os.Handler;
import android.os.HandlerThread;

/**
 * Stand-in for Launcher3's Executors. Only MODEL_EXECUTOR's handler, which DragView uses for spring
 * icons (not used in TextLauncher: drags are started with allowSpringDrawable = false).
 */
public class Executors {
    public static final LooperExecutor MODEL_EXECUTOR = new LooperExecutor("launcher-loader");

    public static class LooperExecutor {
        private final String mName;
        private Handler mHandler;

        LooperExecutor(String name) {
            mName = name;
        }

        public synchronized Handler getHandler() {
            if (mHandler == null) {
                HandlerThread thread = new HandlerThread(mName);
                thread.start();
                mHandler = new Handler(thread.getLooper());
            }
            return mHandler;
        }
    }
}
