package com.malt03.textlauncher.ui.drag

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import com.android.launcher3.DropTarget
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.util.WidgetDragScaleUtils

/**
 * Launcher3's LauncherDragController (android-17.0.0_r1) on top of the copied DragController:
 * the drag view's scale (a widget shrinks to its spring-loaded size), the long-press haptic when
 * the drag view appears, and the home grid as the default drop target.
 *
 * Not ported: fling to delete (FlingToDeleteHelper), and LauncherDragView, which hides the drag
 * view in other launcher states (TextLauncher has none while dragging) and drops it with
 * DragLayer.animateViewIntoPosition.
 */
class HomeDragController(
    private val activity: ActivityContext,
    /** Launcher.isTouchInProgress */
    private val isTouchInProgress: () -> Boolean,
) : DragController(activity) {
    /** Launcher.getWorkspace(): the target for drops that no registered drop target takes. */
    lateinit var defaultDropTarget: DropTarget

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun createDragView(
        drawable: Drawable?,
        view: View?,
        originalView: DraggableView,
        dragInfo: ItemInfo,
        dragLayerX: Int,
        dragLayerY: Int,
        dragRegion: Rect,
        initialDragViewScale: Float,
        dragViewScaleOnDrop: Float,
        allowSpringDrawable: Boolean,
    ): DragView {
        val registrationX = mMotionDown.x - dragLayerX
        val registrationY = mMotionDown.y - dragLayerY

        val res = activity.asContext().resources
        val scalePx = if (originalView.viewType == DraggableView.DRAGGABLE_WIDGET) {
            if (mIsInPreDrag) 0f else getWidgetDragScalePx(drawable, view, dragInfo)
        } else {
            if (mIsInPreDrag) res.getDimensionPixelSize(R.dimen.pre_drag_view_scale).toFloat() else 0f
        }
        // TextLauncher: DragView in place of LauncherDragView.
        return if (drawable != null) {
            DragView(
                activity,
                drawable,
                registrationX,
                registrationY,
                initialDragViewScale,
                dragViewScaleOnDrop,
                scalePx,
                allowSpringDrawable,
            )
        } else {
            DragView(
                activity,
                view,
                view!!.measuredWidth,
                view.measuredHeight,
                registrationX,
                registrationY,
                initialDragViewScale,
                dragViewScaleOnDrop,
                scalePx,
                allowSpringDrawable,
            )
        }
    }

    override fun onDragViewInitialized() {
        activity.dragLayer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (!isItemPinnable() || (!mIsInPreDrag && !isTouchInProgress() &&
                mOptions.simulatedDndStartPoint == null)
        ) {
            // If it is an internal drag and the touch is already complete, cancel immediately
            mainHandler.post(::cancelDrag)
        }
    }

    /**
     * Returns the scale in terms of pixels (to be applied on width) to scale the preview
     * during drag and drop.
     */
    fun getWidgetDragScalePx(drawable: Drawable?, view: View?, dragInfo: ItemInfo): Float {
        var draggedViewWidthPx = 0f
        var draggedViewHeightPx = 0f

        if (view != null) {
            draggedViewWidthPx = view.measuredWidth.toFloat()
            draggedViewHeightPx = view.measuredHeight.toFloat()
        } else if (drawable != null) {
            draggedViewWidthPx = drawable.intrinsicWidth.toFloat()
            draggedViewHeightPx = drawable.intrinsicHeight.toFloat()
        }

        return WidgetDragScaleUtils.getWidgetDragScalePx(
            activity.asContext(), activity.deviceProfile,
            draggedViewWidthPx, draggedViewHeightPx, dragInfo,
        )
    }

    override fun getDefaultDropTarget(dropCoordinates: IntArray): DropTarget {
        activity.dragLayer.mapCoordInSelfToDescendant(defaultDropTarget.dropView, dropCoordinates)
        return defaultDropTarget
    }
}
