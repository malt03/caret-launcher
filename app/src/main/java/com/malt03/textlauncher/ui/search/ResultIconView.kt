package com.malt03.textlauncher.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.util.Themes
import com.malt03.textlauncher.ui.GridSpec

/**
 * The all-apps BubbleTextView (all_apps_icon.xml): the icon, and the app name below it, in the
 * text style [onSurface] (the all apps list's sheet, BaseIcon.AllApps: materialColorOnSurface,
 * no shadow) or over the wallpaper (the search results, which take the home screen's place:
 * BaseIcon.Workspace.Shadows, workspaceTextColor with workspaceShadowColor).
 */
@SuppressLint("ViewConstructor")
class ResultIconView(context: Context, grid: GridSpec, onSurface: Boolean = false) :
    BubbleTextView(context, grid.iconSizePx, DISPLAY_ALL_APPS) {
    init {
        // BaseIconUnBounded: focusable, without the default focus highlight.
        isFocusable = true
        defaultFocusHighlightEnabled = false
        val density = resources.displayMetrics.density
        setCenterVertically(true)
        compoundDrawablePadding = (6 * density).toInt()
        val padding = (4 * density).toInt()
        setPadding(padding, padding, padding, padding)
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setTextSize(TypedValue.COMPLEX_UNIT_SP, grid.iconTextSizeSp)
        // BaseIcon: one line (not singleLine, which scrolls the text horizontally and moves the
        // focus outline with it).
        setLines(1)
        ellipsize = TextUtils.TruncateAt.END
        if (onSurface) {
            setTextColor(context.getColor(com.android.launcher3.R.color.materialColorOnSurface))
        } else {
            setTextColor(Themes.getAttrColor(context, com.android.launcher3.R.attr.workspaceTextColor))
            // android:shadowRadius 2.0 with ?attr/workspaceShadowColor
            setShadowLayer(SHADOW_RADIUS, 0f, 0f, Themes.getAttrColor(context, com.android.launcher3.R.attr.workspaceShadowColor))
        }
    }

    private companion object {
        /** BaseIcon.Workspace.Shadows: android:shadowRadius. */
        const val SHADOW_RADIUS = 2f
    }
}
