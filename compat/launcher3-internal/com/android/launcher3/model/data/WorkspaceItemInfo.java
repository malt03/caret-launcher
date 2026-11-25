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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ContentWriter;

/**
 * Represents a launchable icon on the workspaces and in folders.
 *
 * <p>Stand-in for Launcher3's WorkspaceItemInfo (android-17.0.0_r1), made from
 * WorkspaceItemInfo.java by deleting what TextLauncher does not have (restore and
 * install status, promise icons, archived apps, bubbles and private space); changes are marked
 * with "TextLauncher:".
 */
public class WorkspaceItemInfo extends ItemInfoWithIcon {

    /**
     * The intent used to start the application.
     */
    @NonNull
    // TextLauncher: never null, for the items the app makes before their intents are known.
    public Intent intent = new Intent();

    /**
     * A message to display when the user tries to start a disabled shortcut.
     * This is currently only used for deep shortcuts.
     */
    public CharSequence disabledMessage;

    /**
     * A set of person's Id associated with the WorkspaceItemInfo, this is only used if the item
     * represents a deep shortcut.
     */
    @NonNull private String[] personKeys = Utilities.EMPTY_STRING_ARRAY;

    public WorkspaceItemInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    }

    public WorkspaceItemInfo(WorkspaceItemInfo info) {
        super(info);
        title = info.title;
        intent = new Intent(info.intent);
        // TextLauncher: no status.
        personKeys = info.personKeys.clone();
    }

    /** TODO: Remove this.  It's only called by ApplicationInfo.makeWorkspaceItem. */
    public WorkspaceItemInfo(AppInfo info) {
        super(info);
        title = Utilities.trim(info.title);
        intent = new Intent(info.getIntent());
    }

    /**
     * Creates a {@link WorkspaceItemInfo} from a {@link ShortcutInfo}.
     */
    public WorkspaceItemInfo(ShortcutInfo shortcutInfo, Context context) {
        user = shortcutInfo.getUserHandle();
        itemType = Favorites.ITEM_TYPE_DEEP_SHORTCUT;
        // TextLauncher: no private space (docs/spec.md), so nothing is marked not pinnable here.
        updateFromDeepShortcutInfo(shortcutInfo, context);
    }

    @Override
    public void onAddToDatabase(@NonNull ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(Favorites.TITLE, title)
                .put(Favorites.INTENT, getIntent());
        // TextLauncher: no options or status (RESTORED); the columns keep their defaults.

        if (!getMatchingLookupFlag().useLowRes()) {
            writer.putIcon(bitmap, user);
        }
    }

    @Override
    @NonNull
    public Intent getIntent() {
        return intent;
    }

    /**
     * Returns true if the workspace item supports promise icon UI. There are a few cases where they
     * are supported:
     * 1. Icons to be restored via backup/restore.
     * 2. Icons added as an auto-install app.
     * 3. Icons added due to it being an active install session created by the user.
     * 4. Icons for archived apps.
     * TextLauncher: only installed apps are shown, so there are no promise icons.
     */
    public boolean hasPromiseIconUi() {
        return false;
    }

    public void updateFromDeepShortcutInfo(@NonNull final ShortcutInfo shortcutInfo,
            @NonNull final Context context) {
        // {@link ShortcutInfo#getActivity} can change during an update. Recreate the intent
        intent = ShortcutKey.makeIntent(shortcutInfo);
        title = shortcutInfo.getShortLabel();

        CharSequence label = shortcutInfo.getLongLabel();
        if (TextUtils.isEmpty(label)) {
            label = shortcutInfo.getShortLabel();
        }
        try {
            contentDescription = context.getPackageManager().getUserBadgedLabel(label, user);
        } catch (SecurityException e) {
            contentDescription = null;
            Log.e(TAG, "Failed to get content description", e);
        }

        if (shortcutInfo.isEnabled()) {
            runtimeStatusFlags &= ~FLAG_DISABLED_BY_PUBLISHER;
        } else {
            Log.w(TAG, "updateFromDeepShortcutInfo: Updated shortcut has been disabled. "
                    + " package=" + shortcutInfo.getPackage()
                    + " disabledReason=" + shortcutInfo.getDisabledReason());
            runtimeStatusFlags |= FLAG_DISABLED_BY_PUBLISHER;
        }

        if (shortcutInfo.getDisabledReason() == ShortcutInfo.DISABLED_REASON_VERSION_LOWER) {
            runtimeStatusFlags |= FLAG_DISABLED_VERSION_LOWER;
        } else {
            runtimeStatusFlags &= ~FLAG_DISABLED_VERSION_LOWER;
        }

        // TextLauncher: no person keys; ApiWrapper.getPersons is empty outside the system image
        // (ShortcutInfo.getPersons is not public).
    }

    /**
     * {@code true} if the shortcut is disabled due to its app being a lower version.
     */
    public boolean isDisabledVersionLower() {
        return (runtimeStatusFlags & FLAG_DISABLED_VERSION_LOWER) != 0;
    }

    /** Returns the WorkspaceItemInfo id associated with the deep shortcut. */
    public String getDeepShortcutId() {
        return itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT
                ? getIntent().getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID) : null;
    }

    @NonNull
    public String[] getPersonKeys() {
        return personKeys;
    }

    @Override
    public WorkspaceItemInfo clone() {
        return new WorkspaceItemInfo(this);
    }
}
