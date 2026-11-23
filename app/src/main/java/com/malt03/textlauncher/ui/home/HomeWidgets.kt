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
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.WidgetManagerHelper
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

    /**
     * DatabaseHelper.removeGhostWidgets: removes widgets which are registered to the Launcher's
     * host, but are not present in our model ([validWidgets], the home model's widget ids).
     */
    fun removeGhostWidgets(validWidgets: Set<Int>) {
        // Get all existing widget ids.
        val allWidgets = appWidgetIds
        var isAnyWidgetRemoved = false
        for (widgetId in allWidgets) {
            if (widgetId !in validWidgets) {
                try {
                    Log.d(TAG, "Deleting widget not found in db: appWidgetId=$widgetId")
                    deleteAppWidgetId(widgetId)
                    isAnyWidgetRemoved = true
                } catch (e: RuntimeException) {
                    // Ignore
                }
            }
        }
        if (isAnyWidgetRemoved) {
            Log.d(
                TAG,
                "One or more widgets was removed: " +
                    " allLauncherHostWidgetIds=${allWidgets.contentToString()}" +
                    ", allValidLauncherDbWidgetIds=$validWidgets",
            )
        }
    }

    companion object {
        /** DatabaseHelper.TAG */
        private const val TAG = "DatabaseHelper"

        /** LauncherWidgetHolder.APPWIDGET_HOST_ID */
        const val APPWIDGET_HOST_ID = 1024
    }
}

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

    /**
     * LauncherAppWidgetProviderInfo.initSpans: [LauncherAppWidgetProviderInfo.spanX] and spanY
     * are what a new widget gets, the min and max what it can be resized to. The cells are the
     * home grid's, which it sets in the device profile as it measures.
     */
    fun initSpans(info: AppWidgetProviderInfo): LauncherAppWidgetProviderInfo =
        LauncherAppWidgetProviderInfo.fromProviderInfo(context, info)

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
 * Launcher's widget adding for a widget from the widget picker (docs/spec.md 5.2):
 * Launcher.addAppWidgetFromDrop allocates a widget id and binds the widget to it with its size
 * options, asking the user first when the launcher may not bind widgets (startBindFlow,
 * REQUEST_BIND_APPWIDGET); a widget that needs configuring gets its configuration activity
 * (WidgetAddFlowHandler, REQUEST_CREATE_APPWIDGET); then it is added (completeAddAppWidget). A
 * cancelled flow deletes the id (completeTwoStageWidgetDrop).
 *
 * TextLauncher: a widget that is configured is added once its configuration activity comes back,
 * in the first free cell then; Launcher3 adds a pending widget view at once
 * (FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2).
 */
internal class HomeWidgetAddFlow(private val home: HomeController) {
    private val activity get() = home.context as Activity
    private val appWidgetManager = AppWidgetManager.getInstance(home.context)

    /** Launcher.mPendingRequestArgs: the widget id of the add flow in progress. */
    private var pendingAddWidgetId = -1

    /** Launcher.mPendingActivityResult: a result that came while the home screen was loading. */
    private var pendingActivityResult: ActivityResultInfo? = null

    /**
     * Launcher.addAppWidgetFromDrop: binds the widget to a new id, or asks the user whether the
     * launcher may (startBindFlow), and goes on with addAppWidgetImpl.
     */
    fun addAppWidgetFromDrop(info: PendingAddWidgetInfo) {
        // In this case, we either need to start an activity to get permission to bind
        // the widget, or we need to start an activity to configure the widget, or both.
        // TextLauncher: no custom widgets (ITEM_TYPE_CUSTOM_APPWIDGET).
        val appWidgetId = home.widgetHost.allocateAppWidgetId()
        val options = info.bindOptions

        val success = WidgetManagerHelper(home.context).bindAppWidgetIdIfAllowed(appWidgetId, info.info, options)
        if (success) {
            addAppWidgetImpl(appWidgetId)
        } else {
            startBindFlow(appWidgetId, info.info)
        }
    }

    /** WidgetAddFlowHandler.startBindFlow with LauncherWidgetHolder.startBindFlow. */
    private fun startBindFlow(appWidgetId: Int, info: AppWidgetProviderInfo) {
        pendingAddWidgetId = appWidgetId
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        // TODO: we need to make sure that this accounts for the options bundle.
        // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity to bind the widget", e)
            pendingAddWidgetId = -1
            cancel(appWidgetId)
        }
    }

    /**
     * Launcher.addAppWidgetImpl: the widget's configuration activity if it needs one
     * (WidgetAddFlowHandler.startConfigActivity), or else the widget is added.
     */
    private fun addAppWidgetImpl(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            cancel(appWidgetId)
            return
        }
        if (needsConfigure(info)) {
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

    /**
     * The widget's long-press menu's "Settings" (AppWidgetSystemShortcuts' widget settings): the
     * widget's configuration activity, for a widget already on the home screen.
     */
    fun startReconfigure(view: AppWidgetHostView) {
        // LauncherWidgetHolder.startConfigActivity
        try {
            home.widgetHost.startAppWidgetConfigureActivityForResult(
                activity, view.appWidgetId, 0, REQUEST_RECONFIGURE_APPWIDGET, null,
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(activity, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    /** Launcher.onActivityResult. Returns whether the result was for a widget. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        // Launcher.completeAdd for REQUEST_RECONFIGURE_APPWIDGET only marks a restored widget as
        // restored (completeRestoreAppWidget); the widget updates itself. TextLauncher: widgets are
        // not restored, so there is nothing to do.
        if (requestCode == REQUEST_RECONFIGURE_APPWIDGET) return true
        if (requestCode != REQUEST_BIND_APPWIDGET && requestCode != REQUEST_CREATE_APPWIDGET) return false
        // Launcher.handleActivityResult: process the result once the workspace has loaded.
        if (home.isLoaded) {
            handleActivityResult(requestCode, resultCode, data)
        } else {
            pendingActivityResult = ActivityResultInfo(requestCode, resultCode, data)
        }
        return true
    }

    /** Called once the home screen is loaded. */
    fun onLoaded() {
        // LoaderTask.sanitizeWidgetsShortcutsAndPackages: remove any ghost widgets, e.g. those the
        // grid migration or checkItemPlacement left out. TextLauncher: the home screen loads with
        // each activity rather than once per process, so the ids of an add flow in progress, and
        // of its result waiting for the load, are not ghosts.
        val validWidgets = home.model.allItems().filterIsInstance<WidgetItemInfo>().map { it.appWidgetId }.toMutableSet()
        if (pendingAddWidgetId >= 0) validWidgets += pendingAddWidgetId
        pendingActivityResult?.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it >= 0 }?.let { validWidgets += it }
        home.widgetHost.removeGhostWidgets(validWidgets)
        pendingActivityResult?.let {
            pendingActivityResult = null
            handleActivityResult(it.requestCode, it.resultCode, it.data)
        }
    }

    /** Launcher.handleActivityResult for the widget requests. */
    private fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val appWidgetId = if (widgetId < 0) pendingAddWidgetId else widgetId
        pendingAddWidgetId = -1
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            // This is called only if the user did not previously have permissions to bind widgets
            if (resultCode == Activity.RESULT_OK && appWidgetId >= 0) {
                addAppWidgetImpl(appWidgetId)
            } else {
                cancel(appWidgetId)
            }
            return
        }
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
        const val REQUEST_BIND_APPWIDGET = 11
        const val REQUEST_RECONFIGURE_APPWIDGET = 13
    }

    /** ActivityResultInfo */
    private class ActivityResultInfo(val requestCode: Int, val resultCode: Int, val data: Intent?)
}
