package com.malt03.textlauncher.ui.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.content.pm.ShortcutInfo
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate
import com.android.launcher3.dagger.ActivityComponent
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupDataProvider
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.LauncherBindableItemsContainer
import com.android.launcher3.util.RunnableList
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.malt03.textlauncher.data.AllAppsAppInfo
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.data.isDeepShortcut
import com.malt03.textlauncher.ui.GridSpec
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

    private val deviceProfile = newDeviceProfile(context, gridSpec).also {
        // InvariantDeviceProfile: the grid and its profile, for the widget picker and the widget
        // sizes (LauncherAppWidgetProviderInfo.initSpans).
        LauncherAppState.getIDP(context).setDeviceProfile(gridSpec.columns, gridSpec.rows, it)
    }
    private val activityComponent = ActivityComponent().apply {
        // PopupDataProvider: the long-press menu's app shortcuts and the notification dots
        // (NotificationRepository, filled by NotificationListener).
        setPopupDataProvider(
            PopupDataProvider(
                context, this@HomeActivityContext,
                LauncherComponentProvider.get(context).notificationRepository, appsStore,
            ),
        )
    }

    /** Launcher.getContent (Workspace): the home screen's items, for the notification dots. */
    override fun getContent(): LauncherBindableItemsContainer =
        LauncherBindableItemsContainer { op -> home.mapOverItems(op) }
    override fun getDeviceProfile(): DeviceProfile = deviceProfile
    override fun getActivityComponent(): ActivityComponent = activityComponent

    /** ItemLongClickListener.INSTANCE_ALL_APPS: the menu, and a drag once the finger moves. */
    override fun getAllAppsItemLongClickListener(): View.OnLongClickListener = View.OnLongClickListener { v ->
        home.itemLongClickListener.onAllAppsItemLongPress(v as BubbleTextView)
        true
    }

    /** ItemClickHandler, for the items in folders and the long-press menu's app shortcuts. */
    override fun getItemOnClickListener(): View.OnClickListener = ItemClickHandler.INSTANCE

    /**
     * Launcher.startActivitySafely for ItemClickHandler: starts the app, or the app shortcut with
     * LauncherApps.startShortcut (ActivityContext.startShortcutIntentSafely).
     */
    override fun startActivitySafely(v: View, intent: Intent, item: ItemInfo?): RunnableList? {
        when {
            item is AppItemInfo -> item.entry?.let { home.host.launch(it, v) }
            item is AllAppsAppInfo -> home.host.launch(item.entry, v)
            item != null && item.isDeepShortcut -> {
                val key = ShortcutKey.fromItemInfo(item)
                home.host.startShortcut(key.packageName, key.id, v, item.user)
            }
        }
        return null
    }
    override fun asContext() = context

    override fun isStarted(): Boolean = home.isStarted

    override fun isWorkspaceLoading(): Boolean = home.isWorkspaceLoading

    /** Edit mode hides the keyboard once the drag starts (docs/spec.md 5.4), not at the long press. */
    override fun hideKeyboard() = Unit

    override fun getDragController(): DragController = home.dragController

    override fun getAccessibilityDelegate(): LauncherAccessibilityDelegate = this

    override fun getModel(): LauncherModel = this

    /**
     * LauncherModel.updateAndBindWorkspaceItem for DeepShortcutView.getFinalInfo: the shortcut's
     * title, and its icon badged with its app's icon (IconCache.getShortcutIcon), saved and bound.
     */
    override fun updateAndBindWorkspaceItem(si: WorkspaceItemInfo, info: ShortcutInfo) {
        si.updateFromDeepShortcutInfo(info, context)
        home.app.iconLoader.loadShortcut(si, info) { bitmap ->
            si.bitmap = bitmap
            // bindUpdatedWorkspaceItems: written and bound.
            home.model.writer.updateItemInDatabase(si)
            home.bindItemsUpdated(setOf(si))
        }
    }

    /** The widget's spans on the home grid, for the resize frame (LauncherAppWidgetProviderInfo). */
    override fun getLauncherAppWidgetInfo(info: AppWidgetProviderInfo?): LauncherAppWidgetProviderInfo? =
        info?.let { LauncherAppWidgetProviderInfo.fromProviderInfo(context, it) }

    /** LauncherModel.getWriter: the home model's writer. */
    override fun getModelWriter(): IModelWriter = home.model.writer

    override fun getAppWidgetHolder(): AppWidgetHost = home.widgetHost

    override fun onWidgetResizeFrameOpenChanged(open: Boolean) = home.onWidgetResizeFrameOpenChanged(open)

    override fun getRotationHelper(): RotationHelper = home.host.rotationHelper

    override fun getIntent(): Intent = (context as Activity).intent

    override fun setIntent(intent: Intent) {
        (context as Activity).intent = intent
    }

    /** StateManager.goToState(NORMAL): the home screen, without the search results or all apps. */
    override fun goToNormalState(animated: Boolean, onDone: Runnable?) =
        home.host.showHome { onDone?.run() }

    /** Launcher.addPendingItem: a widget or shortcut dropped from the widget picker. */
    override fun addPendingItem(info: PendingAddItemInfo, container: Int, screenId: Int, cell: IntArray?, spanX: Int, spanY: Int) =
        home.addPendingItem(info, container, cell, spanX, spanY)

    /** LauncherAccessibilityDelegate.addToWorkspace for the menu's add button. */
    override fun addToWorkspace(item: ItemInfo, accessibility: Boolean, finishCallback: Consumer<Boolean>?): Boolean {
        val added = home.addToWorkspace(item)
        finishCallback?.accept(added)
        return added
    }
}

/**
 * The home screen's device profile for [gridSpec]: its folders, its workspace and all apps cells
 * and the device's properties.
 */
internal fun newDeviceProfile(context: Context, gridSpec: GridSpec): DeviceProfile {
    /** The open folder's cells: the icon with Launcher3's folder cell padding around it. */
    val folderCellSizePx = gridSpec.iconSizePx + (24 * context.resources.displayMetrics.density).toInt()
    return DeviceProfile(
        DeviceProfile.FolderProfile(
            gridSpec.iconSizePx, 0, gridSpec.folderColumns, gridSpec.folderRows, gridSpec.iconSizePx,
            folderCellSizePx, folderCellSizePx,
        ),
        // No labels (docs/spec.md 5.2): the icons are centered in their cells.
        DeviceProfile.WorkspaceProfile(gridSpec.iconSizePx, /* iconCenterVertically= */ true),
        DeviceProfile.DeviceProperties(
            context.resources.displayMetrics.heightPixels, gridSpec.isLargeScreen, gridSpec.isTwoPanels,
        ),
        DeviceProfile.AllAppsProfile(gridSpec.columns, gridSpec.allAppsCellHeightPx, gridSpec.iconSizePx),
    )
}

/**
 * InvariantDeviceProfile's grid before the home screen sets its own, e.g. for a widget picker made
 * again before the home screen after the process ended: the grid for the context's display, with
 * the cells it has in the smallest window of the displays (as the home screen has until its grid
 * is measured).
 */
internal object HomeGridInitializer : InvariantDeviceProfile.GridInitializer {
    override fun initGrid(context: Context, idp: InvariantDeviceProfile) {
        val gridSpec = GridSpec.from(context)
        val deviceProfile = newDeviceProfile(context, gridSpec)
        val widgetPadding = ShortcutAndWidgetContainer.getWidgetPadding(context, Point(0, 0))
        deviceProfile.workspaceProfile.setCellSize(gridSpec.homeCellWidthPx, gridSpec.homeCellHeightPx, widgetPadding)
        idp.setDeviceProfile(gridSpec.columns, gridSpec.rows, deviceProfile)
    }
}
