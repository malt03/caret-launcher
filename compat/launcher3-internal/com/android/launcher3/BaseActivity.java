/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
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
 * Stand-in for Launcher3's BaseActivity (android-17.0.0_r1): an Activity that is its own lifecycle
 * and saved state owner (for Compose), with the device profile, the system UI controller and the
 * top resumed callbacks. TextLauncher's home screen is not a BaseActivity; the widget picker is.
 */
public abstract class BaseActivity extends Activity
        implements ActivityContext, LifecycleOwner, SavedStateRegistryOwner {

    public static final int ACTIVITY_STATE_STARTED = 1 << 0;
    public static final int ACTIVITY_STATE_RESUMED = 1 << 1;
    public static final int ACTIVITY_STATE_IS_TOP_RESUMED = 1 << 6;

    private final SavedStateRegistryController mSavedStateRegistryController =
            SavedStateRegistryController.create(this);
    private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

    protected volatile DeviceProfile mDeviceProfile;
    protected SystemUiController mSystemUiController;
    private StatsLogManager mStatsLogManager;
    private int mActivityFlags;

    // Callbacks to be run when the top resumed activity state changes.
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
    public Context asContext() {
        return this;
    }

    @Override
    public void hideKeyboard() {
    }

    @Override
    protected void onStart() {
        addActivityFlags(ACTIVITY_STATE_STARTED);
        super.onStart();
    }

    @Override
    protected void onResume() {
        addActivityFlags(ACTIVITY_STATE_RESUMED);
        super.onResume();
    }

    @Override
    protected void onPause() {
        removeActivityFlags(ACTIVITY_STATE_RESUMED);
        super.onPause();
    }

    @Override
    protected void onStop() {
        removeActivityFlags(ACTIVITY_STATE_STARTED);
        super.onStop();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerBackDispatcher();
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
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
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

    /** Adds a callback to be run when the top resumed activity state changes. */
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
