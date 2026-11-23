package com.malt03.textlauncher.ui.allapps

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import com.android.app.animation.Interpolators
import com.android.launcher3.touch.BaseSwipeDetector
import com.android.launcher3.touch.OverScroll
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.malt03.textlauncher.ui.LauncherRootLayout
import kotlin.math.abs

/**
 * AllAppsSwipeController with AbstractStateChangeTouchController (android-17.0.0_r1): a vertical
 * swipe opens the all apps list or closes it (docs/spec.md 6.1, 6.2, 6.6). SingleAxisSwipeDetector
 * finds the swipe; when the finger lifts, the velocity and progress decide the state as in
 * Launcher3 (a fling goes its way, otherwise past half opens), and the rest is animated over
 * BaseSwipeDetector.calculateDuration with scrollInterpolatorForVelocity. From the drag until that
 * animation ends (mCurrentAnimation), a touch catches it: the detector stays settling, so the touch
 * starts a drag right away (onDragStart) and the sheet follows the finger from where it was.
 *
 * TextLauncher: the swipe first holds back until [THRESHOLD_DP] (OverScroll.dampedScroll), then
 * with the haptic the field catches up with the finger and follows it (docs/spec.md 6.2). Let go
 * before that, it goes back. Launcher3 has no such hold; it plays the haptic as the swipe starts.
 */
class AllAppsSwipeController(
    context: Context,
    private val root: LauncherRootLayout,
    private val transition: AllAppsTransitionController,
    private val callbacks: Callbacks,
) : SingleAxisSwipeDetector.Listener {

    interface Callbacks {
        /** AllAppsSwipeController.canInterceptTouch: no drag, open menu or folder, edit mode. */
        fun canSwipe(): Boolean

        /**
         * LauncherStateManager.getState: whether the list is open, or going to be (ALL_APPS), or
         * closed or going to be (NORMAL).
         */
        fun isAllAppsOpen(): Boolean

        /** A swipe up from here may open the list (the field, or the home screen at its bottom). */
        fun canOpenFrom(ev: MotionEvent): Boolean

        /** A swipe down from here may close the list (the field, or the list at its top). */
        fun canCloseFrom(ev: MotionEvent): Boolean

        /** The swipe went past the hold: the keyboard goes down when opening. */
        fun onThresholdCrossed(opening: Boolean)

        /**
         * The list is going to [open] after the finger lifted (the keyboard comes back to close);
         * only when that is not the state it is in ([isAllAppsOpen]).
         */
        fun onSettling(open: Boolean)

        /**
         * The list got to [open] (AbstractStateChangeTouchController.onSwipeInteractionCompleted);
         * not when the swipe went back to the state it started from.
         */
        fun onSettled(open: Boolean)
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

    /**
     * [Callbacks.onSettled] runs at the end even in the state the swipe started from: the drag
     * caught an animation whose end action it dropped.
     */
    private var mustSettle = false

    private var opening = false
    private var startBarTop = 0f
    private var crossed = false
    private var lastTravel = 0f

    /** How far behind the finger the field was when the hold gave way. */
    private var lagAtCross = 0f
    private var catchUp: ValueAnimator? = null

    /**
     * LauncherRootLayout's touch interceptor (AbstractStateChangeTouchController
     * .onControllerInterceptTouchEvent): true while this gesture has the touches.
     */
    fun onTouch(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            noIntercept = !canInterceptTouch(ev)
            if (noIntercept) return false
            if (hasCurrentAnimation) {
                detector.setDetectableScrollConditions(SingleAxisSwipeDetector.DIRECTION_BOTH, true)
            } else if (callbacks.isAllAppsOpen()) {
                detector.setDetectableScrollConditions(SingleAxisSwipeDetector.DIRECTION_NEGATIVE, false)
            } else {
                detector.setDetectableScrollConditions(SingleAxisSwipeDetector.DIRECTION_POSITIVE, false)
            }
        }
        if (noIntercept) return false
        // TextLauncher: a menu or folder opened since (a long press) gets the touches first, as
        // BaseDragLayer gives them to the floating view's controller.
        if (!detector.isDraggingOrSettling && !callbacks.canSwipe()) {
            noIntercept = true
            return false
        }
        detector.onTouchEvent(ev)
        return detector.isDraggingOrSettling
    }

    /** AllAppsSwipeController.canInterceptTouch */
    private fun canInterceptTouch(ev: MotionEvent): Boolean {
        // If we are already animating from a previous state, we can intercept.
        if (hasCurrentAnimation) return true
        if (!callbacks.canSwipe()) return false
        return if (callbacks.isAllAppsOpen()) callbacks.canCloseFrom(ev) else callbacks.canOpenFrom(ev)
    }

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        if (!hasCurrentAnimation) {
            hasCurrentAnimation = true
            opening = !callbacks.isAllAppsOpen()
            // An animation without a gesture (back, the menu's "Apps") gives way to the swipe, as
            // createAnimationToNewWorkspace cancels the state animation.
            mustSettle = transition.isAnimating
            transition.cancelAnimation()
        } else {
            // AbstractStateChangeTouchController.onDragStart: mCurrentAnimation.pause(). Its end
            // action is taken over by the one this drag ends with.
            transition.pauseAnimation()
        }
        val progress = transition.progress
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
            callbacks.onThresholdCrossed(opening)
        }
        update()
        return true
    }

    /** Places the field where the finger has taken it. */
    private fun update() {
        val travel = if (crossed) {
            lastTravel + lagAtCross * (1f - (catchUp?.animatedValue as? Float ?: 1f))
        } else {
            held(lastTravel)
        }
        val barTop = if (opening) startBarTop - travel else startBarTop + travel
        val range = root.closedBarTop - root.openBarTop
        if (range > 0f) transition.progress = (root.closedBarTop - barTop) / range
    }

    /** The hold before the threshold (docs/spec.md 6.2): a little of the finger's travel. */
    private fun held(travel: Float): Float {
        val range = (root.closedBarTop - root.openBarTop).toInt().coerceAtLeast(1)
        return OverScroll.dampedScroll(travel.coerceAtLeast(0f), range).toFloat()
    }

    override fun onDragEnd(velocity: Float) {
        catchUp?.cancel()
        catchUp = null
        val progress = transition.progress
        val toOpen = if (!crossed) {
            // TextLauncher: let go during the hold, it goes back.
            !opening
        } else if (detector.isFling(velocity)) {
            // AbstractStateChangeTouchController.onDragEnd: snap the way of the fling (up opens).
            velocity < 0
        } else {
            progress > SUCCESS_TRANSITION_PROGRESS
        }
        val target = if (toOpen) 1f else 0f
        val duration = BaseSwipeDetector.calculateDuration(velocity, abs(target - progress))
        // AbstractStateChangeTouchController.goToTargetState: only when the list is not in the
        // target state already. TextLauncher: its state changes when the field comes up past the
        // hold (onThresholdCrossed) or here.
        val changesState = toOpen != callbacks.isAllAppsOpen()
        val settles = changesState || mustSettle
        mustSettle = settles
        if (changesState) callbacks.onSettling(toOpen)
        transition.animateTo(
            target,
            duration,
            Interpolators.scrollInterpolatorForVelocity(velocity),
            onCancel = ::clearState,
        ) { onSwipeInteractionCompleted(toOpen, settles) }
    }

    /** AbstractStateChangeTouchController.onSwipeInteractionCompleted */
    private fun onSwipeInteractionCompleted(open: Boolean, settles: Boolean) {
        clearState()
        if (settles) callbacks.onSettled(open)
    }

    /** AbstractStateChangeTouchController.clearState */
    private fun clearState() {
        hasCurrentAnimation = false
        mustSettle = false
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
    }

    private companion object {
        /** LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS */
        const val SUCCESS_TRANSITION_PROGRESS = 0.5f

        /** TextLauncher: how far the finger goes before the hold gives way. */
        const val THRESHOLD_DP = 48f

        /** TextLauncher: how fast the field catches up with the finger at the threshold. */
        const val CATCH_UP_MS = 150L
    }
}
