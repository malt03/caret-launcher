package com.malt03.textlauncher.ui.allapps

import android.animation.Animator
import android.animation.ObjectAnimator
import android.util.FloatProperty
import android.view.HapticFeedbackConstants
import android.view.View
import com.android.app.animation.Interpolators
import com.android.app.animation.Interpolators.DECELERATE_1_7
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.Flags
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_WORKSPACE_STATE
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.Workspace
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS
import com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.malt03.textlauncher.ui.LauncherRootLayout
import com.malt03.textlauncher.ui.LauncherState
import com.malt03.textlauncher.ui.statehandlers.DepthController
import com.malt03.textlauncher.ui.statemanager.StateManager

/**
 * AllAppsTransitionController (android-17.0.0_r1): the all apps list's vertical progress, which
 * moves the search field with the list on its sheet ([setProgress]), as a state handler.
 *
 * TextLauncher: the progress is the other way round from Launcher3's (0 is closed and 1 open), and
 * the field comes up with the list: the sheet is always right under the field, so it comes out
 * from behind the keyboard as the field goes up (docs/spec.md 6.4). The field's closed position
 * follows the keyboard, so [LauncherRootLayout] has it placed again as the keyboard moves
 * ([applyProgress]). The home screen's scale for ALL_APPS (WorkspaceStateTransitionAnimation's
 * part) is here too.
 */
class AllAppsTransitionController(
    private val root: LauncherRootLayout,
    private val stateManager: StateManager,
    private val depthController: DepthController,
    private val workspace: Workspace<*>,
) : StateManager.StateHandler {

    // When {@link mProgress} is 0, all apps container is pulled down.
    // When {@link mProgress} is 1, all apps container is pulled up.
    private var progress = 0f // [0, 1]

    /** TextLauncher: the scale of the workspace's state (SCALE_INDEX_WORKSPACE_STATE) set here. */
    private var workspaceScale = 1f
    private val context = root.context

    init {
        root.onAllAppsLayoutChanged = ::applyProgress
    }

    /**
     * Note this method should not be called outside this class and the all apps swipe.
     *
     * @param progress value between 0 and 1, 0 shows workspace and 1 shows all apps
     */
    fun setProgress(progress: Float) {
        if (this.progress.compareTo(progress) == 0) {
            return
        }
        this.progress = progress
        applyProgress()
    }

    fun getProgress(): Float = progress

    /** TextLauncher: true while the all apps list is shown, open or on its way (docs/spec.md 6.4). */
    val isAllAppsShown: Boolean get() = progress > 0f

    /**
     * TextLauncher: places the field and the sheet for [progress]. The field goes from
     * [LauncherRootLayout.closedBarTop] to [LauncherRootLayout.openBarTop] (the keyboard goes down),
     * and the sheet follows right under it; it is laid out open, where the field is at the top of
     * the screen.
     */
    private fun applyProgress() {
        val shown = progress > 0f
        root.isAllAppsShown = shown
        val barTop = root.closedBarTop + (root.openBarTop - root.closedBarTop) * progress
        root.searchBarArea.translationY = barTop - root.searchBarArea.top
        root.allAppsSheet.visibility = if (shown) View.VISIBLE else View.GONE
        root.allAppsSheet.translationY = barTop - root.openBarTop
    }

    /**
     * TextLauncher: a swipe moves the states' properties itself (there is no
     * AnimatorPlaybackController), as the user-controlled state animation would at the fraction
     * [progress]: the vertical progress LINEAR, and the home screen's scale and the depth with
     * ALL_APPS_SHEET_DEPTH (AllAppsSwipeController.applyNormalToAllAppsAnimConfig, and its reverse
     * from ALL_APPS).
     */
    fun setUserControlledProgress(progress: Float) {
        setProgress(progress)
        val sheetDepth = AllAppsSwipeController.ALL_APPS_SHEET_DEPTH.getInterpolation(progress)
        val allAppsScale = LauncherState.ALL_APPS.getWorkspaceScale(context)
        setWorkspaceScale(1f + (allAppsScale - 1f) * sheetDepth)
        depthController.stateDepth.setValue(LauncherState.ALL_APPS.getDepth(context) * sheetDepth)
    }

    private fun setWorkspaceScale(scale: Float) {
        workspaceScale = scale
        WORKSPACE_SCALE_PROPERTY.set(workspace, scale)
    }

    /**
     * Sets the vertical transition progress to [state] and updates all the dependent UI
     * accordingly.
     */
    override fun setState(state: LauncherState) {
        setProgress(state.getVerticalProgress())
        // WorkspaceStateTransitionAnimation.setWorkspaceProperty. TextLauncher: only when it
        // changes, as SpringLoadedListener uses the same index.
        val scale = state.getWorkspaceScale(context)
        if (workspaceScale != scale) setWorkspaceScale(scale)
    }

    /**
     * Creates an animation which updates the vertical transition progress and updates all the
     * dependent UI using various animation events
     *
     * This method also dictates where along the progress the haptics should be played.
     */
    override fun setStateWithAnimation(toState: LauncherState, config: StateAnimationConfig, builder: PendingAnimation) {
        // WorkspaceStateTransitionAnimation.setWorkspaceProperty: the workspace's scale.
        builder.setFloat(
            this, WORKSPACE_STATE_SCALE, toState.getWorkspaceScale(context),
            config.getInterpolator(ANIM_WORKSPACE_SCALE, Interpolators.ZOOM_OUT),
        )

        val targetProgress = toState.getVerticalProgress()
        if (progress.compareTo(targetProgress) == 0) {
            // Fail fast
            return
        }

        // need to decide depending on the release velocity
        val verticalProgressInterpolator = config.getInterpolator(
            ANIM_VERTICAL_PROGRESS,
            if (config.isUserControlled) LINEAR else DECELERATE_1_7,
        )
        val anim = createSpringAnimation(progress, targetProgress)
        // TextLauncher: no cancel listener that sets the target progress; a swipe that catches the
        // animation takes the list from where it is.
        anim.interpolator = verticalProgressInterpolator
        builder.add(anim)

        // This controls both haptics for tapping on QSB and going to all apps.
        if (toState == LauncherState.ALL_APPS && stateManager.isInState(LauncherState.NORMAL)) {
            performStateHaptic(config.isUserControlled)
        }
    }

    fun createSpringAnimation(vararg progressValues: Float): Animator =
        ObjectAnimator.ofFloat(this, ALL_APPS_PROGRESS, *progressValues)

    /**
     * AllAppsTransitionController.setStateWithAnimation's haptic: SWIPE_THRESHOLD_INDICATOR for a
     * swipe ([userControlled]) and TAP_HIGH_EMPHASIS without one.
     *
     * TextLauncher: a swipe plays it when it gets past the hold (docs/spec.md 6.2), and a swipe
     * that closes the list plays it too.
     */
    fun performStateHaptic(userControlled: Boolean) {
        if (Flags.msdlFeedback()) {
            MSDLPlayerWrapper.INSTANCE.get(context).playToken(
                if (userControlled) MSDLToken.SWIPE_THRESHOLD_INDICATOR else MSDLToken.TAP_HIGH_EMPHASIS,
            )
        } else {
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
        }
    }

    private companion object {
        val ALL_APPS_PROGRESS = object : FloatProperty<AllAppsTransitionController>("allAppsProgress") {
            override fun get(controller: AllAppsTransitionController): Float = controller.progress

            override fun setValue(controller: AllAppsTransitionController, progress: Float) =
                controller.setProgress(progress)
        }

        /** WorkspaceStateTransitionAnimation.WORKSPACE_SCALE_PROPERTY */
        val WORKSPACE_SCALE_PROPERTY: FloatProperty<Workspace<*>> =
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_WORKSPACE_STATE)

        /** TextLauncher: [WORKSPACE_SCALE_PROPERTY] through [workspaceScale]. */
        val WORKSPACE_STATE_SCALE = object : FloatProperty<AllAppsTransitionController>("workspaceStateScale") {
            override fun get(controller: AllAppsTransitionController): Float = controller.workspaceScale

            override fun setValue(controller: AllAppsTransitionController, scale: Float) =
                controller.setWorkspaceScale(scale)
        }
    }
}
