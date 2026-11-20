package com.malt03.textlauncher.ui.drag

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.malt03.textlauncher.R

/**
 * "Remove" and "Uninstall" drop targets at the top while dragging (docs/spec.md 5.3), like
 * Launcher3's DropTargetBar.
 */
@SuppressLint("ViewConstructor")
class DropTargetBar(context: Context) : LinearLayout(context) {
    enum class Target { REMOVE, UNINSTALL }

    private val remove = button(R.string.drop_target_remove)
    private val uninstall = button(R.string.drop_target_uninstall)
    private val normalColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
    private val hoverColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(remove, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(8) })
        addView(uninstall, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setHovered(null)
    }

    fun show(canUninstall: Boolean) {
        uninstall.visibility = if (canUninstall) View.VISIBLE else View.GONE
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(150).start()
    }

    fun hide() {
        animate().alpha(0f).setDuration(150).withEndAction { visibility = View.GONE }.start()
    }

    fun targetAt(rawX: Float, rawY: Float): Target? {
        if (visibility != View.VISIBLE) return null
        return when {
            contains(remove, rawX, rawY) -> Target.REMOVE
            contains(uninstall, rawX, rawY) -> Target.UNINSTALL
            else -> null
        }
    }

    fun setHovered(target: Target?) {
        (remove.background as GradientDrawable).setColor(if (target == Target.REMOVE) hoverColor else normalColor)
        (uninstall.background as GradientDrawable).setColor(if (target == Target.UNINSTALL) hoverColor else normalColor)
    }

    private fun contains(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val location = IntArray(2).also { view.getLocationOnScreen(it) }
        // The whole bar height counts, so the target is easy to hit.
        val barLocation = IntArray(2).also { getLocationOnScreen(it) }
        return Rect(location[0], barLocation[1], location[0] + view.width, barLocation[1] + height)
            .contains(rawX.toInt(), rawY.toInt())
    }

    private fun button(textRes: Int) = TextView(context).apply {
        setText(textRes)
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        background = GradientDrawable().apply { cornerRadius = dp(24).toFloat() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
