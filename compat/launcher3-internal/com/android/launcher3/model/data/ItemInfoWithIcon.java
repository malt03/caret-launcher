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

package com.android.launcher3.model.data;

import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Flags;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.icons.cache.CacheLookupFlag;

/**
 * Represents an ItemInfo which also holds an icon.
 *
 * <p>Stand-in for Launcher3's ItemInfoWithIcon (android-17.0.0_r1), made from
 * ItemInfoWithIcon.java by deleting what TextLauncher does not have (install progress, archived
 * apps, multi-instance and resize flags, App Lock, automation and home screen files); changes are
 * marked with "TextLauncher:".
 */
public abstract class ItemInfoWithIcon extends ItemInfo {

    public static final String TAG = "ItemInfoDebug";

    /**
     * The bitmap for the application icon
     */
    @NonNull
    public BitmapInfo bitmap = BitmapInfo.LOW_RES_INFO;

    /**
     * Indicates that the icon is disabled due to safe mode restrictions.
     */
    public static final int FLAG_DISABLED_SAFEMODE = 1 << 0;

    /**
     * Indicates that the icon is disabled as the app is not available.
     */
    public static final int FLAG_DISABLED_NOT_AVAILABLE = 1 << 1;

    /**
     * Indicates that the icon is disabled as the app is suspended
     */
    public static final int FLAG_DISABLED_SUSPENDED = 1 << 2;

    /**
     * Indicates that the icon is disabled as the user is in quiet mode.
     */
    public static final int FLAG_DISABLED_QUIET_USER = 1 << 3;

    /**
     * Indicates that the icon is disabled as the publisher has disabled the actual shortcut.
     */
    public static final int FLAG_DISABLED_BY_PUBLISHER = 1 << 4;

    /**
     * Indicates that the icon is disabled as the user partition is currently locked.
     */
    public static final int FLAG_DISABLED_LOCKED_USER = 1 << 5;

    /**
     * The item points to a system app.
     */
    public static final int FLAG_SYSTEM_YES = 1 << 6;

    /**
     * The item points to a non system app.
     */
    public static final int FLAG_SYSTEM_NO = 1 << 7;

    public static final int FLAG_SYSTEM_MASK = FLAG_SYSTEM_YES | FLAG_SYSTEM_NO;

    /**
     * The icon is being installed. If {@link WorkspaceItemInfo#FLAG_RESTORED_ICON} or
     * {@link WorkspaceItemInfo#FLAG_AUTOINSTALL_ICON} is set, then the icon is either being
     * installed or is in a broken state.
     */
    public static final int FLAG_INSTALL_SESSION_ACTIVE = 1 << 10;

    /**
     * This icon is still being downloaded.
     */
    public static final int FLAG_INCREMENTAL_DOWNLOAD_ACTIVE = 1 << 11;

    public static final int FLAG_SHOW_DOWNLOAD_PROGRESS_MASK = FLAG_INSTALL_SESSION_ACTIVE
            | FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;

    /**
     * Indicates that the icon is a disabled shortcut and application updates are required.
     */
    public static final int FLAG_DISABLED_VERSION_LOWER = 1 << 12;

    /**
     * Flag indicating this item can't be pinned to home screen.
     */
    public static final int FLAG_NOT_PINNABLE = 1 << 13;

    /**
     * Flag indicating whether the package related to the item & user corresponds to that of
     * archived app.
     */
    public static final int FLAG_ARCHIVED = 1 << 14;

    /**
     * Flag indicating whether the package related to the item & user does not support resizing.
     */
    public static final int FLAG_NOT_RESIZEABLE = 1 << 15;

    /**
     * Flag indicating whether the package related to the item & user supports multiple instances.
     */
    public static final int FLAG_SUPPORTS_MULTI_INSTANCE = 1 << 16;

    /**
     * Indicates that the icon is disabled because the file system is not ready.
     */
    public static final int FLAG_DISABLED_FILE_SYSTEM_NOT_READY = 1 << 17;

    public static final int FLAG_DISABLED_MASK = FLAG_DISABLED_SAFEMODE
            | FLAG_DISABLED_NOT_AVAILABLE | FLAG_DISABLED_SUSPENDED
            | FLAG_DISABLED_QUIET_USER | FLAG_DISABLED_BY_PUBLISHER | FLAG_DISABLED_LOCKED_USER
            | FLAG_DISABLED_VERSION_LOWER | FLAG_DISABLED_FILE_SYSTEM_NOT_READY;

    /**
     * Indicates that the package is currently being automated.
     */
    public static final int FLAG_AUTOMATED = 1 << 18;

    /**
     * Status associated with the system state of the underlying item. This is calculated every
     * time a new info is created and not persisted on the disk.
     */
    public int runtimeStatusFlags = 0;

    protected ItemInfoWithIcon() {
    }

    protected ItemInfoWithIcon(ItemInfoWithIcon info) {
        super(info);
        bitmap = info.bitmap;
        runtimeStatusFlags = info.runtimeStatusFlags;
        user = info.user;
    }

    @Override
    public boolean isDisabled() {
        return (runtimeStatusFlags & FLAG_DISABLED_MASK) != 0;
    }

    /**
     * Returns the lookup flag to match this current state of this info
     */
    public CacheLookupFlag getMatchingLookupFlag() {
        return bitmap.getMatchingLookupFlag();
    }

    /** Creates an intent to that launches the app store at this app's page. */
    @Nullable
    public Intent getMarketIntent(Context context) {
        String targetPackage = getTargetPackage();

        return targetPackage != null
                // TextLauncher: ApiWrapper.getAppMarketActivityIntent's market intent, inline
                // (without the referrer).
                ? new Intent(Intent.ACTION_VIEW)
                        .setData(new Uri.Builder()
                                .scheme("market")
                                .authority("details")
                                .appendQueryParameter("id", targetPackage)
                                .build())
                : null;
    }


    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public FastBitmapDrawable newIcon(Context context) {
        return newIcon(context, 0);
    }

    public FastBitmapDrawable newIcon(Context context, @DrawableCreationFlags int creationFlags) {
        ThemeManager themeManager = ThemeManager.INSTANCE.get(context);
        IconShape iconShape = null;
        // TextLauncher: no home screen files; ThemeManager.getIconShapeData() returns the value.
        if (supportsCustomShapes(creationFlags)) {
            iconShape = themeManager.getIconShapeData();
        }
        if (!themeManager.isIconThemeEnabled()) {
            creationFlags &= ~FLAG_THEMED;
        }
        FastBitmapDrawable drawable = bitmap.newIcon(context, creationFlags, iconShape);
        drawable.setDisabled(isDisabled());
        return drawable;
    }

    /**
     * Returns true if the current BitmapInfo can support cropping to custom icon shapes.
     */
    public boolean supportsCustomShapes(@DrawableCreationFlags int creationFlags) {
        return Flags.enableLauncherIconShapes()
                && (creationFlags & FLAG_THEMED) != 0
                && bitmap.isFullBleed();
    }

    /**
     * Returns true if item is a Promise Icon or actively downloading, and the item is not an
     * inactive archived app.
     *
     * TextLauncher: only installed apps are shown, so nothing is pending or downloading.
     */
    public boolean shouldShowPendingIcon() {
        return false;
    }
}
