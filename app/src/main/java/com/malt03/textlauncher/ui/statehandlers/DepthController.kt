package com.malt03.textlauncher.ui.statehandlers

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.util.FloatProperty
import android.view.View
import android.view.WindowManager
import com.android.app.animation.Interpolators
import com.android.launcher3.R
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH
import com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherState.SPRING_LOADED
import com.android.launcher3.statemanager.StateManager
import java.util.function.Consumer

/**
 * DepthController with BaseDepthControllerImpl (android-17.0.0_r1): the depth of the state the
 * launcher is in or going to, which blurs what is behind the window (the wallpaper).
 *
 * TextLauncher: Launcher3 sets the background blur radius on its surface. An app sets it on its
 * window ([Window.setBackgroundBlurRadius], drawn with each frame; the window is translucent for
 * this), and the home screen, which is in this window, is blurred with a [RenderEffect] on
 * [workspaceContent] with the all apps sheet's depth (AllAppsState.shouldBlurWorkspace). There is
 * no wallpaper zoom (WallpaperManager.setWallpaperZoomOut needs the system).
 */
class DepthController(
    private val activity: Activity,
    private val rootView: View,
    private val workspaceContent: View,
) : StateManager.StateHandler<LauncherState> {
    /** Property to set the depth for state transition. */
    val stateDepth: MultiPropertyFactory<DepthController>.MultiProperty

    /**
     * TextLauncher: the spring-loaded state's depth, which SpringLoadedListener animates on its own
     * (SpringLoadedState.getDepth); it is combined with [stateDepth] with max, as Launcher3 combines
     * the state's and the widget picker's depths.
     */
    val springLoadedDepth: MultiPropertyFactory<DepthController>.MultiProperty

    /** Blur radius when completely zoomed out, in pixels. */
    private val maxBlurRadius = activity.resources.getDimensionPixelSize(R.dimen.max_depth_blur_radius_enhanced)
    private var crossWindowBlursEnabled = false

    /** Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in. */
    private var depth = 0f

    /** Last blur value, in pixels, that was applied. */
    private var currentBlur = -1

    /** TextLauncher: the last blur applied to [workspaceContent]. */
    private var currentWorkspaceBlur = -1f

    /** TextLauncher: [WindowBlurState] listeners, such as the all apps sheet's color. */
    private val blurStateListeners = mutableListOf<(Boolean) -> Unit>()

    /** WindowBlurState: whether windows can be blurred now (CrossWindowBlurListeners). */
    private val blurListener = Consumer<Boolean> { blurEnabled ->
        if (crossWindowBlursEnabled != blurEnabled) {
            crossWindowBlursEnabled = blurEnabled
            applyDepthAndBlur()
        }
        blurStateListeners.forEach { it(blurEnabled) }
    }

    init {
        val depthProperty = MultiPropertyFactory(this, DEPTH, DEPTH_INDEX_COUNT) { a, b -> maxOf(a, b) }
        stateDepth = depthProperty.get(DEPTH_INDEX_STATE_TRANSITION)
        springLoadedDepth = depthProperty.get(DEPTH_INDEX_SPRING_LOADED)
        // DepthController.ensureDependencies, with the blur state's listener.
        rootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                activity.getSystemService(WindowManager::class.java)
                    .addCrossWindowBlurEnabledListener(activity.mainExecutor, blurListener)
                applyDepthAndBlur()
            }

            override fun onViewDetachedFromWindow(view: View) {
                activity.getSystemService(WindowManager::class.java)
                    .removeCrossWindowBlurEnabledListener(blurListener)
            }
        })
    }

    /**
     * LauncherState.getDepth. TextLauncher: SPRING_LOADED's (SpringLoadedState.DEPTH_15_PERCENT) is
     * SpringLoadedListener's ([springLoadedDepth]), which animates it with the drag.
     */
    private fun depth(state: LauncherState): Float =
        if (state == SPRING_LOADED) 0f else state.getDepth(Launcher.getLauncher(activity))

    override fun setState(state: LauncherState) {
        stateDepth.setValue(depth(state))
    }

    override fun setStateWithAnimation(toState: LauncherState, config: StateAnimationConfig, animation: PendingAnimation) {
        if (config.hasAnimationFlag(SKIP_DEPTH_CONTROLLER)) {
            return
        }

        val toDepth = depth(toState)
        animation.setFloat(stateDepth, MULTI_PROPERTY_VALUE, toDepth, config.getInterpolator(ANIM_DEPTH, Interpolators.LINEAR))
    }

    /** TextLauncher: called with whether windows can be blurred, as it changes. */
    fun addBlurStateListener(listener: (Boolean) -> Unit) {
        blurStateListeners += listener
    }

    private fun shouldBlur(): Boolean = crossWindowBlursEnabled

    private fun applyDepthAndBlur() {
        // Only use depth for blur, not wallpaper zoom amount in case only zoom is applied.
        val blurAmount = mapDepthToBlur(depth)
        val newBlur = if (shouldBlur()) (blurAmount * maxBlurRadius).toInt() else 0
        if (newBlur != currentBlur) {
            currentBlur = newBlur
            activity.window.setBackgroundBlurRadius(currentBlur)
        }
        // TextLauncher: the home screen in this window, with the all apps sheet's depth only.
        val workspaceBlur = if (shouldBlur()) mapDepthToBlur(stateDepth.value) * maxBlurRadius else 0f
        if (workspaceBlur != currentWorkspaceBlur) {
            currentWorkspaceBlur = workspaceBlur
            workspaceContent.setRenderEffect(
                if (workspaceBlur > 0f) {
                    RenderEffect.createBlurEffect(workspaceBlur, workspaceBlur, Shader.TileMode.CLAMP)
                } else {
                    null
                },
            )
        }
    }

    /**
     * TextLauncher: no rounding (Launcher3 rounds to 1/256 outside the target state's depth), and
     * it applies even when the depth stays: the workspace blur follows [stateDepth], which can change
     * under the max.
     */
    private fun setDepth(depth: Float) {
        this.depth = depth.coerceIn(0f, 1f)
        applyDepthAndBlur()
    }

    private companion object {
        // Includes both Wallpaper Zoom and Blur
        val DEPTH = object : FloatProperty<DepthController>("depth") {
            override fun setValue(depthController: DepthController, depth: Float) =
                depthController.setDepth(depth)

            override fun get(depthController: DepthController): Float = depthController.depth
        }

        const val DEPTH_INDEX_STATE_TRANSITION = 0

        /** TextLauncher: in place of DEPTH_INDEX_WIDGET. */
        const val DEPTH_INDEX_SPRING_LOADED = 1
        const val DEPTH_INDEX_COUNT = 2

        /**
         * Maps depth values to blur amounts as a percentage of the max blur.
         * The blur percentage grows linearly with depth, and maxes out at 30% depth.
         */
        fun mapDepthToBlur(depth: Float): Float = Interpolators.clampToProgress(Interpolators.LINEAR, depth, 0f, 0.3f)
    }
}
