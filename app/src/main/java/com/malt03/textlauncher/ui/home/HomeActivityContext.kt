package com.malt03.textlauncher.ui.home

import android.content.pm.ShortcutInfo
import android.view.View
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherModel
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate
import com.android.launcher3.dagger.ActivityComponent
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupDataProvider
import java.util.function.Consumer

/**
 * Launcher of the home screen, which is also the ActivityContext of the copied code as in
 * Launcher3: the device profile, the drag layer and controller, the item click handler, and the
 * model and accessibility delegate the long-press menu's shortcut rows use.
 */
internal class HomeActivityContext(private val home: HomeController) :
    Launcher(home.root.overlay, home.workspace, home.root.hotseat, home.root.scrim),
    LauncherModel,
    LauncherAccessibilityDelegate {
    private val context = home.context
    private val gridSpec = home.gridSpec

    /** The open folder's cells: the icon with Launcher3's folder cell padding around it. */
    private val folderCellSizePx = gridSpec.iconSizePx + (24 * context.resources.displayMetrics.density).toInt()

    private val deviceProfile = DeviceProfile(
        DeviceProfile.FolderProfile(
            gridSpec.iconSizePx, 0, gridSpec.folderColumns, gridSpec.folderRows, gridSpec.iconSizePx,
            folderCellSizePx, folderCellSizePx,
        ),
        // No labels (docs/spec.md 5.2): the icons are centered in their cells.
        DeviceProfile.WorkspaceProfile(gridSpec.iconSizePx, /* iconCenterVertically= */ true),
        DeviceProfile.DeviceProperties(context.resources.displayMetrics.heightPixels),
    )
    private val activityComponent = ActivityComponent().apply {
        setPopupDataProvider(PopupDataProvider(context))
    }
    override fun getDeviceProfile(): DeviceProfile = deviceProfile
    override fun getActivityComponent(): ActivityComponent = activityComponent

    /**
     * ItemClickHandler for the items in folders and the long-press menu's app shortcuts
     * (onClickAppShortcut).
     */
    override fun getItemOnClickListener(): View.OnClickListener = View.OnClickListener { v ->
        val info = v.tag as? WorkspaceItemInfo ?: return@OnClickListener
        home.itemInflater.onClick(info, v)
    }
    override fun asContext() = context

    /** Edit mode hides the keyboard once the drag starts (docs/spec.md 5.4), not at the long press. */
    override fun hideKeyboard() = Unit

    override fun getDragController(): DragController = home.dragController

    override fun getAccessibilityDelegate(): LauncherAccessibilityDelegate = this

    override fun getModel(): LauncherModel = this

    /**
     * LauncherModel.updateAndBindWorkspaceItem for DeepShortcutView.getFinalInfo: the shortcut's
     * title, and its icon badged with its app's icon (IconCache.getShortcutIcon). TextLauncher: done
     * right away; the item is not on the home screen yet, so there is nothing to bind.
     */
    override fun updateAndBindWorkspaceItem(si: WorkspaceItemInfo, info: ShortcutInfo) {
        si.updateFromDeepShortcutInfo(info, context)
        si.bitmap = home.badgedShortcutIcon(si, info)
    }

    /** LauncherAccessibilityDelegate.addToWorkspace for the menu's add button. */
    override fun addToWorkspace(item: ItemInfo, accessibility: Boolean, finishCallback: Consumer<Boolean>?): Boolean {
        val added = home.addToWorkspace(item)
        finishCallback?.accept(added)
        return added
    }
}
