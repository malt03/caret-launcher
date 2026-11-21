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

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for Launcher3's FolderInfo. Only the contents, as FolderGridOrganizer and the folder
 * icon read them. Folders have no titles in TextLauncher (docs/spec.md 5.2).
 */
public class FolderInfo extends ItemInfo {
    private final List<ItemInfo> mContents = new ArrayList<>();

    public FolderInfo() {
        itemType = ITEM_TYPE_FOLDER;
    }

    public List<ItemInfo> getContents() {
        return mContents;
    }

    public static boolean willAcceptItemType(int itemType) {
        return itemType == ITEM_TYPE_APPLICATION
                || itemType == ITEM_TYPE_DEEP_SHORTCUT
                || itemType == ITEM_TYPE_APP_GROUP;
    }
}
