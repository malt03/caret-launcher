package com.malt03.textlauncher.ui.home

import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions

/**
 * Launcher3's SPRING_LOADED state while dragging, as docs/spec.md 5.4's edit mode: folders close,
 * the keyboard goes down, and the grid makes room for the drop targets and for drops below its
 * last item.
 */
internal class SpringLoadedListener(private val home: HomeController) : DragController.DragListener {
    private var editing = false

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        editing = true
        AbstractFloatingView.closeOpenViews(home.activityContext, true, AbstractFloatingView.TYPE_FOLDER)
        home.root.animateWallpaperScrim(true)
        home.host.onEditModeChanged(true)
        home.grid.headroomRows = dragObject.dragInfo.spanY + 1
        home.scrollView.animate().translationY(home.root.dropTargetBarHeight.toFloat()).setDuration(EDIT_ANIMATION_MS)
            .withEndAction { if (home.dragController.isDragging) home.dragController.forceTouchMove() }.start()
    }

    override fun onDragEnd() {
        if (!editing) return
        editing = false
        home.root.animateWallpaperScrim(false)
        home.grid.headroomRows = 0
        home.scrollView.animate().translationY(0f).setDuration(EDIT_ANIMATION_MS).start()
        home.host.onEditModeChanged(false)
    }

    private companion object {
        const val EDIT_ANIMATION_MS = 150L
    }
}
