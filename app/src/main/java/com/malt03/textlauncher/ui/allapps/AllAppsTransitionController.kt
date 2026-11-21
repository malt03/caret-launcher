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

    var progress: Float
        get() = root.allAppsProgress
        set(value) {
            root.allAppsProgress = value.coerceIn(0f, 1f)
        }

    val isAnimating: Boolean get() = animator != null

    /** Stops a running animation where it is, without calling its end action. */
    fun cancelAnimation() {
        val running = animator ?: return
        animator = null
        running.removeAllListeners()
        running.cancel()
    }

    /**
     * Animates to [target] (0 or 1), like the state animation AbstractStateChangeTouchController
     * finishes a swipe with; [onEnd] runs when it gets there.
     */
    fun animateTo(target: Float, duration: Long, interpolator: Interpolator, onEnd: () -> Unit) {
        cancelAnimation()
        if (duration <= 0 || progress == target) {
            progress = target
            onEnd()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { progress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
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
            animateTo(1f, OPEN_DURATION_MS, Interpolators.EMPHASIZED, onEnd)
        } else {
            animateTo(0f, CLOSE_DURATION_MS, Interpolators.ACCELERATE, onEnd)
        }
    }

    private companion object {
        /** config_allAppsOpenDuration */
        const val OPEN_DURATION_MS = 600L

        /** AllAppsSwipeController.applyAllAppsToNormalConfig */
        const val CLOSE_DURATION_MS = 200L
    }
}
