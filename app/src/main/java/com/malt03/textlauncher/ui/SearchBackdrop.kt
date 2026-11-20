package com.malt03.textlauncher.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.Window
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

/**
 * Blurs and dims the wallpaper while search results are shown.
 *
 * Blur: Window.setBackgroundBlurRadius (the whole window; per-view backdrop blur has no public
 * API). Radius from Launcher3 res folder_blur_radius / popup_blur_radius (20dp).
 * Dim: Launcher3's allAppsScrimColor (materialColorSurfaceDim).
 */
class SearchBackdrop(private val window: Window, private val target: View) {
    private val maxBlurPx = 20 * target.resources.displayMetrics.density
    private val scrimColor = MaterialColors.getColor(target, com.google.android.material.R.attr.colorSurfaceDim)
    private var progress = 0f
    private var visible = false
    private var animator: ValueAnimator? = null

    fun setVisible(show: Boolean) {
        if (show == visible) return
        visible = show
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, if (show) 1f else 0f).apply {
            duration = DURATION_MS
            addUpdateListener { apply(it.animatedValue as Float) }
            start()
        }
    }

    private fun apply(value: Float) {
        progress = value
        window.setBackgroundBlurRadius((maxBlurPx * value).toInt())
        target.setBackgroundColor(ColorUtils.setAlphaComponent(scrimColor, (MAX_SCRIM_ALPHA * value).toInt()))
    }

    private companion object {
        const val DURATION_MS = 200L
        const val MAX_SCRIM_ALPHA = 0.55f * 255
    }
}
