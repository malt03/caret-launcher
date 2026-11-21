package com.malt03.textlauncher.ui.home

import android.content.pm.ShortcutInfo
import android.view.View
import com.android.launcher3.DeviceProfile
import com.android.launcher3.dagger.ActivityComponent
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupDataProvider
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.malt03.textlauncher.data.ShortcutItemInfo

/**
 * Launcher as the ActivityContext of the copied folder, drag and long-press menu code: the device
 * profile, the drag layer and controller, the item click handler, and what the menu's shortcut rows
 * ask of the launcher (DeepShortcutView.WorkspaceHost).
 */
internal class HomeActivityContext(private val home: HomeController) : ActivityContext, DeepShortcutView.WorkspaceHost {
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
    override fun getDragLayer(): BaseDragLayer<*> = home.root.overlay
    override fun asContext() = context

    /** Edit mode hides the keyboard once the drag starts (docs/spec.md 5.4), not at the long press. */
    override fun hideKeyboard() = Unit

    override fun getDragController(): DragController = home.dragController

    /**
     * DeepShortcutView.getFinalInfo: the shortcut as it goes on the home screen, badged with
     * its app's icon (IconCache.getShortcutInfoBadge).
     */
    override fun makeWorkspaceItem(info: WorkspaceItemInfo, detail: ShortcutInfo): WorkspaceItemInfo {
        val app = home.app
        val key = app.shortcutRepository.keyOf(detail)
        val bitmap = app.iconLoader.cachedShortcut(key)
            ?: info.bitmap.withBadgeInfo(app.iconLoader.badgeFor(home.itemInflater.badgeAppOf(detail)))
        return ShortcutItemInfo(key, detail, context, bitmap)
    }

    override fun addToWorkspace(item: ItemInfo): Boolean = home.addToWorkspace(item)
}
