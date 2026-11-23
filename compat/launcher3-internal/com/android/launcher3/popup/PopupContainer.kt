/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.popup

import android.animation.AnimatorSet
import android.content.Context
import android.graphics.Rect
import android.os.Trace
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_VIEW
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.views.ActivityContext

/**
 * Base popup container for shortcuts associated with the item {@code originalView}
 *
 * <p>Stand-in for Launcher3's PopupContainer (android-17.0.0_r1), made from PopupContainer.kt by
 * deleting what TextLauncher does not use (the Compose version of the menu, which
 * Flags.expandableLongPressMenu turns on and the release config disables; deep shortcut clicks
 * and the add button, which only the Compose version has; stats logging; dismissInvalidPopup);
 * changes are marked with "TextLauncher:". [PopupData] and [PopupCategory] are from
 * PopupDataMapper.kt, at the end of this file.
 *
 * @param <T> The activity on which the popup shows </T>
 * @param context The context in which the popup is created.
 * @param originalView The view from which this popup was opened.
 */
open class PopupContainer<T : ActivityContext>(
    context: Context?,
    val originalView: View,
    val itemInfo: ItemInfo,
    val updateIconUi: Boolean,
) : ArrowPopup<T>(context), DragSource, DragController.DragListener, Popup {
    /** Here we hold the system shortcuts that we show for the Popup. */
    // TODO b/441320297
    var systemShortcutContainer: ViewGroup? = null

    /** If the distance the user drags surpasses this number, then we should start drag. */
    val startDragThreshold =
        originalView.context.resources.getDimensionPixelSize(
            R.dimen.deep_shortcuts_start_drag_threshold
        )

    private val iconViewController: IconViewController? = originalView as? IconViewController

    private var fixedDragLayerPos: Rect? = null

    fun showForSystemShortcuts(
        systemShortcuts: List<PopupData>,
        activityContext: ActivityContext,
        itemView: View,
    ) {
        // TextLauncher: no Compose version (Flags.expandableLongPressMenu is disabled in the
        // release config), so only upstream's else branch is kept.
        run {
            systemShortcutContainer = inflateAndAdd(R.layout.system_shortcut_rows_container, this)
            systemShortcuts.forEach { systemShortcut ->
                val view: DeepShortcutView =
                    inflateAndAdd(R.layout.system_shortcut, systemShortcutContainer)

                view.iconView.setBackgroundResource(systemShortcut.iconResId)
                view.bubbleText.setText(systemShortcut.labelResId)

                view.tag = systemShortcut
                view.setOnClickListener {
                    // TextLauncher: no stats logging (PopupData has no eventId).
                    systemShortcut.popupAction.invoke(activityContext, itemInfo, itemView)
                }
            }
            show()
        }
    }

    @CallSuper
    override fun handleClose(animate: Boolean) {
        Trace.beginAsyncSection("dismissPopupMenu", hashCode())
        super.handleClose(animate)
    }

    @CallSuper
    override fun closeComplete() {
        super.closeComplete()
        mActivityContext?.dragController?.removeDragListener(this)
        val openPopup = getOpen(mActivityContext)
        if (openPopup == null || openPopup.originalView !== iconViewController) {
            iconViewController?.getFloatingViewTextAlpha()?.value = 1f
            iconViewController?.setForceHideDot(false)
        }
        // TextLauncher: no stats logging.
        Trace.endAsyncSection("dismissPopupMenu", hashCode())
    }

    @CallSuper
    override fun onCreateCloseAnimation(anim: AnimatorSet) {
        // Animate original icon's text back in.
        anim.play(iconViewController?.getFloatingViewTextAlpha()?.animateToValue(1f))
        iconViewController?.setForceHideDot(false)
    }

    override fun getAccessibilityInitialFocusView(): View {
        return systemShortcutContainer?.getChildAt(0) ?: super.getAccessibilityInitialFocusView()
    }

    @CallSuper
    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val dl = popupContainer
            if (!dl.isEventOverView(this, ev)) {
                // TODO: add WW log if want to log if tap closed deep shortcut container.
                close(true)

                // We let touches on the original view go through so that users can launch
                // the item with one tap.
                return !dl.isEventOverView(originalView, ev)
            }
        }
        return false
    }

    @CallSuper
    override fun getTargetObjectLocation(outPos: Rect) {
        fixedDragLayerPos?.let {
            outPos.set(it)
            return
        }
        popupContainer.getDescendantRectRelativeToSelf(originalView, outPos)
        outPos.top += originalView.paddingTop
        outPos.left += originalView.paddingLeft
        outPos.right -= originalView.paddingRight
        val iconHeight = iconViewController?.getIconHeight()
        outPos.bottom =
            outPos.top +
                if (iconHeight != null && iconHeight > 0) iconHeight else originalView.height
    }

    override fun isOfType(type: Int): Boolean {
        return (type and AbstractFloatingView.TYPE_ACTION_POPUP) != 0
    }

    @CallSuper
    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        // Either the original item or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        mDeferContainerRemoval = true
        handleClose(/* animate */ true)
    }

    // TextLauncher: `View?`, since DragController.cancelDrag passes a null target, and the Gradle
    // Kotlin build's parameter null check would throw on it for a non-null `View`.
    override fun onDropCompleted(target: View?, d: DragObject, success: Boolean) {}

    @CallSuper
    override fun onDragEnd() {
        if (!isOpen) {
            if (mOpenCloseAnimator != null) {
                // Close animation is running.
                mDeferContainerRemoval = false
            } else {
                // Close animation is not running.
                if (mDeferContainerRemoval) {
                    closeComplete()
                }
            }
        }
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    override fun createPreDragCondition(): DragOptions.PreDragCondition {
        return object : DragOptions.PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double): Boolean {
                return distanceDragged > startDragThreshold
            }

            override fun onPreDragStart(dragObject: DragObject) {
                if (iconViewController == null || !updateIconUi) return
                iconViewController.setForceHideDot(true)
                if (mIsAboveIcon) {
                    // Hide only the icon, keep the text visible.
                    iconViewController.setIconVisible(false)
                    originalView.visibility = VISIBLE
                } else {
                    // Hide both the icon and text.
                    originalView.visibility = INVISIBLE
                }
            }

            override fun onPreDragEnd(dragObject: DragObject, dragStarted: Boolean) {
                if (iconViewController == null || !updateIconUi) return
                iconViewController.setIconVisible(true)
                if (dragStarted) {
                    // Make sure we keep the original icon hidden while it is being dragged.
                    originalView.visibility = INVISIBLE
                } else {
                    if (!mIsAboveIcon) {
                        // Show the icon but keep the text hidden.
                        originalView.visibility = VISIBLE
                        iconViewController.getFloatingViewTextAlpha()?.value = 0f
                    }
                }
            }
        }
    }

    companion object {
        /** Returns a PopupContainer which is already open or null */
        @JvmStatic
        fun getOpen(context: ActivityContext): PopupContainer<*>? =
            getOpenView(context, TYPE_ACTION_POPUP)

        /**
         * Creates a new instance of [PopupContainer].
         *
         * @param context The context in which the popup will be created.
         * @param originalView The view that the popup is associated with.
         * @param updateIconUi Whether to update the icon UI during drag and drop.
         * @return A new instance of [PopupContainer].
         */
        fun <T : ActivityContext> create(
            context: Context,
            originalView: View,
            itemInfo: ItemInfo,
            updateIconUi: Boolean = true,
        ): PopupContainer<T> {
            val container = PopupContainer<T>(context, originalView, itemInfo, updateIconUi)
            container.id = R.id.popup_container
            container.clipChildren = false
            container.clipToPadding = false
            container.orientation = VERTICAL
            container.layoutParams =
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            return container
        }

        /**
         * Shows a menu for the [view] with the provided [items]
         *
         * @param popupPos Position of the event relative to dragLayer, where the popup should be
         *   anchored
         */
        @JvmOverloads
        fun showForMenuItems(
            activity: ActivityContext,
            view: View,
            items: List<PopupData>,
            popupPos: Rect? = null,
        ): PopupContainer<ActivityContext>? {
            if (items.isEmpty()) return null
            return create<ActivityContext>(
                    activity.asContext(),
                    view,
                    view.tag as? ItemInfo ?: ItemInfo().apply { itemType = ITEM_TYPE_CUSTOM_VIEW },
                )
                .apply {
                    fixedDragLayerPos = popupPos
                    showForSystemShortcuts(items, activity, view)
                }
        }
    }

    // TextLauncher: members TextLauncher adds.

    /**
     * The drag layer ends above the keyboard (LauncherRootLayout), so it grows and shrinks when the
     * keyboard goes down or comes back while the menu is open (a widget's resize frame puts it
     * down, docs/spec.md 5.4). A menu above its item is placed from the drag layer's bottom
     * (orientAboutObject), so it keeps its place on the screen by the same amount.
     */
    private val keepPlaceOnScreen =
        View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val delta = (bottom - top) - (oldBottom - oldTop)
            val lp =
                layoutParams as? android.widget.FrameLayout.LayoutParams
                    ?: return@OnLayoutChangeListener
            if (delta == 0 || oldBottom == oldTop || lp.gravity != android.view.Gravity.BOTTOM) {
                return@OnLayoutChangeListener
            }
            lp.bottomMargin += delta
            (mArrow.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin += delta
            post { requestLayout() }
        }

    /** Starts [keepPlaceOnScreen] following the drag layer's height. */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        popupContainer.addOnLayoutChangeListener(keepPlaceOnScreen)
    }

    /** Stops [keepPlaceOnScreen]. */
    override fun onDetachedFromWindow() {
        popupContainer.removeOnLayoutChangeListener(keepPlaceOnScreen)
        super.onDetachedFromWindow()
    }
}

// TextLauncher: PopupCategory and PopupData are from PopupDataMapper.kt (android-17.0.0_r1), which
// is not copied (PopupDataMapper itself is not used). PopupData has no eventId: TextLauncher logs
// no stats, and its StatsLogManager has no LauncherEvent.IGNORE.

/**
 * Enum for the category of popup we have, as we handle different categories of shortcuts
 * differently depending on the category.
 */
enum class PopupCategory {
    SYSTEM_SHORTCUT,
    SYSTEM_SHORTCUT_FIXED,
}

/** Data class which stores all the values we need to create a long press menu shortcut. */
data class PopupData(
    val iconResId: Int,
    val labelResId: Int,
    val category: PopupCategory,
    val popupAction: (context: ActivityContext, itemInfo: ItemInfo, view: View) -> Unit,
)
