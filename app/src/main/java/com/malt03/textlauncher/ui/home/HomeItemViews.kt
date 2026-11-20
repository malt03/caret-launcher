package com.malt03.textlauncher.ui.home

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags
import com.android.launcher3.Reorderable
import com.android.launcher3.folder.ClippedFolderIconLayoutRule
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.folder.PreviewItemDrawingParams
import com.android.launcher3.folder.PreviewItemManager
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.views.ActivityContext

/** A view on the home grid. Launcher3's ReorderPreviewAnimation shakes it through [Reorderable]. */
interface HomeItemView : Reorderable {
    /** The icon's bounds inside this view. */
    fun iconLeft(): Int
    fun iconTop(): Int
}

/** BubbleTextView.setReorderBounceScale and friends, shared by the home item views. */
private class ReorderableState(private val view: View) {
    val translateDelegate = MultiTranslateDelegate(view)
    var bounceScale = 1f
        set(value) {
            field = value
            view.scaleX = value
            view.scaleY = value
        }
}

/**
 * An app on the home screen: icon only, no label (docs/spec.md 5.2). Stands in for Launcher3's
 * BubbleTextView. The pressed state is passed to the icon, so iconloaderlib's FastBitmapDrawable
 * plays Launcher3's click feedback.
 */
@SuppressLint("ViewConstructor")
class AppIconView(context: Context, private val iconSizePx: Int) : BubbleTextView(context), HomeItemView {
    private val reorderable = ReorderableState(this)

    var icon: Drawable? = null
        set(value) {
            field?.callback = null
            field = value
            value?.callback = this
            value?.state = drawableState
            invalidate()
        }

    override fun getIconSize(): Int = iconSizePx
    override fun iconLeft(): Int = (width - iconSizePx) / 2
    override fun iconTop(): Int = (height - iconSizePx) / 2

    override fun getTranslateDelegate(): MultiTranslateDelegate = reorderable.translateDelegate
    override fun setReorderBounceScale(scale: Float) {
        reorderable.bounceScale = scale
    }
    override fun getReorderBounceScale(): Float = reorderable.bounceScale

    override fun onDraw(canvas: Canvas) {
        val drawable = icon ?: return
        drawable.setBounds(iconLeft(), iconTop(), iconLeft() + iconSizePx, iconTop() + iconSizePx)
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
 * A folder on the home screen: no name (docs/spec.md 5.2). Stands in for Launcher3's FolderIcon:
 * the background is drawn by the copied PreviewBackground, and the preview items are placed by
 * the copied ClippedFolderIconLayoutRule, offset and clipped like PreviewItemManager.
 */
@SuppressLint("ViewConstructor")
class FolderIconView(
    context: Context,
    private val iconSizePx: Int,
    private val activityContext: ActivityContext,
) : FolderIcon(context), HomeItemView {
    private val reorderable = ReorderableState(this)

    var icons: List<Drawable> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val layoutRule = ClippedFolderIconLayoutRule()
    private val previewItemManager = PreviewItemManager { setupPreview() }
    private val params = PreviewItemDrawingParams()
    private val previewClip = android.graphics.Path()

    /** 0..1 while the folder is being created: items move from a full icon into the preview. */
    private var createProgress = 1f

    override fun getLayoutRule(): ClippedFolderIconLayoutRule = layoutRule
    override fun getPreviewItemManager(): PreviewItemManager = previewItemManager
    override fun iconLeft(): Int = (width - iconSizePx) / 2
    override fun iconTop(): Int = (height - iconSizePx) / 2

    override fun getTranslateDelegate(): MultiTranslateDelegate = reorderable.translateDelegate
    override fun setReorderBounceScale(scale: Float) {
        reorderable.bounceScale = scale
    }
    override fun getReorderBounceScale(): Float = reorderable.bounceScale

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupPreview()
    }

    /** PreviewItemManager.computePreviewDrawingParams */
    private fun setupPreview() {
        if (width == 0) return
        mBackground.setup(context, activityContext, this, width, iconTop())
        layoutRule.init(
            mBackground.radius * 2, iconSizePx.toFloat(), layoutDirection == LAYOUT_DIRECTION_RTL,
            activityContext.deviceProfile.folderProfile.numColumns,
        )
    }

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
        mBackground.drawBackground(canvas)
        // PreviewBackground.basePreviewOffsetX / Y are package private; they equal the icon bounds.
        val offsetX = iconLeft().toFloat()
        val offsetY = iconTop().toFloat()
        val shown = icons.take(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW)
        // PreviewItemManager.draw: clip the items to the folder shape.
        val count = canvas.save()
        // getClipPath() refills one shared Path; clip with a copy so a later call cannot change it.
        if (Flags.enableLauncherIconShapes()) {
            previewClip.set(mBackground.clipPath.path)
            canvas.clipPath(previewClip)
        }
        shown.forEachIndexed { index, drawable ->
            layoutRule.computePreviewItemDrawingParams(index, shown.size, params)
            var transX = params.transX
            var transY = params.transY
            var scale = params.scale
            if (createProgress < 1f && index == 0) {
                transX *= createProgress
                transY *= createProgress
                scale = 1f + (scale - 1f) * createProgress
            }
            canvas.save()
            canvas.translate(offsetX + transX, offsetY + transY)
            canvas.scale(scale, scale)
            drawable.setBounds(0, 0, iconSizePx, iconSizePx)
            if (createProgress < 1f && index > 0) drawable.alpha = (255 * createProgress).toInt()
            drawable.draw(canvas)
            drawable.alpha = 255
            canvas.restore()
        }
        canvas.restoreToCount(count)
    }

    private companion object {
        /** FolderIcon.DROP_IN_ANIMATION_DURATION */
        const val DROP_IN_ANIMATION_DURATION = 400L
    }
}
