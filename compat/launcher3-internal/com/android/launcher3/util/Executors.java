/*
 * Copyright (C) 2008 The Android Open Source Project
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
