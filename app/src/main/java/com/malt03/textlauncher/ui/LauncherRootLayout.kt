package com.malt03.textlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.animation.Interpolator
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
     * The all apps list (docs/spec.md 6), set by the activity. It is on [allAppsSheet], which
     * [allAppsProgress] brings up from below the screen to right under the search field, as the
     * field goes from above the keyboard to the top of the screen (AllAppsTransitionController).
     */
    var allAppsView: AllAppsView? = null
        set(value) {
            check(field == null)
            field = value
            value?.let {
                it.setBottomInset(navBottom)
                allAppsSheet.setList(it)
            }
            applyAllAppsProgress()
        }

    /** The all apps bottom sheet with its handle. */
    val allAppsSheet = AllAppsSheet(context)

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

    /** Called with the space at the bottom of the screen the keyboard or the navigation bar covers. */
    var onBottomInsetChanged: ((Int) -> Unit)? = null

    /**
     * BaseDepthControllerImpl: the depth of the spring-loaded state (SpringLoadedState.DEPTH_15_PERCENT
     * while editing), which blurs the wallpaper. The all apps sheet's depth comes with
     * [allAppsProgress].
     */
    var springLoadedDepth = 0f
        set(value) {
            field = value
            applyDepth()
        }

    /** WindowBlurState: whether windows can be blurred now (CrossWindowBlurListeners). */
    private var isBlurEnabled = false
    private val blurListener = java.util.function.Consumer<Boolean> { enabled ->
        isBlurEnabled = enabled
        allAppsSheet.setBlurEnabled(enabled)
        applyDepth()
    }
    private val maxBlurRadius = resources.getDimensionPixelSize(com.android.launcher3.R.dimen.max_depth_blur_radius_enhanced)

    /**
     * BottomSheetProfile.getBottomSheetDepth: the depth that zooms the wallpaper to
     * config_wallpaperMaxScale times workspace_content_scale (1.1 and 0.97 on phones).
     */
    private val allAppsDepth: Float = run {
        val maxWallpaperScale = 1.1f
        val contentScale = resources.getFloat(com.android.launcher3.R.dimen.workspace_content_scale)
        // Utilities.mapToRange(maxWallpaperScale * contentScale, maxWallpaperScale, 1f, 0f, 1f, LINEAR)
        (maxWallpaperScale * contentScale - maxWallpaperScale) / (1f - maxWallpaperScale)
    }
    private val workspaceContentScale = resources.getFloat(com.android.launcher3.R.dimen.workspace_content_scale)

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
        onBottomInsetChanged?.invoke(maxOf(imeBottom, navBottom))
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
        val page = height - navBottom - barContainer.measuredHeight - statusBarTop
        if (page != pageHeight) {
            pageHeight = page
            onPageHeightChanged?.invoke(page)
        }
        measureContentArea(width, contentBottom() - statusBarTop)
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
        // The sheet is laid out open and moved with the progress (applyAllAppsProgress).
        val sheetTop = sheetOpenTop()
        allAppsSheet.layout(0, sheetTop, width, sheetTop + allAppsSheet.measuredHeight)
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
        /** AllAppsSwipeController.ALL_APPS_SHEET_DEPTH */
        val ALL_APPS_SHEET_DEPTH: Interpolator = android.view.animation.PathInterpolator(0f, 0f, .2f, 1f)

        /** BaseDepthControllerImpl.mapDepthToBlur */
        fun mapDepthToBlur(depth: Float): Float = Interpolators.clampToProgress(Interpolators.LINEAR, depth, 0f, 0.3f)

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

    /** The top of the open sheet: right under the field at the top of the screen. */
    private fun sheetOpenTop(): Int = statusBarTop + barContainer.measuredHeight

    /** The top of the sheet's handle area on the screen, while it is shown. */
    val sheetTop: Float get() = allAppsSheet.top + allAppsSheet.translationY

    /** bottom_sheet_handle_area_height */
    val sheetHandleAreaHeight: Int get() = allAppsSheet.handleAreaHeight

    /**
     * AllAppsTransitionController.setProgress: the field goes from [closedBarTop] to [openBarTop]
     * (the keyboard goes down), and the sheet follows right under it, coming out from behind the
     * keyboard. The home screen stays, shrunk to workspace_content_scale and blurred with the depth
     * (AllAppsSwipeController.applyNormalToAllAppsAnimConfig: ALL_APPS_SHEET_DEPTH for the
     * workspace scale and the depth).
     */
    private fun applyAllAppsProgress() {
        val progress = allAppsProgress
        val barTop = closedBarTop + (openBarTop - closedBarTop) * progress
        barContainer.translationY = barTop - barContainer.top
        val shown = progress > 0f
        allAppsSheet.visibility = if (shown) VISIBLE else GONE
        // TextLauncher: the sheet is always right under the field, so it comes out from behind
        // the keyboard as the field goes up (docs/spec.md 6.4). It is laid out open, where the
        // field is at the top of the screen.
        allAppsSheet.translationY = barTop - openBarTop
        val sheetDepth = ALL_APPS_SHEET_DEPTH.getInterpolation(progress)
        val scale = 1f - (1f - workspaceContentScale) * sheetDepth
        contentArea.scaleX = scale
        contentArea.scaleY = scale
        applyDepth()
    }

    /**
     * BaseDepthControllerImpl.applyDepthAndBlur: the depth blurs what is behind the window (the
     * wallpaper; mapDepthToBlur up to max_depth_blur_radius_enhanced). The home screen, which is
     * in this window, is blurred with the all apps sheet's depth (AllAppsState.shouldBlurWorkspace).
     */
    private fun applyDepth() {
        val window = (context as? android.app.Activity)?.window ?: return
        val sheetDepth = allAppsDepth * ALL_APPS_SHEET_DEPTH.getInterpolation(allAppsProgress)
        val depth = maxOf(springLoadedDepth, sheetDepth)
        val blur = if (isBlurEnabled) (mapDepthToBlur(depth) * maxBlurRadius).toInt() else 0
        if (blur != appliedWindowBlur) {
            appliedWindowBlur = blur
            // TextLauncher: Launcher3 sets the background blur radius on its surface; an app sets it
            // on its window, which draws it with each frame (the window is translucent for this).
            window.setBackgroundBlurRadius(blur)
        }
        val contentBlur = if (isBlurEnabled) mapDepthToBlur(sheetDepth) * maxBlurRadius else 0f
        if (contentBlur != appliedContentBlur) {
            appliedContentBlur = contentBlur
            contentArea.setRenderEffect(
                if (contentBlur > 0f) RenderEffect.createBlurEffect(contentBlur, contentBlur, Shader.TileMode.CLAMP) else null,
            )
        }
    }

    private var appliedWindowBlur = -1
    private var appliedContentBlur = -1f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.getSystemService(android.view.WindowManager::class.java)
            .addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
    }

    override fun onDetachedFromWindow() {
        context.getSystemService(android.view.WindowManager::class.java)
            .removeCrossWindowBlurEnabledListener(blurListener)
        super.onDetachedFromWindow()
    }

    /**
     * The bottom of the content area: the top of the closed search field, with the keyboard
     * translation, or without it while editing ([isEditLayout]).
     */
    private fun contentBottom(): Int = if (isEditLayout) barContainer.top else closedBarTop.toInt()

    /** The top of the closed search field with the keyboard down (the hotseat's top while editing). */
    val keyboardHiddenBarTop: Float get() = barContainer.top.toFloat()

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
