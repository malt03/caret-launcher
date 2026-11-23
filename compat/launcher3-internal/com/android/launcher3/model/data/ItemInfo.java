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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings.Favorites;

import java.util.Optional;

/**
 * Represents an item in the launcher.
 *
 * <p>Stand-in for Launcher3's ItemInfo (android-17.0.0_r1), made from ItemInfo.java by deleting
 * what TextLauncher does not have (the database, screens, animation types, logging protos,
 * extended containers and activity aliases); changes are marked with "TextLauncher:".
 */
public class ItemInfo {

    public static final int NO_ID = -1;

    /**
     * The id in the settings database for this item
     */
    public int id = NO_ID;

    /**
     * One of {@link Favorites#ITEM_TYPE_APPLICATION},
     * {@link Favorites#ITEM_TYPE_DEEP_SHORTCUT}
     * {@link Favorites#ITEM_TYPE_FOLDER},
     * {@link Favorites#ITEM_TYPE_APP_GROUP},
     * {@link Favorites#ITEM_TYPE_APPWIDGET} or
     * {@link Favorites#ITEM_TYPE_CUSTOM_APPWIDGET}.
     * {@link Favorites#ITEM_TYPE_TASK}.
     * {@link Favorites#ITEM_TYPE_QSB}.
     * {@link Favorites#ITEM_TYPE_SEARCH_ACTION}.
     * {@link Favorites#ITEM_TYPE_PRIVATE_SPACE_INSTALL_APP_BUTTON}.
     * {@link Favorites#ITEM_TYPE_FILE_SYSTEM_FILE}.
     * {@link Favorites#ITEM_TYPE_FILE_SYSTEM_FOLDER}.
     */
    public int itemType;

    /**
     * The id of the container that holds this item. For the desktop, this will be
     * {@link Favorites#CONTAINER_DESKTOP}. For the all applications folder it
     * will be {@link #NO_ID} (since it is not stored in the settings DB). For user folders
     * it will be the id of the folder.
     */
    public int container = NO_ID;

    /**
     * Indicates the X position of the associated cell.
     */
    public int cellX = -1;

    /**
     * Indicates the Y position of the associated cell.
     */
    public int cellY = -1;

    /**
     * Indicates the X cell span.
     */
    public int spanX = 1;

    /**
     * Indicates the Y cell span.
     */
    public int spanY = 1;

    /**
     * Indicates the minimum X cell span.
     */
    public int minSpanX = 1;

    /**
     * Indicates the minimum Y cell span.
     */
    public int minSpanY = 1;

    /**
     * Indicates the position in an ordered list.
     */
    public int rank = 0;

    /**
     * Title of the item
     */
    @Nullable
    public CharSequence title;

    /**
     * Optionally set: The appTitle might e.g. be different if {@code title} is used to
     * display progress (e.g. Downloading..).
     */
    @Nullable
    public CharSequence appTitle;

    /**
     * Content description of the item.
     */
    @Nullable
    public CharSequence contentDescription;

    /**
     * When the instance is created using {@link #copyFrom}, this field is used to keep track of
     * original {@link ComponentName}.
     */
    @Nullable
    private ComponentName mComponentName;

    @NonNull
    public UserHandle user;

    public ItemInfo() {
        user = Process.myUserHandle();
    }

    protected ItemInfo(@NonNull final ItemInfo info) {
        copyFrom(info);
    }

    public void copyFrom(@NonNull final ItemInfo info) {
        id = info.id;
        title = info.title;
        cellX = info.cellX;
        cellY = info.cellY;
        spanX = info.spanX;
        spanY = info.spanY;
        minSpanX = info.minSpanX;
        minSpanY = info.minSpanY;
        rank = info.rank;
        itemType = info.itemType;
        container = info.container;
        user = info.user;
        contentDescription = info.contentDescription;
        mComponentName = info.getTargetComponent();
    }

    @Nullable
    public Intent getIntent() {
        return null;
    }

    /**
     * Returns the Activity TargetComponent of the item that an Intent is trying to start.
     */
    @Nullable
    public ComponentName getTargetComponent() {
        // TextLauncher: mComponentName through getComponent(), which the app's item for an app
        // overrides.
        return Optional.ofNullable(getIntent()).map(Intent::getComponent).orElse(getComponent());
    }

    /**
     * Returns this item's package name.
     *
     * Prioritizes the component package name, then uses the intent package name as a fallback.
     * This ensures deep shortcuts are supported.
     */
    @Nullable
    public String getTargetPackage() {
        ComponentName component = getTargetComponent();
        Intent intent = getIntent();

        return component != null
                ? component.getPackageName()
                : intent != null
                        ? intent.getPackage()
                        : null;
    }

    @NonNull
    protected String dumpProperties() {
        return "id=" + id
                // TextLauncher: no item type names, container infos or resolved targets.
                + " type=" + itemType
                + " container=" + container
                + " targetComponent=" + getTargetComponent()
                + " cell(" + cellX + "," + cellY + ")"
                + " span(" + spanX + "," + spanY + ")"
                + " minSpan(" + minSpanX + "," + minSpanY + ")"
                + " rank=" + rank
                + " user=" + user
                + " title=" + title;
    }

    /**
     * Whether this item is disabled.
     */
    public boolean isDisabled() {
        return false;
    }

    /**
     * Returns shallow copy of the object.
     */
    @NonNull
    public ItemInfo makeShallowCopy() {
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.copyFrom(this);
        return itemInfo;
    }

    // TextLauncher: members TextLauncher adds.

    /**
     * The component of an item whose intent has none: {@link #mComponentName}. The app's item for
     * an app overrides it, as its component comes from the installed app it is bound to.
     */
    @Nullable
    protected ComponentName getComponent() {
        return mComponentName;
    }
}
