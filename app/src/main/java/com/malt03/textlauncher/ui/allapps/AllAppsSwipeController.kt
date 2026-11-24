package com.malt03.textlauncher.ui.allapps

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators
import com.android.app.animation.Interpolators.ACCELERATE
import com.android.app.animation.Interpolators.DECELERATED_EASE
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH
import com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS
import com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE
import com.android.launcher3.touch.BaseSwipeDetector
import com.android.launcher3.touch.OverScroll
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.TouchController
import com.android.launcher3.views.ActivityContext
import com.malt03.textlauncher.ui.LauncherRootLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherState.ALL_APPS
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.statemanager.StateManager
import kotlin.math.abs

/**
 * AllAppsSwipeController with AbstractStateChangeTouchController (android-17.0.0_r1): the
 * TouchController that switches between the NORMAL and ALL_APPS states with a vertical swipe
 * (docs/spec.md 6.1, 6.2, 6.6), one of the drag layer's controllers (Launcher.createTouchControllers).
 * SingleAxisSwipeDetector finds the swipe; when the finger lifts, the velocity and progress decide
 * the state as in Launcher3 (a fling goes its way, otherwise past half opens), and the rest is
 * animated over BaseSwipeDetector.calculateDuration with scrollInterpolatorForVelocity. From the
 * drag until that animation ends (mCurrentAnimation), a touch catches it: the detector stays
 * settling, so the touch starts a drag right away (onDragStart) and the sheet follows the finger
 * from where it was.
 *
 * TextLauncher: there is no AnimatorPlaybackController. The field's closed position follows the
 * keyboard, which goes down as the list opens, so the progress is worked out from where the finger
 * has taken the field ([update]) rather than from a fixed progress multiplier, and the swipe moves
 * the states' properties itself (AllAppsTransitionController.setUserControlledProgress). The swipe
 * first holds back until [THRESHOLD_DP] (OverScroll.dampedScroll), then with the haptic the field
 * catches up with the finger and follows it (docs/spec.md 6.2); let go before that, it goes back.
 * Launcher3 has no such hold; it plays the haptic as the swipe starts. The state changes when the
 * field comes up past the hold, or when the finger lifts toward the other state, rather than when
 * the drag starts.
 */
class AllAppsSwipeController(
    context: Context,
    private val root: LauncherRootLayout,
    private val transition: AllAppsTransitionController,
    private val stateManager: StateManager<LauncherState, Launcher>,
    private val activityContext: ActivityContext,
    private val callbacks: Callbacks,
) : TouchController, SingleAxisSwipeDetector.Listener {

    /** TextLauncher: what the swipe asks of the launcher's views. */
    interface Callbacks {
        /** TextLauncher: no drag and drop is in progress. */
        fun isDragging(): Boolean

        /** TextLauncher: a swipe up from here may open the list (the field, or the home screen at its bottom). */
        fun canOpenFrom(ev: MotionEvent): Boolean

        /**
         * ActivityAllAppsContainerView.shouldContainerScroll: a swipe down from here may close the
         * list (the field, or the list at its top).
         */
        fun shouldContainerScroll(ev: MotionEvent): Boolean
    }

    private val detector = SingleAxisSwipeDetector(context, this, SingleAxisSwipeDetector.VERTICAL)
    private val threshold = THRESHOLD_DP * context.resources.displayMetrics.density

    /** AbstractStateChangeTouchController.mNoIntercept: this gesture is not watched. */
    private var noIntercept = true

    /**
     * AbstractStateChangeTouchController.mCurrentAnimation: from the drag until the animation that
     * finishes it ends (clearState).
     */
    private var hasCurrentAnimation = false

    /** mCurrentAnimation's animation player, once the finger lifted. */
    private var animationPlayer: ValueAnimator? = null

    /** TextLauncher: whether the animation that finishes a swipe is running. */
    val isSettling: Boolean get() = animationPlayer != null

    /**
     * The swipe's state changes at the end even when it ends in the state it started from: the drag
     * caught a state animation whose end it dropped.
     */
    private var mustSettle = false

    private var opening = false
    private var startBarTop = 0f
    private var crossed = false
    private var lastTravel = 0f

    /** How far behind the finger the field was when the hold gave way. */
    private var lagAtCross = 0f
    private var catchUp: ValueAnimator? = null

    /** mClearStateOnCancelListener: something else changed the state. */
    private val clearStateOnCancel = Runnable {
        stopAnimationPlayer()
        clearState()
    }

    /** AllAppsSwipeController.canInterceptTouch */
    private fun canInterceptTouch(ev: MotionEvent): Boolean {
        if (hasCurrentAnimation) {
            // If we are already animating from a previous state, we can intercept.
            return true
        }
        if (AbstractFloatingView.getTopOpenView(activityContext) != null) {
            return false
        }
        if (stateManager.state != NORMAL && stateManager.state != ALL_APPS) {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false
        }
        // TextLauncher: not while an item is dragged.
        if (callbacks.isDragging()) {
            return false
        }
        if (stateManager.state == ALL_APPS && !callbacks.shouldContainerScroll(ev)) {
            return false
        }
        // TextLauncher: a swipe up opens the list only from the field or the home screen's bottom.
        if (stateManager.state == NORMAL && !callbacks.canOpenFrom(ev)) {
            return false
        }
        return true
    }

    /** AbstractStateChangeTouchController.onControllerInterceptTouchEvent */
    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            noIntercept = !canInterceptTouch(ev)
            if (noIntercept) {
                return false
            }

            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            val directionsToDetectScroll: Int
            var ignoreSlopWhenSettling = false

            if (hasCurrentAnimation) {
                directionsToDetectScroll = SingleAxisSwipeDetector.DIRECTION_BOTH
                ignoreSlopWhenSettling = true
            } else {
                directionsToDetectScroll = getSwipeDirection()
                if (directionsToDetectScroll == 0) {
                    noIntercept = true
                    return false
                }
            }
            detector.setDetectableScrollConditions(directionsToDetectScroll, ignoreSlopWhenSettling)
        }

        if (noIntercept) {
            return false
        }
        // TextLauncher: a menu or folder opened since (a long press) gets the touches first, as
        // the drag layer gives them to the floating view's controller.
        if (!detector.isDraggingOrSettling && (
                AbstractFloatingView.getTopOpenView(activityContext) != null || callbacks.isDragging() ||
                    (stateManager.state != NORMAL && stateManager.state != ALL_APPS)
                )
        ) {
            noIntercept = true
            return false
        }

        onControllerTouchEvent(ev)
        return detector.isDraggingOrSettling
    }

    private fun getSwipeDirection(): Int {
        val fromState = stateManager.state
        var swipeDirection = 0
        if (getTargetState(fromState, isDragTowardPositive = true) != fromState) {
            swipeDirection = swipeDirection or SingleAxisSwipeDetector.DIRECTION_POSITIVE
        }
        if (getTargetState(fromState, isDragTowardPositive = false) != fromState) {
            swipeDirection = swipeDirection or SingleAxisSwipeDetector.DIRECTION_NEGATIVE
        }
        return swipeDirection
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean = detector.onTouchEvent(ev)

    /** AllAppsSwipeController.getTargetState */
    private fun getTargetState(fromState: LauncherState, isDragTowardPositive: Boolean): LauncherState =
        if (fromState == NORMAL && isDragTowardPositive) {
            ALL_APPS
        } else if (fromState == ALL_APPS && !isDragTowardPositive) {
            NORMAL
        } else {
            fromState
        }

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        if (!hasCurrentAnimation) {
            hasCurrentAnimation = true
            opening = stateManager.state != ALL_APPS
            // An animation without a gesture (back, the menu's "Apps") gives way to the swipe, as
            // createAnimationToNewWorkspace cancels the state animation.
            mustSettle = stateManager.isInTransition
            stateManager.setCurrentUserControlledAnimation(clearStateOnCancel)
        } else {
            // AbstractStateChangeTouchController.onDragStart: mCurrentAnimation.pause(). Its end
            // action is taken over by the one this drag ends with.
            stopAnimationPlayer()
        }
        val progress = transition.getProgress()
        startBarTop = root.closedBarTop + (root.openBarTop - root.closedBarTop) * progress
        // Caught in the middle of an animation, the field already follows the finger.
        crossed = progress > 0f && progress < 1f
        lagAtCross = 0f
        lastTravel = 0f
        catchUp?.cancel()
        catchUp = null
    }

    override fun onDrag(displacement: Float): Boolean {
        // Up is negative; the travel is toward the other state.
        lastTravel = if (opening) -displacement else displacement
        if (!crossed && lastTravel >= threshold) {
            crossed = true
            lagAtCross = held(lastTravel) - lastTravel
            catchUp = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = CATCH_UP_MS
                interpolator = Interpolators.DECELERATE_1_5
                addUpdateListener { update() }
                start()
            }
            // AllAppsTransitionController.setStateWithAnimation: the haptic for going to all apps.
            transition.performStateHaptic(userControlled = true)
            // TextLauncher: the keyboard goes down as the field comes up (docs/spec.md 6.2).
            if (opening) stateManager.onUserControlledTransitionStart(ALL_APPS)
        }
        update()
        return true
    }

    /** TextLauncher: places the field where the finger has taken it. */
    private fun update() {
        val travel = if (crossed) {
            lastTravel + lagAtCross * (1f - (catchUp?.animatedValue as? Float ?: 1f))
        } else {
            held(lastTravel)
        }
        val barTop = if (opening) startBarTop - travel else startBarTop + travel
        val range = root.closedBarTop - root.openBarTop
        if (range > 0f) transition.setUserControlledProgress(((root.closedBarTop - barTop) / range).coerceIn(0f, 1f))
    }

    /** TextLauncher: the hold before the threshold (docs/spec.md 6.2), a little of the finger's travel. */
    private fun held(travel: Float): Float {
        val range = (root.closedBarTop - root.openBarTop).toInt().coerceAtLeast(1)
        return OverScroll.dampedScroll(travel.coerceAtLeast(0f), range).toFloat()
    }

    override fun onDragEnd(velocity: Float) {
        if (!hasCurrentAnimation) {
            // Unlikely, but we may have been canceled just before onDragEnd(). We assume whoever
            // canceled us will handle a new state transition to clean up.
            return
        }
        catchUp?.cancel()
        catchUp = null
        val progress = transition.getProgress()
        val toOpen = if (!crossed) {
            // TextLauncher: let go during the hold, it goes back.
            !opening
        } else if (detector.isFling(velocity)) {
            // snap to top or bottom using the release velocity (up opens)
            velocity < 0
        } else {
            progress > SUCCESS_TRANSITION_PROGRESS
        }
        val targetState = if (toOpen) ALL_APPS else NORMAL
        val target = transition.verticalProgress(targetState)
        val duration = BaseSwipeDetector.calculateDuration(velocity, abs(target - progress))
        // AbstractStateChangeTouchController.goToTargetState: only when the launcher is not in the
        // target state already. TextLauncher: the state changes when the finger lifts toward the
        // other state (or the field came up past the hold), and its end follows the animation.
        val changesState = stateManager.state != targetState
        val settles = changesState || mustSettle
        mustSettle = settles
        if (changesState) stateManager.onUserControlledTransitionStart(targetState)
        if (duration <= 0 || progress == target) {
            transition.setUserControlledProgress(target)
            onSwipeInteractionCompleted(targetState, settles)
            return
        }
        animationPlayer = ValueAnimator.ofFloat(progress, target).apply {
            this.duration = duration
            interpolator = Interpolators.scrollInterpolatorForVelocity(velocity)
            addUpdateListener { transition.setUserControlledProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animationPlayer !== animation) return
                    animationPlayer = null
                    onSwipeInteractionCompleted(targetState, settles)
                }
            })
            start()
        }
    }

    /** AnimatorPlaybackController.pause: stops the animation player where it is, without its end. */
    private fun stopAnimationPlayer() {
        val player = animationPlayer ?: return
        animationPlayer = null
        player.removeAllListeners()
        player.cancel()
    }

    private fun onSwipeInteractionCompleted(targetState: LauncherState, settles: Boolean) {
        clearState()
        stateManager.onUserControlledTransitionEnd(targetState, /* changed= */ settles)
    }

    private fun clearState() {
        hasCurrentAnimation = false
        mustSettle = false
        stateManager.clearCurrentUserControlledAnimation()
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
    }

    companion object {
        /** LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS */
        private const val SUCCESS_TRANSITION_PROGRESS = 0.5f

        // Depth to apply behind All Apps when it's presented on a sheet.
        val ALL_APPS_SHEET_DEPTH: Interpolator = DECELERATED_EASE

        /** TextLauncher: how far the finger goes before the hold gives way. */
        private const val THRESHOLD_DP = 48f

        /** TextLauncher: how fast the field catches up with the finger at the threshold. */
        private const val CATCH_UP_MS = 150L

        /**
         * Applies Animation config values for transition from all apps to home.
         *
         * TextLauncher: without the scrim, the fade and the hotseat (the list is on the sheet under
         * the field, which does not scale).
         */
        @JvmStatic
        fun applyAllAppsToNormalConfig(config: StateAnimationConfig) {
            if (!config.isUserControlled) {
                config.duration = 200
                config.setInterpolator(ANIM_VERTICAL_PROGRESS, ACCELERATE)
            }
            config.setInterpolator(ANIM_WORKSPACE_SCALE, Interpolators.reverse(ALL_APPS_SHEET_DEPTH))
            config.setInterpolator(ANIM_DEPTH, Interpolators.reverse(ALL_APPS_SHEET_DEPTH))
        }

        /**
         * Applies Animation config values for transition from home to all apps.
         *
         * TextLauncher: without the scrim, the fade and the hotseat.
         */
        @JvmStatic
        fun applyNormalToAllAppsAnimConfig(config: StateAnimationConfig) {
            if (!config.isUserControlled) {
                config.setInterpolator(ANIM_VERTICAL_PROGRESS, EMPHASIZED)
            }
            config.setInterpolator(ANIM_WORKSPACE_SCALE, ALL_APPS_SHEET_DEPTH)
            config.setInterpolator(ANIM_DEPTH, ALL_APPS_SHEET_DEPTH)
        }
    }
}
