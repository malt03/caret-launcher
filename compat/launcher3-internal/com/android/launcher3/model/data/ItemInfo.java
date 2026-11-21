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

package com.android.launcher3.model.data;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;

import java.util.Optional;

/**
 * Stand-in for Launcher3's ItemInfo (android-17.0.0_r1). Only the fields and methods that copied
 * code reads; the method bodies are copied from ItemInfo.java.
 */
public class ItemInfo {
    public static final int NO_ID = -1;

    public int id = NO_ID;
    public int container = NO_ID;
    public int cellX = -1;
    public int cellY = -1;
    public int spanX = 1;
    public int spanY = 1;
    public int rank = 0;
    public int itemType = 0;
    public CharSequence title;
    public CharSequence contentDescription;
    public UserHandle user = Process.myUserHandle();

    public ItemInfo makeShallowCopy() {
        ItemInfo info = new ItemInfo();
        info.id = id;
        info.container = container;
        info.cellX = cellX;
        info.cellY = cellY;
        info.spanX = spanX;
        info.spanY = spanY;
        info.rank = rank;
        info.itemType = itemType;
        info.title = title;
        info.contentDescription = contentDescription;
        info.user = user;
        return info;
    }

    public Intent getIntent() {
        return null;
    }

    /**
     * Returns the Activity TargetComponent of the item that an Intent is trying to start.
     * TextLauncher: the component of an item without an intent comes from
     * {@link #getComponent()} (mComponentName in Launcher3).
     */
    public ComponentName getTargetComponent() {
        return Optional.ofNullable(getIntent()).map(Intent::getComponent).orElse(getComponent());
    }

    /** TextLauncher: ItemInfo.mComponentName; the app's ItemInfo for an app overrides it. */
    protected ComponentName getComponent() {
        return null;
    }

    public String getTargetPackage() {
        ComponentName component = getTargetComponent();
        Intent intent = getIntent();

        return component != null
                ? component.getPackageName()
                : intent != null
                        ? intent.getPackage()
                        : null;
    }

    public boolean isDisabled() {
        return false;
    }
}
