package com.malt03.textlauncher.ui.home

import android.view.View
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions

/**
 * An app dragged out of a folder: the drag source part of Launcher3's Folder (android-17.0.0_r1).
 * The open folder is gone once the drag starts (it is rebuilt each time it opens), so this keeps
 * what Folder keeps between onDragStart and onDropCompleted.
 *
 * - onDragStart: the app leaves the folder's contents.
 * - onDropCompleted: on success, a folder left with one app turns back into that app
 *   (Folder.replaceFolderWithFinalItem); on failure, the app goes back into the folder with
 *   FolderIcon.onDrop's animation.
 */
class FolderDragSource(
    val folderIcon: FolderIconView,
    private val dragController: DragController,
    /** LauncherDelegate.replaceFolderWithFinalItem */
    private val replaceFolderWithFinalItem: (FolderIconView) -> Unit,
) : DragSource, DragController.DragListener {
    private var isDragInProgress = false
    private var itemAddedBackToSelfViaIcon = false

    /** Folder.startDrag: listens for the drag start. */
    fun startDrag() {
        dragController.addDragListener(this)
    }

    /** FolderIcon calls this through Folder.notifyDrop. */
    fun notifyDrop() {
        if (isDragInProgress) {
            itemAddedBackToSelfViaIcon = true
        }
    }

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        if (dragObject.dragSource !== this) {
            return
        }
        isDragInProgress = true

        // We do not want to get events for the item being removed, as they will get handled
        // when the drop completes
        folderIcon.folder.removeFolderContent(true, dragObject.dragInfo)
        itemAddedBackToSelfViaIcon = false
    }

    override fun onDragEnd() {
        isDragInProgress = false
        dragController.removeDragListener(this)
    }

    override fun onDropCompleted(target: View?, d: DragObject, success: Boolean) {
        if (success) {
            if (folderIcon.folderInfo.contents.size <= 1 && !itemAddedBackToSelfViaIcon) {
                replaceFolderWithFinalItem(folderIcon)
            }
        } else if (folderIcon.parent != null && d.dragInfo !in folderIcon.folderInfo.contents) {
            // The drag failed, we need to return the item to the folder
            // TextLauncher: only once the drag had started; a long press that never moved did not
            // take the app out.
            folderIcon.onDrop(d, true /* itemReturnedOnFailedDrop */)
        }

        isDragInProgress = false
        itemAddedBackToSelfViaIcon = false
    }
}
