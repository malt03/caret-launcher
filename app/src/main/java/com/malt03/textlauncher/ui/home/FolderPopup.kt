package com.malt03.textlauncher.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.FloatProperty
import androidx.dynamicanimation.animation.DynamicAnimation
import com.android.launcher3.anim.SpringAnimationBuilder
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import com.google.android.material.color.MaterialColors
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.NoPressDelayGridLayout
import kotlin.math.ceil
import kotlin.math.min

/**
 * An open folder (docs/spec.md 5.2): the apps inside, icons only. Tapping outside closes it.
 * TODO(Tex-ebnm): open/close animation following Launcher3's FolderAnimationManager.
 */
class FolderPopup(
    private val overlay: FrameLayout,
    anchor: View,
    entries: List<AppEntry>,
    grid: GridSpec,
    iconLoader: AppIconLoader,
    onClick: (AppEntry, View) -> Unit,
    onLongPress: (AppEntry, View) -> Unit,
    /** The views behind the folder that shrink while it is open (Launcher3: workspace, hotseat). */
    private val backgroundViews: List<View>,
    private val onDismiss: () -> Unit,
) {
    private val context = overlay.context
    private val density = context.resources.displayMetrics.density
    private val scrim = View(context).apply {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        setOnClickListener { dismiss() }
    }
    private var dismissed = false
    private var backgroundAnimators = AnimatorSet()
    private val card = NoPressDelayGridLayout(context)

    init {
        val columns = min(grid.columns, maxOf(2, ceil(Math.sqrt(entries.size.toDouble())).toInt()))
        val cellSize = grid.iconSizePx + (24 * density).toInt()
        val padding = (16 * density).toInt()
        card.columnCount = columns
        card.setPadding(padding, padding, padding, padding)
        card.background = GradientDrawable().apply {
            cornerRadius = 28 * density
            setColor(MaterialColors.getColor(overlay, com.google.android.material.R.attr.colorSurfaceContainer))
        }
        card.elevation = 8 * density
        for (entry in entries) {
            val icon = AppIconView(context, grid.iconSizePx)
            iconLoader.load(entry) { info -> icon.icon = iconLoader.newDrawable(info) }
            icon.setOnClickListener {
                dismiss()
                onClick(entry, icon)
            }
            icon.setOnLongClickListener {
                onLongPress(entry, icon)
                true
            }
            card.addView(icon, GridLayout.LayoutParams().apply { width = cellSize; height = cellSize })
        }
        overlay.addView(scrim, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val rows = ceil(entries.size / columns.toDouble()).toInt()
        val cardWidth = columns * cellSize + padding * 2
        val cardHeight = rows * cellSize + padding * 2
        overlay.addView(card, FrameLayout.LayoutParams(cardWidth, cardHeight))

        val overlayLocation = IntArray(2).also { overlay.getLocationOnScreen(it) }
        val anchorLocation = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val centerY = anchorLocation[1] - overlayLocation[1] + anchor.height / 2
        card.x = (overlay.width - cardWidth) / 2f
        card.y = (centerY - cardHeight / 2f).coerceIn(padding.toFloat(), (overlay.height - cardHeight - padding).toFloat())
        card.alpha = 0f
        card.scaleX = 0.9f
        card.scaleY = 0.9f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        playBackgroundAnimation(isOpening = true)
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        onDismiss()
        card.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150).start()
        playBackgroundAnimation(isOpening = false) {
            overlay.removeView(scrim)
            overlay.removeView(card)
        }
    }

    /**
     * FolderSpringAnimatorSet.addWorkspaceAnimators (android-17.0.0_r1, expressive folder
     * expansion): a black scrim fades to 32% (dark theme) / 20% (light theme) and the views behind
     * shrink to LAUNCHER_SCALE, both on a spring (stiffness 380, damping 0.98).
     */
    private fun playBackgroundAnimation(isOpening: Boolean, onEnd: () -> Unit = {}) {
        backgroundAnimators.cancel()
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val finalScrimAlpha = if (isDark) 0.32f else 0.2f
        val set = AnimatorSet()
        set.play(spring(scrim.alpha, if (isOpening) finalScrimAlpha else 0f, DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA)
            .build(scrim, VIEW_ALPHA))
        for (view in backgroundViews) {
            set.play(spring(view.scaleX, if (isOpening) LAUNCHER_SCALE else 1f, DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
                .build(view, VIEW_SCALE))
        }
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = onEnd()
        })
        backgroundAnimators = set
        set.start()
    }

    private fun spring(from: Float, to: Float, minVisibleChange: Float) = SpringAnimationBuilder(context)
        .setStiffness(STIFFNESS_LAUNCHER_SCRIM)
        .setDampingRatio(DAMPING_LAUNCHER_SCRIM)
        .setStartValue(from)
        .setEndValue(to)
        .setMinimumVisibleChange(minVisibleChange)

    private companion object {
        // FolderSpringAnimatorSet
        const val LAUNCHER_SCALE = 0.975f
        const val STIFFNESS_LAUNCHER_SCRIM = 380f
        const val DAMPING_LAUNCHER_SCRIM = 0.98f

        val VIEW_ALPHA = object : FloatProperty<View>("alpha") {
            override fun setValue(view: View, value: Float) {
                view.alpha = value
            }
            override fun get(view: View): Float = view.alpha
        }
        val VIEW_SCALE = object : FloatProperty<View>("scale") {
            override fun setValue(view: View, value: Float) {
                view.scaleX = value
                view.scaleY = value
            }
            override fun get(view: View): Float = view.scaleX
        }
    }
}
