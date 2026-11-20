package com.malt03.textlauncher.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.FloatProperty
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.launcher3.folder.ClippedFolderIconLayoutRule
import com.android.launcher3.folder.PreviewItemDrawingParams
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.NoPressDelayGridLayout
import kotlin.math.ceil
import kotlin.math.min

/**
 * An open folder (docs/spec.md 5.2): the apps inside, icons only. Tapping outside closes it.
 *
 * Opening and closing follow Launcher3's expressive folder animation (android-17.0.0_r1,
 * FolderAnimationSpringBuilderManager / FolderSpringAnimatorSet with FolderAnimationData,
 * ClipRevealData and IconAnimationData):
 * - the folder starts at the folder icon, scaled to the preview, and springs to its place
 * - its background is revealed from the icon's circle to the rounded rectangle, and its color
 *   changes from the preview color to the folder color
 * - each icon moves from its spot in the preview to its cell, 5ms after the previous one
 * - the home screen behind is dimmed and shrinks (addWorkspaceAnimators)
 * Closing plays the same springs backwards.
 */
class FolderPopup(
    private val overlay: FrameLayout,
    private val anchor: FolderIconView,
    entries: List<AppEntry>,
    private val grid: GridSpec,
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

    /** Launcher3's Folder: background and reveal clip. */
    private val folder = FrameLayout(context)

    /** Launcher3's Folder.content (pivot 0,0). */
    private val content = NoPressDelayGridLayout(context)
    private val icons = mutableListOf<AppIconView>()

    private val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private val background = GradientDrawable().apply { cornerRadius = 28 * density }
    private val cellSize = grid.iconSizePx + (24 * density).toInt()
    private val padding = (16 * density).toInt()
    private val columns = min(grid.columns, maxOf(2, ceil(Math.sqrt(entries.size.toDouble())).toInt()))
    private val layoutRule = ClippedFolderIconLayoutRule().also {
        it.init(grid.iconSizePx, grid.iconSizePx.toFloat(), overlay.layoutDirection == View.LAYOUT_DIRECTION_RTL, columns)
    }

    // Reveal clip (ShapeDelegate.createRevealAnimator): from the icon's circle to the folder.
    private val revealRect = RectF()
    private var revealRadius = 0f
    private val reveal = RevealProgress()

    private var dismissed = false
    private var animators = AnimatorSet()

    init {
        folder.background = background
        folder.elevation = 8 * density
        folder.clipToOutline = true
        folder.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    Rect(revealRect.left.toInt(), revealRect.top.toInt(), revealRect.right.toInt(), revealRect.bottom.toInt()),
                    revealRadius,
                )
            }
        }
        content.columnCount = columns
        content.setPadding(padding, padding, padding, padding)
        content.clipChildren = false
        content.clipToPadding = false
        content.pivotX = 0f
        content.pivotY = 0f
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
            icon.pivotX = 0f
            icon.pivotY = 0f
            icons += icon
            content.addView(icon, GridLayout.LayoutParams().apply { width = cellSize; height = cellSize })
        }
        folder.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        overlay.addView(scrim, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val rows = ceil(entries.size / columns.toDouble()).toInt()
        val width = columns * cellSize + padding * 2
        val height = rows * cellSize + padding * 2
        // The resting position goes in the layout params: translation is what the animation moves.
        val overlayLocation = IntArray(2).also { overlay.getLocationOnScreen(it) }
        val anchorLocation = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val centerY = anchorLocation[1] - overlayLocation[1] + anchor.height / 2
        overlay.addView(folder, FrameLayout.LayoutParams(width, height).apply {
            leftMargin = (overlay.width - width) / 2
            topMargin = (centerY - height / 2).coerceIn(padding, overlay.height - height - padding)
        })
        folder.visibility = View.INVISIBLE
        // Positions inside the folder are known after layout.
        folder.post { if (!dismissed) play(isOpening = true) }
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        onDismiss()
        play(isOpening = false) {
            overlay.removeView(scrim)
            overlay.removeView(folder)
        }
    }

    private fun play(isOpening: Boolean, onEnd: () -> Unit = {}) {
        animators.cancel()
        val set = AnimatorSet()

        // ---- FolderAnimationData ----
        val n = icons.size
        val previewScale = layoutRule.scaleForItem(min(n, ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW), 0)
        val iconScale = layoutRule.iconSize * previewScale / grid.iconSizePx
        // The icon area of FolderIconView is the preview background (a circle of iconSize).
        val initialFolderSize = grid.iconSizePx.toFloat()
        val initialFolderScale = iconScale
        val overlayLocation = IntArray(2).also { overlay.getLocationOnScreen(it) }
        val anchorLocation = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val previewLeft = anchorLocation[0] - overlayLocation[0] + anchor.iconLeft().toFloat()
        val previewTop = anchorLocation[1] - overlayLocation[1] + anchor.iconTop().toFloat()
        val contentOffset = padding * initialFolderScale
        val xDistance = previewLeft - contentOffset - folder.left
        val yDistance = previewTop - contentOffset - folder.top

        // ---- ClipRevealData ----
        val startRect = RectF(contentOffset, contentOffset, contentOffset + initialFolderSize, contentOffset + initialFolderSize)
        val endRect = RectF(0f, 0f, folder.width.toFloat(), folder.height.toFloat())
        reveal.set(startRect, endRect, initialFolderSize / 2, background.cornerRadius)

        // ---- setupFolder ----
        if (isOpening) {
            content.scaleX = initialFolderScale
            content.scaleY = initialFolderScale
            folder.translationX = xDistance
            folder.translationY = yDistance
            reveal.setValue(this, 0f)
            anchor.visibility = View.INVISIBLE
            folder.visibility = View.VISIBLE
        }

        // addFolderScaleAndTranslateAnimators
        set.play(spring(isOpening, xDistance, 0f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS).build(folder, TRANSLATE_X))
        set.play(spring(isOpening, yDistance, 0f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS).build(folder, TRANSLATE_Y))
        set.play(spring(isOpening, initialFolderScale, 1f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE).build(content, SCALE))
        set.play(
            spring(isOpening, -folder.elevation, 0f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
                .build(folder, TRANSLATE_Z)
                .apply { startDelay = if (isOpening) DEFAULT_DURATION / 2 else 0 },
        )

        // addClipRevealAnimators: the reveal follows the same spring as the folder's position.
        set.play(
            spring(isOpening, 0f, 1f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
                .build(this, reveal),
        )

        // addAlphaAndColorAnimators: folderPreviewColor -> folderBackgroundColor
        val previewColor = if (isDark) FOLDER_PREVIEW_DARK else FOLDER_PREVIEW_LIGHT
        val folderColor = if (isDark) FOLDER_BACKGROUND_DARK else FOLDER_BACKGROUND_LIGHT
        set.play(
            ObjectAnimator.ofArgb(background, "color", if (isOpening) previewColor else folderColor, if (isOpening) folderColor else previewColor)
                .apply { duration = DEFAULT_DURATION },
        )

        // addContentIconAnimators (IconAnimationData)
        val params = PreviewItemDrawingParams()
        val iconOffset = (cellSize - grid.iconSizePx) / 2f
        val localIconScale = iconScale / initialFolderScale
        var delay = if (isOpening) 0 else n * ICON_DELAY_INCREMENT
        icons.forEachIndexed { index, icon ->
            layoutRule.computeSpringAnimationItemParams(index, n, 0, columns, params)
            // Put the icon drawable on its preview spot while the content is scaled down.
            val startX = params.transX / initialFolderScale - localIconScale * iconOffset - icon.left + padding
            val startY = params.transY / initialFolderScale - localIconScale * iconOffset - icon.top + padding
            val inPreview = index < ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW
            if (isOpening) {
                icon.translationX = startX
                icon.translationY = startY
                icon.scaleX = localIconScale
                icon.scaleY = localIconScale
                if (!inPreview) icon.alpha = 0f
            }
            val iconDelay = delay.toLong()
            set.play(spring(isOpening, startX, 0f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS).build(icon, TRANSLATE_X).apply { startDelay = iconDelay })
            set.play(spring(isOpening, startY, 0f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS).build(icon, TRANSLATE_Y).apply { startDelay = iconDelay })
            set.play(spring(isOpening, localIconScale, 1f, STIFFNESS_SHAPE_POSITION, DAMPING_SHAPE_POSITION, DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE).build(icon, SCALE).apply { startDelay = iconDelay })
            if (!inPreview) {
                set.play(spring(isOpening, 0f, 1f, STIFFNESS_ALPHA, DAMPING_ALPHA, DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA).build(icon, ALPHA).apply { startDelay = iconDelay })
            }
            delay += if (isOpening) ICON_DELAY_INCREMENT else -ICON_DELAY_INCREMENT
        }

        // addWorkspaceAnimators
        val finalScrimAlpha = if (isDark) 0.32f else 0.2f
        set.play(spring(isOpening, 0f, finalScrimAlpha, STIFFNESS_LAUNCHER_SCRIM, DAMPING_LAUNCHER_SCRIM, DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA).build(scrim, ALPHA))
        for (view in backgroundViews) {
            set.play(spring(isOpening, 1f, LAUNCHER_SCALE, STIFFNESS_LAUNCHER_SCRIM, DAMPING_LAUNCHER_SCRIM, DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE).build(view, SCALE))
        }

        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isOpening) anchor.visibility = View.VISIBLE
                onEnd()
            }
        })
        animators = set
        set.start()
    }

    /** SpringAnimationBuilder with FolderSpringAnimatorSet.playSpringAnimation's reversal rule. */
    private fun spring(isOpening: Boolean, start: Float, end: Float, stiffness: Float, damping: Float, minVisibleChange: Float) =
        SpringAnimationBuilder(context)
            .setStiffness(stiffness)
            .setDampingRatio(damping)
            .setStartValue(if (isOpening) start else end)
            .setEndValue(if (isOpening) end else start)
            .setMinimumVisibleChange(minVisibleChange)

    /** Progress of ShapeDelegate's reveal: rect and corner radius from the icon to the folder. */
    private inner class RevealProgress : FloatProperty<FolderPopup>("reveal") {
        private val start = RectF()
        private val end = RectF()
        private var startRadius = 0f
        private var endRadius = 0f
        private var progress = 0f

        fun set(start: RectF, end: RectF, startRadius: Float, endRadius: Float) {
            this.start.set(start)
            this.end.set(end)
            this.startRadius = startRadius
            this.endRadius = endRadius
        }

        override fun setValue(target: FolderPopup, value: Float) {
            progress = value
            revealRect.set(
                start.left + (end.left - start.left) * value,
                start.top + (end.top - start.top) * value,
                start.right + (end.right - start.right) * value,
                start.bottom + (end.bottom - start.bottom) * value,
            )
            revealRadius = startRadius + (endRadius - startRadius) * value
            folder.invalidateOutline()
        }

        override fun get(target: FolderPopup): Float = progress
    }

    private companion object {
        // FolderSpringAnimatorSet
        const val LAUNCHER_SCALE = 0.975f
        const val STIFFNESS_SHAPE_POSITION = 380f
        const val DAMPING_SHAPE_POSITION = 0.8f
        const val STIFFNESS_ALPHA = 1600f
        const val DAMPING_ALPHA = 0.9f
        const val STIFFNESS_LAUNCHER_SCRIM = 380f
        const val DAMPING_LAUNCHER_SCRIM = 0.98f
        // res/values/config.xml config_materialFolderExpandDuration
        const val DEFAULT_DURATION = 200L
        // IconAnimationData.ICON_DELAY_INCREMENT
        const val ICON_DELAY_INCREMENT = 5
        // res/values/colors.xml folder_preview_* / folder_background_*
        val FOLDER_PREVIEW_LIGHT = Color.parseColor("#7FCFFF")
        val FOLDER_PREVIEW_DARK = Color.parseColor("#1E1F20")
        val FOLDER_BACKGROUND_LIGHT = Color.parseColor("#EFEDED")
        val FOLDER_BACKGROUND_DARK = Color.parseColor("#1F2020")

        val ALPHA = viewProperty("alpha", View::setAlpha, View::getAlpha)
        val TRANSLATE_X = viewProperty("translationX", View::setTranslationX, View::getTranslationX)
        val TRANSLATE_Y = viewProperty("translationY", View::setTranslationY, View::getTranslationY)
        val TRANSLATE_Z = viewProperty("translationZ", View::setTranslationZ, View::getTranslationZ)
        val SCALE = viewProperty("scale", { v, s -> v.scaleX = s; v.scaleY = s }, View::getScaleX)

        fun viewProperty(name: String, set: (View, Float) -> Unit, get: (View) -> Float) = object : FloatProperty<View>(name) {
            override fun setValue(view: View, value: Float) = set(view, value)
            override fun get(view: View): Float = get(view)
        }
    }
}
