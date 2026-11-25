package com.malt03.caretlauncher.ui

import android.app.Activity
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.launcher3.R as L3R

/**
 * The look of the system Settings' pages for the app's own ones ([AppSettingsActivity] and
 * [LicensesActivity]): a round back button and the title at the top, then the page's content on
 * the surface color, scrolling under the navigation bar (the window is edge to edge).
 */
internal fun Activity.setSettingsPage(title: CharSequence, content: View) {
    val padding = dp(16)
    val back = ImageButton(this).apply {
        setImageDrawable(themeDrawable(android.R.attr.homeAsUpIndicator))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(getColor(L3R.color.materialColorSurfaceContainerHigh))
        }
        foreground = themeDrawable(android.R.attr.selectableItemBackgroundBorderless)
        // Launcher3's widget picker's label for its back button ("Back").
        contentDescription = getString(com.android.launcher3.widgetpicker.R.string.widget_search_bar_back_button_label)
        setOnClickListener { finish() }
    }
    val titleView = TextView(this).apply {
        text = title
        setTextColor(getColor(L3R.color.materialColorOnSurface))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        gravity = Gravity.CENTER_VERTICAL
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(padding, padding, padding, padding)
        addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = padding })
    }
    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(content, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    val scrollView = ScrollView(this).apply {
        clipToPadding = false
        setBackgroundColor(getColor(L3R.color.materialColorSurfaceContainer))
        addView(page)
        setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
    setContentView(scrollView)
}

/** A row of a Settings page: a rounded card with the title, which [onClick] opens. */
internal fun Activity.settingsRow(title: CharSequence, onClick: () -> Unit): View {
    val radius = dp(24).toFloat()
    return TextView(this).apply {
        text = title
        setTextColor(getColor(L3R.color.materialColorOnSurface))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(72)
        setPadding(dp(24), dp(16), dp(24), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(getColor(L3R.color.materialColorSurfaceBright))
        }
        foreground = themeDrawable(android.R.attr.selectableItemBackground)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
        setOnClickListener { onClick() }
    }
}

internal fun Activity.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

private fun Activity.themeDrawable(attr: Int) =
    obtainStyledAttributes(intArrayOf(attr)).let { a -> a.getDrawable(0).also { a.recycle() } }
