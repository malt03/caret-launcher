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

package com.android.launcher3;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.ContextTracker;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

/**
 * Stand-in for Launcher3's Launcher (android-17.0.0_r1). As in Launcher3, the launcher is the
 * ActivityContext of the home screen, so copied code can use {@link #getLauncher(Context)} and
 * {@code instanceof Launcher}. TextLauncher's home screen extends this; the launcher is not an
 * Activity here, and only the members copied code uses are declared.
 */
public abstract class Launcher implements ActivityContext {
    /**
     * TextLauncher: a ContextTracker rather than ContextTracker.ActivityTracker, since the launcher
     * is not a BaseActivity; the home is started as {@link #isStarted()} says. The home screen
     * calls handleCreate, handleNewIntent and onContextDestroyed where Launcher3's Launcher does.
     */
    public static final ContextTracker<Launcher> ACTIVITY_TRACKER = new LauncherTracker();

    /** TextLauncher: ContextTracker.ActivityTracker for a launcher that is not an activity. */
    private static final class LauncherTracker extends ContextTracker<Launcher> {
        @Override
        public boolean isHomeStarted(Launcher launcher) {
            return launcher.isStarted();
        }
    }

    private final DragLayer mDragLayer;
    private final Workspace<?> mWorkspace;
    private final Hotseat mHotseat;
    private final ScrimView mScrimView;
    private final StatsLogManager mStatsLogManager = new StatsLogManager();

    public Launcher(DragLayer dragLayer, Workspace<?> workspace, Hotseat hotseat,
            ScrimView scrimView) {
        mDragLayer = dragLayer;
        mWorkspace = workspace;
        mHotseat = hotseat;
        mScrimView = scrimView;
    }

    public static Launcher getLauncher(Context context) {
        return (Launcher) ActivityContext.lookupContext(context);
    }

    @Override
    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    public Workspace<?> getWorkspace() {
        return mWorkspace;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public ScrimView getScrimView() {
        return mScrimView;
    }

    /**
     * Starts the item's activity: an app or an app shortcut.
     * TextLauncher: the home screen starts it (Launcher.startActivitySafely and
     * ActivityContext.startActivitySafely).
     */
    public abstract RunnableList startActivitySafely(View v, Intent intent, ItemInfo item);

    /** TextLauncher: the app adds items to its home screen (LauncherAccessibilityDelegate). */
    public abstract LauncherAccessibilityDelegate getAccessibilityDelegate();

    /** BaseActivity.isStarted: whether the home activity is started. */
    public abstract boolean isStarted();

    /** TextLauncher: no logging; see {@link StatsLogManager}. */
    public StatsLogManager getStatsLogManager() {
        return mStatsLogManager;
    }

    /** TextLauncher: the app's home model (LauncherModel). */
    public abstract LauncherModel getModel();

    /**
     * TextLauncher: WidgetManagerHelper.getLauncherAppWidgetInfo, with the spans the widget takes
     * on the home grid (LauncherAppWidgetProviderInfo.initSpans), for the widget resize frame.
     */
    @Nullable
    public abstract LauncherAppWidgetProviderInfo getLauncherAppWidgetInfo(
            @Nullable AppWidgetProviderInfo info);

    /** TextLauncher: WidgetSizeHandler.updateSizeRanges, which tells the widget its size. */
    public abstract void updateWidgetSizeRanges(LauncherAppWidgetHostView widgetView, int spanX,
            int spanY);

    /**
     * TextLauncher: the database writes of CellLayout.commitTempPlacement. The home screen saves
     * its items' cells and spans from their views.
     */
    public abstract void saveItemPlacement();

    /**
     * TextLauncher: a widget's resize frame opened or closed. While it is open the home screen is
     * in edit mode (docs/spec.md 5.4).
     */
    public void onWidgetResizeFrameOpenChanged(boolean open) {}

    /** No wallpaper zoom: it needs a system permission. */
    public DepthController getDepthController() {
        return null;
    }

    /** Stand-in for statehandlers.DepthController. */
    public static class DepthController {
        public MultiPropertyFactory<?>.MultiProperty getFolderZoom() {
            return null;
        }
    }
}
