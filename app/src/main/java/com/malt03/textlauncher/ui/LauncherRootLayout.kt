package com.malt03.textlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import com.android.app.animation.Interpolators
import com.android.launcher3.Utilities
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import com.android.launcher3.anim.KeyboardInsetAnimationCallback
import com.malt03.textlauncher.R
import com.malt03.textlauncher.ui.allapps.AllAppsView
import com.android.launcher3.Hotseat
import com.android.launcher3.dragndrop.DragLayer
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
    val overlay = DragLayer(context)

    /** Dims the home screen while a folder is open (Launcher3's ScrimView). */
    val scrim = ScrimView(context)

    /**
     * Darkens only the wallpaper while editing, behind everything else. Stands in for the depth
     * (wallpaper blur) of Launcher3's spring-loaded state, which keeps the drop targets readable.
     */
    private val wallpaperScrim = View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
    }
    private var dropTargetBar: View? = null
    private val dropTargetBarSize = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.dynamic_grid_drop_target_size)
    private val dropTargetBarTopMargin = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_top_margin)

    /**
     * DeviceProfile.getDropTargetBarHeight: the bar with its margins, which the home grid makes
     * room for while dragging (the top of Launcher3's spring-loaded workspace).
     */
    val dropTargetBarHeight = dropTargetBarSize + dropTargetBarTopMargin +
        resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_bottom_margin) +
        resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_button_workspace_edge_gap)

    /**
     * Sees every touch event before the children, in this view's coordinates, which are the
     * overlay's. When it returns true the event is consumed and the children get ACTION_CANCEL
     * once (BaseDragLayer's touch controllers: drag and drop).
     */
    /**
     * The all apps list (docs/spec.md 6.4), set by the activity. Open, the search field sits at
     * the top of the screen and the list fills the space below it; closed, the list is hidden.
     * TextLauncher: the states only; the transition between them (AllAppsTransitionController) is
     * not here yet.
     */
    var allAppsView: AllAppsView? = null
        set(value) {
            field?.let(::removeView)
            field = value
            value?.let {
                it.visibility = if (isAllAppsOpen) VISIBLE else GONE
                it.setBottomInset(navBottom)
                addView(it, indexOfChild(barContainer))
            }
        }

    var isAllAppsOpen = false
        set(value) {
            if (field == value) return
            field = value
            allAppsView?.visibility = if (value) VISIBLE else GONE
            contentArea.visibility = if (value) INVISIBLE else VISIBLE
            // The field does not follow the keyboard while it is at the top.
            barContainer.setWindowInsetsAnimationCallback(if (value) null else keyboardCallback)
            barContainer.translationY = barTranslation()
            requestLayout()
        }

    var touchInterceptor: ((MotionEvent) -> Boolean)? = null
    private var childrenCancelled = false

    /** Launcher.isTouchInProgress */
    var isTouchInProgress = false
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

    /** The part of the window the keyboard covers; the drag layer ends above it. */
    private var overlayBottomCut = 0
    var isKeyboardVisible = false
        private set

    init {
        clipChildren = false
        addView(wallpaperScrim)
        addView(contentArea)
        barContainer.addView(searchBar)
        addView(barContainer)
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
                    barContainer.translationY = barTranslation()
                }
                layoutContentArea()
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTouchInProgress = true
                childrenCancelled = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isTouchInProgress = false
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
        (dropTargetBar?.layoutParams as? MarginLayoutParams)?.topMargin = statusBarTop + dropTargetBarTopMargin
        navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        allAppsView?.setBottomInset(navBottom)
        val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        // Launcher3's DragLayer gets the system bar insets; floating views such as the long-press
        // menu keep inside them. TextLauncher: the keyboard is shown most of the time
        // (docs/spec.md 1.3), so the drag layer ends at its top (see onLayout), and floating
        // views placed or centered in it stay above it.
        overlayBottomCut = imeBottom
        overlay.setInsets(android.graphics.Rect(0, statusBarTop, 0, if (imeBottom > 0) 0 else navBottom))
        // KeyboardInsetAnimationCallback animates from the old to this value.
        targetBarTranslation = -(imeBottom - navBottom).coerceAtLeast(0).toFloat()
        barContainer.translationY = barTranslation()

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
        allAppsView?.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height - statusBarTop - barContainer.measuredHeight).coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
        overlay.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height - overlayBottomCut, MeasureSpec.EXACTLY),
        )
        scrim.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        wallpaperScrim.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val barTop = if (isAllAppsOpen) statusBarTop else height - navBottom - barContainer.measuredHeight
        barContainer.layout(0, barTop, width, barTop + barContainer.measuredHeight)
        allAppsView?.let {
            val listTop = statusBarTop + barContainer.measuredHeight
            it.layout(0, listTop, width, listTop + it.measuredHeight)
        }
        searchBar.layout(barMarginH, barMarginV, width - barMarginH, barMarginV + barHeight)
        layoutContentArea(force = true)
        overlay.layout(0, 0, width, height - overlayBottomCut)
        scrim.layout(0, 0, width, height)
        wallpaperScrim.layout(0, 0, width, height)
    }

    /**
     * Fades the wallpaper scrim in or out with the spring-loaded state: Launcher3 animates the
     * depth over SpringLoadedState.getTransitionDuration (150ms, both ways) with LINEAR.
     */
    fun animateWallpaperScrim(visible: Boolean) {
        // TextLauncher: a little lighter than the folder scrim (0.2, or 0.32 in the dark theme).
        val finalAlpha = if (!visible) 0f else if (Utilities.isDarkTheme(context)) EDIT_SCRIM_ALPHA_DARK else EDIT_SCRIM_ALPHA
        wallpaperScrim.animate().cancel()
        wallpaperScrim.animate().alpha(finalAlpha)
            .setDuration(SPRING_LOADED_TRANSITION_MS)
            .setInterpolator(Interpolators.LINEAR)
            .start()
    }

    private companion object {
        const val SPRING_LOADED_TRANSITION_MS = 150L
        const val EDIT_SCRIM_ALPHA = 0.15f
        const val EDIT_SCRIM_ALPHA_DARK = 0.24f
    }

    /** The field follows the keyboard (docs/spec.md 1.3), except at the top with all apps open. */
    private fun barTranslation(): Float = if (isAllAppsOpen) 0f else targetBarTranslation

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

    /** Launcher3's DropTargetBar sits in the drag layer, below the status bar (DropTargetBar.setupInsets). */
    fun attachDropTargetBar(bar: View) {
        dropTargetBar = bar
        val horizontalMargin = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_bar_margin_horizontal)
        overlay.addView(
            bar, 0,
            BaseDragLayer.LayoutParams(LayoutParams.MATCH_PARENT, dropTargetBarSize).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                topMargin = statusBarTop + dropTargetBarTopMargin
                leftMargin = horizontalMargin
                rightMargin = horizontalMargin
                // The margins above already include the status bar.
                ignoreInsets = true
            },
        )
    }

    fun isInsideSearchBar(view: View): Boolean = view === searchBar || view.parent === searchBar
}
