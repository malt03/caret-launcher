package com.malt03.textlauncher.ui.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
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
 * TextLauncher: the search field is not on the sheet; it sits right above it (docs/spec.md 6.4).
 */
@SuppressLint("ViewConstructor")
class AllAppsSheet(context: Context) : FrameLayout(context) {
    private val radius = Themes.getDialogCornerRadius(context)
    private val sheetBackground = GradientDrawable().apply {
        // mBottomSheetCornerRadii: the top corners only.
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
    }

    /** bottom_sheet_handle_area_height: the list starts below it. */
    val handleAreaHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_handle_area_height)

    init {
        background = sheetBackground
        // bottom_sheet_handle with bg_rounded_corner_bottom_sheet_handle.
        addView(
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
    fun setList(list: View) {
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = handleAreaHeight
        })
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
