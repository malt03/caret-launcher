package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.LauncherDelegate
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import kotlin.math.max

/**
 * The folder of a folder icon on the home screen (docs/spec.md 5.2): the apps and shortcuts inside,
 * icons only. Launcher3's Folder (the stand-in) opens, closes, drags, reorders and takes drops with
 * the copied code and its expressive folder animation; this class gives it TextLauncher's look, its
 * item views, its focus rule and keeps it inside the drag layer as the keyboard comes back.
 */
@SuppressLint("ViewConstructor")
class HomeFolder(
    context: Context,
    activityContext: ActivityContext,
    launcherDelegate: LauncherDelegate,
    private val iconSizePx: Int,
) : Folder(context, activityContext, launcherDelegate) {

    /**
     * TextLauncher: the drag layer ends above the keyboard (LauncherRootLayout), so it gets shorter
     * when the keyboard comes back while the folder is open, e.g. after a drop into a folder that
     * opened during the drag with the keyboard down (docs/spec.md 5.4). The folder then moves up to
     * stay inside it, as centerAboutIcon keeps it when it opens. Launcher3's drag layer keeps its
     * size, and the folder stays where it is while the workspace changes behind it, as it does here
     * when the drag layer grows (the keyboard goes down).
     */
    private val dragLayerSizeListener =
        View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top < oldBottom - oldTop) post(::keepInsideDragLayer)
        }

    init {
        // Folder is focusable in touch mode, so that it takes the focus back from its name field.
        // TextLauncher's folders have no name, and a touch must not take the focus from the search
        // field (docs/spec.md 1, 2.6): only keys give it the focus.
        isFocusableInTouchMode = false

        val padding = (16 * resources.displayMetrics.density).toInt()
        mContent.setPadding(padding, padding, padding, padding)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mActivityContext.dragLayer.addOnLayoutChangeListener(dragLayerSizeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mActivityContext.dragLayer.removeOnLayoutChangeListener(dragLayerSizeListener)
    }

    /**
     * Launcher3's open folder always has the focus. This one has it only when it was opened with
     * keys and nothing took it since (the home button gives it to the search field), and only
     * then does it go back to the icon.
     */
    override fun shouldFocusIconOnClose(hadFocus: Boolean): Boolean = hadFocus

    /** R.layout.folder_application: an icon without a label, focusable like Launcher3's icons. */
    override fun inflateIcon(): BubbleTextView = AppIconView(context, iconSizePx).apply {
        isFocusable = true
        defaultFocusHighlightEnabled = false
    }

    /** Moves the open folder up into the drag layer, with centerAboutIcon's margin below it. */
    private fun keepInsideDragLayer() {
        if (!isOpen) return
        val lp = layoutParams as? BaseDragLayer.LayoutParams ?: return
        val margin = mContent.paddingTop
        val maxTop = mActivityContext.dragLayer.height - lp.height - margin
        if (lp.y > maxTop) {
            lp.y = max(margin, maxTop)
            requestLayout()
        }
    }
}
