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
 * Modified for CaretLauncher: derived from Launcher3's states/RotationHelper.java; changes are
 * marked "CaretLauncher:". See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.states;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.launcher3.util.window.WindowManagerProxy;

/**
 * Utility class to manage launcher rotation
 *
 * CaretLauncher: the activity passes on its configuration changes ({@link #onConfigurationChanged})
 * in place of DisplayController and the device profile listener, and whether the screen is large
 * is LauncherDisplayInfo.isLargeScreen with the display's real bounds.
 */
// CaretLauncher: not a DeviceProfile.OnDeviceProfileChangeListener (see onConfigurationChanged).
public class RotationHelper {

    public static final int REQUEST_NONE = 0;
    public static final int REQUEST_ROTATE = 1;
    public static final int REQUEST_LOCK = 2;

    private boolean mIsFixedLandscape = false;

    // CaretLauncher: any Activity; LauncherActivity is not a BaseActivity.
    @NonNull
    private final Activity mActivity;
    private final Handler mRequestOrientationHandler;

    private boolean mIgnoreAutoRotateSettings;
    private boolean mForceAllowRotationForTesting;

    /**
     * Rotation request made by
     * {@link ContextTracker.SchedulerCallback}.
     * This supersedes any other request.
     */
    private int mStateHandlerRequest = REQUEST_NONE;
    /**
     * Rotation request made by an app transition
     */
    private int mCurrentTransitionRequest = REQUEST_NONE;
    /**
     * Rotation request made by a Launcher State
     */
    private int mCurrentStateRequest = REQUEST_NONE;

    // This is used to defer setting rotation flags until the activity is being created
    private boolean mInitialized;
    private boolean mDestroyed;

    // Initialize mLastActivityFlags to a value not used by SCREEN_ORIENTATION flags
    private int mLastActivityFlags = -999;

    // CaretLauncher: any Activity.
    public RotationHelper(@NonNull Activity activity) {
        mActivity = activity;
        mRequestOrientationHandler =
                new Handler(UI_HELPER_EXECUTOR.getLooper(), this::setOrientationAsync);
    }

    private void setIgnoreAutoRotateSettings(boolean ignoreAutoRotateSettings) {
        if (mDestroyed) return;
        // On large devices we do not handle auto-rotate differently.
        mIgnoreAutoRotateSettings = ignoreAutoRotateSettings;
    }

    /**
     * CaretLauncher: in place of DisplayController's display info changes and
     * onDeviceProfileChanged.
     */
    public void onConfigurationChanged(Configuration config) {
        onIgnoreAutoRotateChanged(isLargeScreen());
    }

    private void onIgnoreAutoRotateChanged(boolean ignoreAutoRotateSettings) {
        if (mDestroyed) return;
        if (mIgnoreAutoRotateSettings != ignoreAutoRotateSettings) {
            setIgnoreAutoRotateSettings(ignoreAutoRotateSettings);
            notifyChange();
        }
    }

    public void setStateHandlerRequest(int request) {
        if (mDestroyed || mStateHandlerRequest == request) return;
        mStateHandlerRequest = request;
        notifyChange();
    }

    public void setCurrentTransitionRequest(int request) {
        if (mDestroyed || mCurrentTransitionRequest == request) return;
        mCurrentTransitionRequest = request;
        notifyChange();
    }

    public void setCurrentStateRequest(int request) {
        if (mDestroyed || mCurrentStateRequest == request) return;
        mCurrentStateRequest = request;
        notifyChange();
    }

    public boolean isFixedLandscape() {
        return mIsFixedLandscape;
    }

    /**
     * If fixedLandscape is true then the Launcher become landscape until set false..
     */
    public void setFixedLandscape(boolean fixedLandscape) {
        mIsFixedLandscape = fixedLandscape;
        notifyChange();
    }

    // Used by tests only.
    public void forceAllowRotationForTesting(boolean allowRotation) {
        if (mDestroyed) return;
        mForceAllowRotationForTesting = allowRotation;
        notifyChange();
    }

    public void initialize() {
        if (mInitialized) return;
        mInitialized = true;
        // CaretLauncher: no DisplayController and no device profile listener; the activity passes
        // on its configuration changes (onConfigurationChanged).
        setIgnoreAutoRotateSettings(isLargeScreen());
        notifyChange();
    }

    public void destroy() {
        if (mDestroyed) return;
        mDestroyed = true;
        // CaretLauncher: no listeners to remove.
    }

    private void notifyChange() {
        if (!mInitialized || mDestroyed) {
            return;
        }

        final int activityFlags;
        if (mIsFixedLandscape) {
            activityFlags = SCREEN_ORIENTATION_USER_LANDSCAPE;
        } else if (mStateHandlerRequest != REQUEST_NONE) {
            activityFlags = mStateHandlerRequest == REQUEST_LOCK ?
                    SCREEN_ORIENTATION_LOCKED : SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (mCurrentTransitionRequest != REQUEST_NONE) {
            activityFlags = mCurrentTransitionRequest == REQUEST_LOCK ?
                    SCREEN_ORIENTATION_LOCKED : SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (mCurrentStateRequest == REQUEST_LOCK) {
            activityFlags = SCREEN_ORIENTATION_LOCKED;
        } else if (mIgnoreAutoRotateSettings
                || mCurrentStateRequest == REQUEST_ROTATE
                || mForceAllowRotationForTesting
        ) {
            activityFlags = SCREEN_ORIENTATION_UNSPECIFIED;
        } else {
            // If auto rotation is off, allow rotation on the activity, in case the user is using
            // forced rotation.
            activityFlags = SCREEN_ORIENTATION_NOSENSOR;
        }
        if (activityFlags != mLastActivityFlags) {
            mLastActivityFlags = activityFlags;
            mRequestOrientationHandler.sendEmptyMessage(activityFlags);
        }
        // CaretLauncher: no debug log for b/475447538.
    }

    @WorkerThread
    private boolean setOrientationAsync(Message msg) {
        if (mDestroyed) return true;
        mActivity.setRequestedOrientation(msg.what);
        return true;
    }

    /**
     * @return how many factors {@param newRotation} is rotated 90 degrees clockwise.
     * E.g. 1->Rotated by 90 degrees clockwise, 2->Rotated 180 clockwise...
     * A value of 0 means no rotation has been applied
     */
    public static int deltaRotation(int oldRotation, int newRotation) {
        int delta = newRotation - oldRotation;
        if (delta < 0) delta += 4;
        return delta;
    }

    @Override
    public String toString() {
        return String.format("[mStateHandlerRequest=%d, mCurrentStateRequest=%d, "
                        + "mLastActivityFlags=%d, mIgnoreAutoRotateSettings=%b, "
                        + "mForceAllowRotationForTesting=%b, mDestroyed=%b, mIsFixedLandscape=%b]",
                mStateHandlerRequest, mCurrentStateRequest, mLastActivityFlags,
                mIgnoreAutoRotateSettings, mForceAllowRotationForTesting,
                mDestroyed, mIsFixedLandscape);
    }

    // CaretLauncher: members CaretLauncher adds.

    /**
     * CaretLauncher: LauncherDisplayInfo.isLargeScreen(realBounds) (whether the device is a desktop
     * is not known to an app).
     */
    private boolean isLargeScreen() {
        Rect bounds = mActivity.getSystemService(WindowManager.class)
                .getMaximumWindowMetrics().getBounds();
        float smallestSizeDp = Math.min(bounds.width(), bounds.height())
                / mActivity.getResources().getDisplayMetrics().density;
        return smallestSizeDp >= WindowManagerProxy.MIN_TABLET_WIDTH
                || mActivity.getDisplay().getDisplayId() != Display.DEFAULT_DISPLAY;
    }
}
