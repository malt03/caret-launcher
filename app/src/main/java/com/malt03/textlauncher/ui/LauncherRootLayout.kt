package com.malt03.textlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import com.android.launcher3.anim.KeyboardInsetAnimationCallback
import com.malt03.textlauncher.R
import com.malt03.textlauncher.ui.drag.DropTargetBar
import com.android.launcher3.Hotseat
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.ScrimView

/**
 * Lays out the home area / search results above the search field, and keeps the field right
 * above the keyboard (docs/spec.md 1.3).
 *
 * The field follows the keyboard animation with Launcher3's KeyboardInsetAnimationCallback,
 * which animates translationY. The content area above it is re-laid out every frame so that its
 * bottom stays at the field's top.
 */
@SuppressLint("ViewConstructor")
class LauncherRootLayout(context: Context) : FrameLayout(context) {
    /** Holds the home layout or the search results. */
    val contentArea = FrameLayout(context)
    val searchBar = SearchBarView(context)

    /** Called when the keyboard went from visible to hidden. */
    var onKeyboardHidden: (() -> Unit)? = null

    /** Full-size layer on top of everything: drag views, open folders (Launcher3's drag layer). */
    val overlay = BaseDragLayer(context)

    /** Dims the home screen while a folder is open (Launcher3's ScrimView). */
    val scrim = ScrimView(context)
    val dropTargetBar = DropTargetBar(context)
    private val dropTargetBarHeight = (64 * resources.displayMetrics.density).toInt()

    /**
     * Sees every touch event before the children. When it returns true the event is consumed
     * and the children get ACTION_CANCEL once (used by drag and drop).
     */
    var touchInterceptor: ((MotionEvent) -> Boolean)? = null
    private var childrenCancelled = false

    /** Raw position of the latest ACTION_DOWN. */
    var lastDownRawX = 0f
        private set
    var lastDownRawY = 0f
        private set

    /** The search field and its margins; Launcher3's hotseat for the folder animation. */
    val hotseat = Hotseat(context)
    private val barContainer: FrameLayout get() = hotseat

    /** The search field with its margins; it moves with the keyboard. */
    val searchBarArea: View get() = barContainer
    private val barHeight = resources.getDimensionPixelSize(R.dimen.search_bar_height)
    private val barMarginH = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
    private val barMarginV = resources.getDimensionPixelSize(R.dimen.search_bar_margin_vertical)

    private val keyboardCallback = KeyboardInsetAnimationCallback(barContainer)
    private var targetBarTranslation = 0f
    private var statusBarTop = 0
    private var navBottom = 0
    var isKeyboardVisible = false
        private set

    init {
        clipChildren = false
        addView(contentArea)
        barContainer.addView(searchBar)
        addView(barContainer)
        overlay.addView(dropTargetBar, LayoutParams(LayoutParams.MATCH_PARENT, dropTargetBarHeight))
        addView(scrim)
        addView(overlay)
        isFocusableInTouchMode = true
        barContainer.setWindowInsetsAnimationCallback(keyboardCallback)
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // When no keyboard animation is driving the field, make sure it sits at the
                // position the latest insets ask for. An animation can be cut short (for example
                // while the activity is coming back to the front) and leave it behind.
                if (keyboardCallback.keyboardTranslationState ==
                    KeyboardInsetAnimationCallback.KeyboardTranslationState.SYSTEM
                ) {
                    barContainer.translationY = targetBarTranslation
                }
                layoutContentArea()
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastDownRawX = ev.rawX
            lastDownRawY = ev.rawY
            childrenCancelled = false
        }
        if (touchInterceptor?.invoke(ev) == true) {
            if (!childrenCancelled) {
                childrenCancelled = true
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        statusBarTop = insets.getInsets(WindowInsets.Type.statusBars()).top
        navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        // KeyboardInsetAnimationCallback animates from the old to this value.
        targetBarTranslation = -(imeBottom - navBottom).coerceAtLeast(0).toFloat()
        barContainer.translationY = targetBarTranslation

        val visible = insets.isVisible(WindowInsets.Type.ime())
        val wasVisible = isKeyboardVisible
        isKeyboardVisible = visible
        if (wasVisible && !visible) post { onKeyboardHidden?.invoke() }
        requestLayout()
        return WindowInsets.CONSUMED
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        barContainer.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(barHeight + barMarginV * 2, MeasureSpec.EXACTLY),
        )
        searchBar.measure(
            MeasureSpec.makeMeasureSpec(width - barMarginH * 2, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY),
        )
        measureContentArea(width, contentBottom() - statusBarTop)
        overlay.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        scrim.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val barTop = height - navBottom - barContainer.measuredHeight
        barContainer.layout(0, barTop, width, barTop + barContainer.measuredHeight)
        searchBar.layout(barMarginH, barMarginV, width - barMarginH, barMarginV + barHeight)
        layoutContentArea(force = true)
        overlay.layout(0, 0, width, height)
        scrim.layout(0, 0, width, height)
        dropTargetBar.layout(0, statusBarTop, width, statusBarTop + dropTargetBarHeight)
    }

    /** The visual top of the search field container, including keyboard translation. */
    private fun contentBottom(): Int = (barContainer.top + barContainer.translationY).toInt()

    private fun measureContentArea(width: Int, height: Int) {
        contentArea.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
    }

    private fun layoutContentArea(force: Boolean = false) {
        if (barContainer.height == 0) return
        val bottom = contentBottom()
        val unchanged = contentArea.top == statusBarTop && contentArea.bottom == bottom && contentArea.width == width
        if (unchanged && !force && !contentArea.isLayoutRequested) return
        measureContentArea(width, bottom - statusBarTop)
        contentArea.layout(0, statusBarTop, width, bottom)
    }

    fun isInsideSearchBar(view: View): Boolean = view === searchBar || view.parent === searchBar
}
