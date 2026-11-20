package com.malt03.textlauncher.ui.home

import android.graphics.drawable.GradientDrawable
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
    private val onDismiss: () -> Unit,
) {
    private val context = overlay.context
    private val density = context.resources.displayMetrics.density
    private val scrim = View(context).apply { setOnClickListener { dismiss() } }
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
    }

    fun dismiss() {
        if (scrim.parent == null) return
        overlay.removeView(scrim)
        overlay.removeView(card)
        onDismiss()
    }
}
