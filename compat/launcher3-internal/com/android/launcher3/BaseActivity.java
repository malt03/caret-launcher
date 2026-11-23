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

package com.android.launcher3;

import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleRegistry;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.LifecycleHelper;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;

/**
 * Launcher BaseActivity
 *
 * <p>Stand-in for Launcher3's BaseActivity (android-17.0.0_r1), made from BaseActivity.java by
 * deleting what TextLauncher does not use; changes are marked with "TextLauncher:". TextLauncher's
 * home screen is not a BaseActivity; the widget picker is.
 */
// TextLauncher: ActivityContext is not a SavedStateRegistryOwner here, so this activity is one.
public abstract class BaseActivity extends Activity
        implements ActivityContext, SavedStateRegistryOwner {

    private final SavedStateRegistryController mSavedStateRegistryController =
            SavedStateRegistryController.create(this);
    private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

    protected volatile DeviceProfile mDeviceProfile;
    protected SystemUiController mSystemUiController;
    private StatsLogManager mStatsLogManager;

    public static final int ACTIVITY_STATE_STARTED = 1 << 0;
    public static final int ACTIVITY_STATE_RESUMED = 1 << 1;

    public static final int ACTIVITY_STATE_IS_TOP_RESUMED = 1 << 7;

    private int mActivityFlags;

    // List of persistent callbacks (i.e. the list is not cleared when callbacks are first run) to
    // be run when the top resumed activity state changes.
    private final ArrayList<Runnable> mTopResumedChangedCallbacks = new ArrayList<Runnable>();

    public BaseActivity() {
        mSavedStateRegistryController.performAttach();
        registerActivityLifecycleCallbacks(
                new LifecycleHelper(this, mSavedStateRegistryController, mLifecycleRegistry));
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    /**
     * Returns {@link StatsLogManager} for user event logging.
     */
    public StatsLogManager getStatsLogManager() {
        if (mStatsLogManager == null) {
            mStatsLogManager = StatsLogManager.newInstance(this);
        }
        return mStatsLogManager;
    }

    public SystemUiController getSystemUiController() {
        if (mSystemUiController == null) {
            mSystemUiController = new SystemUiController(getWindow().getDecorView());
        }
        return mSystemUiController;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerBackDispatcher();
    }

    @Override
    protected void onStart() {
        addActivityFlags(ACTIVITY_STATE_STARTED);
        super.onStart();
    }

    @Override
    protected void onResume() {
        addActivityFlags(ACTIVITY_STATE_RESUMED); // TextLauncher: setResumed without user activity.
        super.onResume();
    }

    @Override
    protected void onStop() {
        removeActivityFlags(ACTIVITY_STATE_STARTED); // TextLauncher: no ACTIVITY_STATE_USER_ACTIVE.
        super.onStop();


        // Reset the overridden sysui flags used for the task-swipe launch animation, this is a
        // catch all for if we do not get resumed (and therefore not paused below)
        getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
    }

    @Override
    protected void onPause() {
        removeActivityFlags(ACTIVITY_STATE_RESUMED); // TextLauncher: setPaused without deferred.
        super.onPause();

        // Reset the overridden sysui flags used for the task-swipe launch animation, we do this
        // here instead of at the end of the animation because the start of the new activity does
        // not happen immediately, which would cause us to reset to launcher's sysui flags and then
        // back to the new app (causing a flash)
        getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
    }

    @Override
    public void onTopResumedActivityChanged(boolean isTopResumed) {
        super.onTopResumedActivityChanged(isTopResumed);

        if (isTopResumed) {
            addActivityFlags(ACTIVITY_STATE_IS_TOP_RESUMED);
        } else {
            removeActivityFlags(ACTIVITY_STATE_IS_TOP_RESUMED);
        }

        for (Runnable callback : mTopResumedChangedCallbacks) {
            callback.run();
        }
    }

    protected void registerBackDispatcher() {
        if (Utilities.ATLEAST_T) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        onBackPressed();
                    });
        }
    }

    public boolean isStarted() {
        return (mActivityFlags & ACTIVITY_STATE_STARTED) != 0;
    }

    /**
     * isResumed in already defined as a hidden final method in Activity.java
     */
    public boolean hasBeenResumed() {
        return (mActivityFlags & ACTIVITY_STATE_RESUMED) != 0;
    }

    /**
     * @return true if Launcher is the current top resumed activity.
     */
    public boolean isTopResumedActivity() {
        return (mActivityFlags & ACTIVITY_STATE_IS_TOP_RESUMED) != 0;
    }

    protected void addActivityFlags(int toAdd) {
        mActivityFlags |= toAdd;
    }

    protected void removeActivityFlags(int toRemove) {
        mActivityFlags &= ~toRemove;
    }

    /** Adds a callback to run when top resumed activity state changes. */
    public void addTopResumedChangedCallback(Runnable callback) {
        mTopResumedChangedCallbacks.add(callback);
    }

    /** Removes a callback previously added using [addTopResumedChangedCallback]. */
    public void removeTopResumedChangedCallback(Runnable callback) {
        mTopResumedChangedCallbacks.remove(callback);
    }

    @NonNull
    @Override
    public SavedStateRegistry getSavedStateRegistry() {
        return mSavedStateRegistryController.getSavedStateRegistry();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    public static <T extends BaseActivity> T fromContext(Context context) {
        if (context instanceof BaseActivity) {
            return (T) context;
        } else if (context instanceof ContextWrapper cw) {
            return fromContext(cw.getBaseContext());
        } else {
            throw new IllegalArgumentException("Cannot find BaseActivity in parent tree");
        }
    }
}
