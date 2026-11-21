/*
 * Copyright (C) 2015 The Android Open Source Project
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
