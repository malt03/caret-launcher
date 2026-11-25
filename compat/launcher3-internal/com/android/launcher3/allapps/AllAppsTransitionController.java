/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.util.FloatProperty;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.FloatRange;

import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.ScrollableLayoutManager;

import com.google.android.msdl.data.model.MSDLToken;

/**
 * Handles AllApps view transition.
 * 1) Slides all apps view using direct manipulation
 * 2) When finger is released, animate to either top or bottom accordingly.
 * <p/>
 * Algorithm:
 * If release velocity > THRES1, snap according to the direction of movement.
 * If release velocity < THRES1, snap according to either top or bottom depending on whether it's
 * closer to top or closer to the page indicator.
 */
/**
 * Stand-in for Launcher3's AllAppsTransitionController (android-17.0.0_r1), made from
 * AllAppsTransitionController.java by deleting the scrim, the alphas, the device profile changes,
 * the system UI, the header scale and the all apps search's back animation; changes are marked
 * with "CaretLauncher:". The progress places CaretLauncher's search field and the all apps sheet
 * right under it ({@link Host}): the field goes from its closed position, above the keyboard, to
 * the top of the screen, and the sheet comes out from behind the keyboard (docs/spec.md 6.4).
 */
public class AllAppsTransitionController
        implements StateHandler<LauncherState> {
    private static final String TAG = "AllAppsTransitionController";
    // This constant should match the second derivative of the animator interpolator.
    public static final float INTERP_COEFF = 1.7f;
    public static final int REVERT_SWIPE_ALL_APPS_TO_HOME_ANIMATION_DURATION_MS = 200;

    // CaretLauncher: no system UI (NAV_BAR_COLOR_FORCE_UPDATE_THRESHOLD).

    public static final FloatProperty<AllAppsTransitionController> ALL_APPS_PROGRESS =
            new FloatProperty<>("allAppsProgress") {
                @Override
                public Float get(AllAppsTransitionController controller) {
                    return controller.mProgress;
                }

                @Override
                public void setValue(AllAppsTransitionController controller, float progress) {
                    controller.setProgress(progress);
                }
            };

    // CaretLauncher: no apps view properties (INDEX_APPS_VIEW_PROGRESS, APPS_VIEW_INDEX_COUNT,
    // mAppsView); the host places the sheet.

    private final Launcher mLauncher;
    private final AnimatedFloat mAllAppScale = new AnimatedFloat(this::onScaleProgressChanged);
    // CaretLauncher: no system UI (mNavScrimFlag), all apps search's back animation
    // (mAllAppsSearchBackAnimationListener) or vertical bar layout (mIsVerticalLayout).

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    // CaretLauncher: the shift range is the field's (getShiftRange).
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent

    // CaretLauncher: no scrim (mScrimView), apps view properties (mAppsViewAlpha,
    // mAppsViewTranslationY) or clipping during the scale (mHasScaleEffect).

    private final MSDLPlayerWrapper mMSDLPlayerWrapper;
    // CaretLauncher: no header scale (mShouldScaleHeader).

    // CaretLauncher: with the host that places the field and the sheet.
    public AllAppsTransitionController(Launcher l, Host host) {
        mLauncher = l;
        mHost = host;
        mProgress = 1f;
        // CaretLauncher: no device profile changes (the field's positions are the host's).
        mAllAppScale.value = 1;
        // CaretLauncher: the Launcher stand-in is not a Context.
        mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(
                mLauncher.asContext().getApplicationContext());
    }

    public float getShiftRange() {
        // CaretLauncher: from the field's open position to its closed one, which follows the
        // keyboard.
        return mHost.getClosedBarTop() - mHost.getOpenBarTop();
    }

    // CaretLauncher: no device profile changes (onDeviceProfileChanged).

    /**
     * Note this method should not be called outside this class. This is public because it is used
     * in xml-based animations which also handle updating the appropriate UI.
     *
     * @param progress value between 0 and 1, 0 shows all apps and 1 shows workspace
     * @see #setState(LauncherState)
     * @see #setStateWithAnimation(LauncherState, StateAnimationConfig, PendingAnimation)
     */
    public void setProgress(float progress) {
        if (Float.compare(mProgress, progress) == 0) {
            return;
        }
        mProgress = progress;
        // CaretLauncher: the host places the field and the sheet under it (no background app,
        // onAllAppsTransition or system UI).
        mHost.onAllAppsProgressChanged(mProgress);
    }

    public float getProgress() {
        return mProgress;
    }

    // CaretLauncher: no apps view properties (getAppsViewProgressTranslationY,
    // getAppsViewProgressAlpha).

    /**
     * Sets the vertical transition progress to {@param state} and updates all the dependent UI
     * accordingly.
     */
    @Override
    public void setState(LauncherState state) {
        setProgress(state.getVerticalProgress(mLauncher));
        // CaretLauncher: no alphas (setAlphas).
    }

    // CaretLauncher: no header scale (onBackStarted).

    @Override
    public void onBackProgressed(
            LauncherState toState, @FloatRange(from = 0.0, to = 1.0) float backProgress) {
        if (!mLauncher.isInState(ALL_APPS) || !NORMAL.equals(toState)) {
            return;
        }

        float scaleProgress = ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE
                + (1 - ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE)
                * (1 - backProgress);

        mAllAppScale.updateValue(scaleProgress);
    }

    private void onScaleProgressChanged() {
        final float scaleProgress = mAllAppScale.value;
        // CaretLauncher: the host's sheet; no header scale or clipping during the scale.
        SCALE_PROPERTY.set(mHost.getAppsView(), scaleProgress);
    }

    /**
     * @return AnimatedFloat for all apps scale. This will scale the allapps content by default.
     * @see #setShouldScaleHeader(boolean)
     */
    public AnimatedFloat getAllAppScale() {
        return mAllAppScale;
    }

    // CaretLauncher: no header scale (setShouldScaleHeader) or all apps search's back animation
    // (setAllAppsSearchBackAnimationListener, animateAllAppsToNoScale).

    /**
     * Creates an animation which updates the vertical transition progress and updates all the
     * dependent UI using various animation events
     *
     * This method also dictates where along the progress the haptics should be played. As the user
     * scrolls up from workspace or down from AllApps, a drag haptic is being played until the
     * commit point where it plays a commit haptic. Where we play the haptics differs when going
     * from workspace -> allApps and vice versa.
     */
    @Override
    public void setStateWithAnimation(LauncherState toState,
            StateAnimationConfig config, PendingAnimation builder) {
        if (mLauncher.isInState(ALL_APPS) && !ALL_APPS.equals(toState)) {
            // Reset scale after switching states.
            builder.addEndListener(success -> mAllAppScale.updateValue(1f));
        }

        float targetProgress = toState.getVerticalProgress(mLauncher);
        if (Float.compare(mProgress, targetProgress) == 0) {
            // CaretLauncher: no alphas (setAlphas).
            // Fail fast
            return;
        }

        // need to decide depending on the release velocity
        Interpolator verticalProgressInterpolator = config.getInterpolator(ANIM_VERTICAL_PROGRESS,
                config.isUserControlled() ? LINEAR : DECELERATE_1_7);
        Animator anim = createSpringAnimation(mProgress, targetProgress);
        // CaretLauncher: no cancel listener that sets the target progress; a swipe that catches the
        // animation takes the list from where it is.
        anim.setInterpolator(verticalProgressInterpolator);
        builder.add(anim);

        // CaretLauncher: no alphas (setAlphas).
        // This controls both haptics for tapping on QSB and going to all apps.
        // CaretLauncher: a swipe plays its haptic when its hold gives way
        // (performSwipeThresholdHaptic).
        if (ALL_APPS.equals(toState) && mLauncher.isInState(NORMAL)
                && !config.isUserControlled()) {
            if (Flags.msdlFeedback()) {
                if (config.isUserControlled()) {
                    mMSDLPlayerWrapper.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR);
                } else {
                    mMSDLPlayerWrapper.playToken(MSDLToken.TAP_HIGH_EMPHASIS);
                }
            } else {
                // CaretLauncher: the host's sheet.
                mHost.getAppsView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        }
    }

    public Animator createSpringAnimation(float... progressValues) {
        return ObjectAnimator.ofFloat(this, ALL_APPS_PROGRESS, progressValues);
    }

    // CaretLauncher: no alphas (setAlphas), and the host has the views (setupViews,
    // isAllAppsRenderedByLauncher).

    // CaretLauncher: the shift range is the field's (setShiftRange).

    // ---- CaretLauncher ----

    /** The app's views the transition moves. */
    public interface Host {
        /** Where the search field's top is with the list closed; it follows the keyboard. */
        float getClosedBarTop();

        /** Where the search field's top is with the list open. */
        float getOpenBarTop();

        /**
         * Places the search field and the sheet right under it for {@code progress} (0 shows all
         * apps and 1 the workspace).
         */
        void onAllAppsProgressChanged(float progress);

        /** The all apps sheet. */
        View getAppsView();
    }

    private final Host mHost;

    /** Whether the all apps list is shown, open or on its way (docs/spec.md 6.4). */
    public boolean isAllAppsShown() {
        return mProgress < 1f;
    }

    /** Where the search field's top is for the current progress. */
    public float getBarTop() {
        return mHost.getOpenBarTop() + getShiftRange() * mProgress;
    }

    /** The progress that puts the search field's top at {@code barTop}. */
    public float getVerticalProgressForBarTop(float barTop) {
        float shiftRange = getShiftRange();
        if (shiftRange <= 0) {
            return mProgress;
        }
        return Math.max(0f, Math.min(1f, (barTop - mHost.getOpenBarTop()) / shiftRange));
    }

    /**
     * The haptic of a swipe whose hold gives way (docs/spec.md 6.2): setStateWithAnimation's for a
     * user controlled animation, which a swipe that closes the list plays too.
     */
    public void performSwipeThresholdHaptic() {
        if (Flags.msdlFeedback()) {
            mMSDLPlayerWrapper.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR);
        } else {
            mHost.getAppsView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }
}
