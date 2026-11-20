package com.malt03.textlauncher.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import com.android.launcher3.Launcher
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderAnimationSpringBuilderManager
import com.android.launcher3.folder.FolderGridOrganizer
import com.android.launcher3.folder.LauncherDelegate
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.ui.GridSpec

/**
 * An open folder (docs/spec.md 5.2): the apps inside, icons only. Tapping outside closes it.
 *
 * Extends the Launcher3 Folder stand-in so that Launcher3's expressive folder animation is used
 * as copied (FolderAnimationSpringBuilderManager, FolderSpringAnimatorSet, FolderAnimationData,
 * ClipRevealData, IconAnimationData, android-17.0.0_r1). Like Folder, it lays the apps out with
 * FolderGridOrganizer and sits in the drag layer at a custom position near its icon.
 */
@SuppressLint("ViewConstructor")
class OpenFolder(
    private val dragLayer: BaseDragLayer<*>,
    private val folderIconView: FolderIconView,
    items: List<AppItemInfo>,
    grid: GridSpec,
    iconLoader: AppIconLoader,
    activityContext: ActivityContext,
    private val launcher: Launcher,
    onClick: (AppEntry, View) -> Unit,
    onLongPress: (AppItemInfo, AppIconView) -> Unit,
    private val onDismiss: () -> Unit,
) : Folder(dragLayer.context, activityContext) {
    private val density = context.resources.displayMetrics.density

    /** Closes the folder on a tap outside (Launcher3: AbstractFloatingView touch handling). */
    private val outsideTouch = View(context).apply { setOnClickListener { dismiss() } }
    private var dismissed = false
    private var animator: AnimatorSet? = null

    init {
        mFolderIcon = folderIconView
        // Folder: R.drawable.round_rect_folder
        val drawable = ResourcesCompat.getDrawable(resources, com.android.launcher3.R.drawable.round_rect_folder, context.theme) as GradientDrawable
        mBackground.color = drawable.color
        mBackground.cornerRadius = drawable.cornerRadius

        // FolderGridOrganizer, as Folder sizes its pages.
        repeat(items.size) { mInfo.contents.add(ItemInfo()) }
        val organizer = FolderGridOrganizer.createFolderGridOrganizer(activityContext.deviceProfile).setFolderInfo(mInfo)
        val countX = organizer.countX
        val countY = organizer.countY
        val cellWidth = grid.iconSizePx + (24 * density).toInt()
        val cellHeight = cellWidth
        val padding = (16 * density).toInt()
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        items.forEachIndexed { rank, item ->
            val entry = item.entry
            val icon = AppIconView(context, grid.iconSizePx)
            icon.itemInfo = item
            iconLoader.load(entry) { info -> icon.iconDrawable = iconLoader.newDrawable(info) }
            icon.setOnClickListener {
                dismiss()
                onClick(entry, icon)
            }
            icon.setOnLongClickListener {
                onLongPress(item, icon)
                true
            }
            val pos = organizer.getPosForRank(rank)
            val lp = CellLayoutLayoutParams(pos.x, pos.y, 1, 1)
            lp.setup(cellWidth, cellHeight, rtl, countX, countY, Point(0, 0))
            mContent.addView(icon, lp)
        }
        mContent.setPadding(padding, padding, padding, padding)
        addView(mContent, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(mFooter, FrameLayout.LayoutParams(0, 0))

        val width = countX * cellWidth + padding * 2
        val height = countY * cellHeight + padding * 2
        // Folder.centerAboutIcon: centered horizontally, vertically on the icon, kept on screen.
        val iconRect = Rect()
        dragLayer.getDescendantRectRelativeToSelf(folderIconView, iconRect)
        val lp = BaseDragLayer.LayoutParams(width, height).apply {
            customPosition = true
            x = (dragLayer.width - width) / 2
            y = (iconRect.centerY() - height / 2).coerceIn(padding, dragLayer.height - height - padding)
        }
        dragLayer.addView(outsideTouch, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        dragLayer.addView(this, lp)
        visibility = View.INVISIBLE
        // Folder.animateOpen runs once the folder is laid out.
        post { if (!dismissed) animateOpen() }
    }

    private fun createAnimator(isOpening: Boolean): AnimatorSet =
        FolderAnimationSpringBuilderManager(
            this,
            ThemeManager.INSTANCE.get(context).folderShape,
            LauncherDelegate(launcher),
        ).createAnimatorSet(isOpening)

    /** Folder.animateOpen: the folder icon hides while its folder is open. */
    private fun animateOpen() {
        visibility = View.VISIBLE
        folderIconView.visibility = View.INVISIBLE
        animator = createAnimator(isOpening = true).also { it.start() }
    }

    /** Folder.animateClosed */
    fun dismiss() {
        if (dismissed) return
        dismissed = true
        onDismiss()
        animator?.cancel()
        dragLayer.removeView(outsideTouch)
        mIsAnimatingClosed = true
        createAnimator(isOpening = false).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mIsAnimatingClosed = false
                    folderIconView.visibility = View.VISIBLE
                    dragLayer.removeView(this@OpenFolder)
                }
            })
            start()
        }
    }
}
