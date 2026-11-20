package com.malt03.textlauncher.ui.drag

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.google.android.material.color.MaterialColors
import com.malt03.textlauncher.R
import com.malt03.textlauncher.ui.home.AppItemInfo

/**
 * The "Remove" and "Uninstall" targets at the top while dragging (docs/spec.md 5.3), following
 * Launcher3's DropTargetBar, ButtonDropTarget, DeleteDropTarget and SecondaryDropTarget
 * (android-17.0.0_r1). The buttons are text only, so the drop animation ends at the button's
 * center instead of its icon.
 */
@SuppressLint("ViewConstructor")
class DropTargetBar(
    context: Context,
    private val activity: () -> ActivityContext,
    private val dragController: () -> DragController,
    /** Which items the Remove target removes (DeleteDropTarget.canRemove). */
    private val canRemove: (DragObject) -> Boolean,
) : LinearLayout(context), DragController.DragListener {
    val remove = DeleteDropTarget()
    val uninstall = UninstallDropTarget()
    private var deferOnDragEnd = false
    private var visible = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(remove, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(8) })
        addView(uninstall, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    /** Registers the buttons with the drag controller (Launcher.setupViews). */
    fun setup(controller: DragController) {
        controller.addDragListener(this)
        for (target in listOf(remove, uninstall)) {
            controller.addDragListener(target)
            controller.addDropTarget(target)
        }
    }

    /** DropTargetBar.onDragStart */
    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        animateToVisibility(true)
    }

    /** DropTargetBar.deferOnDragEnd: stay visible until the drop animation into a button ends. */
    fun deferOnDragEnd() {
        deferOnDragEnd = true
    }

    /** DropTargetBar.onDragEnd */
    override fun onDragEnd() {
        if (!deferOnDragEnd) {
            animateToVisibility(false)
        } else {
            deferOnDragEnd = false
        }
    }

    /** DropTargetBar.animateToVisibility */
    private fun animateToVisibility(isVisible: Boolean) {
        if (visible != isVisible) {
            visible = isVisible

            // Cancel any existing animation
            animate().cancel()

            val finalAlpha = if (visible) 1f else 0f
            if (alpha.compareTo(finalAlpha) != 0) {
                visibility = View.VISIBLE
                animate().alpha(finalAlpha)
                    .setInterpolator(Interpolators.ACCELERATE)
                    .setDuration(DEFAULT_DRAG_FADE_DURATION)
                    .withEndAction { updateVisibility() }
            }
        }
    }

    /** AlphaUpdateListener.updateVisibility */
    private fun updateVisibility() {
        visibility = if (alpha > 0f) View.VISIBLE else View.GONE
    }

    init {
        alpha = 0f
    }

    /** ButtonDropTarget */
    abstract inner class ButtonDropTarget : TextView(context), DropTarget, DragController.DragListener {
        private val normalColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        private val hoverColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer)
        private val dragDistanceThreshold = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drag_distanceThreshold)
        private var active = false
        private val tempCords = IntArray(2)

        init {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(normalColor)
            }
        }

        override fun setSelected(selected: Boolean) {
            super.setSelected(selected)
            (background as? GradientDrawable)?.setColor(if (selected) hoverColor else normalColor)
        }

        protected abstract fun supportsDrop(info: DragObject): Boolean
        protected abstract fun setupItemInfo(d: DragObject)
        abstract fun completeDrop(d: DragObject)

        override fun onDragEnter(d: DragObject) {
            // TextLauncher: stands in for MSDL's SWIPE_THRESHOLD_INDICATOR until msdllib is added.
            performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            d.dragView.alpha = DRAG_VIEW_HOVER_OVER_OPACITY
            isSelected = true
        }

        override fun onDragOver(d: DragObject) {
            // Do nothing
        }

        override fun onDragExit(d: DragObject) {
            if (!d.dragComplete) {
                d.dragView.alpha = 1f
                isSelected = false
            } else {
                d.dragView.alpha = DRAG_VIEW_HOVER_OVER_OPACITY
            }
        }

        override fun onDragStart(dragObject: DragObject, options: DragOptions) {
            if (options.isKeyboardDrag || options.isMouseDrag) {
                active = false
            } else {
                setupItemInfo(dragObject)
                active = supportsDrop(dragObject)
            }
            visibility = if (active) View.VISIBLE else View.GONE
        }

        override fun acceptDrop(dragObject: DragObject): Boolean = supportsDrop(dragObject)

        override fun isDropEnabled(): Boolean =
            active && dragController().distanceDragged >= dragDistanceThreshold

        override fun onDragEnd() {
            active = false
            isSelected = false
        }

        /** On drop animate the dropView to the icon. */
        override fun onDrop(d: DragObject, options: DragOptions) {
            val dragLayer = activity().dragLayer
            val dragView = d.dragView
            val to = getIconRect(d)
            val scale = to.width().toFloat() / dragView.measuredWidth
            dragView.detachContentView(/* reattachToPreviousParent= */ true)

            deferOnDragEnd()

            val onAnimationEndRunnable = Runnable {
                completeDrop(d)
                this@DropTargetBar.onDragEnd()
            }

            dragLayer.animateView(
                d.dragView, to, scale, 0.1f, 0.1f,
                DRAG_VIEW_DROP_DURATION,
                Interpolators.DECELERATE_2, onAnimationEndRunnable,
                BaseDragLayer.ANIMATION_END_DISAPPEAR, null,
            )
        }

        override fun prepareAccessibilityDrop() = Unit

        override fun getHitRectRelativeToDragLayer(outRect: Rect) {
            super.getHitRect(outRect)
            outRect.bottom += resources.getDimensionPixelSize(R.dimen.drop_target_drag_padding)

            tempCords[0] = 0
            tempCords[1] = 0
            activity().dragLayer.getDescendantCoordRelativeToSelf(this, tempCords)
            outRect.offsetTo(tempCords[0], tempCords[1])
        }

        /**
         * ButtonDropTarget.getIconRect. TextLauncher: the button has no icon, so the rect is a
         * drop_target_button_drawable_size square at the button's center.
         */
        private fun getIconRect(dragObject: DragObject): Rect {
            val viewWidth = dragObject.dragView.measuredWidth
            val viewHeight = dragObject.dragView.measuredHeight
            val size = resources.getDimensionPixelSize(R.dimen.drop_target_button_drawable_size)
            val dragLayer = activity().dragLayer

            // Find the rect to animate to (the view is center aligned)
            val to = Rect()
            dragLayer.getViewRectRelativeToSelf(this, to)
            val left = to.centerX() - size / 2
            val top = to.top + (measuredHeight - size) / 2
            to.set(left, top, left + size, top + size)

            // Center the destination rect about the trash icon
            val xOffset = -(viewWidth - size) / 2
            val yOffset = -(viewHeight - size) / 2
            to.offset(xOffset, yOffset)
            return to
        }
    }

    /** DeleteDropTarget: removes the item from home; for other items it only cancels the drag. */
    inner class DeleteDropTarget : ButtonDropTarget() {
        override fun setupItemInfo(d: DragObject) {
            setText(if (canRemove(d)) R.string.drop_target_remove else android.R.string.cancel)
        }

        override fun supportsDrop(info: DragObject): Boolean = true

        /**
         * DeleteDropTarget.completeDrop removes the item from the model. TextLauncher: the drag
         * source does that in onDropCompleted, since it owns the home model.
         */
        override fun completeDrop(d: DragObject) = Unit
    }

    /** SecondaryDropTarget with the UNINSTALL action. */
    inner class UninstallDropTarget : ButtonDropTarget() {
        private var deferred: DeferredOnComplete? = null

        init {
            setText(R.string.drop_target_uninstall)
        }

        override fun setupItemInfo(d: DragObject) = Unit

        /** SecondaryDropTarget.supportsDrop for UNINSTALL: apps that are not system apps. */
        override fun supportsDrop(info: DragObject): Boolean {
            val app = info.dragInfo as? AppItemInfo ?: return false
            return app.entry.info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
        }

        override fun onDrop(d: DragObject, options: DragOptions) {
            // Defer onComplete
            d.dragSource = DeferredOnComplete(d.dragSource).also { deferred = it }
            super.onDrop(d, options)
        }

        override fun completeDrop(d: DragObject) {
            val target = performDropAction(d.dragInfo)
            // DropTargetHandler.onSecondaryTargetCompleteDrop
            val source = d.dragSource as? DeferredOnComplete ?: return
            if (target != null) {
                source.packageName = target.packageName
            } else {
                source.sendFailure()
                deferred = null
            }
        }

        /** SecondaryDropTarget.performDropAction for UNINSTALL. */
        private fun performDropAction(info: ItemInfo): ComponentName? {
            val app = info as? AppItemInfo ?: return null
            val component = app.entry.component
            return try {
                val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", component.packageName, component.className))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra(Intent.EXTRA_USER, app.entry.user)
                context.startActivity(intent)
                component
            } catch (e: Exception) {
                null
            }
        }

        /** Launcher.addOnResumeCallback(DeferredOnComplete::onLauncherResume) */
        fun onLauncherResume() {
            val pending = deferred ?: return
            if (pending.packageName == null) return
            deferred = null
            pending.onLauncherResume()
        }

        /**
         * A wrapper around [DragSource] which delays the onDropCompleted action until
         * [onLauncherResume].
         */
        inner class DeferredOnComplete(private val original: DragSource) : DragSource {
            var packageName: String? = null
            private var dragObject: DragObject? = null

            override fun onDropCompleted(target: View?, d: DragObject, success: Boolean) {
                dragObject = d
            }

            fun onLauncherResume() {
                val d = dragObject ?: return
                val app = d.dragInfo as? AppItemInfo
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                val installed = app != null && runCatching {
                    launcherApps.getApplicationInfo(app.entry.component.packageName, 0, app.entry.user)
                }.isSuccess
                if (!installed) {
                    d.dragSource = original
                    original.onDropCompleted(this@UninstallDropTarget, d, true)
                } else {
                    sendFailure()
                }
            }

            fun sendFailure() {
                val d = dragObject ?: return
                d.dragSource = original
                d.cancelled = true
                original.onDropCompleted(this@UninstallDropTarget, d, false)
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** ButtonDropTarget */
        const val DRAG_VIEW_DROP_DURATION = 285
        const val DRAG_VIEW_HOVER_OVER_OPACITY = 0.65f

        /** DropTargetBar.DEFAULT_DRAG_FADE_DURATION */
        const val DEFAULT_DRAG_FADE_DURATION = 175L
    }
}
