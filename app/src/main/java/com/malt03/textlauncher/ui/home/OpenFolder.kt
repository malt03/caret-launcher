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
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.malt03.textlauncher.ui.GridSpec

/**
 * An open folder (docs/spec.md 5.2): the apps and shortcuts inside, icons only. Tapping outside closes it.
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
    items: List<WorkspaceItemInfo>,
    grid: GridSpec,
    activityContext: ActivityContext,
    private val launcher: Launcher,
    /** Sets the item's icon on the view, now or once it is loaded. */
    bindIcon: (WorkspaceItemInfo, AppIconView) -> Unit,
    onClick: (WorkspaceItemInfo, View) -> Unit,
    onLongPress: (WorkspaceItemInfo, AppIconView) -> Unit,
    private val onDismiss: () -> Unit,
) : Folder(dragLayer.context, activityContext) {
    private val density = context.resources.displayMetrics.density

    /** Closes the folder on a tap outside (Launcher3: AbstractFloatingView touch handling). */
    private val outsideTouch = View(context).apply { setOnClickListener { dismiss() } }
    private var dismissed = false
    private var animator: AnimatorSet? = null

    init {
        mFolderIcon = folderIconView
        // Folder is focusable in touch mode, so that it takes the focus back from its name field.
        // TextLauncher's folders have no name, and a touch must not take the focus from the search
        // field (docs/spec.md 1, 2.6): only keys give it the focus.
        isFocusableInTouchMode = false
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
            val icon = AppIconView(context, grid.iconSizePx)
            icon.itemInfo = item
            bindIcon(item, icon)
            // FolderPagedView.createNewView
            icon.setFocusableAsLauncher3Icon(mContent.focusIndicatorHelper)
            icon.setOnClickListener {
                dismiss()
                onClick(item, icon)
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
        // Folder.onAttachedToWindow's requestFocus: the folder is attached invisible until it is
        // laid out, which drops the focus, so it is asked again once shown. Opened by touch, the
        // search field keeps the focus and the keyboard stays up (not focusable in touch mode).
        requestFocus()
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
                    // Launcher3's open folder always has the focus. TextLauncher's has it only
                    // when it was opened with keys and nothing took it since (the home button
                    // gives it to the search field), and only then does it go back to the icon.
                    val hadFocus = hasFocus()
                    dragLayer.removeView(this@OpenFolder)
                    // Folder.closeComplete: the keyboard focus goes back to the folder icon.
                    clearFocus()
                    folderIconView.visibility = View.VISIBLE
                    if (hadFocus) folderIconView.requestFocus()
                }
            })
            start()
        }
    }
}
