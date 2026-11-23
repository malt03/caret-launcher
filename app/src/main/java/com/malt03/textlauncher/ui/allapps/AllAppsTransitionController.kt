package com.malt03.textlauncher.ui.allapps

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators
import com.android.launcher3.Flags
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.malt03.textlauncher.ui.LauncherRootLayout

/**
 * AllAppsTransitionController (android-17.0.0_r1): the all apps progress, from 0 (closed) to 1
 * (open), which moves the search field with the list on its sheet (LauncherRootLayout), and its
 * animation.
 *
 * TextLauncher: the progress is the other way round from Launcher3's vertical progress (1 is
 * closed there), and the field comes up with the list.
 */
class AllAppsTransitionController(private val root: LauncherRootLayout) {
    private var animator: ValueAnimator? = null

    /** The running animation's cancel action ([animateTo]). */
    private var onCancel: (() -> Unit)? = null

    var progress: Float
        get() = root.allAppsProgress
        set(value) {
            root.allAppsProgress = value.coerceIn(0f, 1f)
        }

    val isAnimating: Boolean get() = animator != null

    /**
     * Stops a running animation where it is, without calling its end action; its cancel action runs
     * (AbstractStateChangeTouchController.mClearStateOnCancelListener).
     */
    fun cancelAnimation() {
        val cancelAction = onCancel
        pauseAnimation()
        cancelAction?.invoke()
    }

    /**
     * AnimatorPlaybackController.pause: a swipe caught the animation that finishes the last one,
     * which stops where it is without its end or cancel action.
     */
    fun pauseAnimation() {
        val running = animator ?: return
        animator = null
        onCancel = null
        running.removeAllListeners()
        running.cancel()
    }

    /**
     * Animates to [target] (0 or 1), like the state animation AbstractStateChangeTouchController
     * finishes a swipe with: the whole animation is played with [interpolator]
     * (updateSwipeCompleteAnimation), the vertical progress being LINEAR with a gesture. [onEnd]
     * runs when it gets there, and [onCancel] when [cancelAnimation] stops it before that.
     */
    fun animateTo(
        target: Float,
        duration: Long,
        interpolator: Interpolator,
        onCancel: (() -> Unit)? = null,
        onEnd: () -> Unit,
    ) = animate(target, duration, interpolator, interpolator, onCancel, onEnd)

    /**
     * Plays the fraction linearly and puts each property through its interpolator
     * (StateAnimationConfig.getInterpolator): [verticalInterpolator] for the progress
     * (ANIM_VERTICAL_PROGRESS), [depthInterpolator] for the depth progress, which the home screen's
     * scale and the depth follow (ANIM_WORKSPACE_SCALE, ANIM_DEPTH).
     */
    private fun animate(
        target: Float,
        duration: Long,
        verticalInterpolator: Interpolator,
        depthInterpolator: Interpolator,
        onCancel: (() -> Unit)?,
        onEnd: () -> Unit,
    ) {
        cancelAnimation()
        if (duration <= 0 || (progress == target && root.allAppsDepthProgress == target)) {
            progress = target
            onEnd()
            return
        }
        this.onCancel = onCancel
        val startProgress = progress
        val startDepthProgress = root.allAppsDepthProgress
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = Interpolators.LINEAR
            addUpdateListener {
                val fraction = it.animatedFraction
                root.setAllAppsProgress(
                    startProgress + (target - startProgress) * verticalInterpolator.getInterpolation(fraction),
                    startDepthProgress + (target - startDepthProgress) * depthInterpolator.getInterpolation(fraction),
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    this@AllAppsTransitionController.onCancel = null
                    progress = target
                    onEnd()
                }
            })
            start()
        }
    }

    /**
     * The open and close without a gesture (the menu's "Apps", the back gesture): Launcher3's state
     * animation, EMPHASIZED over config_allAppsOpenDuration to open
     * (AllAppsSwipeController.applyNormalToAllAppsAnimConfig), ACCELERATE over 200ms to close
     * (applyAllAppsToNormalConfig). Those are for the vertical progress only; the home screen's
     * scale and the depth take ALL_APPS_SHEET_DEPTH of the fraction (LauncherRootLayout). Opening
     * plays the haptic (setStateWithAnimation).
     */
    fun animateTo(open: Boolean, onEnd: () -> Unit) {
        if (open) {
            performStateHaptic(userControlled = false)
            animate(1f, OPEN_DURATION_MS, Interpolators.EMPHASIZED, Interpolators.LINEAR, null, onEnd)
        } else {
            animate(0f, CLOSE_DURATION_MS, Interpolators.ACCELERATE, Interpolators.LINEAR, null, onEnd)
        }
    }

    /**
     * AllAppsTransitionController.setStateWithAnimation: "This controls both haptics for tapping on
     * QSB and going to all apps", SWIPE_THRESHOLD_INDICATOR for a swipe ([userControlled]) and
     * TAP_HIGH_EMPHASIS without one. Launcher3 plays it going from NORMAL to ALL_APPS.
     *
     * TextLauncher: a swipe plays it when it gets past the hold (docs/spec.md 6.2), and a swipe
     * that closes the list plays it too.
     */
    fun performStateHaptic(userControlled: Boolean) {
        if (Flags.msdlFeedback()) {
            MSDLPlayerWrapper.INSTANCE.get(root.context).playToken(
                if (userControlled) MSDLToken.SWIPE_THRESHOLD_INDICATOR else MSDLToken.TAP_HIGH_EMPHASIS,
            )
        } else {
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
        }
    }

    private companion object {
        /** config_allAppsOpenDuration */
        const val OPEN_DURATION_MS = 600L

        /** AllAppsSwipeController.applyAllAppsToNormalConfig */
        const val CLOSE_DURATION_MS = 200L
    }
}
