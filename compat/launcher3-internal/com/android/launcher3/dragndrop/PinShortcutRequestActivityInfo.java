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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.dragndrop;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.cache.CachedObject;
import com.android.launcher3.icons.cache.IconLoadRequest;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;

import java.util.function.Supplier;

/**
 * Extension of ShortcutConfigActivityInfo to be used in the confirmation prompt for pin item
 * request.
 *
 * <p>Stand-in for Launcher3's PinShortcutRequestActivityInfo (android-17.0.0_r1): what the widget
 * picker's list shows. CaretLauncher: pin requests are not handled yet, so the shortcut cannot be
 * added from it (no createWorkspaceItemInfo or startConfigActivity, which take
 * PinRequestHelper and the launcher states).
 */
public class PinShortcutRequestActivityInfo extends ShortcutConfigActivityInfo {

    // Class name used in the target component, such that it will never represent an
    // actual existing class.
    private static final String STUB_COMPONENT_CLASS = "pinned-shortcut";

    private final Supplier<PinItemRequest> mRequestSupplier;
    private final ShortcutInfo mInfo;
    private final Context mContext;

    public PinShortcutRequestActivityInfo(PinItemRequest request, Context context) {
        this(request.getShortcutInfo(), () -> request, context);
    }

    public PinShortcutRequestActivityInfo(
            ShortcutInfo si, Supplier<PinItemRequest> requestSupplier, Context context) {
        super(new ComponentName(si.getPackage(), STUB_COMPONENT_CLASS),
                si.getUserHandle(), context);
        mRequestSupplier = requestSupplier;
        mInfo = si;
        mContext = context;
    }

    @Override
    public int getItemType() {
        return LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
    }

    @Override
    public CharSequence getLabel() {
        return mInfo.getShortLabel();
    }

    @Override
    public Drawable getFullResIcon(@NonNull IconLoadRequest<CachedObject> request) {
        Drawable d = mContext.getSystemService(LauncherApps.class)
                .getShortcutIconDrawable(mInfo, request.iconDpi);
        return d != null ? d : request.getDefaultIcon().newIcon(mContext);
    }

    @Override
    public boolean isPersistable() {
        return false;
    }
}
