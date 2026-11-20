package com.android.launcher3.popup

import android.content.Context
import android.graphics.Typeface
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.android.launcher3.DragSource
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.DeepShortcutTextView
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.views.ActivityContext

/**
 * Stand-in for Launcher3's PopupContainerWithArrow (android-17.0.0_r1): the long-press menu of an
 * app icon. The members that show system shortcut rows are copied from
 * PopupContainerWithArrow.kt; changes are marked with "TextLauncher:".
 *
 * TextLauncher: the menu shows only the launcher's own options (docs/spec.md 5.3), so the app
 * shortcut (deep shortcut) rows, the icon-only row for many options and the Compose version are
 * left out, along with their layouts.
 */
class PopupContainerWithArrow<T : ActivityContext>
private constructor(
    context: Context,
    val originalIcon: View,
    itemInfo: ItemInfo,
    updateIconUi: Boolean,
) :
    PopupContainer<T>(context, originalIcon, itemInfo, updateIconUi),
    DragSource,
    DragController.DragListener,
    Popup {

    private val interceptTouchDown = PointF()

    private var containerWidth: Int = resources.getDimensionPixelSize(R.dimen.bg_popup_item_width)

    var widgetContainer: ViewGroup? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            interceptTouchDown[ev.x] = ev.y
        }
        // Stop sending touch events to deep shortcut views if user moved beyond touch slop.
        return (Utilities.squaredHypot(interceptTouchDown.x - ev.x, interceptTouchDown.y - ev.y) >
            Utilities.squaredTouchSlop(context))
    }

    override fun handleClose(animate: Boolean) {
        super.handleClose(animate)
        if (hasFocus() && originalView.isAttachedToWindow()) {
            originalView.requestFocus()
        }
    }

    override fun requestFocusOnOpened(): Boolean {
        return true
    }

    /**
     * Populate and show shortcuts for the Launcher U app shortcut design. Will inflate the
     * container and shortcut View instances for the popup container.
     *
     * TextLauncher: there are no deep shortcuts, so only the system shortcut rows are added.
     *
     * @param systemShortcuts List of SystemShortcuts to add to container
     */
    fun populateAndShowRows(systemShortcuts: List<SystemShortcut<*>>) {
        containerWidth = resources.getDimensionPixelSize(R.dimen.bg_popup_item_width)

        if (systemShortcuts.isNotEmpty()) {
            addSystemShortcuts(
                systemShortcuts,
                R.layout.system_shortcut_rows_container,
                R.layout.system_shortcut,
            )
        }
        show()
    }

    /**
     * Inflates the given systemShortcutContainerLayout as a container, and populates with the
     * systemShortcuts as views using the systemShortcutLayout
     *
     * @param systemShortcuts List of SystemShortcut to inflate as Views
     * @param systemShortcutContainerLayout Layout Resource for the Container of shortcut Views
     * @param systemShortcutLayout Layout Resource for the individual shortcut Views
     */
    private fun addSystemShortcuts(
        systemShortcuts: List<SystemShortcut<*>>,
        @LayoutRes systemShortcutContainerLayout: Int,
        @LayoutRes systemShortcutLayout: Int,
    ) {
        if (systemShortcuts.isEmpty()) {
            return
        }
        systemShortcutContainer = inflateAndAdd(systemShortcutContainerLayout, this)
        widgetContainer = systemShortcutContainer
        for (i in systemShortcuts.indices) {
            initializeSystemShortcut(
                systemShortcutLayout,
                systemShortcutContainer,
                systemShortcuts[i],
                i < systemShortcuts.size - 1,
            )
        }
    }

    /**
     * Initializes and adds View for given SystemShortcut to a container.
     *
     * @param resId Resource id to use for SystemShortcut View.
     * @param container ViewGroup to add the shortcut View to as a parent
     * @param info The SystemShortcut instance to create a View for.
     * @param shouldAppendSpacer If True, will add a spacer after the shortcut, when showing the
     *   SystemShortcut as an icon only. Used to space the shortcut icons evenly.
     * @return The view inflated for the SystemShortcut
     */
    private fun initializeSystemShortcut(
        resId: Int,
        container: ViewGroup?,
        info: SystemShortcut<*>,
        shouldAppendSpacer: Boolean,
    ): View {
        val view = inflateAndAdd<View>(resId, container)
        if (view is DeepShortcutView) {
            // System shortcut takes entire row with icon and text
            val shortcutView = view
            if (com.android.wm.shell.Flags.enableGsf()) {
                shortcutView.bubbleText.typeface =
                    Typeface.create(
                        DeepShortcutTextView.GOOGLE_SANS_FLEX_LABEL_LARGE,
                        Typeface.NORMAL,
                    )
            }
            info.setIconAndLabelFor(shortcutView.iconView, shortcutView.bubbleText)
        }
        view.tag = info
        view.setOnClickListener(info)
        return view
    }

    companion object {
        /**
         * Creates a new instance of [PopupContainerWithArrow].
         *
         * @param context The context in which the popup will be created.
         * @param originalView The view that the popup is associated with.
         * @param updateIconUi Whether to update the icon UI during drag and drop.
         * @return A new instance of [PopupContainerWithArrow].
         */
        @JvmStatic
        fun <T : ActivityContext> create(
            context: Context,
            originalView: View,
            itemInfo: ItemInfo,
            updateIconUi: Boolean = true,
        ): PopupContainerWithArrow<T> {
            val container =
                PopupContainerWithArrow<T>(context, originalView, itemInfo, updateIconUi)
            container.id = R.id.popup_container
            container.isFocusable = true
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
    }
}
