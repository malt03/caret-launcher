/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3.model.data;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.util.ContentWriter;

/**
 * Represents a widget (either instantiated or about to be) in the Launcher.
 *
 * <p>Stand-in for Launcher3's LauncherAppWidgetInfo (android-17.0.0_r1). CaretLauncher: the home
 * screen's widget item (the app's WidgetItemInfo) extends it; only its id, provider, options and
 * the container it was added from, which are saved, are ported (no restore, install progress,
 * pending widgets, custom widgets or widget features).
 */
public class LauncherAppWidgetInfo extends ItemInfo {

    public static final int RESTORE_COMPLETED = 0;

    public static final int NO_ID = -1;

    /**
     * Flags for recording all the features that a widget has enabled.
     * @see widgetFeatures
     */
    public static final int FEATURE_RECONFIGURABLE = 1;
    public static final int FEATURE_OPTIONAL_CONFIGURATION = 1 << 1;
    public static final int FEATURE_PREVIEW_LAYOUT = 1 << 2;
    public static final int FEATURE_TARGET_CELL_SIZE = 1 << 3;
    public static final int FEATURE_MIN_SIZE = 1 << 4;
    public static final int FEATURE_MAX_SIZE = 1 << 5;

    /**
     * Identifier for this widget when talking with
     * {@link android.appwidget.AppWidgetManager} for updates.
     */
    public int appWidgetId = NO_ID;

    public ComponentName providerName;

    /**
     * Indicates the restore status of the widget.
     */
    public int restoreStatus;

    /**
     * Optional extras sent during widget bind.
     */
    public Intent bindOptions;

    /**
     * Widget options
     */
    public int options;

    /**
     * The container from which this widget was added (e.g. widgets tray, pin widget, search)
     */
    public int sourceContainer = LauncherSettings.Favorites.CONTAINER_UNKNOWN;

    /**
     * Contains a binary representation indicating which widget features are enabled. This value is
     * -1 if widget features could not be identified.
     */
    // CaretLauncher: set as the widget's view is made (the home screen's ItemInflater), where
    // Launcher3 sets it as the item loads (WorkspaceItemProcessor); -1 until then.
    private int widgetFeatures = -1;

    public LauncherAppWidgetInfo(int appWidgetId, ComponentName providerName) {
        this.appWidgetId = appWidgetId;
        this.providerName = providerName;

        // CaretLauncher: no custom widgets.
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;

        // Since the widget isn't instantiated yet, we don't know these values. Set them to -1
        // to indicate that they should be calculated based on the layout and minWidth/minHeight
        spanX = -1;
        spanY = -1;
        // We only support app widgets on current user.
        user = Process.myUserHandle();
        restoreStatus = RESTORE_COMPLETED;
    }

    public LauncherAppWidgetInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
    }

    /**
     * @return true if the widget is reconfigurable.
     */
    public boolean isReconfigurable() {
        return widgetFeatures != -1 && (widgetFeatures & FEATURE_RECONFIGURABLE) != 0;
    }

    @SuppressWarnings("NewApi")
    public void updateWidgetFeatures(LauncherAppWidgetProviderInfo providerInfo) {
        widgetFeatures = 0;
        if (providerInfo.isReconfigurable()) {
            widgetFeatures |= FEATURE_RECONFIGURABLE;
        }
        if (providerInfo.isConfigurationOptional()) {
            widgetFeatures |= FEATURE_OPTIONAL_CONFIGURATION;
        }
        if (providerInfo.previewLayout != android.content.res.Resources.ID_NULL) {
            widgetFeatures |= FEATURE_PREVIEW_LAYOUT;
        }
        if (providerInfo.targetCellWidth > 0 || providerInfo.targetCellHeight > 0) {
            widgetFeatures |= FEATURE_TARGET_CELL_SIZE;
        }
        if (providerInfo.minResizeWidth > 0 || providerInfo.minResizeHeight > 0) {
            widgetFeatures |= FEATURE_MIN_SIZE;
        }
        if (providerInfo.maxResizeWidth > 0 || providerInfo.maxResizeHeight > 0) {
            widgetFeatures |= FEATURE_MAX_SIZE;
        }
    }

    @Nullable
    @Override
    public ComponentName getTargetComponent() {
        return providerName;
    }

    @Override
    public void onAddToDatabase(@NonNull ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId)
                .put(LauncherSettings.Favorites.APPWIDGET_PROVIDER, providerName.flattenToString())
                .put(LauncherSettings.Favorites.RESTORED, restoreStatus)
                .put(LauncherSettings.Favorites.OPTIONS, options)
                .put(LauncherSettings.Favorites.INTENT, bindOptions)
                .put(LauncherSettings.Favorites.APPWIDGET_SOURCE, sourceContainer);
    }

    @Override
    protected String dumpProperties() {
        return super.dumpProperties()
                + " providerName=" + providerName
                + " appWidgetId=" + appWidgetId;
    }

    public final boolean isWidgetIdAllocated() {
        // CaretLauncher: no restore, so the id is always allocated.
        return true;
    }
}
