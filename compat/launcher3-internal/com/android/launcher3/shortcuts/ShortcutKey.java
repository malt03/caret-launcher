/*
 * The upstream file has no license header; Launcher3 is licensed as below.
 *
 * Copyright (C) The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.content.Intent;
import android.content.pm.ShortcutInfo;

/**
 * Stand-in for Launcher3's ShortcutKey (android-17.0.0_r1). Only the intent that identifies an app
 * shortcut, copied from ShortcutKey.java.
 */
public class ShortcutKey {

    public static final String EXTRA_SHORTCUT_ID = "shortcut_id";
    private static final String INTENT_CATEGORY = "com.android.launcher3.DEEP_SHORTCUT";

    public static Intent makeIntent(ShortcutInfo si) {
        return makeIntent(si.getId(), si.getPackage()).setComponent(si.getActivity());
    }

    /**
     * Creates an intent for shortcut id and package name.
     */
    public static Intent makeIntent(String shortcutId, String packageName) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(INTENT_CATEGORY)
                .setPackage(packageName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(EXTRA_SHORTCUT_ID, shortcutId);
    }
}
