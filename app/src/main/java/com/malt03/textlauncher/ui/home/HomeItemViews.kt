package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.folder.ClippedFolderIconLayoutRule
import com.android.launcher3.folder.PreviewItemDrawingParams
import com.google.android.material.color.MaterialColors

/** An app on the home screen: icon only, no label (docs/spec.md 5.2). */
@SuppressLint("ViewConstructor")
class AppIconView(context: Context, private val iconSize: Int) : View(context) {
    var icon: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val drawable = icon ?: return
        val left = (width - iconSize) / 2
        val top = (height - iconSize) / 2
        drawable.setBounds(left, top, left + iconSize, top + iconSize)
        drawable.draw(canvas)
    }

    /** The icon's bounds inside this view. */
    fun iconLeft(): Int = (width - iconSize) / 2
    fun iconTop(): Int = (height - iconSize) / 2
}

/**
 * A folder on the home screen: no name (docs/spec.md 5.2). The preview places up to four icons
 * with Launcher3's ClippedFolderIconLayoutRule on a round background (PreviewBackground).
 */
@SuppressLint("ViewConstructor")
class FolderIconView(context: Context, private val iconSize: Int) : View(context) {
    var icons: List<Drawable> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val layoutRule = ClippedFolderIconLayoutRule().also {
        it.init(iconSize, iconSize.toFloat(), layoutDirection == LAYOUT_DIRECTION_RTL, 2)
    }
    private val params = PreviewItemDrawingParams()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@FolderIconView, com.google.android.material.R.attr.colorSurfaceContainerHighest)
        alpha = 230
    }

    override fun onDraw(canvas: Canvas) {
        val left = (width - iconSize) / 2f
        val top = (height - iconSize) / 2f
        canvas.drawCircle(left + iconSize / 2f, top + iconSize / 2f, iconSize / 2f, backgroundPaint)
        val shown = icons.take(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW)
        shown.forEachIndexed { index, drawable ->
            layoutRule.computePreviewItemDrawingParams(index, shown.size, params)
            canvas.save()
            canvas.translate(left + params.transX, top + params.transY)
            canvas.scale(params.scale, params.scale)
            drawable.setBounds(0, 0, iconSize, iconSize)
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}
