package com.malt03.textlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import com.android.app.animation.Interpolators
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.FrameLayout
import com.android.launcher3.anim.KeyboardInsetAnimationCallback
import com.malt03.textlauncher.R
import com.malt03.textlauncher.ui.allapps.AllAppsSheet
import com.malt03.textlauncher.ui.allapps.AllAppsView
import com.android.launcher3.Hotseat
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.ScrimView

/**
 * Lays out the home area / search results above the search field, and keeps the field right
 * above the keyboard (docs/spec.md 1.3). The all apps list comes up with the field
 * (AllAppsTransitionController places them, docs/spec.md 6).
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
    private val dropTargetBarMarginH = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_bar_margin_horizontal)
    private val dropTargetBarTopMargin = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_top_margin)

    /**
     * DeviceProfile.getCellLayoutSpringLoadShrunkTop without the top inset: the drop target bar
     * with its top and bottom margins, below which the home screen shrinks while editing.
     */
    val dropTargetBarShrunkHeight = dropTargetBarTopMargin + dropTargetBarSize +
        resources.getDimensionPixelSize(com.android.launcher3.R.dimen.drop_target_bottom_margin)

    /**
     * The all apps list (docs/spec.md 6), set by the activity. It is on [allAppsSheet], which
     * AllAppsTransitionController keeps right under the search field as the field goes from above
     * the keyboard to the top of the screen, so it comes out from behind the keyboard
     * (docs/spec.md 6.4).
     */
    var allAppsView: AllAppsView? = null
        set(value) {
            check(field == null)
            field = value
            value?.let(allAppsSheet::setList)
            onAllAppsLayoutChanged?.invoke()
        }

    /** The all apps bottom sheet with its handle. */
    val allAppsSheet = AllAppsSheet(context)

    /**
     * docs/spec.md 5.5: with two panels (DeviceProfile.isTwoPanels), the all apps list is always
     * shown on the left half ([enableTwoPanels]), and the home screen ([contentArea]) is on the right.
     */
    var allAppsPanel: AllAppsSheet? = null
        private set
    private val panelMargin = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)

    /**
     * Called with the part of the all apps panel under the search field's top, which the list
     * leaves out at its bottom (the panel goes down behind the field and the keyboard).
     */
    var onAllAppsPanelBottomInsetChanged: ((Int) -> Unit)? = null
    private var allAppsPanelBottomInset = -1

    /** Lays out two panels: the all apps list on the left, returned, and the home screen on the right. */
    fun enableTwoPanels(): AllAppsSheet {
        check(allAppsPanel == null)
        return AllAppsSheet(context, isPanel = true).also {
            allAppsPanel = it
            addView(it, indexOfChild(contentArea) + 1)
            requestLayout()
        }
    }

    /** The left edge of [contentArea]: the right panel's with two panels. */
    private fun contentLeft(width: Int): Int = if (allAppsPanel != null) width / 2 else 0

    /**
     * The home page's height: from below the status bar to the search field with the keyboard
     * down (Launcher3's workspace above the hotseat). Called as it is measured.
     */
    var onPageHeightChanged: ((Int) -> Unit)? = null
    private var pageHeight = 0

    /**
     * While editing (docs/spec.md 5.4) the home screen is laid out at once as with the keyboard
     * down, so it does not follow the keyboard as it goes down and comes back.
     */
    var isEditLayout = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /**
     * Called with the space at the bottom of the screen the navigation bar and the keyboard cover
     * (the window insets), now and as they change.
     */
    var onBottomInsetsChanged: ((navBottom: Int, imeBottom: Int) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(navBottom, imeBottom)
        }

    /**
     * AllAppsTransitionController places the search field and the all apps sheet for its progress;
     * called when the field's closed position may have moved (the keyboard, the layout).
     */
    var onAllAppsLayoutChanged: (() -> Unit)? = null

    /**
     * Set by AllAppsTransitionController: whether the all apps list is shown, open or on its way.
     * The field then leaves the keyboard ([closedBarTop]).
     */
    var isAllAppsShown = false

    /**
     * Sees every touch event before the children, in this view's coordinates, which are the
     * overlay's. When it returns true the event is consumed and the children get ACTION_CANCEL
     * once (BaseDragLayer's touch controllers: drag and drop, the all apps swipe, the status bar's
     * swipe down).
     */
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

    /**
     * Follows the keyboard for the field (KeyboardInsetAnimationCallback animates its
     * translationY); the field is placed from it and the all apps progress.
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
    private var imeBottom = 0

    /** The part of the window the keyboard covers; the drag layer ends above it. */
    private var overlayBottomCut = 0
    var isKeyboardVisible = false
        private set

    init {
        clipChildren = false
        addView(wallpaperScrim)
        addView(contentArea)
        addView(allAppsSheet)
        allAppsSheet.visibility = GONE
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
                onAllAppsLayoutChanged?.invoke()
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
        imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        // Launcher3's DragLayer gets the system bar insets; floating views such as the long-press
        // menu keep inside them. TextLauncher: the keyboard is shown most of the time
        // (docs/spec.md 1.3), so the drag layer ends at its top (see onLayout), and floating
        // views placed or centered in it stay above it.
        overlayBottomCut = imeBottom
        onBottomInsetsChanged?.invoke(navBottom, imeBottom)
        overlay.setInsets(android.graphics.Rect(0, statusBarTop, 0, if (imeBottom > 0) 0 else navBottom))
        // KeyboardInsetAnimationCallback animates from the old to this value.
        targetBarTranslation = -(imeBottom - navBottom).coerceAtLeast(0).toFloat()
        if (imeBottom > 0) keyboardShownTranslation = targetBarTranslation
        if (keyboardCallback.keyboardTranslationState ==
            KeyboardInsetAnimationCallback.KeyboardTranslationState.SYSTEM
        ) {
            keyboardTracker.translationY = targetBarTranslation
        }
        onAllAppsLayoutChanged?.invoke()

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
        val page = height - navBottom - barContainer.measuredHeight - statusBarTop
        if (page != pageHeight) {
            pageHeight = page
            onPageHeightChanged?.invoke(page)
        }
        measureContentArea(width, contentBottom() - statusBarTop)
        measureAllAppsPanel(width)
        // docs/spec.md 5.5: with two panels, the drop target bar is over the home screen's panel
        // (from its left edge: a centered view would be moved by half the left margin).
        (dropTargetBar?.layoutParams as? BaseDragLayer.LayoutParams)?.apply {
            leftMargin = contentLeft(width) + dropTargetBarMarginH
            gravity = android.view.Gravity.TOP or
                if (allAppsPanel != null) android.view.Gravity.START else android.view.Gravity.CENTER_HORIZONTAL
        }
        // The sheet, open, is right under the field at the top of the screen.
        allAppsSheet.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height - sheetOpenTop()).coerceAtLeast(0), MeasureSpec.EXACTLY),
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
        // The sheet is laid out open and moved with the progress (AllAppsTransitionController).
        val sheetTop = sheetOpenTop()
        allAppsSheet.layout(0, sheetTop, width, sheetTop + allAppsSheet.measuredHeight)
        searchBar.layout(barMarginH, barMarginV, width - barMarginH, barMarginV + barHeight)
        layoutContentArea(force = true)
        onAllAppsLayoutChanged?.invoke()
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
        val translation = if (isAllAppsShown || holdFieldAboveKeyboard) {
            minOf(tracked, keyboardShownTranslation)
        } else {
            tracked
        }
        return barContainer.top + translation
    }

    /** The top of the field's container when the list is open: at the top of the screen. */
    val openBarTop: Float get() = statusBarTop.toFloat()

    /** The top of the open sheet: right under the field at the top of the screen. */
    private fun sheetOpenTop(): Int = statusBarTop + barContainer.measuredHeight

    /**
     * The bottom of the content area: the top of the closed search field, with the keyboard
     * translation, or without it while editing ([isEditLayout]).
     */
    private fun contentBottom(): Int = if (isEditLayout) barContainer.top else closedBarTop.toInt()

    /** The top of the closed search field with the keyboard down (the hotseat's top while editing). */
    val keyboardHiddenBarTop: Float get() = barContainer.top.toFloat()

    private fun measureContentArea(width: Int, height: Int) {
        contentArea.measure(
            MeasureSpec.makeMeasureSpec(width - contentLeft(width), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
    }

    /**
     * The all apps panel is the left half, inside the search field's side margin, with the same gap
     * below the status bar. Like Launcher3's all apps sheet on a large screen, it goes down to the
     * bottom of the screen with square bottom corners: it comes out from behind the search field
     * and the keyboard, as the list does on a phone (docs/spec.md 6.4).
     */
    private fun measureAllAppsPanel(width: Int) {
        val panel = allAppsPanel ?: return
        panel.measure(
            MeasureSpec.makeMeasureSpec((width / 2 - panelMargin * 2).coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((measuredHeight - statusBarTop - panelMargin).coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
    }

    private fun layoutContentArea(force: Boolean = false) {
        if (barContainer.height == 0) return
        val bottom = contentBottom()
        val left = contentLeft(width)
        val unchanged = contentArea.top == statusBarTop && contentArea.bottom == bottom &&
            contentArea.left == left && contentArea.right == width
        if (unchanged && !force && !contentArea.isLayoutRequested && allAppsPanel?.isLayoutRequested != true) return
        measureContentArea(width, bottom - statusBarTop)
        contentArea.layout(left, statusBarTop, width, bottom)
        allAppsPanel?.let {
            measureAllAppsPanel(width)
            it.layout(panelMargin, statusBarTop + panelMargin, panelMargin + it.measuredWidth, height)
            val inset = (height - bottom).coerceAtLeast(0)
            if (inset != allAppsPanelBottomInset) {
                allAppsPanelBottomInset = inset
                onAllAppsPanelBottomInsetChanged?.invoke(inset)
            }
        }
    }

    /** Launcher3's DropTargetBar sits in the drag layer, below the status bar (DropTargetBar.setupInsets). */
    fun attachDropTargetBar(bar: View) {
        dropTargetBar = bar
        overlay.addView(
            bar, 0,
            BaseDragLayer.LayoutParams(LayoutParams.MATCH_PARENT, dropTargetBarSize).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                topMargin = statusBarTop + dropTargetBarTopMargin
                leftMargin = dropTargetBarMarginH
                rightMargin = dropTargetBarMarginH
                // The margins above already include the status bar.
                ignoreInsets = true
            },
        )
    }
}
