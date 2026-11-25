/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.logging;

import android.util.Log;

/** Stand-in for Launcher3's FileLog: logs to logcat only. */
public final class FileLog {
    private FileLog() {}

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
    }

    public static void d(String tag, String msg, Exception e) {
        Log.d(tag, msg, e);
    }

    public static void e(String tag, String msg, Exception e) {
        Log.e(tag, msg, e);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void w(String tag, String msg, Exception e) {
        Log.w(tag, msg, e);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
    }
}
