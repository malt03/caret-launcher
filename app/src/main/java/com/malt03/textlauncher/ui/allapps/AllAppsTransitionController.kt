package com.malt03.textlauncher.ui.allapps

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators
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
     * finishes a swipe with; [onEnd] runs when it gets there, and [onCancel] when [cancelAnimation]
     * stops it before that.
     */
    fun animateTo(
        target: Float,
        duration: Long,
        interpolator: Interpolator,
        onCancel: (() -> Unit)? = null,
        onEnd: () -> Unit,
    ) {
        cancelAnimation()
        if (duration <= 0 || progress == target) {
            progress = target
            onEnd()
            return
        }
        this.onCancel = onCancel
        animator = ValueAnimator.ofFloat(progress, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { progress = it.animatedValue as Float }
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
     * (applyAllAppsToNormalConfig).
     */
    fun animateTo(open: Boolean, onEnd: () -> Unit) {
        if (open) {
            animateTo(1f, OPEN_DURATION_MS, Interpolators.EMPHASIZED, onEnd = onEnd)
        } else {
            animateTo(0f, CLOSE_DURATION_MS, Interpolators.ACCELERATE, onEnd = onEnd)
        }
    }

    private companion object {
        /** config_allAppsOpenDuration */
        const val OPEN_DURATION_MS = 600L

        /** AllAppsSwipeController.applyAllAppsToNormalConfig */
        const val CLOSE_DURATION_MS = 200L
    }
}
