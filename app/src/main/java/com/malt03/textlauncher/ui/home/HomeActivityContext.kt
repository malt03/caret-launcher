package com.malt03.textlauncher.ui.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Point
import android.content.pm.ShortcutInfo
import android.util.TypedValue
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.dagger.ActivityComponent
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.deviceprofile.DropTargetProfile
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.popup.PopupDataProvider
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.LauncherBindableItemsContainer
import com.android.launcher3.util.RunnableList
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.malt03.textlauncher.data.isApp
import com.malt03.textlauncher.data.isAvailable
import com.malt03.textlauncher.data.isDeepShortcut
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTargetBar
import com.android.launcher3.Workspace
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.widget.LauncherAppWidgetHostView
import java.util.stream.Stream
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.allapps.AllAppsSwipeController
import com.android.launcher3.LauncherState
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StateManager.StateHandler
import com.android.launcher3.states.StateAnimationConfig

/**
 * Launcher of the home screen, which is also the ActivityContext of the copied code as in
 * Launcher3: the device profile, the drag layer and controller, the item click handler, and the
 * model the long-press menu's shortcut rows use.
 */
internal class HomeActivityContext(private val home: HomeController) :
    Launcher(home.root.overlay, home.root.hotseat, home.root.scrim) {
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

    /** ItemClickHandler, for the items in folders and the long-press menu's app shortcuts. */
    override fun getItemOnClickListener(): View.OnClickListener = ItemClickHandler.INSTANCE

    /**
     * Launcher.startActivitySafely for ItemClickHandler: starts the app, or the app shortcut with
     * LauncherApps.startShortcut (ActivityContext.startShortcutIntentSafely).
     */
    override fun startActivitySafely(v: View, intent: Intent, item: ItemInfo?): RunnableList? {
        when {
            item is WorkspaceItemInfo && item.isApp -> if (item.isAvailable) home.host.launch(item, v)
            item is AppInfo -> home.host.launch(item, v)
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

    override fun getWorkspace(): Workspace<*> = home.workspace

    override fun isTouchInProgress(): Boolean = home.root.isTouchInProgress

    override fun addFolder(layout: CellLayout, container: Int, screenId: Int, cellX: Int, cellY: Int): FolderIcon =
        home.addFolder(container, screenId, cellX, cellY)

    override fun getDropTargetBar(): DropTargetBar = home.dropTargetBar

    override fun inflateItem(info: ItemInfo, parent: CellLayout, container: Int): View =
        home.itemInflater.inflateItem(info)!!

    /** Launcher.getSupportedShortcuts: the long-press menu's options (docs/spec.md 5.3). */
    override fun getSupportedShortcuts(itemInfo: ItemInfo): Stream<SystemShortcut.Factory<*>> =
        home.systemShortcuts.getSupportedShortcuts()

    /** The widget's spans on the home grid, for the resize frame (LauncherAppWidgetProviderInfo). */
    override fun getLauncherAppWidgetInfo(info: AppWidgetProviderInfo?): LauncherAppWidgetProviderInfo? =
        info?.let { LauncherAppWidgetProviderInfo.fromProviderInfo(context, it) }

    /** Launcher.getModelWriter: the home screen's writer (LauncherModel.getWriter). */
    override fun getModelWriter(): IModelWriter = home.modelWriter

    override fun getAppWidgetHolder(): AppWidgetHost = home.widgetHost

    override fun onWidgetResizeFrameOpenChanged(open: Boolean) = home.onWidgetResizeFrameOpenChanged(open)

    override fun getRotationHelper(): RotationHelper = home.host.rotationHelper

    override fun getIntent(): Intent = (context as Activity).intent

    override fun setIntent(intent: Intent) {
        (context as Activity).intent = intent
    }

    override fun collectStateHandlers(out: MutableList<StateHandler<LauncherState>>) = home.host.collectStateHandlers(out)

    /**
     * TextLauncher: the state the last transition went to, which the next one comes from (the
     * state manager's state before it changes), and the stable state the last one ended in.
     */
    private var transitionState: LauncherState = LauncherState.NORMAL
    private var stableState: LauncherState = LauncherState.NORMAL

    override fun onStateSetStart(state: LauncherState) {
        super.onStateSetStart(state)
        val fromState = transitionState
        transitionState = state
        // Animated unless the handlers are set to the state at once (StateManager.goToState).
        val animated = stateManager.isInTransition || stateManager.hasUserControlledSwipe()
        home.onStateSetStart(state)
        home.host.onStateSetStart(fromState, state, animated)
    }

    override fun onStateSetEnd(state: LauncherState) {
        val lastStableState = stableState
        stableState = state
        home.onStateSetEnd(state)
        home.host.onStateSetEnd(state, lastStableState)
    }

    /**
     * QuickstepAtomicAnimationFactory.prepareForAtomicAnimation: the interpolators of the all apps
     * transitions.
     */
    override fun createAtomicAnimationFactory() = object : StateManager.AtomicAnimationFactory<LauncherState>(0) {
        override fun prepareForAtomicAnimation(fromState: LauncherState, toState: LauncherState, config: StateAnimationConfig) {
            if (fromState == LauncherState.ALL_APPS && toState == LauncherState.NORMAL) {
                AllAppsSwipeController.applyAllAppsToNormalConfig(config)
            } else if (fromState == LauncherState.NORMAL && toState == LauncherState.ALL_APPS) {
                AllAppsSwipeController.applyNormalToAllAppsAnimConfig(config)
            }
        }
    }

    /** StateManager.goToState(NORMAL): the home screen, without the search results or all apps. */
    override fun goToNormalState(animated: Boolean, onDone: Runnable?) =
        home.host.showHome { onDone?.run() }

    /** The root view, which holds the empty space's menu (Launcher.showDefaultOptions). */
    override fun getRootView(): View = home.root

    /** The NORMAL state, with no drag (WorkspaceTouchListener.canHandleLongPress). */
    override fun isInNormalState(): Boolean = home.host.canShowOptions() && !home.isDragging

    override fun canOpenAllApps(): Boolean = home.host.openAllApps != null

    override fun openAllApps() {
        home.host.openAllApps?.invoke()
    }

    override fun showNotificationAccessConfirmation() = NotificationAccessConfirmation.show(context)

    /** Launcher.runWhenResumed: the widget picker's add waits for the home screen to be resumed. */
    override fun runWhenResumed(runnable: Runnable) = home.runWhenResumed(runnable)
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
        DeviceProfile.WorkspaceProfile(
            gridSpec.iconSizePx, /* iconCenterVertically= */ true,
            context.resources.getDimensionPixelSize(com.android.launcher3.R.dimen.grid_visualization_horizontal_cell_spacing),
            context.resources.getDimensionPixelSize(com.android.launcher3.R.dimen.grid_visualization_vertical_cell_spacing),
            context.resources.getFloat(com.android.launcher3.R.dimen.workspace_content_scale),
        ),
        DeviceProfile.DeviceProperties(
            context.resources.displayMetrics.heightPixels, gridSpec.isLargeScreen, gridSpec.isTwoPanels,
        ),
        DeviceProfile.AllAppsProfile(
            gridSpec.columns, gridSpec.allAppsCellHeightPx, gridSpec.iconSizePx,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, gridSpec.iconTextSizeSp, context.resources.displayMetrics),
            ALL_APPS_OPEN_DURATION_MS, ALL_APPS_CLOSE_DURATION_MS,
        ),
        DropTargetProfile.createDropTargetProfile(context.resources, shouldApplyWidePortraitDimens = false),
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

/**
 * A context that gives [launcher] to ActivityContext.lookupContext (Launcher.getLauncher), for the
 * Launcher3 views the home screen makes before the activity holds it (the drop target bar, which
 * Launcher3 inflates in the launcher's own context).
 */
internal class LauncherContextWrapper(base: Context, private val launcher: Launcher) :
    ContextWrapper(base), ActivityContext.Provider {
    override fun getActivityContext(): ActivityContext = launcher
}

/** config_allAppsOpenDuration */
private const val ALL_APPS_OPEN_DURATION_MS = 600

/** config_allAppsCloseDuration */
private const val ALL_APPS_CLOSE_DURATION_MS = 300
