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
package com.android.launcher3.touch;

import static com.android.app.animation.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.newCancelListener;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;

import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.view.MotionEvent;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.FlingBlockCheck;
import com.android.launcher3.util.TouchController;

/**
 * TouchController for handling state changes
 */
/**
 * Stand-in for Launcher3's AbstractStateChangeTouchController (android-17.0.0_r1), made from
 * AbstractStateChangeTouchController.java by deleting the logs, the trackpad, the mouse, the
 * desktop and the all apps overscroll; changes are marked with "CaretLauncher:".
 *
 * CaretLauncher: the search field's closed position follows the keyboard, which goes down as the
 * all apps list opens, so the progress is worked out from where the finger has taken the field
 * rather than with a fixed progress multiplier. The swipe first holds back until
 * {@link #HOLD_THRESHOLD_DP} (OverScroll.dampedScroll), then with the haptic the field catches up
 * with the finger and follows it (docs/spec.md 6.2); let go before that, it goes back. The state
 * changes (the animation's dispatchOnStart) when the list opening gets past the hold, or when the
 * finger lifts toward the other state, rather than when the drag starts.
 */
public abstract class AbstractStateChangeTouchController
        implements TouchController, SingleAxisSwipeDetector.Listener {

    protected final Launcher mLauncher;
    protected final SingleAxisSwipeDetector mDetector;
    protected final SingleAxisSwipeDetector.Direction mSwipeDirection;

    protected final AnimatorListener mClearStateOnCancelListener =
            newCancelListener(this::clearState, /* isSingleUse = */ false);
    private final FlingBlockCheck mFlingBlockCheck = new FlingBlockCheck();

    protected int mStartContainerType;

    protected LauncherState mStartState;
    protected LauncherState mFromState;
    protected LauncherState mToState;
    protected AnimatorPlaybackController mCurrentAnimation;
    protected boolean mGoingBetweenStates = true;
    // Ratio of transition process [0, 1] to drag displacement (px)
    protected float mProgressMultiplier;
    // CaretLauncher: no trackpad (mIsTrackpadReverseScroll).

    private boolean mNoIntercept;
    // CaretLauncher: no logs (mIsLogContainerSet).
    private float mStartProgress;
    private float mDisplacementShift;
    private boolean mCanBlockFling;
    // CaretLauncher: no all apps overscroll (mAllAppsOvershootStarted).

    public AbstractStateChangeTouchController(Launcher l, SingleAxisSwipeDetector.Direction dir) {
        mLauncher = l;
        // CaretLauncher: the Launcher stand-in is not a Context.
        mDetector = new SingleAxisSwipeDetector(l.asContext(), this, dir);
        mSwipeDirection = dir;
        mHoldThreshold = HOLD_THRESHOLD_DP
                * l.asContext().getResources().getDisplayMetrics().density;
    }

    protected abstract boolean canInterceptTouch(MotionEvent ev);

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                return false;
            }

            // CaretLauncher: no trackpad (mIsTrackpadReverseScroll).

            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            final int directionsToDetectScroll;
            boolean ignoreSlopWhenSettling = false;

            if (mCurrentAnimation != null) {
                directionsToDetectScroll = SingleAxisSwipeDetector.DIRECTION_BOTH;
                ignoreSlopWhenSettling = true;
            } else {
                directionsToDetectScroll = getSwipeDirection();
                // CaretLauncher: no mouse or desktop.
                if (directionsToDetectScroll == 0) {
                    mNoIntercept = true;
                    return false;
                }
            }
            mDetector.setDetectableScrollConditions(
                    directionsToDetectScroll, ignoreSlopWhenSettling);
        }

        if (mNoIntercept) {
            return false;
        }
        // CaretLauncher: a menu or folder opened since the touch began (a long press), or a drag
        // started, gets the touches first, as the drag layer gives them to its controller.
        if (!mDetector.isDraggingOrSettling() && !canKeepIntercepting()) {
            mNoIntercept = true;
            return false;
        }

        onControllerTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    private int getSwipeDirection() {
        LauncherState fromState = mLauncher.getStateManager().getState();
        int swipeDirection = 0;
        if (getTargetState(fromState, true /* isDragTowardPositive */) != fromState) {
            swipeDirection |= SingleAxisSwipeDetector.DIRECTION_POSITIVE;
        }
        if (getTargetState(fromState, false /* isDragTowardPositive */) != fromState) {
            swipeDirection |= SingleAxisSwipeDetector.DIRECTION_NEGATIVE;
        }
        return swipeDirection;
    }

    @Override
    public final boolean onControllerTouchEvent(MotionEvent ev) {
        return mDetector.onTouchEvent(ev);
    }

    protected float getShiftRange() {
        // CaretLauncher: the subclass gives the all apps transition.
        return getAllAppsController().getShiftRange();
    }

    /**
     * Returns the state to go to from fromState given the drag direction. If there is no state in
     * that direction, returns fromState.
     */
    protected abstract LauncherState getTargetState(LauncherState fromState,
            boolean isDragTowardPositive);

    protected abstract float initCurrentAnimation();

    private boolean reinitCurrentAnimation(boolean reachedToState, boolean isDragTowardPositive) {
        LauncherState newFromState = mFromState == null ? mLauncher.getStateManager().getState()
                : reachedToState ? mToState : mFromState;
        LauncherState newToState = getTargetState(newFromState, isDragTowardPositive);

        onReinitToState(newToState);

        if (newFromState == mFromState && newToState == mToState || (newFromState == newToState)) {
            return false;
        }

        mFromState = newFromState;
        mToState = newToState;

        mStartProgress = 0;
        if (mCurrentAnimation != null) {
            mCurrentAnimation.getTarget().removeListener(mClearStateOnCancelListener);
        }
        mProgressMultiplier = initCurrentAnimation();
        // CaretLauncher: the state changes when the hold gives way, or when the finger lifts.
        mStateChangePending = true;
        mStartBarTop = getAllAppsController().getBarTop();
        return true;
    }

    protected void onReinitToState(LauncherState newToState) {
    }

    protected void onReachedFinalState(LauncherState newToState) {
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        mStartState = mLauncher.getStateManager().getState();
        // CaretLauncher: no logs (mIsLogContainerSet).

        if (mCurrentAnimation == null) {
            mFromState = mStartState;
            mToState = null;
            cancelAnimationControllers();
            // CaretLauncher: a state animation this drag catches gives its end to the drag's.
            mMustSettle = mLauncher.getStateManager().isInTransition();
            // CaretLauncher: caught in the middle of an animation, the field follows the finger at
            // once.
            mHoldGaveWay = isAllAppsBetweenStates();
            reinitCurrentAnimation(false, mDetector.wasInitialTouchPositive());
            mDisplacementShift = 0;
        } else {
            mCurrentAnimation.pause();
            mStartProgress = mCurrentAnimation.getProgressFraction();
            // CaretLauncher: the field goes on from where the animation took it.
            mHoldGaveWay = true;
            mStartBarTop = getAllAppsController().getBarTop();
        }
        // CaretLauncher: the catch-up starts again.
        cancelCatchUp();
        mHoldLag = 0;
        mCanBlockFling = mFromState == NORMAL;
        mFlingBlockCheck.unblockFling();
    }

    @Override
    public boolean onDrag(float displacement) {
        // CaretLauncher: the progress from where the finger has taken the field, after the hold
        // (mProgressMultiplier and mStartProgress stand for a field whose positions do not move).
        mLastDisplacement = displacement - mDisplacementShift;
        float progress = getProgressForDisplacement(mLastDisplacement);
        updateProgress(progress);
        boolean isDragTowardPositive = mSwipeDirection.isPositive(
                displacement - mDisplacementShift);
        if (progress <= 0) {
            if (reinitCurrentAnimation(false, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                if (mCanBlockFling) {
                    mFlingBlockCheck.blockFling();
                }
            }
            // CaretLauncher: no all apps overscroll (onPull).
        } else if (progress >= 1) {
            if (reinitCurrentAnimation(true, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                if (mCanBlockFling) {
                    mFlingBlockCheck.blockFling();
                }
            }
            // CaretLauncher: no all apps overscroll (onPull).
        } else {
            mFlingBlockCheck.onEvent();

        }

        return true;
    }

    // CaretLauncher: no logs or trackpad (onDrag(float, MotionEvent)).

    protected void updateProgress(float fraction) {
        if (mCurrentAnimation == null) {
            return;
        }
        mCurrentAnimation.setPlayFraction(fraction);
    }

    /**
     * Returns animation config for state transition between provided states
     */
    protected StateAnimationConfig getConfigForStates(
            LauncherState fromState, LauncherState toState) {
        return new StateAnimationConfig();
    }

    @Override
    public void onDragEnd(float velocity) {
        if (mCurrentAnimation == null) {
            // Unlikely, but we may have been canceled just before onDragEnd(). We assume whoever
            // canceled us will handle a new state transition to clean up.
            return;
        }

        // CaretLauncher: no trackpad (mIsTrackpadReverseScroll), and the catch-up stops.
        cancelCatchUp();
        boolean fling = mDetector.isFling(velocity);

        boolean blockedFling = fling && mFlingBlockCheck.isBlocked();
        if (blockedFling) {
            fling = false;
        }

        final LauncherState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();
        final float progressVelocity = velocity * mProgressMultiplier;
        final float interpolatedProgress = mCurrentAnimation.getInterpolatedProgress();
        if (!mHoldGaveWay) {
            // CaretLauncher: let go during the hold, it goes back.
            targetState = mFromState;
        } else if (fling) {
            targetState =
                    Float.compare(Math.signum(velocity), Math.signum(mProgressMultiplier)) == 0
                            ? mToState : mFromState;
            // snap to top or bottom using the release velocity
        } else {
            float successTransitionProgress = SUCCESS_TRANSITION_PROGRESS;
            if (mLauncher.getDeviceProfile().getDeviceProperties().isLargeScreen()
                    && (mToState == ALL_APPS || mFromState == ALL_APPS)) {
                successTransitionProgress = TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS;
            }
            targetState =
                    (interpolatedProgress > successTransitionProgress) ? mToState : mFromState;
        }

        final float endProgress;
        final float startProgress;
        final long duration;
        // Increase the duration if we prevented the fling, as we are going against a high velocity.
        final int durationMultiplier = blockedFling && targetState == mFromState
                ? LauncherAnimUtils.blockedFlingDurationFactor(velocity) : 1;

        if (targetState == mToState) {
            endProgress = 1;
            if (progress >= 1) {
                duration = 0;
                startProgress = 1;
            } else {
                // CaretLauncher: the Launcher stand-in is not a Context.
                startProgress = Utilities.boundToRange(progress
                        + progressVelocity * getSingleFrameMs(mLauncher.asContext()), 0f, 1f);
                duration = BaseSwipeDetector.calculateDuration(velocity,
                        endProgress - Math.max(progress, 0)) * durationMultiplier;
            }
        } else {
            // Let the state manager know that the animation didn't go to the target state,
            // but don't cancel ourselves (we already clean up when the animation completes).
            mCurrentAnimation.getTarget().removeListener(mClearStateOnCancelListener);
            // CaretLauncher: only the success listeners (the state's end) are told, as the
            // dispatchOnStart that lets the controller play again does not follow (the state does
            // not change to mToState).
            cancelSuccessListeners();
            // CaretLauncher: a swipe that changed the state changes it back as the field goes back,
            // and gives the state its end when it gets there (goToTargetState).
            if (!mStateChangePending) {
                mLauncher.getStateManager().onUserControlledTransitionStart(mFromState);
                mMustSettle = true;
            }
            mStateChangePending = false;

            endProgress = 0;
            if (progress <= 0) {
                duration = 0;
                startProgress = 0;
            } else {
                // CaretLauncher: the Launcher stand-in is not a Context.
                startProgress = Utilities.boundToRange(progress
                        + progressVelocity * getSingleFrameMs(mLauncher.asContext()), 0f, 1f);
                duration = BaseSwipeDetector.calculateDuration(velocity,
                        Math.min(progress, 1) - endProgress) * durationMultiplier;
            }
        }
        mCurrentAnimation.setEndAction(() -> onSwipeInteractionCompleted(targetState));
        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(startProgress, endProgress);
        updateSwipeCompleteAnimation(anim, duration, targetState, velocity, fling);
        // CaretLauncher: the state changes here unless it did when the hold gave way.
        if (mStateChangePending) {
            mStateChangePending = false;
            mCurrentAnimation.dispatchOnStart();
        }
        // CaretLauncher: no all apps overscroll or spring (onRelease,
        // addSpringFromFlingUpdateListener).
        anim.start();
    }

    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        animator.setDuration(expectedDuration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity));
    }

    protected void onSwipeInteractionCompleted(LauncherState targetState) {
        onReachedFinalState(mToState);
        clearState();
        boolean shouldGoToTargetState = mGoingBetweenStates || (mToState != targetState);
        if (shouldGoToTargetState) {
            goToTargetState(targetState);
        }
        // CaretLauncher: no logs (logReachedState).
    }

    protected void goToTargetState(LauncherState targetState) {
        if (!mLauncher.isInState(targetState)) {
            // If we're already in the target state, don't jump to it at the end of the animation in
            // case the user started interacting with it before the animation finished.
            // CaretLauncher: no logs (logReachedState).
            mLauncher.getStateManager().goToState(targetState, false /* animated */);
        } else if ((mMustSettle && targetState == mFromState)
                || mLauncher.getStateManager().getCurrentStableState() != targetState) {
            // CaretLauncher: the state the swipe went back to, or the state of the animation it
            // caught (or of its own animation it caught going back), gets its end, which the
            // swipe's animation did not give it.
            mLauncher.getStateManager().onUserControlledTransitionEnd(targetState);
        }
        // CaretLauncher: no system UI scrim (getSysUiScrim).
    }

    // CaretLauncher: no logs (logReachedState, getContainerInfo).

    protected void clearState() {
        cancelAnimationControllers();
        mGoingBetweenStates = true;
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
        // CaretLauncher: no trackpad (mIsTrackpadReverseScroll), and the hold is done.
        cancelCatchUp();
        mStateChangePending = false;
    }

    private void cancelAnimationControllers() {
        mCurrentAnimation = null;
    }

    protected boolean shouldOpenAllApps(boolean isDragTowardPositive) {
        // CaretLauncher: no trackpad (mIsTrackpadReverseScroll).
        return isDragTowardPositive;
    }

    // ---- CaretLauncher ----

    /** How far the finger goes before the hold gives way (docs/spec.md 6.2). */
    private static final float HOLD_THRESHOLD_DP = 48f;

    /** How fast the field catches up with the finger when the hold gives way. */
    private static final long CATCH_UP_DURATION_MS = 150;

    private final float mHoldThreshold;

    /** Whether the hold gave way: the field follows the finger. */
    private boolean mHoldGaveWay;

    /** How far behind the finger the field was when the hold gave way. */
    private float mHoldLag;

    /** The field catching up with the finger after the hold. */
    private ValueAnimator mCatchUp;

    /** Where the field was when the drag (or its animation) started. */
    private float mStartBarTop;

    /** The drag's displacement since the animation started. */
    private float mLastDisplacement;

    /** The animation's dispatchOnStart (the state change) waits for the hold or the lift. */
    private boolean mStateChangePending;

    /**
     * The swipe's state needs its end when the swipe ends in the state it started from: it caught
     * a state animation whose end it dropped, or it changed the state and went back.
     */
    private boolean mMustSettle;

    /** The vertical progress the current animation starts from (initCurrentAnimation). */
    protected float mAnimationStartProgress;

    /** The all apps transition, whose field and progress the swipe moves. */
    protected abstract AllAppsTransitionController getAllAppsController();

    /** Whether the touches that began may still become the swipe. */
    protected boolean canKeepIntercepting() {
        return true;
    }

    /** Whether the swipe's animation runs to its end (the finger was lifted). */
    public boolean isSettling() {
        return mCurrentAnimation != null && mCurrentAnimation.getAnimationPlayer().isRunning();
    }

    private boolean isAllAppsBetweenStates() {
        float progress = getAllAppsController().getProgress();
        return progress > 0f && progress < 1f;
    }

    /**
     * The animation's progress for the drag's {@code displacement}: the field goes from where it
     * was by the finger's travel (a little of it during the hold), and the animation's vertical
     * progress (LINEAR while user controlled) is where that puts the field, wherever the keyboard
     * has taken its closed position. Past the hold, the haptic plays, the field catches up with
     * the finger, and the list opening changes the state (the keyboard goes down).
     */
    private float getProgressForDisplacement(float displacement) {
        if (mCurrentAnimation == null) {
            return 0;
        }
        AllAppsTransitionController controller = getAllAppsController();
        float toProgress = mToState.getVerticalProgress(mLauncher);
        float direction = Math.signum(toProgress - mAnimationStartProgress);
        if (direction == 0) {
            return 1;
        }
        // The vertical progress grows toward NORMAL, where the field is lower on the screen.
        float travel = displacement * direction;
        if (!mHoldGaveWay && travel >= mHoldThreshold) {
            mHoldGaveWay = true;
            mHoldLag = held(travel) - travel;
            mCatchUp = ValueAnimator.ofFloat(0f, 1f);
            mCatchUp.setDuration(CATCH_UP_DURATION_MS);
            mCatchUp.setInterpolator(Interpolators.DECELERATE_1_5);
            mCatchUp.addUpdateListener(a -> updateProgress(
                    getProgressForDisplacement(mLastDisplacement)));
            mCatchUp.start();
            controller.performSwipeThresholdHaptic();
            if (mToState == ALL_APPS && mStateChangePending) {
                mStateChangePending = false;
                mCurrentAnimation.dispatchOnStart();
            }
        }
        float fieldTravel;
        if (mHoldGaveWay) {
            float catchUp = mCatchUp == null ? 1f : (float) mCatchUp.getAnimatedValue();
            fieldTravel = travel + mHoldLag * (1f - catchUp);
        } else {
            fieldTravel = held(travel);
        }
        float vertical = controller.getVerticalProgressForBarTop(
                mStartBarTop + fieldTravel * direction);
        return (vertical - mAnimationStartProgress) / (toProgress - mAnimationStartProgress);
    }

    /** The hold before the threshold (docs/spec.md 6.2): a little of the finger's travel. */
    private float held(float travel) {
        int range = Math.max((int) getShiftRange(), 1);
        return OverScroll.dampedScroll(Math.max(travel, 0f), range);
    }

    /** The animation's success listeners learn that it does not get to mToState. */
    private void cancelSuccessListeners() {
        AnimatorPlaybackController.callListenerCommandRecursively(mCurrentAnimation.getTarget(),
                (listener, animator) -> {
                    if (listener instanceof AnimationSuccessListener) {
                        listener.onAnimationCancel(animator);
                    }
                });
    }

    private void cancelCatchUp() {
        if (mCatchUp != null) {
            ValueAnimator catchUp = mCatchUp;
            mCatchUp = null;
            catchUp.cancel();
        }
    }
}
