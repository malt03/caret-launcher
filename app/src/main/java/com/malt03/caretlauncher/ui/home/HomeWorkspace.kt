package com.malt03.caretlauncher.ui.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Workspace

/**
 * The home screen's Workspace: Launcher3's Workspace (the derived copy in compat) with what
 * CaretLauncher's home screen adds. The workspace holds the one page in its vertical scroll
 * container ([HomeScrollView]); a drag near the top or bottom of the visible part scrolls it.
 *
 * [context] gives the home screen's Launcher (LauncherContextWrapper), which Workspace looks up.
 */
class HomeWorkspace internal constructor(
    context: Context,
    private val home: HomeController,
) : Workspace<Any>(context, null) {

    /**
     * CaretLauncher: the workspace scales about the middle of its keyboard-down height
     * (DeviceProfile.getCellLayoutHeight), which SpringLoadedState's translation assumes, as the
     * page grows to that height while editing starts.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        pivotX = width / 2f
        pivotY = home.launcher.deviceProfile.cellLayoutHeight / 2f
    }

    // ---- auto scroll (CaretLauncher) ----

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private var autoScrollStep = 0
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (autoScrollStep == 0 || !home.dragController.isDragging) return
            home.scrollView.scrollBy(0, autoScrollStep)
            home.dragController.forceTouchMove()
            handler.postDelayed(this, AUTO_SCROLL_INTERVAL_MS)
        }
    }

    override fun onDragOver(d: DragObject) {
        super.onDragOver(d)
        updateAutoScroll(d)
    }

    override fun onDragExit(d: DragObject) {
        super.onDragExit(d)
        setAutoScroll(0)
    }

    override fun onDragEnd() {
        super.onDragEnd()
        setAutoScroll(0)
    }

    /**
     * While a drag is near the top or bottom of the page's visible part, the page scrolls (the
     * drag's coordinates are the workspace's, where the scroll container is).
     */
    private fun updateAutoScroll(d: DragObject) {
        val scrollView = home.scrollView
        val zone = AUTO_SCROLL_ZONE_DP * density
        val step = (AUTO_SCROLL_STEP_DP * density).toInt()
        val visibleY = d.y - scrollView.top
        setAutoScroll(
            when {
                visibleY < zone && scrollView.canScrollVertically(-1) -> -step
                visibleY > scrollView.height - zone && scrollView.canScrollVertically(1) -> step
                else -> 0
            },
        )
    }

    private fun setAutoScroll(step: Int) {
        val wasScrolling = autoScrollStep != 0
        autoScrollStep = step
        if (step == 0) {
            handler.removeCallbacks(autoScrollRunnable)
        } else if (!wasScrolling) {
            handler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val AUTO_SCROLL_ZONE_DP = 56
        const val AUTO_SCROLL_STEP_DP = 12
        const val AUTO_SCROLL_INTERVAL_MS = 16L
    }
}
