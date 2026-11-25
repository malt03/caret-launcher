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
package com.android.launcher3.uioverrides.touchcontrollers;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SWIPE_DOWN_WORKSPACE_NOTISHADE_OPEN;

import android.annotation.SuppressLint;
import android.app.StatusBarManager;
import android.graphics.PointF;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.TouchController;
import com.google.android.msdl.data.model.MSDLToken;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Handles touch events for the system status bar.
 * <p>
 * This controller intercepts touch events and, after a vertical drag gesture exceeds the system
 * touch slop, forwards them to the System UI. Events occurring before the touch slop are also
 * forwarded in the order we received them, to ensure ACTION_DOWN and any velocity data is passed.
 * <p>
 * The controller only handles events if:
 * - The System UI Proxy is active.
 * - A provided `isEnabledCheck` supplier returns `true`.
 * - There are no floating views disallowing status bar swipes.
 * - For touch events (not trackpad scrolls or mouse input if specific features are enabled),
 *   the initial touch occurs above the navigation bar area.
 * <p>
 * CaretLauncher: SystemUiProxy is only bound by the launcher that is the system's Recents, so the
 * touches cannot be forwarded to the System UI. Once the swipe is detected, the notification
 * shade is opened with StatusBarManager.expandNotificationsPanel (EXPAND_STATUS_BAR) instead, and
 * it opens with its own animation rather than following the finger. As the shade does not move
 * with the finger, a haptic marks the moment it opens (CaretLauncher's own; Launcher3 has none).
 */
public class StatusBarTouchController implements TouchController {

    private static final String TAG = "StatusBarController";

    // CaretLauncher: the launcher stand-in, which is not a BaseActivity.
    private final Launcher mLauncher;
    // CaretLauncher: no SystemUiProxy; see expandNotificationsPanel.
    private final float mTouchSlop;
    private final PointF mDownEvent = new PointF();
    // CaretLauncher: the check gets the ACTION_DOWN event (the home screen scrolls vertically).
    private final Predicate<MotionEvent> mIsEnabledCheck;
    // CaretLauncher: no CachedEventDispatcher; nothing is forwarded.

    /* If {@code false}, this controller should not handle the input {@link MotionEvent}.*/
    private boolean mCanIntercept;

    public StatusBarTouchController(Launcher l, Predicate<MotionEvent> isEnabledCheck) {
        mLauncher = l;
        mTouchSlop = ViewConfiguration.get(l.asContext()).getScaledTouchSlop();
        mIsEnabledCheck = isEnabledCheck;
    }

    @Override
    public String dump() {
        return TAG + " mCanIntercept:" + mCanIntercept
                + " , expandNotificationsPanel available:" + (EXPAND_NOTIFICATIONS_PANEL != null);
    }

    // CaretLauncher: no dispatchTouchEvent; the shade is opened once (expandNotificationsPanel).

    @Override
    public final boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == ACTION_DOWN) {
            mCanIntercept = canInterceptTouch(ev);
            if (!mCanIntercept) {
                return false;
            }
            mDownEvent.set(ev.getX(), ev.getY());
        }
        if (!mCanIntercept) {
            cleanupAfterGesture();
            return false;
        }

        // CaretLauncher: no events are cached to be forwarded.

        if (action == ACTION_MOVE) {
            float dy = ev.getY() - mDownEvent.y;
            float dx = ev.getX() - mDownEvent.x;
            if (dy > mTouchSlop && dy > Math.abs(dx)) {
                // CaretLauncher: in place of forwarding the touches from here on, the shade is
                // opened once.
                expandNotificationsPanel();
                return true;
            }
            if (Math.abs(dx) > mTouchSlop) {
                mCanIntercept = false;
            }
        } else if (action == ACTION_UP || action == ACTION_CANCEL) {
            cleanupAfterGesture();
        }
        return false;
    }

    @Override
    public final boolean onControllerTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        // CaretLauncher: the rest of the gesture is taken and not forwarded.
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            mLauncher.getStatsLogManager().logger()
                    .log(LAUNCHER_SWIPE_DOWN_WORKSPACE_NOTISHADE_OPEN);
            cleanupAfterGesture();
        }
        return true;
    }

    private void cleanupAfterGesture() {
        // CaretLauncher: no CachedEventDispatcher to clear.
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (isTrackpadMultiFingerSwipe(ev)) {
            // Trackpad events are handled separately, see e.g. TrackpadStatusBarInputConsumer.
            return false;
        }
        if (!mIsEnabledCheck.test(ev)) {
            return false;
        }
        if (AbstractFloatingView.getTopOpenViewWithType(mLauncher,
                AbstractFloatingView.TYPE_STATUS_BAR_SWIPE_DOWN_DISALLOW) != null) {
            return false;
        }
        // CaretLauncher: phones only (R.bool.desktop_form_factor is false), so mouse input is not
        // left out (Utilities.shouldEnableMouseInteractionChanges).
        // For NORMAL state, only listen if the event originated above the navbar height
        // CaretLauncher: the drag layer's insets are the device profile's (DeviceProfile.getInsets).
        if (ev.getY() > (mLauncher.getDragLayer().getHeight()
                - mLauncher.getDragLayer().getInsets().bottom)) {
            return false;
        }
        // CaretLauncher: in place of SystemUiProxy.isActive.
        return EXPAND_NOTIFICATIONS_PANEL != null;
    }

    // CaretLauncher: members CaretLauncher adds.

    /**
     * StatusBarManager.expandNotificationsPanel, which is hidden but not restricted: it is
     * {@code @UnsupportedAppUsage} without a maxTargetSdk and is in none of the hiddenapi
     * max-target lists of android-17.0.0_r1 (frameworks/base boot/hiddenapi), so apps may call it.
     * StatusBarManagerService checks EXPAND_STATUS_BAR, a normal permission. Null if the method
     * cannot be found, which leaves the swipe to the home screen.
     */
    @SuppressLint({"DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
    private static final Method EXPAND_NOTIFICATIONS_PANEL = findExpandNotificationsPanel();

    @SuppressLint({"DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
    private static Method findExpandNotificationsPanel() {
        try {
            return StatusBarManager.class.getMethod("expandNotificationsPanel");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "StatusBarManager.expandNotificationsPanel is not available", e);
            return null;
        }
    }

    /** Opens the notification shade with its own animation. */
    private void expandNotificationsPanel() {
        StatusBarManager statusBarManager =
                mLauncher.asContext().getSystemService(StatusBarManager.class);
        if (statusBarManager == null || EXPAND_NOTIFICATIONS_PANEL == null) {
            return;
        }
        try {
            EXPAND_NOTIFICATIONS_PANEL.invoke(statusBarManager);
            performOpenHaptic();
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Unable to open the notification shade", e);
        }
    }

    /**
     * The haptic of the shade opening: the one Launcher3 plays when a swipe gets past its
     * threshold (AllAppsTransitionController.setStateWithAnimation's SWIPE_THRESHOLD_INDICATOR),
     * as the swipe has just got past the touch slop.
     */
    private void performOpenHaptic() {
        if (Flags.msdlFeedback()) {
            MSDLPlayerWrapper.INSTANCE.get(mLauncher.asContext())
                    .playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR);
        } else {
            mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }
}
