package com.malt03.textlauncher.ui.home

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.Reorderable
import com.android.launcher3.folder.ClippedFolderIconLayoutRule
import com.android.launcher3.folder.PreviewItemDrawingParams
import com.android.launcher3.util.MultiTranslateDelegate

/**
 * Base of the views on the home grid. Implements Launcher3's [Reorderable] so that the copied
 * ReorderPreviewAnimation can shake it, the same way BubbleTextView does.
 */
abstract class HomeItemView(context: Context, protected val iconSize: Int) : View(context), Reorderable {
    private val translateDelegate = MultiTranslateDelegate(this)
    private var reorderBounceScale = 1f

    override fun getTranslateDelegate(): MultiTranslateDelegate = translateDelegate

    /** BubbleTextView.setReorderBounceScale */
    override fun setReorderBounceScale(scale: Float) {
        reorderBounceScale = scale
        scaleX = scale
        scaleY = scale
    }

    override fun getReorderBounceScale(): Float = reorderBounceScale

    /** The icon's bounds inside this view. */
    fun iconLeft(): Int = (width - iconSize) / 2
    fun iconTop(): Int = (height - iconSize) / 2
}

/**
 * An app on the home screen: icon only, no label (docs/spec.md 5.2).
 * The view's pressed state is passed to the icon, so iconloaderlib's FastBitmapDrawable plays
 * Launcher3's click feedback (PRESSED_SCALE over CLICK_FEEDBACK_DURATION).
 */
@SuppressLint("ViewConstructor")
class AppIconView(context: Context, iconSize: Int) : HomeItemView(context, iconSize) {
    var icon: Drawable? = null
        set(value) {
            field?.callback = null
            field = value
            value?.callback = this
            value?.state = drawableState
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val drawable = icon ?: return
        drawable.setBounds(iconLeft(), iconTop(), iconLeft() + iconSize, iconTop() + iconSize)
        drawable.draw(canvas)
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === icon || super.verifyDrawable(who)

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        icon?.let { if (it.isStateful && it.setState(drawableState)) invalidate() }
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        icon?.jumpToCurrentState()
    }
}

/**
 * A folder on the home screen: no name (docs/spec.md 5.2). The preview places up to four icons
 * with Launcher3's ClippedFolderIconLayoutRule on a round background (PreviewBackground).
 */
@SuppressLint("ViewConstructor")
class FolderIconView(context: Context, iconSize: Int) : HomeItemView(context, iconSize) {
    var icons: List<Drawable> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val layoutRule = ClippedFolderIconLayoutRule().also {
        it.init(iconSize, iconSize.toFloat(), layoutDirection == LAYOUT_DIRECTION_RTL, 2)
    }
    private val params = PreviewItemDrawingParams()
    /**
     * Launcher3's folderPreviewColor, the same color the open folder starts from and closes to,
     * so the folder turns back into this icon without a jump.
     */
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        color = if (isDark) FOLDER_PREVIEW_DARK else FOLDER_PREVIEW_LIGHT
    }

    /** 0..1 while the folder is being created: items move from a full icon into the preview. */
    private var createProgress = 1f

    /**
     * FolderIcon's create animation: the first item shrinks from a full icon into its preview
     * position while the others arrive (DROP_IN_ANIMATION_DURATION = 400ms).
     */
    fun playCreateAnimation() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DROP_IN_ANIMATION_DURATION
            interpolator = com.android.app.animation.Interpolators.DECELERATE_1_5
            addUpdateListener {
                createProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val left = iconLeft().toFloat()
        val top = iconTop().toFloat()
        canvas.drawCircle(left + iconSize / 2f, top + iconSize / 2f, iconSize / 2f, backgroundPaint)
        val shown = icons.take(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW)
        shown.forEachIndexed { index, drawable ->
            layoutRule.computePreviewItemDrawingParams(index, shown.size, params)
            var transX = params.transX
            var transY = params.transY
            var scale = params.scale
            if (createProgress < 1f && index == 0) {
                // From the full icon (translation 0, scale 1) to the preview position.
                transX *= createProgress
                transY *= createProgress
                scale = 1f + (scale - 1f) * createProgress
            }
            canvas.save()
            canvas.translate(left + transX, top + transY)
            canvas.scale(scale, scale)
            drawable.setBounds(0, 0, iconSize, iconSize)
            if (createProgress < 1f && index > 0) drawable.alpha = (255 * createProgress).toInt()
            drawable.draw(canvas)
            drawable.alpha = 255
            canvas.restore()
        }
    }

    companion object {
        // Launcher3 res/values/colors.xml folder_preview_light / folder_preview_dark
        val FOLDER_PREVIEW_LIGHT = android.graphics.Color.parseColor("#7FCFFF")
        val FOLDER_PREVIEW_DARK = android.graphics.Color.parseColor("#1E1F20")

        /** FolderIcon.DROP_IN_ANIMATION_DURATION */
        private const val DROP_IN_ANIMATION_DURATION = 400L
    }
}
