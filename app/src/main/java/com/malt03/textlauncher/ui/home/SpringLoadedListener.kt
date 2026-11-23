package com.malt03.textlauncher.ui.home

import android.animation.ValueAnimator
import android.util.FloatProperty
import com.android.app.animation.Interpolators
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_WORKSPACE_STATE
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.R
import com.android.launcher3.Workspace
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions

/**
 * Launcher3's SPRING_LOADED state while dragging, as docs/spec.md 5.4's edit mode: the keyboard
 * goes down, and the home screen shrinks below the drop target bar
 * (SpringLoadedState.getWorkspaceScaleAndTranslation) with its page background (CellLayout's
 * spring-loaded progress) over the darkened wallpaper (getWorkspaceBackgroundAlpha), animated over
 * the state's 150ms as WorkspaceStateTransitionAnimation does.
 *
 * TextLauncher: the page is the whole home grid, which the content area shows in full while
 * editing (the keyboard is down). As in Launcher3, an open folder stays open over it (a drag from
 * the folder reorders it until the item leaves it).
 */
internal class SpringLoadedListener(private val home: HomeController) : DragController.DragListener {
    private var editing = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val workspace get() = home.workspace
    private val res = home.context.resources

    /** The success callbacks of the transition back to NORMAL (StateManager.goToState). */
    private val normalCallbacks = mutableListOf<Runnable>()

    init {
        workspace.background = home.grid.cellLayout.pageBackground
        // The layout changes while the keyboard moves; the scale follows it.
        workspace.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (progress > 0f) apply() }
    }

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        editing = true
        // The transition back to NORMAL did not finish, so its callbacks do not run.
        normalCallbacks.clear()
        // docs/spec.md 5.4: the home screen takes its keyboard-down size at once.
        home.root.isEditLayout = true
        home.root.animateWallpaperScrim(true)
        home.host.onEditModeChanged(true)
        animateTo(1f) { if (home.dragController.isDragging) home.dragController.forceTouchMove() }
    }

    override fun onDragEnd() {
        if (!editing) return
        editing = false
        home.root.animateWallpaperScrim(false)
        // A widget dropped on the home screen shows its resize frame once the home screen is back
        // (Workspace.getWidgetResizeFrameRunnable), which keeps edit mode (docs/spec.md 5.4); the
        // keyboard does not come back in between.
        val frameFollows = normalCallbacks.isNotEmpty()
        animateTo(0f) {
            if (!editing) {
                runNormalCallbacks()
                if (!home.isWidgetResizeFrameOpen) {
                    home.root.isEditLayout = false
                    if (frameFollows) home.host.onEditModeChanged(false)
                }
            }
        }
        if (!frameFollows) home.host.onEditModeChanged(false)
    }

    /**
     * Runs [callback] once the home screen is in the NORMAL state: now, or when the transition
     * from SPRING_LOADED ends (StateManager.goToState with a success callback).
     */
    fun runWhenNormal(callback: Runnable) {
        normalCallbacks += callback
        if (!editing && animator?.isRunning != true) runNormalCallbacks()
    }

    private fun runNormalCallbacks() {
        val callbacks = normalCallbacks.toList()
        normalCallbacks.clear()
        callbacks.forEach { it.run() }
    }

    private fun animateTo(target: Float, onEnd: () -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = TRANSITION_DURATION_MS
            interpolator = Interpolators.LINEAR
            addUpdateListener {
                progress = it.animatedValue as Float
                apply()
            }
            doOnEnd { onEnd() }
            start()
        }
    }

    private inline fun ValueAnimator.doOnEnd(crossinline action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
        })
    }

    /**
     * WorkspaceStateTransitionAnimation.setWorkspaceProperty and applyChildState for [progress]
     * between NORMAL (0) and SPRING_LOADED (1): the scale and translation with ZOOM_OUT, the page
     * background with ZOOM_OUT.
     */
    private fun apply() {
        val zoom = Interpolators.ZOOM_OUT.getInterpolation(progress)
        val (scale, translationY) = springLoadedScaleAndTranslation()
        WORKSPACE_SCALE_PROPERTY.set(workspace, 1f + (scale - 1f) * zoom)
        workspace.translationY = translationY * zoom
        home.grid.cellLayout.setSpringLoadedProgress(zoom)
        // SpringLoadedState.getDepth: the wallpaper's depth (blur), LINEAR.
        home.root.springLoadedDepth = DEPTH_15_PERCENT * progress
    }

    /**
     * SpringLoadedState.getWorkspaceScaleAndTranslation (centerSpringLoadedStateVertically is not
     * set in the release) with DeviceProfile.getWorkspaceSpringLoadScale: the page shrinks to fit
     * between the drop target bar and the hotseat (the search field), at most so that 48dp of the
     * next pages would show on each side, and its top goes below the drop target bar.
     */
    private fun springLoadedScaleAndTranslation(): Pair<Float, Float> {
        val root = home.root
        val cellLayoutHeight = workspace.height.toFloat()
        if (cellLayoutHeight <= 0f) return 1f to 0f
        // getCellLayoutSpringLoadShrunkTop: the drop target bar with its margins, in root coordinates.
        val shrunkTop = root.statusBarTop + root.dropTargetBarShrunkHeight.toFloat()
        val scale = workspaceSpringLoadScale(cellLayoutHeight)

        val halfHeight = cellLayoutHeight / 2
        val myCenter = root.contentArea.top + workspace.top + halfHeight
        // The page is the workspace itself, so its top is the workspace's.
        val cellTopFromCenter = halfHeight
        val actualCellTop = myCenter - cellTopFromCenter * scale
        return scale to (shrunkTop - actualCellTop)
    }

    /**
     * DeviceProfile.getWorkspaceSpringLoadScale, which the widget drag scale uses
     * (WidgetDragScaleUtils). The page's height is the home screen's with the keyboard down
     * (getCellLayoutHeight), which it takes while editing (docs/spec.md 5.4); a drag works it out
     * before the layout changes.
     */
    fun workspaceSpringLoadScale(): Float {
        val root = home.root
        val cellLayoutHeight = root.keyboardHiddenBarTop - root.statusBarTop - workspace.top
        if (cellLayoutHeight <= 0f) return 1f
        return workspaceSpringLoadScale(cellLayoutHeight)
    }

    /** DeviceProfile.getWorkspaceSpringLoadScale for a page [cellLayoutHeight] high. */
    private fun workspaceSpringLoadScale(cellLayoutHeight: Float): Float {
        val root = home.root
        // getCellLayoutSpringLoadShrunkTop: the drop target bar with its margins, in root coordinates.
        val shrunkTop = root.statusBarTop + root.dropTargetBarShrunkHeight.toFloat()
        // getCellLayoutSpringLoadShrunkBottom: above the hotseat and its spring-loaded top margin.
        val shrunkBottom = root.keyboardHiddenBarTop - res.getDimensionPixelSize(R.dimen.spring_loaded_hotseat_top_margin)
        var scale = ((shrunkBottom - shrunkTop) / cellLayoutHeight).coerceAtMost(1f)
        // Reduce scale if next pages would not be visible after scaling the workspace.
        val workspaceWidth = workspace.width.toFloat()
        val scaledWorkspaceWidth = workspaceWidth * scale
        val maxAvailableWidth = workspaceWidth -
            2 * res.getDimensionPixelSize(R.dimen.dynamic_grid_spring_loaded_min_next_space_visible)
        if (scaledWorkspaceWidth > maxAvailableWidth) scale *= maxAvailableWidth / scaledWorkspaceWidth
        return scale
    }

    private companion object {
        /** SpringLoadedState.DEPTH_15_PERCENT */
        const val DEPTH_15_PERCENT = 0.15f

        /** SpringLoadedState.getTransitionDuration */
        const val TRANSITION_DURATION_MS = 150L

        /** WorkspaceStateTransitionAnimation.WORKSPACE_SCALE_PROPERTY */
        val WORKSPACE_SCALE_PROPERTY: FloatProperty<Workspace<*>> =
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_WORKSPACE_STATE)
    }
}
