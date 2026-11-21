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

package com.android.launcher3.icons;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.launcher3.BuildConfig;

import com.android.launcher3.icons.cache.CacheLookupFlag;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.util.ApplicationInfoWrapper;

import java.util.concurrent.Executor;

/**
 * Stand-in for Launcher3's IconCache. TextLauncher loads every icon at full resolution with its
 * theme icon (icons/AppIconLoader in the app), so there is never a higher-res icon to fetch. App
 * shortcut icons are made by the app's icon factory, which the app sets here.
 */
public class IconCache {
    /** IconCache.ItemInfoUpdateReceiver */
    public interface ItemInfoUpdateReceiver {
        void reapplyItemInfo(ItemInfoWithIcon info);
    }

    /**
     * TextLauncher: the app's icon factory, which makes the shortcut icons the way
     * CacheableShortcutCachingLogic.loadIcon does with LauncherIcons.
     */
    public interface ShortcutIconFactory {
        Context getContext();

        /** LauncherIcons.fullResIconDpi */
        int getFullResIconDpi();

        /**
         * LauncherIcons.createBadgedIconBitmap(icon, IconOptions().setExtractedColor(
         * Themes.getColorAccent(context))).
         */
        BitmapInfo createShortcutIconBitmap(Drawable icon);
    }

    private volatile ShortcutIconFactory mShortcutIconFactory;

    public void setShortcutIconFactory(ShortcutIconFactory factory) {
        mShortcutIconFactory = factory;
    }

    public void updateIconInBackground(Executor uiExecutor, ItemInfoUpdateReceiver caller,
            ItemInfoWithIcon info, CacheLookupFlag lookupFlag) { }

    /**
     * Fill in [info] with the icon for [si]. If the icon is not available, it keeps the old icon.
     * TextLauncher: shortcut icons are made each time instead of cached, and are not badged (the
     * long-press menu hides badges).
     */
    public void getShortcutIcon(ItemInfoWithIcon info, ShortcutInfo si,
            ApplicationInfoWrapper appInfo) {
        ShortcutIconFactory li = mShortcutIconFactory;
        if (li == null) return;
        Drawable d = getIcon(li.getContext(), si, li.getFullResIconDpi());
        if (d == null) return;
        info.bitmap = li.createShortcutIconBitmap(d);
    }

    /**
     * CacheableShortcutInfo.getIcon: similar to [LauncherApps.getShortcutIconDrawable] with
     * additional Launcher specific checks
     */
    private static Drawable getIcon(Context context, ShortcutInfo shortcutInfo, int density) {
        if (!BuildConfig.WIDGETS_ENABLED) {
            return null;
        }
        try {
            return context
                    .getSystemService(LauncherApps.class)
                    .getShortcutIconDrawable(shortcutInfo, density);
        } catch (Exception e) {
            Log.e("CacheableShortcutInfo", "Failed to get shortcut icon", e);
            return null;
        }
    }
}
