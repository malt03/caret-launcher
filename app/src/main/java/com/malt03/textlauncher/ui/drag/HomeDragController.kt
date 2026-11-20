package com.malt03.textlauncher.ui.drag

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import com.android.launcher3.DropTarget
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.views.ActivityContext

/**
 * Launcher3's LauncherDragController (android-17.0.0_r1) on top of the copied DragController:
 * the long-press haptic when the drag view appears, and the home grid as the default drop target.
 *
 * Not ported: fling to delete (FlingToDeleteHelper) and widget drag scaling, since there are no
 * widgets yet.
 */
class HomeDragController(
    private val activity: ActivityContext,
    /** Launcher.isTouchInProgress */
    private val isTouchInProgress: () -> Boolean,
) : DragController(activity) {
    /** Launcher.getWorkspace(): the target for drops that no registered drop target takes. */
    lateinit var defaultDropTarget: DropTarget

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onDragViewInitialized() {
        activity.dragLayer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (!isItemPinnable() || (!mIsInPreDrag && !isTouchInProgress() &&
                mOptions.simulatedDndStartPoint == null)
        ) {
            // If it is an internal drag and the touch is already complete, cancel immediately
            mainHandler.post(::cancelDrag)
        }
    }

    override fun getDefaultDropTarget(dropCoordinates: IntArray): DropTarget {
        activity.dragLayer.mapCoordInSelfToDescendant(defaultDropTarget.dropView, dropCoordinates)
        return defaultDropTarget
    }
}
