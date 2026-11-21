/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.popup;

import android.content.ComponentName;
import android.content.Context;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.ShortcutUtil;

/**
 * Stand-in for Launcher3's PopupDataProvider (android-17.0.0_r1): how many app shortcuts an item
 * has. getShortcutCountForItem is copied from PopupDataProvider.java.
 *
 * TextLauncher: Launcher3 reads the count from the deep shortcut map that its model loads in the
 * background (published shortcuts per activity). There is no such model here, so the same query
 * runs when the menu opens.
 */
public class PopupDataProvider {
    private final Context mContext;

    public PopupDataProvider(Context context) {
        mContext = context;
    }

    public int getShortcutCountForItem(ItemInfo info) {
        if (!ShortcutUtil.supportsDeepShortcuts(info)) {
            return 0;
        }
        ComponentName component = info.getTargetComponent();
        if (component == null) {
            return 0;
        }

        return new ShortcutRequest(mContext, info.user)
                .withContainer(component)
                .query(ShortcutRequest.PUBLISHED)
                .size();
    }
}
