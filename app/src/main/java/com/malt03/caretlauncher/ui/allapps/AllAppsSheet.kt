package com.malt03.caretlauncher.ui.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes

/**
 * The all apps bottom sheet (Flags.allAppsSheetForHandheld, enabled in the release):
 * ActivityAllAppsContainerView's mBottomSheetBackground (all_apps_bottom_sheet_background.xml) with
 * its handle, and the list below the handle area.
 *
 * Its color is ActivityAllAppsContainerView.getBackgroundColor: translucent over the blur
 * (blur_shade_panel_fg over blur_shade_panel_bg), or, when windows cannot be blurred, the opaque
 * fallback (system_accent2_200, or system_accent2_800 in the dark theme).
 *
 * CaretLauncher: the search field is not on the sheet; it sits right above it (docs/spec.md 6.4).
 *
 * [isPanel]: the left panel of two panels (docs/spec.md 5.5), which is always shown: the same sheet
 * as Launcher3's on a large screen, but without the handle, as there is nothing to drag.
 */
@SuppressLint("ViewConstructor")
class AllAppsSheet(context: Context, private val isPanel: Boolean = false) : FrameLayout(context) {
    private val radius = Themes.getDialogCornerRadius(context)
    private val sheetBackground = GradientDrawable().apply {
        // mBottomSheetCornerRadii: the top corners only.
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
    }

    /**
     * bottom_sheet_handle_area_height: the list starts below it. The panel keeps the space without
     * the handle, so its list starts where the sheet's does.
     */
    val handleAreaHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_handle_area_height)

    /** The main page's list (mAH's MAIN) and the search results (SEARCH). */
    private var list: AllAppsView? = null
    private var searchResults: RecyclerView? = null

    init {
        background = sheetBackground
        // bottom_sheet_handle with bg_rounded_corner_bottom_sheet_handle.
        if (!isPanel) addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = resources.getDimension(R.dimen.bottom_sheet_handle_corner_radius)
                    setColor(Themes.getAttrColor(context, R.attr.bottomSheetDragHandleColor))
                }
            },
            LayoutParams(
                resources.getDimensionPixelSize(R.dimen.bottom_sheet_handle_width),
                resources.getDimensionPixelSize(R.dimen.bottom_sheet_handle_height),
                Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.bottom_sheet_handle_margin) },
        )
        setBlurEnabled(false)
    }

    /** The list, under the handle area. */
    fun setList(list: AllAppsView) {
        this.list = list
        addUnderHandleArea(list)
    }

    /** The results of the search in the list (the container's search RecyclerView), under the handle area. */
    fun setSearchResults(results: RecyclerView) {
        searchResults = results
        addUnderHandleArea(results)
    }

    private fun addUnderHandleArea(view: View) {
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = handleAreaHeight
        })
    }

    /**
     * ActivityAllAppsContainerView.shouldContainerScroll: a swipe down from [ev] (in [eventSource]'s
     * coordinates) may close the list: on the handle area, or on the active list
     * (getActiveRecyclerView: the search results while searching) at its top and not on the
     * scrollbar's thumb.
     *
     * CaretLauncher: the search field is not on the sheet (the caller checks it), and a touch above
     * the sheet is not on the container's scrim but on the field or the status bar, so it does not
     * close the list.
     */
    fun shouldContainerScroll(ev: MotionEvent, eventSource: View): Boolean {
        val point = floatArrayOf(ev.x, ev.y)
        Utilities.mapCoordInSelfToDescendant(this, eventSource, point)
        if (point[1] < 0f) return false
        if (point[1] < handleAreaHeight) return true
        val results = searchResults
        if (results != null && results.isVisible) {
            // FastScrollRecyclerView.shouldContainerScroll: the search results have no scrollbar.
            return results.computeVerticalScrollOffset() == 0
        }
        return list?.shouldContainerScroll(ev, eventSource) ?: true
    }

    /** ActivityAllAppsContainerView.getBackgroundColor for whether the blur is on. */
    fun setBlurEnabled(enabled: Boolean) {
        sheetBackground.setColor(
            if (enabled) {
                ColorUtils.compositeColors(
                    context.getColor(R.color.blur_shade_panel_fg),
                    context.getColor(R.color.blur_shade_panel_bg),
                )
            } else {
                context.getColor(
                    if (Utilities.isDarkTheme(context)) android.R.color.system_accent2_800 else android.R.color.system_accent2_200,
                )
            },
        )
    }
}
