package com.malt03.textlauncher.ui.home

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import com.android.launcher3.CellLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.Workspace
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.dot.DotInfo
import com.android.launcher3.dragndrop.SimpleDragLayer
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.preview.LauncherPreviewRenderer
import com.android.launcher3.util.LauncherBindableItemsContainer
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.malt03.textlauncher.R
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.isApp
import com.malt03.textlauncher.data.isDeepShortcut
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.SearchBarView

/**
 * Launcher3's LauncherPreviewRenderer for TextLauncher's home screen: the preview the system
 * "Wallpaper & style" app shows (GridCustomizationsProxy.getPreview, docs/spec.md 7.3). It is the
 * ActivityContext of the preview's views, binds the app's model (LauncherModel.addCallbacksAndLoad)
 * and lays out the home screen as it is with the keyboard down: the home page
 * ([HomeScrollView] with its [CellLayout]) and the search field below it, above the navigation
 * bar. The keyboard is another app's window (the IME) and is not drawn. The wallpaper is drawn by
 * the "Wallpaper & style" app under the preview.
 *
 * TextLauncher: Launcher3 inflates launcher_preview_layout (its workspace page and hotseat) and
 * makes the items with ItemInflater; the home screen here is laid out by the app
 * ([com.malt03.textlauncher.ui.LauncherRootLayout] and [HomeController]), so this makes the same
 * views itself. The icons have no notification dots, and the folder icons have no folder.
 */
internal class HomePreviewRenderer(
    context: Context,
    workspaceScreenId: Int,
    private val model: LauncherModel,
    themeRes: Int,
) : LauncherPreviewRenderer(context, themeRes),
    WorkspaceLayoutManager,
    LauncherBindableItemsContainer,
    BgDataModel.Callbacks {

    private val gridSpec = GridSpec.from(this)

    /**
     * The home screen's device profile (newDeviceProfile), with an InvariantDeviceProfile of its
     * own, so that the app's grid is left as the home screen set it.
     */
    private val deviceProfile: DeviceProfile = newDeviceProfile(this, gridSpec).also {
        InvariantDeviceProfile().setDeviceProfile(gridSpec.columns, gridSpec.rows, it)
    }

    /** WindowManager's metrics of the display, as the home screen's window has them. */
    private val windowMetrics = getSystemService(WindowManager::class.java).currentWindowMetrics
    private val statusBarTop = windowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
    private val navBottom = windowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom

    private val rootView = LauncherPreviewLayout(this)
    private val content = FrameLayout(this)
    private val scrollView = HomeScrollView(this, gridSpec)
    private val cellLayout = CellLayout(this, null as AttributeSet?)
    private val searchBar = SearchBarView(this).apply {
        setShareTargets((applicationContext as TextLauncherApplication).shareTargets.resolve())
    }
    private val workspaceScreens = mapOf(workspaceScreenId to cellLayout)

    /** LauncherWidgetHolder: makes the widgets' views, without listening for their updates. */
    private val widgetHost = HomeWidgetHost(this)

    init {
        // HomeController.init: the page's padding and cell size (DeviceProfile's).
        val padding = gridSpec.homePaddingPx
        cellLayout.setPadding(padding, padding, padding, padding)
        val widgetPadding = ShortcutAndWidgetContainer.getWidgetPadding(this, android.graphics.Point(0, 0))
        deviceProfile.workspaceProfile.setCellSize(gridSpec.homeCellWidthPx, gridSpec.homeCellHeightPx, widgetPadding)
        scrollView.onCellSizeChanged = { width, height ->
            deviceProfile.workspaceProfile.setCellSize(width, height, widgetPadding)
        }
        scrollView.isFillViewport = true
        scrollView.cellLayout = cellLayout
        layoutContent()
        rootView.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        measureAndLayoutRootView()

        model.addCallbacksAndLoad(this)
    }

    /**
     * LauncherRootLayout's layout with the keyboard down: the page from below the status bar to
     * the search field's container, which is above the navigation bar. With two panels the home
     * screen is the right one (docs/spec.md 5.5); the all apps list on the left is not drawn.
     */
    private fun layoutContent() {
        val width = windowMetrics.bounds.width()
        val height = windowMetrics.bounds.height()
        val barHeight = resources.getDimensionPixelSize(R.dimen.search_bar_height)
        val barMarginH = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
        val barMarginV = resources.getDimensionPixelSize(R.dimen.search_bar_margin_vertical)
        val barContainerHeight = barHeight + barMarginV * 2
        val pageHeight = height - navBottom - barContainerHeight - statusBarTop
        val contentLeft = if (gridSpec.isTwoPanels) width / 2 else 0
        scrollView.pageHeight = pageHeight
        content.addView(
            scrollView,
            FrameLayout.LayoutParams(width - contentLeft, pageHeight).apply {
                leftMargin = contentLeft
                topMargin = statusBarTop
            },
        )
        content.addView(
            searchBar,
            FrameLayout.LayoutParams(width - barMarginH * 2, barHeight).apply {
                leftMargin = barMarginH
                topMargin = height - navBottom - barContainerHeight + barMarginV
            },
        )
    }

    override fun getDragLayer(): BaseDragLayer<*> = rootView

    override fun getDeviceProfile(): DeviceProfile = deviceProfile

    override fun getContent(): LauncherBindableItemsContainer = this

    /** TextLauncher: the preview shows no notification dots. */
    override fun getDotInfoForItem(info: ItemInfo?): DotInfo? = null

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    override fun hideBottomRow(hide: Boolean) {
        // TextLauncher: the search field is the hotseat's QSB.
        searchBar.alpha = if (hide) 0f else 1f
    }

    override fun getScreenWithId(screenId: Int): CellLayout? = workspaceScreens[screenId]

    override fun getCellPosMapper(): CellPosMapper = CellPosMapper.DEFAULT

    override fun mapOverItems(op: ItemOperator): View? =
        Workspace.mapOverCellLayouts(workspaceScreens.values.toTypedArray(), op)

    private fun dispatchVisibilityAggregated(view: View, isVisible: Boolean) {
        // Similar to View.dispatchVisibilityAggregated implementation.
        val thisVisible = view.visibility == View.VISIBLE
        if (thisVisible || !isVisible) {
            view.onVisibilityAggregated(isVisible)
        }

        if (view is ViewGroup) {
            val visible = thisVisible && isVisible
            for (i in 0 until view.childCount) {
                dispatchVisibilityAggregated(view.getChildAt(i), visible)
            }
        }
    }

    private fun bindCompleteUI(itemIdMap: WorkspaceData) {
        workspaceScreens.values.forEach(CellLayout::removeAllViews)

        // Separate the items that are on the current screen, and the other remaining items.
        itemIdMap
            .filter {
                it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                    it.screenId in workspaceScreens
            }
            .forEach(::inflateAndAdd)
        // TextLauncher: no hotseat predictions.

        measureAndLayoutRootView()
        dispatchVisibilityAggregated(rootView, true)
        measureAndLayoutRootView()
        // Additional measure for views which use auto text size API
        measureAndLayoutRootView()
        initialRender.complete(rootView)
    }

    override fun bindCompleteModel(itemIdMap: WorkspaceData, isBindingSync: Boolean) {
        bindCompleteUI(itemIdMap)
        // TextLauncher: the preview binds the app's model (Launcher3's has one of its own), whose
        // loader pauses the install queue until the items are bound
        // (ModelCallbacks.finishBindingItems).
        ItemInstallQueue.INSTANCE[this].resumeModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING)
    }

    override fun bindItemsUpdated(updates: Set<ItemInfo>) {
        // TextLauncher: a folder icon here has no folder to close, so only its preview items are
        // updated.
        updateContainerItems(updates.filterNot { it is FolderInfo }.toSet(), this)
    }

    private fun inflateAndAdd(itemInfo: ItemInfo) {
        val view = inflateItem(itemInfo)
        if (view != null) {
            addInScreenFromBind(view, itemInfo)
        }
    }

    /**
     * ItemInflater.inflateItem, as [HomeItemInflater] makes the home screen's views, without the
     * click and focus listeners (the preview takes no input).
     */
    private fun inflateItem(item: ItemInfo): View? = when {
        item is WorkspaceItemInfo && (item.isApp || item.isDeepShortcut) ->
            AppIconView(this, gridSpec.iconSizePx).apply { applyFromWorkspaceItem(item) }
        item is FolderInfo -> FolderIconView(this, this, item)
        item is LauncherAppWidgetInfo -> {
            AppWidgetManager.getInstance(this).getAppWidgetInfo(item.appWidgetId)?.let { providerInfo ->
                item.updateWidgetFeatures(LauncherAppWidgetProviderInfo.fromProviderInfo(this, providerInfo))
                widgetHost.createView(this, item.appWidgetId, providerInfo).apply { tag = item }
            }
        }
        else -> null
    }

    private fun measureAndLayoutRootView() {
        val width = windowMetrics.bounds.width()
        val height = windowMetrics.bounds.height()
        rootView.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY))
        rootView.layout(0, 0, width, height)
    }

    /** BaseContext.onViewDestroyed: LauncherPreviewRenderer's lifecycle observer's onDestroy. */
    override fun onViewDestroyed() {
        model.removeCallbacks(this)
    }

    /** Root layout for launcher preview that intercepts all touch events. */
    class LauncherPreviewLayout(context: Context) : SimpleDragLayer<LauncherPreviewRenderer>(context, null) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
    }
}
