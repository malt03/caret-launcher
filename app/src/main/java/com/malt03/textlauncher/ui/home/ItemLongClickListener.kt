package com.malt03.textlauncher.ui.home

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DragSource
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R as L3R
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.popup.PopupData
import com.android.launcher3.popup.PopupItemDragHandler
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AppItemInfo

/**
 * ItemLongClickListener with PopupControllerForAppIcon and LauncherPopupItemDragHandler: a long
 * press on an item shows its menu (docs/spec.md 5.3), and moving the finger starts the drag.
 */
internal class ItemLongClickListener(private val home: HomeController) {
    private val context = home.context
    private val activityContext get() = home.activityContext

    /** ActivityAllAppsContainerView as a DragSource: nothing to do when an app from search drops. */
    private val searchDragSource = DragSource { _, _, _ -> }

    /** ItemLongClickListener.canStartDrag. */
    private fun canStartDrag(): Boolean {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        if (home.launcher.isWorkspaceLocked) return false
        // Return early if an item is already being dragged (e.g. when long-pressing two shortcuts)
        if (home.dragController.isDragging) return false
        // TextLauncher: no split-screen selection.
        return true
    }

    /**
     * ItemLongClickListener.INSTANCE_ALL_APPS: a long press on an app in the all apps list or the
     * search results shows its menu, and a drag starts once the finger moves.
     */
    fun onAllAppsItemLongPress(iconView: BubbleTextView) {
        val app = iconView.tag as? AppInfo ?: return
        if (!canStartDrag()) return
        // AppInfo.makeWorkspaceItem: the home screen gets its own item, not the list's AppInfo.
        val info = AppItemInfo(app, UserCache.INSTANCE.get(context).getSerialNumberForUser(app.user))
        home.homeWorkspace.beginDragShared(
            iconView, searchDragSource, info, DragPreviewProvider(iconView), DragOptions(),
            startLongPressAction(iconView, info),
        )
    }

    fun onHomeItemLongPress(view: AppIconView) {
        if (!canStartDrag()) return
        val info = view.tag as? WorkspaceItemInfo ?: return
        home.homeWorkspace.startDrag(view, info, DragOptions(), startLongPressAction(view, info))
    }

    /**
     * ItemLongClickListener.INSTANCE_WORKSPACE for a folder. With Flags.homeScreenEditImprovements
     * (enabled in the release), Workspace.startDrag shows the folder's menu
     * (PopupControllerForExtraHomeScreenItems), and the drag starts once the finger moves.
     */
    fun onHomeFolderLongPress(view: FolderIconView) {
        if (!canStartDrag()) return
        val info = view.folderInfo
        home.homeWorkspace.startDrag(view, info, DragOptions()) { showExtraHomeScreenItemPopup(view, info) }
    }

    /**
     * ItemLongClickListener.INSTANCE_WORKSPACE for a widget. With Flags.homeScreenEditImprovements
     * (enabled in the release), Workspace.startDrag shows the widget's menu and its resize frame
     * (PopupControllerForExtraHomeScreenItems), and the drag starts once the finger moves; until
     * then the widget stays where it is, and then it shrinks to its spring-loaded size
     * (Workspace.beginDragShared's deferDragToPreDragEnd and preDragEndScale).
     */
    fun onHomeWidgetLongPress(view: LauncherAppWidgetHostView) {
        if (!canStartDrag()) return
        val info = view.tag as? ItemInfo ?: return
        home.homeWorkspace.startDrag(view, info, DragOptions()) { showExtraHomeScreenItemPopup(view, info) }
    }

    /**
     * PopupControllerForExtraHomeScreenItems.show and Popup.createPreDragCondition: the menu with
     * the item's actions, and a widget's resize frame.
     */
    private fun showExtraHomeScreenItemPopup(view: View, itemInfo: ItemInfo): DragOptions.PreDragCondition {
        val container = PopupContainer.create<ActivityContext>(
            context = view.context,
            originalView = view,
            itemInfo = itemInfo,
        )
        home.dragController.addDragListener(container)
        getAllSupportedPopupActions(itemInfo, view)?.let {
            container.showForSystemShortcuts(it, activityContext, view)
        }
        // showResizeFrameIfNeeded with DefaultPopupResizeStrategy: a widget on the home screen.
        if (view is LauncherAppWidgetHostView) AppWidgetResizeFrame.showForWidget(view, home.grid.cellLayout)
        return container.createPreDragCondition()
    }

    /**
     * PopupDataRepository.getAllSupportedPopupActions with the home screen's mappers:
     * FolderSystemShortcuts and AppWidgetSystemShortcuts (TextLauncher has no app pairs or files).
     */
    private fun getAllSupportedPopupActions(itemInfo: ItemInfo, view: View): List<PopupData>? =
        listOfNotNull(folderSystemShortcuts(itemInfo), appWidgetSystemShortcuts(itemInfo, view))
            .flatten().ifEmpty { null }

    /** PopupDataSource.removePopupData: "Remove". */
    private val removePopupData = PopupData(
        L3R.drawable.ic_remove_no_shadow, L3R.string.remove_system_shortcut_label, PopupCategory.SYSTEM_SHORTCUT_FIXED,
    ) { activityContext, _, v ->
        AbstractFloatingView.closeAllOpenViews(activityContext)
        // TextLauncher: no undo (DropTargetHandler.prepareToUndoDelete).
        home.removeHomeItem(v)
    }

    /** FolderSystemShortcuts.getPopupDataByItemInfo: "Remove". */
    private fun folderSystemShortcuts(itemInfo: ItemInfo): List<PopupData>? =
        if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) listOf(removePopupData) else null

    /**
     * AppWidgetSystemShortcuts.getPopupDataByItemInfo: "Remove", and "Settings" for a
     * reconfigurable widget.
     */
    private fun appWidgetSystemShortcuts(itemInfo: ItemInfo, view: View): List<PopupData>? {
        if (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) return null
        // LauncherAppWidgetInfo.isReconfigurable (LauncherAppWidgetProviderInfo.isReconfigurable)
        val info = (view as? LauncherAppWidgetHostView)?.appWidgetInfo
        return if (info != null && info.configure != null &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        ) {
            listOf(removePopupData, widgetSettingsPopupData)
        } else {
            listOf(removePopupData)
        }
    }

    /** AppWidgetSystemShortcuts.widgetSettingsPopupData: "Settings". */
    private val widgetSettingsPopupData = PopupData(
        L3R.drawable.ic_setting, L3R.string.widget_settings, PopupCategory.SYSTEM_SHORTCUT_FIXED,
    ) { _, _, v ->
        if (v is LauncherAppWidgetHostView) home.widgetAddFlow.startReconfigure(v)
    }

    /**
     * BubbleTextView.startLongPressAction with PopupControllerForAppIcon.show: shows the item's
     * long-press menu (docs/spec.md 5.3) and returns the menu's pre-drag condition. Moving more
     * than deep_shortcuts_start_drag_threshold closes the menu and starts the drag.
     */
    fun startLongPressAction(originalView: View, item: WorkspaceItemInfo): () -> DragOptions.PreDragCondition? = {
        if (PopupContainer.getOpen(activityContext) != null) {
            // There is already an items container open, so don't open this one.
            originalView.clearFocus()
            null
        } else {
            // TextLauncher: the launcher's own options (docs/spec.md 5.3) instead of
            // ActivityContext.getSupportedShortcuts.
            val systemShortcuts = buildList<SystemShortcut<*>> {
                if (item is AppItemInfo && item.isAvailable) add(EditSearchTerms(item, originalView))
                add(SystemShortcut.AppInfo(activityContext, item, originalView))
            }
            val container = PopupContainerWithArrow.create<ActivityContext>(
                context = context,
                originalView = originalView,
                itemInfo = item,
            )
            // PopupContainerWithArrow.configureForLauncher
            container.setPopupItemDragHandler(LauncherPopupItemDragHandler(container))
            home.dragController.addDragListener(container)
            val deepShortcutCount = activityContext.activityComponent.popupDataProvider.getShortcutCountForItem(item)
            container.populateAndShowRows(deepShortcutCount, systemShortcuts)
            container.requestFocus()
            container.createPreDragCondition()
        }
    }

    /** The long-press menu's "Edit search terms" (docs/spec.md 4), a TextLauncher option. */
    private inner class EditSearchTerms(
        private val app: AppItemInfo,
        originalView: View,
    ) : SystemShortcut<ActivityContext>(
        com.android.launcher3.R.drawable.gm_edit_24, R.string.menu_search_terms, activityContext, app, originalView,
    ) {
        override fun onClick(view: View) {
            dismissTaskMenuView()
            home.host.editSearchTerms(app)
        }
    }

    /**
     * LauncherPopupItemDragHandler (popup/PopupItemDragHandler.kt): drags an app shortcut out of
     * the long-press menu onto the home screen.
     */
    private inner class LauncherPopupItemDragHandler(
        private val mContainer: PopupContainerWithArrow<*>,
    ) : PopupItemDragHandler {
        private val mIconLastTouchPos: Point = Point()

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            // Touched a shortcut, update where it was touched so we can drag from there on
            // long click.
            when (ev.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> mIconLastTouchPos[ev.x.toInt()] = ev.y.toInt()
            }
            return false
        }

        override fun onLongClick(v: View): Boolean {
            if (!canStartDrag()) return false
            // Return early if not the correct view
            if (v.parent !is DeepShortcutView) return false

            // Long clicked on a shortcut.
            val sv = v.parent as DeepShortcutView
            sv.setWillDrawIcon(false)

            // Move the icon to align with the center-top of the touch point
            val iconShift = Point()
            iconShift.x = mIconLastTouchPos.x - sv.iconCenter.x
            iconShift.y = mIconLastTouchPos.y - activityContext.deviceProfile.workspaceProfile.iconSizePx

            val draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON)
            val itemInfo = sv.finalInfo
            itemInfo.container = LauncherSettings.Favorites.CONTAINER_SHORTCUTS
            val dv =
                home.homeWorkspace.beginDragShared(
                    sv.iconView,
                    draggableView,
                    mContainer,
                    itemInfo,
                    ShortcutDragPreviewProvider(sv.iconView, iconShift),
                    DragOptions(),
                )
            dv.animateShift(-iconShift.x, -iconShift.y)

            // TODO: support dragging from within folder without having to close it
            home.closeFolders()
            return false
        }
    }
}
