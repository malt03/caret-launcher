package com.malt03.textlauncher.ui.statemanager

import android.animation.Animator
import android.animation.AnimatorSet
import com.android.launcher3.anim.AnimationSuccessListener
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.states.StateAnimationConfig
import com.malt03.textlauncher.ui.LauncherState
import com.malt03.textlauncher.ui.allapps.AllAppsSwipeController

/**
 * StateManager (android-17.0.0_r1): the state the launcher is in or going to ([state]), the last
 * one it got to ([currentStableState]), and the transitions between them, which the
 * [StateHandler]s play (AllAppsTransitionController, DepthController).
 *
 * TextLauncher: [container]'s hooks (Launcher.onStateSetStart / onStateSetEnd) run right away, not
 * when the posted animation starts, and get the states the transition comes from. A swipe does not
 * get an AnimatorPlaybackController (createAnimationToNewWorkspace): it moves the states'
 * properties itself, and tells the transition's start and end here
 * ([setCurrentUserControlledAnimation], [onUserControlledTransitionStart],
 * [onUserControlledTransitionEnd]).
 */
class StateManager(private val container: StatefulContainer) {

    /** StatefulContainer's state hooks (Launcher.onStateSetStart, onStateSetEnd). */
    interface StatefulContainer {
        /**
         * The transition from [fromState] to [toState] starts; [animated] is false when the handlers
         * are set to the state at once.
         */
        fun onStateSetStart(fromState: LauncherState, toState: LauncherState, animated: Boolean)

        /** The launcher got to [state]; [lastStableState] is the one it was in before. */
        fun onStateSetEnd(state: LauncherState, lastStableState: LauncherState)
    }

    /** StateHandler: sets or animates its part of the launcher to a state. */
    interface StateHandler {
        /** Updates the UI to [state] without any animations */
        fun setState(state: LauncherState)

        /** Sets the UI to [toState] by animating from the current state. */
        fun setStateWithAnimation(toState: LauncherState, config: StateAnimationConfig, animation: PendingAnimation)
    }

    var stateHandlers: Array<StateHandler> = emptyArray()

    /** getState: the state the launcher is in or going to. */
    var state = LauncherState.NORMAL
        private set

    /** getCurrentStableState */
    var currentStableState = LauncherState.NORMAL
        private set

    /** mConfig.currentAnimation, when it is not a swipe's. */
    private var currentAnimation: AnimatorSet? = null
    private var currentAnimationTarget: LauncherState? = null

    /** TextLauncher: mConfig.playbackController for a swipe; stops it (its cancel listener). */
    private var userControlledCancel: Runnable? = null

    /** Launcher.isInState */
    fun isInState(state: LauncherState): Boolean = this.state == state

    /** TextLauncher: whether a state animation that is not a swipe's is running. */
    val isAnimating: Boolean get() = currentAnimation != null

    /** Whether a swipe controls the state (StateAnimationConfig.isUserControlled). */
    val isUserControlled: Boolean get() = userControlledCancel != null

    /**
     * Changes the Launcher state to the provided state. [onSuccess] runs once it gets there, and not
     * when something stops the transition before that (AnimatorListeners.forSuccessCallback).
     */
    fun goToState(state: LauncherState, animated: Boolean, onSuccess: (() -> Unit)? = null) {
        if (this.state == state) {
            if (currentAnimation == null && userControlledCancel == null) {
                // Run any queued runnable
                onSuccess?.invoke()
                return
            } else if (userControlledCancel == null && animated && currentAnimationTarget == state) {
                // We are running the same animation as requested -- allow the current animation to
                // complete instead of canceling it.
                if (onSuccess != null) currentAnimation?.addListener(successListener(onSuccess))
                return
            }
        }

        // Cancel the current animation.
        val fromState = this.state
        cancelAnimation()
        val duration = if (state == BASE_STATE) {
            // Since state mBaseState can be reached from multiple states, just assume that the
            // transition plays in reverse and use the same duration as previous state.
            fromState.getTransitionDuration(isToState = false)
        } else {
            state.getTransitionDuration(isToState = true)
        }
        // TextLauncher: a transition without a duration is not animated.
        if (!animated || duration <= 0L) {
            onStateTransitionStart(fromState, state, animated = false)
            for (handler in stateHandlers) handler.setState(state)
            onStateTransitionEnd(state)
            // Run any queued runnable
            onSuccess?.invoke()
            return
        }
        goToStateAnimated(state, fromState, duration, onSuccess)
    }

    private fun goToStateAnimated(state: LauncherState, fromState: LauncherState, duration: Long, onSuccess: (() -> Unit)?) {
        val config = StateAnimationConfig()
        config.duration = duration
        prepareForAtomicAnimation(fromState, state, config)
        val builder = PendingAnimation(config.duration)
        for (handler in stateHandlers) handler.setStateWithAnimation(state, config, builder)
        val animation = builder.buildAnim()
        animation.addListener(object : AnimationSuccessListener() {
            override fun onAnimationSuccess(animator: Animator) {
                if (currentAnimation !== animator) return
                currentAnimation = null
                currentAnimationTarget = null
                onStateTransitionEnd(state)
            }
        })
        if (onSuccess != null) animation.addListener(successListener(onSuccess))
        currentAnimation = animation
        currentAnimationTarget = state
        // TextLauncher: the state changes now, before the animation starts.
        onStateTransitionStart(fromState, state, animated = true)
        animation.start()
    }

    /**
     * QuickstepAtomicAnimationFactory.prepareForAtomicAnimation: the interpolators of the all apps
     * transitions.
     */
    private fun prepareForAtomicAnimation(fromState: LauncherState, toState: LauncherState, config: StateAnimationConfig) {
        if (fromState == LauncherState.ALL_APPS && toState == LauncherState.NORMAL) {
            AllAppsSwipeController.applyAllAppsToNormalConfig(config)
        } else if (fromState == LauncherState.NORMAL && toState == LauncherState.ALL_APPS) {
            AllAppsSwipeController.applyNormalToAllAppsAnimConfig(config)
        }
    }

    private fun onStateTransitionStart(fromState: LauncherState, state: LauncherState, animated: Boolean) {
        this.state = state
        container.onStateSetStart(fromState, state, animated)
    }

    private fun onStateTransitionEnd(state: LauncherState) {
        // Only change the stable states after the transitions have finished
        val lastStableState = currentStableState
        currentStableState = state
        container.onStateSetEnd(state, lastStableState)
    }

    /** Cancels the current animation, and the swipe that controls the state. */
    fun cancelAnimation() {
        currentAnimation?.let {
            currentAnimation = null
            currentAnimationTarget = null
            it.cancel()
        }
        userControlledCancel?.let {
            userControlledCancel = null
            it.run()
        }
    }

    /**
     * TextLauncher: createAnimationToNewWorkspace for a swipe. The current animation is canceled
     * (its success callbacks do not run), and [onCancel] runs when something else changes the state
     * before [clearCurrentUserControlledAnimation].
     */
    fun setCurrentUserControlledAnimation(onCancel: Runnable) {
        cancelAnimation()
        userControlledCancel = onCancel
    }

    /** TextLauncher: the swipe is done (AbstractStateChangeTouchController.clearState). */
    fun clearCurrentUserControlledAnimation() {
        userControlledCancel = null
    }

    /**
     * TextLauncher: the swipe's transition to [state] starts (the animation's dispatchOnStart): at
     * the threshold, or when the finger lifts toward another state.
     */
    fun onUserControlledTransitionStart(state: LauncherState) {
        onStateTransitionStart(this.state, state, animated = true)
    }

    /**
     * TextLauncher: the swipe got to [state] (AbstractStateChangeTouchController.goToTargetState).
     * Unless it [changed] the state (it ended where it started, and caught no state animation whose
     * end it dropped), nothing is told: "If we're already in the target state, don't jump to it".
     */
    fun onUserControlledTransitionEnd(state: LauncherState, changed: Boolean) {
        if (!changed) {
            currentStableState = state
            return
        }
        if (this.state != state) onStateTransitionStart(this.state, state, animated = true)
        onStateTransitionEnd(state)
    }

    private fun successListener(onSuccess: () -> Unit) = object : AnimationSuccessListener() {
        override fun onAnimationSuccess(animator: Animator) = onSuccess()
    }

    private companion object {
        /** LauncherState.NORMAL is the base state (StateManager.mBaseState). */
        val BASE_STATE = LauncherState.NORMAL
    }
}
