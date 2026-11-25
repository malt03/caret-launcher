/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.touch;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_OPEN;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_BY_PUBLISHER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_FILE_SYSTEM_NOT_READY;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ItemInfoMatcher;

import java.util.Collections;

/**
 * Class for handling clicks on workspace and all-apps items
 *
 * <p>Stand-in for Launcher3's ItemClickHandler (android-17.0.0_r1), made from ItemClickHandler.java
 * by deleting what CaretLauncher does not have (app pairs, widgets, promise icons, install
 * sessions, private space); changes are marked with "CaretLauncher:".
 */
public class ItemClickHandler {

    private static final String TAG = "ItemClickHandler";
    private static final boolean DEBUG = true;

    /**
     * Instance used for click handling on items
     */
    public static final OnClickListener INSTANCE = ItemClickHandler::onClick;

    private static void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) return;

        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!launcher.getWorkspace().isFinishedSwitchingState()) return;

        Trace.beginSection("ItemClickHandler#onClick");
        Object tag = v.getTag();
        if (tag instanceof WorkspaceItemInfo) {
            onClickAppShortcut(v, (WorkspaceItemInfo) tag, launcher);
        } else if (tag instanceof FolderInfo) {
            onClickFolderIcon(v);
        // CaretLauncher: no app pairs.
        } else if (tag instanceof AppInfo) {
            startAppShortcutOrInfoActivity(v, (AppInfo) tag, launcher);
        // CaretLauncher: no widgets.
        } else if (tag instanceof ItemClickProxy) {
            ((ItemClickProxy) tag).onItemClicked(v);
        }
        Trace.endSection();
    }

    /**
     * Event handler for a folder icon click.
     *
     * @param v The view that was clicked. Must be an instance of {@link FolderIcon}.
     */
    private static void onClickFolderIcon(View v) {
        Folder folder = ((FolderIcon) v).getFolder();
        if (!folder.isOpen() && !folder.isDestroyed()) {
            // Open the requested folder
            folder.animateOpen();
            StatsLogManager.newInstance(v.getContext()).logger().withItemInfo(folder.mInfo)
                    .log(LAUNCHER_FOLDER_OPEN);
        }
    }

    /**
     * Handles clicking on a disabled shortcut
     *
     * @return true iff the disabled item click has been handled.
     */
    public static boolean handleDisabledItemClicked(WorkspaceItemInfo shortcut, Context context) {
        final int disabledFlags = shortcut.runtimeStatusFlags
                & WorkspaceItemInfo.FLAG_DISABLED_MASK;
        // Handle the case where the disabled reason is DISABLED_REASON_VERSION_LOWER.
        // Show an AlertDialog for the user to choose either updating the app or cancel the launch.
        if (maybeCreateAlertDialogForShortcut(shortcut, context)) {
            return true;
        }

        if ((disabledFlags
                & ~FLAG_DISABLED_SUSPENDED
                & ~FLAG_DISABLED_QUIET_USER) == 0) {
            // If the app is only disabled because of the above flags, launch activity anyway.
            // Framework will tell the user why the app is suspended.
            return false;
        } else {
            if (!TextUtils.isEmpty(shortcut.disabledMessage)) {
                // Use a message specific to this shortcut, if it has one.
                Toast.makeText(context, shortcut.disabledMessage, Toast.LENGTH_SHORT).show();
                return true;
            }
            // Otherwise just use a generic error message.
            final int error;
            final int runtimeStatusFlags = shortcut.runtimeStatusFlags;
            if ((runtimeStatusFlags & FLAG_DISABLED_SAFEMODE) != 0) {
                error = R.string.safemode_shortcut_error;
            } else if ((runtimeStatusFlags & FLAG_DISABLED_BY_PUBLISHER) != 0
                    || (runtimeStatusFlags & FLAG_DISABLED_FILE_SYSTEM_NOT_READY) != 0
                    || (runtimeStatusFlags & FLAG_DISABLED_LOCKED_USER) != 0) {
                error = R.string.shortcut_not_available;
            } else {
                error = R.string.activity_not_available;
            }
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private static boolean maybeCreateAlertDialogForShortcut(final WorkspaceItemInfo shortcut,
            Context context) {
        try {
            final Launcher launcher = Launcher.getLauncher(context);
            if (shortcut.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                    && shortcut.isDisabledVersionLower()) {
                final Intent marketIntent = shortcut.getMarketIntent(context);
                // No market intent means no target package for the shortcut, which should be an
                // issue. Falling back to showing toast messages.
                if (marketIntent == null) {
                    return false;
                }

                new AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_update_title)
                        .setMessage(R.string.dialog_update_message)
                        .setPositiveButton(R.string.dialog_update, (d, i) -> {
                            // Direct the user to the play store to update the app
                            context.startActivity(marketIntent);
                        })
                        .setNeutralButton(R.string.dialog_remove, (d, i) -> {
                            // Remove the icon if launcher is successfully initialized
                            launcher.getWorkspace().persistRemoveItemsByMatcher(ItemInfoMatcher
                                    .ofShortcutKeys(Collections.singleton(ShortcutKey
                                            .fromItemInfo(shortcut))),
                                    "user explicitly removes disabled shortcut");
                        })
                        .create()
                        .show();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating alert dialog", e);
        }

        return false;
    }

    /**
     * Event handler for an app shortcut click.
     *
     * @param v The view that was clicked. Must be a tagged with a {@link WorkspaceItemInfo}.
     */
    public static void onClickAppShortcut(View v, WorkspaceItemInfo shortcut, Launcher launcher) {
        // CaretLauncher: the launcher is not an Activity; its context is.
        if (shortcut.isDisabled() && handleDisabledItemClicked(shortcut, launcher.asContext())) {
            return;
        }

        // CaretLauncher: only installed apps are shown, so there are no promise icons.

        // Start activities
        startAppShortcutOrInfoActivity(v, shortcut, launcher);
    }

    private static void startAppShortcutOrInfoActivity(View v, ItemInfo item, Launcher launcher) {
        Intent intent = item.getIntent();
        // CaretLauncher: no install sessions or private space install button.
        if (intent == null) {
            throw new IllegalArgumentException("Input must have a valid intent");
        }
        // CaretLauncher: no instant app web UI, start-for-result items or floating icon view.
        launcher.startActivitySafely(v, intent, item);
    }

    /**
     * Interface to indicate that an item will handle the click itself.
     */
    public interface ItemClickProxy {

        /**
         * Called when the item is clicked
         */
        void onItemClicked(View view);
    }
}
