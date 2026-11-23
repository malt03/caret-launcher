/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.launcher3.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.PackageUserKey;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to working with {@link AppWidgetManager}
 *
 * <p>Stand-in for Launcher3's WidgetManagerHelper (android-17.0.0_r1). TextLauncher: widgets are
 * always enabled (BuildConfig.WIDGETS_ENABLED), there are no custom widgets (CustomWidgetManager),
 * and generated previews are on (android.appwidget.flags.Flags.generatedPreviews, enabled in the
 * release).
 */
public class WidgetManagerHelper {

    private static final String TAG = "WidgetManagerHelper";

    final AppWidgetManager mAppWidgetManager;
    final Context mContext;

    public WidgetManagerHelper(Context context) {
        this(context, AppWidgetManager.getInstance(context));
    }

    public WidgetManagerHelper(Context context, AppWidgetManager appWidgetManager) {
        mContext = context;
        mAppWidgetManager = appWidgetManager;
    }

    /**
     * @see AppWidgetManager#getAppWidgetInfo(int)
     */
    @Nullable
    public LauncherAppWidgetProviderInfo getLauncherAppWidgetInfo(
            int appWidgetId, ComponentName componentName) {

        // TextLauncher: no custom widgets.

        AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        if (info == null) {
            // TextLauncher: Log rather than FileLog.
            Log.w(TAG,
                    "getLauncherAppWidgetInfo: AppWidgetManager returned null AppWidgetInfo for"
                            + " appWidgetId=" + appWidgetId
                            + ", componentName=" + componentName);
            return null;
        }
        return LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
    }

    /**
     * @see AppWidgetManager#getInstalledProvidersForPackage(String, UserHandle)
     */
    public List<AppWidgetProviderInfo> getAllProviders(@Nullable PackageUserKey packageUser) {
        if (packageUser == null) {
            return allWidgetsSteam(mContext).collect(Collectors.toList());
        }

        try {
            return mAppWidgetManager.getInstalledProvidersForPackage(
                    packageUser.mPackageName, packageUser.mUser);
        } catch (IllegalStateException e) {
            // b/277189566: Launcher will load the widget when it gets the user-unlock event.
            // If exception is thrown because of device is locked, it means a race condition occurs
            // that the user got locked again while launcher is processing the event. In this case
            // we should return empty list.
            Log.e(TAG, "getAllProviders: Error getting installed providers for"
                    + " package=" + packageUser.mPackageName, e);
            return Collections.emptyList();
        }
    }

    /**
     * @see AppWidgetManager#bindAppWidgetIdIfAllowed(int, UserHandle, ComponentName, Bundle)
     */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, AppWidgetProviderInfo info,
            Bundle options) {
        return mAppWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId, info.getProfile(), info.provider, options);
    }

    public LauncherAppWidgetProviderInfo findProvider(ComponentName provider, UserHandle user) {
        for (AppWidgetProviderInfo info :
                getAllProviders(new PackageUserKey(provider.getPackageName(), user))) {
            if (info.provider.equals(provider)) {
                return LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
            }
        }
        Log.w(TAG, "findProvider: No App Widget Provider found for component=" + provider
                + " user=" + user);
        return null;
    }

    /**
     * Load RemoteViews preview for this provider if available.
     *
     * @param info           The provider info for the widget you want to preview.
     * @param widgetCategory The widget category for which you want to display previews.
     * @return Returns the widget preview that matches selected category, if available.
     */
    @Nullable
    public RemoteViews loadGeneratedPreview(@NonNull AppWidgetProviderInfo info,
            int widgetCategory) {
        return mAppWidgetManager.getWidgetPreview(info.provider, info.getProfile(), widgetCategory);
    }

    private static Stream<AppWidgetProviderInfo> allWidgetsSteam(Context context) {
        AppWidgetManager awm = context.getSystemService(AppWidgetManager.class);
        // TextLauncher: no custom widgets.
        return UserCache.INSTANCE.get(context)
                .getUserProfiles()
                .stream()
                .flatMap(u -> awm.getInstalledProvidersForProfile(u).stream());
    }
}
