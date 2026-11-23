package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.LauncherDelegate
import com.android.launcher3.views.ActivityContext

/**
 * The folder of a folder icon on the home screen (docs/spec.md 5.2): the apps and shortcuts inside,
 * icons only. Launcher3's Folder (the stand-in) opens, closes and drags with the copied expressive
 * folder animation; this class gives it TextLauncher's look, its item views, its focus rule and when a failed
 * drag returns its item.
 */
@SuppressLint("ViewConstructor")
class HomeFolder(
    context: Context,
    activityContext: ActivityContext,
    launcherDelegate: LauncherDelegate,
    private val iconSizePx: Int,
) : Folder(context, activityContext, launcherDelegate) {

    init {
        // Folder is focusable in touch mode, so that it takes the focus back from its name field.
        // TextLauncher's folders have no name, and a touch must not take the focus from the search
        // field (docs/spec.md 1, 2.6): only keys give it the focus.
        isFocusableInTouchMode = false

        val padding = (16 * resources.displayMetrics.density).toInt()
        mContent.setPadding(padding, padding, padding, padding)
    }

    /**
     * Launcher3's open folder always has the focus. This one has it only when it was opened with
     * keys and nothing took it since (the home button gives it to the search field), and only
     * then does it go back to the icon.
     */
    override fun shouldFocusIconOnClose(hadFocus: Boolean): Boolean = hadFocus

    /**
     * Only while the folder icon is still on the home screen: the home model may have replaced the
     * folder during the drag (Tex-a8os).
     */
    override fun canReturnItemOnFailedDrop(d: DropTarget.DragObject): Boolean =
        folderIcon.parent != null

    /** R.layout.folder_application: an icon without a label, focusable like Launcher3's icons. */
    override fun inflateIcon(): BubbleTextView = AppIconView(context, iconSizePx).apply {
        isFocusable = true
        defaultFocusHighlightEnabled = false
    }
}
