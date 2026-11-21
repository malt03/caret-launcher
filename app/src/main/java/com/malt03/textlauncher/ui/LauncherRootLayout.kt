package com.malt03.textlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.animation.Interpolator
import com.android.launcher3.util.Themes
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
 * above the keyboard (docs/spec.md 1.3). The all apps list comes up with the field
 * ([allAppsProgress], docs/spec.md 6).
 *
 * The keyboard animation is followed with Launcher3's KeyboardInsetAnimationCallback, which
 * animates a tracker's translationY. The content area above the closed field is re-laid out every
 * frame so that its bottom stays at the field's top.
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
     * DragLayer's workspace drag scrim: darkens the wallpaper while editing, behind the home
     * screen ([animateWallpaperScrim]).
     */
    private val wallpaperScrim = View(context).apply {
        setBackgroundColor(context.getColor(com.android.launcher3.R.color.wallpaper_popup_scrim))
        alpha = 0f
    }
    private var dropTargetBar: View? = null
    private val dropTargetBarSize = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.dynamic_grid_drop_target_size)
    private val dropTargetBarTopMargin = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_top_margin)

    /**
     * DeviceProfile.getDropTargetBarHeight: the bar with its margins, which the home grid makes
     * room for while dragging (the top of Launcher3's spring-loaded workspace).
     */
    /**
     * DeviceProfile.getCellLayoutSpringLoadShrunkTop without the top inset: the drop target bar
     * with its top and bottom margins, below which the home screen shrinks while editing.
     */
    val dropTargetBarShrunkHeight = dropTargetBarTopMargin + dropTargetBarSize +
        resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_bottom_margin)

    val dropTargetBarHeight = dropTargetBarSize + dropTargetBarTopMargin +
        resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_bottom_margin) +
        resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_button_workspace_edge_gap)

    /**
     * The all apps list (docs/spec.md 6), set by the activity. It rides on [allAppsSheet] under
     * the search field, which [allAppsProgress] moves from above the keyboard to the top of the
     * screen (AllAppsTransitionController's panel).
     */
    var allAppsView: AllAppsView? = null
        set(value) {
            field?.let(::removeView)
            field = value
            value?.let {
                it.setBottomInset(navBottom)
                addView(it, indexOfChild(barContainer))
            }
            applyAllAppsProgress()
        }

    /**
     * The all apps bottom sheet (ActivityAllAppsContainerView.mBottomSheetBackground, with
     * Flags.allAppsSheetForHandheld). TextLauncher: without the background blur, so in the color
     * Launcher3 falls back to.
     */
    private val allAppsSheet = View(context).apply {
        val radius = Themes.getDialogCornerRadius(context)
        background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setColor(context.getColor(
                if (Utilities.isDarkTheme(context)) android.R.color.system_accent2_800 else android.R.color.system_accent2_200,
            ))
        }
    }

    /**
     * AllAppsTransitionController's progress, from 0 (closed: the field is above the keyboard) to 1
     * (open: the field is at the top of the screen, with the list under it).
     */
    var allAppsProgress = 0f
        set(value) {
            field = value
            applyAllAppsProgress()
        }

    /** True while the all apps list is open or opening (docs/spec.md 6.4). */
    val isAllAppsShown: Boolean get() = allAppsProgress > 0f

    /**
     * Sees every touch event before the children, in this view's coordinates, which are the
     * overlay's. When it returns true the event is consumed and the children get ACTION_CANCEL
     * once (BaseDragLayer's touch controllers: drag and drop, and the all apps swipe).
     */
    var touchInterceptor: ((MotionEvent) -> Boolean)? = null
    var allAppsTouchInterceptor: ((MotionEvent) -> Boolean)? = null
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

    /**
     * Follows the keyboard for the field (KeyboardInsetAnimationCallback animates its
     * translationY); the field is placed from it and [allAppsProgress].
     */
    private val keyboardTracker = View(context)
    private val keyboardCallback = KeyboardInsetAnimationCallback(keyboardTracker)
    private var targetBarTranslation = 0f

    /** The field's translation the last time the keyboard was up. */
    private var keyboardShownTranslation = 0f

    /**
     * Keeps the closed field above the keyboard while the list closes and the keyboard comes back
     * (docs/spec.md 6.6); it lets go once the keyboard is up.
     */
    var holdFieldAboveKeyboard = false
    var statusBarTop = 0
        private set
    private var navBottom = 0

    /** The part of the window the keyboard covers; the drag layer ends above it. */
    private var overlayBottomCut = 0
    var isKeyboardVisible = false
        private set

    init {
        clipChildren = false
        addView(wallpaperScrim)
        addView(contentArea)
        addView(allAppsSheet)
        barContainer.addView(searchBar)
        addView(barContainer)
        addView(scrim)
        addView(overlay)
        addView(keyboardTracker, 0, 0)
        keyboardTracker.visibility = INVISIBLE
        isFocusableInTouchMode = true
        keyboardTracker.setWindowInsetsAnimationCallback(keyboardCallback)
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // When no keyboard animation is driving the field, make sure it sits at the
                // position the latest insets ask for. An animation can be cut short (for example
                // while the activity is coming back to the front) and leave it behind.
                if (keyboardCallback.keyboardTranslationState ==
                    KeyboardInsetAnimationCallback.KeyboardTranslationState.SYSTEM
                ) {
                    keyboardTracker.translationY = targetBarTranslation
                    if (isKeyboardVisible) holdFieldAboveKeyboard = false
                }
                applyAllAppsProgress()
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
        if (touchInterceptor?.invoke(ev) == true || allAppsTouchInterceptor?.invoke(ev) == true) {
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
        if (imeBottom > 0) keyboardShownTranslation = targetBarTranslation
        if (keyboardCallback.keyboardTranslationState ==
            KeyboardInsetAnimationCallback.KeyboardTranslationState.SYSTEM
        ) {
            keyboardTracker.translationY = targetBarTranslation
        }
        applyAllAppsProgress()

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
        allAppsSheet.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height - statusBarTop).coerceAtLeast(0), MeasureSpec.EXACTLY),
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
        val barTop = height - navBottom - barContainer.measuredHeight
        barContainer.layout(0, barTop, width, barTop + barContainer.measuredHeight)
        // The list and the sheet are laid out open and moved with the field (applyAllAppsProgress).
        allAppsView?.let {
            val listTop = statusBarTop + barContainer.measuredHeight
            it.layout(0, listTop, width, listTop + it.measuredHeight)
        }
        allAppsSheet.layout(0, statusBarTop, width, statusBarTop + allAppsSheet.measuredHeight)
        searchBar.layout(barMarginH, barMarginV, width - barMarginH, barMarginV + barHeight)
        layoutContentArea(force = true)
        applyAllAppsProgress()
        overlay.layout(0, 0, width, height - overlayBottomCut)
        scrim.layout(0, 0, width, height)
        wallpaperScrim.layout(0, 0, width, height)
    }

    /**
     * DragLayer's workspace drag scrim (graphics/Scrim, wallpaper_popup_scrim) with the
     * spring-loaded state: SpringLoadedState.getWorkspaceBackgroundAlpha (0.2), animated over
     * SpringLoadedState.getTransitionDuration (150ms, both ways) with LINEAR
     * (WorkspaceStateTransitionAnimation.setScrim).
     */
    fun animateWallpaperScrim(visible: Boolean) {
        val finalAlpha = if (visible) SPRING_LOADED_BACKGROUND_ALPHA else 0f
        wallpaperScrim.animate().cancel()
        wallpaperScrim.animate().alpha(finalAlpha)
            .setDuration(SPRING_LOADED_TRANSITION_MS)
            .setInterpolator(Interpolators.LINEAR)
            .start()
    }

    private companion object {
        /** AllAppsSwipeController.ALL_APPS_SCRIM_RESPONDER */
        val ALL_APPS_SCRIM_RESPONDER: Interpolator =
            Interpolators.clampToProgress(Interpolators.LINEAR, 0.1f, 0.5f)

        const val SPRING_LOADED_TRANSITION_MS = 150L
        /** SpringLoadedState.getWorkspaceBackgroundAlpha */
        const val SPRING_LOADED_BACKGROUND_ALPHA = 0.2f
    }

    /**
     * The top of the field's container when the list is closed: right above the keyboard. While
     * the list is shown or closing, it stays where the keyboard's top is when the keyboard is up,
     * so the field follows the finger as the keyboard goes down or comes back (docs/spec.md 6.2).
     */
    val closedBarTop: Float get() {
        val tracked = keyboardTracker.translationY
        val translation = if (allAppsProgress > 0f || holdFieldAboveKeyboard) {
            minOf(tracked, keyboardShownTranslation)
        } else {
            tracked
        }
        return barContainer.top + translation
    }

    /** The top of the field's container when the list is open: at the top of the screen. */
    val openBarTop: Float get() = statusBarTop.toFloat()

    /**
     * AllAppsTransitionController.setProgress: the field moves from [closedBarTop] to [openBarTop]
     * with the sheet and the list under it, and the home screen fades out as the scrim of
     * Launcher3 fades in (AllAppsSwipeController.ALL_APPS_SCRIM_RESPONDER).
     */
    private fun applyAllAppsProgress() {
        val progress = allAppsProgress
        val barTop = closedBarTop + (openBarTop - closedBarTop) * progress
        barContainer.translationY = barTop - barContainer.top
        val shown = progress > 0f
        val shift = barTop - statusBarTop
        allAppsSheet.visibility = if (shown) VISIBLE else GONE
        allAppsSheet.translationY = shift
        allAppsView?.let {
            it.visibility = if (shown) VISIBLE else GONE
            it.translationY = shift
        }
        contentArea.alpha = 1f - ALL_APPS_SCRIM_RESPONDER.getInterpolation(progress)
        contentArea.visibility = if (progress >= 1f) INVISIBLE else VISIBLE
    }

    /** The visual top of the search field container when closed, with the keyboard translation. */
    private fun contentBottom(): Int = closedBarTop.toInt()

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
