package com.malt03.textlauncher.ui.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.widget.Toast
import com.android.launcher3.R
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.util.Executors
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.malt03.textlauncher.data.WidgetItemInfo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * LauncherWidgetHolder and LauncherAppWidgetHost: the home screen's widget host, which makes the
 * widgets' views ([LauncherAppWidgetHostView]). It listens for updates while the activity is
 * started.
 */
internal class HomeWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, APPWIDGET_HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherAppWidgetHostView(context)

    companion object {
        /** LauncherWidgetHolder.APPWIDGET_HOST_ID */
        const val APPWIDGET_HOST_ID = 1024
    }
}

/**
 * LauncherAppWidgetProviderInfo's spans: [spanX] and [spanY] are what a new widget gets, the
 * min and max what it can be resized to.
 */
internal data class WidgetSpans(
    val spanX: Int,
    val spanY: Int,
    val minSpanX: Int,
    val minSpanY: Int,
    val maxSpanX: Int,
    val maxSpanY: Int,
)

/**
 * The widget sizes of the home grid: LauncherAppWidgetProviderInfo.initSpans, WidgetSizes and
 * WidgetSizeHandler.
 *
 * TextLauncher: Launcher3 works them out for every supported device profile and takes the largest
 * spans (and gives the widget the size in each profile); the home grid has one profile, its cells
 * as they are now.
 */
internal class HomeWidgetSizes(private val grid: HomeGridView) {
    private val context = grid.context

    /** The cell size (WorkspaceProfile.getCellSize). */
    private val cellSize: Point get() = Point(grid.cellWidth, grid.cellHeight)

    /** The cells have no space between them (WorkspaceProfile.getCellLayoutBorderSpacePx). */
    private val borderSpace = Point(0, 0)

    /** WorkspaceProfile.getWidgetPadding */
    private val widgetPadding get() = ShortcutAndWidgetContainer.getWidgetPadding(context, borderSpace)

    /** LauncherAppWidgetProviderInfo.initSpans */
    fun initSpans(info: AppWidgetProviderInfo): WidgetSpans {
        val numColumns = grid.grid.columns
        val numRows = grid.grid.rows
        var maxSpanX = numColumns
        var maxSpanY = numRows

        val cellSize = cellSize
        val widgetPadding = widgetPadding

        val paddingX = widgetPadding.left + widgetPadding.right
        val paddingY = widgetPadding.top + widgetPadding.bottom
        val minSpanX = getSpan(paddingX, info.minResizeWidth, borderSpace.x, cellSize.x.toFloat())
        val minSpanY = getSpan(paddingY, info.minResizeHeight, borderSpace.y, cellSize.y.toFloat())

        if (info.maxResizeWidth > 0) {
            maxSpanX = min(maxSpanX, getSpan(paddingX, info.maxResizeWidth, borderSpace.x, cellSize.x.toFloat()))
        }
        if (info.maxResizeHeight > 0) {
            maxSpanY = min(maxSpanY, getSpan(paddingY, info.maxResizeHeight, borderSpace.y, cellSize.y.toFloat()))
        }

        var spanX = getSpan(paddingX, info.minWidth, borderSpace.x, cellSize.x.toFloat())
        var spanY = getSpan(paddingY, info.minHeight, borderSpace.y, cellSize.y.toFloat())

        // Ensures maxSpan >= minSpan
        maxSpanX = max(maxSpanX, minSpanX)
        maxSpanY = max(maxSpanY, minSpanY)

        // Use targetCellWidth/Height if it is within the min/max ranges.
        // Otherwise, use the span of minWidth/Height.
        if (info.targetCellWidth in minSpanX..maxSpanX && info.targetCellHeight in minSpanY..maxSpanY) {
            spanX = info.targetCellWidth
            spanY = info.targetCellHeight
        }

        // If minSpanX/Y > spanX/Y, ignore the minSpanX/Y to match the behavior described in
        // minResizeWidth & minResizeHeight Android documentation. See
        // https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo
        return WidgetSpans(
            // Ensures the default span X and span Y will not exceed the current grid size.
            spanX = min(spanX, numColumns),
            spanY = min(spanY, numRows),
            minSpanX = min(spanX, minSpanX),
            minSpanY = min(spanY, minSpanY),
            maxSpanX = maxSpanX,
            maxSpanY = maxSpanY,
        )
    }

    /**
     * Solving the equation:
     *   n * cellSize + (n - 1) * cellSpacing - widgetPadding = widgetSize
     */
    private fun getSpan(widgetPadding: Int, widgetSize: Int, cellSpacing: Int, cellSize: Float): Int =
        max(1, ceil((widgetSize + widgetPadding + cellSpacing) / (cellSize + cellSpacing)).toInt())

    /** WidgetSizes.getWidgetSizePx: the size, in pixels, of a widget of the given spans. */
    fun getWidgetSizePx(spanX: Int, spanY: Int): Size {
        val hBorderSpacing = (spanX - 1) * borderSpace.x
        val vBorderSpacing = (spanY - 1) * borderSpace.y
        val cellSize = cellSize
        val padding = widgetPadding
        return Size(
            (spanX * cellSize.x) + hBorderSpacing - padding.left - padding.right,
            (spanY * cellSize.y) + vBorderSpacing - padding.top - padding.bottom,
        )
    }

    /**
     * WidgetSizeHandler.updateSizeRangesAsync: tells the widget its size, when it changed (updating
     * the options wakes up the provider and updates the whole widget).
     */
    fun updateSizeRanges(view: AppWidgetHostView, spanX: Int, spanY: Int) {
        val widgetId = view.appWidgetId
        if (widgetId <= 0) return
        val sizeOptions = getWidgetSizeOptions(spanX, spanY)
        Executors.UI_HELPER_EXECUTOR.handler.post {
            val widgetManager = AppWidgetManager.getInstance(context)
            if (widgetManager.getAppWidgetInfo(widgetId) == null) return@post
            val widgetOptions = widgetManager.getAppWidgetOptions(widgetId)
            @Suppress("DEPRECATION")
            if (sizeOptions.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) !=
                widgetOptions.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ) {
                widgetManager.updateAppWidgetOptions(widgetId, sizeOptions)
            }
        }
    }

    /**
     * WidgetSizeHandler.getWidgetSizeOptions: the options with the widget's size, for the given
     * spans, in dp.
     */
    fun getWidgetSizeOptions(spanX: Int, spanY: Int): Bundle {
        val density = context.resources.displayMetrics.density
        val sizePx = getWidgetSizePx(spanX, spanY)
        return getWidgetSizeOptions(SizeF(sizePx.width / density, sizePx.height / density))
    }

    /** WidgetSizeHandler.getWidgetSizeOptionsInternal for the one profile. */
    private fun getWidgetSizeOptions(size: SizeF): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.width.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.height.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.width.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.height.toInt())
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size))
    }
}

/**
 * Launcher's widget adding, with the system's widget picker (ACTION_APPWIDGET_PICK, docs/spec.md
 * 5.2): a widget id is allocated and the picker binds the chosen widget to it
 * (REQUEST_PICK_APPWIDGET); a widget that needs configuring gets its configuration activity
 * (WidgetAddFlowHandler, REQUEST_CREATE_APPWIDGET); then it is added in the first free cell
 * (completeAddAppWidget). A cancelled flow deletes the id (completeTwoStageWidgetDrop).
 *
 * TextLauncher: Launcher3's own picker drags the widget to its place; the system's picker only
 * picks it, so it goes in the first free cell, like an item added from a menu.
 */
internal class HomeWidgetAddFlow(private val home: HomeController) {
    private val activity get() = home.context as Activity
    private val appWidgetManager = AppWidgetManager.getInstance(home.context)

    /** Launcher.mPendingRequestArgs: the widget id of the add flow in progress. */
    private var pendingAddWidgetId = -1

    /** Launcher.mPendingActivityResult: a result that came while the home screen was loading. */
    private var pendingActivityResult: (() -> Unit)? = null

    /** WorkspaceLongPressOptions.openWidgetPicker, with the system's picker. */
    fun startPick() {
        val appWidgetId = home.widgetHost.allocateAppWidgetId()
        pendingAddWidgetId = appWidgetId
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_PICK_APPWIDGET)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No widget picker", e)
            cancel(appWidgetId)
        }
    }

    /** Launcher.onActivityResult. Returns whether the result was for a widget. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PICK_APPWIDGET && requestCode != REQUEST_CREATE_APPWIDGET) return false
        val result = { handleActivityResult(requestCode, resultCode, data) }
        // Launcher.handleActivityResult: process the result once the workspace has loaded.
        if (home.isLoaded) result() else pendingActivityResult = result
        return true
    }

    /** Called once the home screen is loaded. */
    fun onLoaded() {
        pendingActivityResult?.let {
            pendingActivityResult = null
            it()
        }
    }

    /** Launcher.handleActivityResult for the widget requests. */
    private fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val appWidgetId = if (widgetId < 0) pendingAddWidgetId else widgetId
        pendingAddWidgetId = -1
        if (appWidgetId < 0 || resultCode == Activity.RESULT_CANCELED) {
            Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not returned from the widget configuration activity.")
            cancel(appWidgetId)
            return
        }
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            cancel(appWidgetId)
            return
        }
        if (requestCode == REQUEST_PICK_APPWIDGET) {
            // PendingAddWidgetInfo.bindOptions: Launcher3 binds the widget with its size options,
            // so it knows its size before it is configured and drawn. TextLauncher: the system's
            // picker binds it, so the options are set as soon as it comes back.
            val spans = home.widgetSizes.initSpans(info)
            appWidgetManager.updateAppWidgetOptions(
                appWidgetId, home.widgetSizes.getWidgetSizeOptions(spans.spanX, spans.spanY),
            )
        }
        if (requestCode == REQUEST_PICK_APPWIDGET && needsConfigure(info)) {
            // WidgetAddFlowHandler.startConfigActivity
            pendingAddWidgetId = appWidgetId
            try {
                home.widgetHost.startAppWidgetConfigureActivityForResult(
                    activity, appWidgetId, 0, REQUEST_CREATE_APPWIDGET, null,
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
                pendingAddWidgetId = -1
                cancel(appWidgetId)
            } catch (e: SecurityException) {
                Toast.makeText(activity, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
                pendingAddWidgetId = -1
                cancel(appWidgetId)
            }
            return
        }
        completeAddAppWidget(appWidgetId, info)
    }

    /** Launcher.completeTwoStageWidgetDrop with RESULT_CANCELED: the id is given back. */
    private fun cancel(appWidgetId: Int) {
        if (appWidgetId >= 0) home.widgetHost.deleteAppWidgetId(appWidgetId)
    }

    /**
     * WidgetAddFlowHandler.needsConfigure: the widget has a configuration activity that it does
     * not mark as optional (AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL, which
     * only counts for a reconfigurable widget).
     */
    private fun needsConfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        val reconfigurable = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        val optional = reconfigurable &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        return !optional
    }

    /**
     * Launcher.completeAddAppWidget: the widget's item in the first free cell with its default
     * spans, its view, and its size. TextLauncher: with no free cell, the widget is not added
     * (Launcher3 would add a page).
     */
    private fun completeAddAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        val spans = home.widgetSizes.initSpans(info)
        val item = WidgetItemInfo(appWidgetId, info.provider.flattenToString()).apply {
            spanX = spans.spanX
            spanY = spans.spanY
            minSpanX = spans.minSpanX
            minSpanY = spans.minSpanY
            user = info.profile
        }
        if (!home.addWidget(item)) {
            Toast.makeText(activity, R.string.out_of_space, Toast.LENGTH_SHORT).show()
            cancel(appWidgetId)
        }
    }

    private companion object {
        const val TAG = "Launcher"

        /** LauncherConstants.ActivityCodes */
        const val REQUEST_CREATE_APPWIDGET = 5
        const val REQUEST_PICK_APPWIDGET = 9
    }
}
