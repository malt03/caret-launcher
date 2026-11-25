package com.malt03.textlauncher.ui.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.pm.PinRequestHelper
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.PendingAddShortcutInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.WidgetManagerHelper

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
 * Launcher's widget adding for a widget from the widget picker (docs/spec.md 5.2):
 * Launcher.addAppWidgetFromDrop allocates a widget id and binds the widget to it with its size
 * options, asking the user first when the launcher may not bind widgets (startBindFlow,
 * REQUEST_BIND_APPWIDGET); a widget that needs configuring gets its configuration activity
 * (WidgetAddFlowHandler, REQUEST_CREATE_APPWIDGET); then it is added (completeAddAppWidget) in the
 * cell and with the spans Launcher.addPendingItem gave the item. A cancelled flow deletes the id
 * (completeTwoStageWidgetDrop). While an activity is in front, the flow (Launcher.mPendingRequestArgs)
 * and a result that waits for the home screen to load (mPendingActivityResult) are kept in the
 * activity's saved state, in case it is created again meanwhile.
 *
 * A shortcut from the widget picker is made by its app's activity (Launcher.processShortcutFromDrop,
 * REQUEST_CREATE_SHORTCUT), whose result holds the pin request of the shortcut to add
 * (completeAddShortcut).
 *
 * TextLauncher: a widget that is configured is added once its configuration activity comes back;
 * Launcher3 adds a pending widget view at once (FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2).
 */
internal class HomeWidgetAddFlow(private val home: HomeController) {
    private val activity get() = home.context as Activity
    private val appWidgetManager = AppWidgetManager.getInstance(home.context)

    /** Launcher.mPendingRequestArgs: the add flow waiting for an activity's result. */
    private var pendingRequestArgs: PendingRequestArgs? = null

    /** Launcher.mPendingActivityResult: a result that came while the home screen was loading. */
    private var pendingActivityResult: ActivityResultInfo? = null

    /** Launcher.setWaitingForResult */
    private fun setWaitingForResult(args: PendingRequestArgs?) {
        pendingRequestArgs = args
        updatePendingWidgetIds()
    }

    /** TextLauncher: [pendingWidgetIds] for the flow and the result waiting for the load. */
    private fun updatePendingWidgetIds() {
        val ids = mutableSetOf<Int>()
        pendingRequestArgs?.widgetId?.takeIf { it >= 0 }?.let { ids += it }
        pendingActivityResult?.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it >= 0 }?.let { ids += it }
        pendingWidgetIds = ids
    }

    /** Launcher.restoreState for the add flow. */
    fun restoreState(savedState: Bundle?) {
        if (savedState == null) return
        savedState.getBundle(RUNTIME_STATE_PENDING_REQUEST_ARGS)?.let {
            setWaitingForResult(PendingRequestArgs.fromBundle(it))
        }
        pendingActivityResult = savedState.getBundle(RUNTIME_STATE_PENDING_ACTIVITY_RESULT)
            ?.let(ActivityResultInfo::fromBundle)
        updatePendingWidgetIds()
    }

    /** Launcher.onSaveInstanceState for the add flow. */
    fun onSaveInstanceState(outState: Bundle) {
        pendingRequestArgs?.let { outState.putBundle(RUNTIME_STATE_PENDING_REQUEST_ARGS, it.toBundle()) }
        pendingActivityResult?.let { outState.putBundle(RUNTIME_STATE_PENDING_ACTIVITY_RESULT, it.toBundle()) }
    }

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
            addAppWidgetImpl(appWidgetId, info)
        } else {
            startBindFlow(appWidgetId, info)
        }
    }

    /** WidgetAddFlowHandler.startBindFlow with LauncherWidgetHolder.startBindFlow. */
    private fun startBindFlow(appWidgetId: Int, info: PendingAddWidgetInfo) {
        setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, info))
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.info.profile)
        // TODO: we need to make sure that this accounts for the options bundle.
        // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_BIND_APPWIDGET)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity to bind the widget", e)
            setWaitingForResult(null)
            cancel(appWidgetId)
        }
    }

    /**
     * Launcher.addAppWidgetImpl: the widget's configuration activity if it needs one
     * (WidgetAddFlowHandler.startConfigActivity), or else the widget is added.
     */
    private fun addAppWidgetImpl(appWidgetId: Int, info: ItemInfo) {
        // WidgetAddFlowHandler.getProviderInfo
        val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (providerInfo == null) {
            cancel(appWidgetId)
            return
        }
        if (needsConfigure(providerInfo)) {
            setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, info))
            try {
                home.widgetHost.startAppWidgetConfigureActivityForResult(
                    activity, appWidgetId, 0, REQUEST_CREATE_APPWIDGET, null,
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
                setWaitingForResult(null)
                cancel(appWidgetId)
            } catch (e: SecurityException) {
                Toast.makeText(activity, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
                setWaitingForResult(null)
                cancel(appWidgetId)
            }
            return
        }
        completeAddAppWidget(appWidgetId, info, providerInfo)
    }

    /**
     * Launcher.processShortcutFromDrop: the activity of the app that makes the shortcut
     * (ACTION_CREATE_SHORTCUT), whose result [completeAddShortcut] adds.
     */
    fun processShortcutFromDrop(info: PendingAddShortcutInfo) {
        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(info.componentName)
        setWaitingForResult(PendingRequestArgs.forIntent(REQUEST_CREATE_SHORTCUT, intent, info))
        if (!info.getActivityInfo(activity).startConfigActivity(activity, REQUEST_CREATE_SHORTCUT)) {
            handleActivityResult(REQUEST_CREATE_SHORTCUT, Activity.RESULT_CANCELED, null)
        }
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
        if (requestCode != REQUEST_BIND_APPWIDGET && requestCode != REQUEST_CREATE_APPWIDGET &&
            requestCode != REQUEST_CREATE_SHORTCUT
        ) {
            return false
        }
        // Launcher.handleActivityResult: process the result once the workspace has loaded.
        if (home.isLoaded) {
            handleActivityResult(requestCode, resultCode, data)
        } else {
            pendingActivityResult = ActivityResultInfo(requestCode, resultCode, data)
            updatePendingWidgetIds()
        }
        return true
    }

    /**
     * Called once the home screen is loaded. LoaderTask removes the ghost widgets
     * ([HomeLoaderHooks], which keeps [pendingWidgetIds]).
     */
    fun onLoaded() {
        // Launcher.processActivityResult
        pendingActivityResult?.let {
            pendingActivityResult = null
            updatePendingWidgetIds()
            handleActivityResult(it.requestCode, it.resultCode, it.data)
        }
    }

    /** Launcher.handleActivityResult for the widget and shortcut requests. */
    private fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Reset the startActivity waiting flag
        val requestArgs = pendingRequestArgs
        setWaitingForResult(null)
        if (requestArgs == null) {
            return
        }

        if (requestCode == REQUEST_CREATE_SHORTCUT) {
            // Handle custom shortcuts created using ACTION_CREATE_SHORTCUT.
            if (resultCode == Activity.RESULT_OK && requestArgs.container != ItemInfo.NO_ID) {
                completeAddShortcut(data, requestArgs)
            }
            return
        }

        val pendingAddWidgetId = requestArgs.widgetId
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        // TextLauncher: the bind result's id too falls back to the flow's.
        val appWidgetId = if (widgetId < 0) pendingAddWidgetId else widgetId
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            // This is called only if the user did not previously have permissions to bind widgets
            if (resultCode == Activity.RESULT_OK && appWidgetId >= 0) {
                addAppWidgetImpl(appWidgetId, requestArgs)
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
        // Launcher.completeAdd for REQUEST_CREATE_APPWIDGET
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            cancel(appWidgetId)
            return
        }
        completeAddAppWidget(appWidgetId, requestArgs, info)
    }

    /**
     * Launcher.completeAddShortcut: the shortcut the app's activity made, from the pin request
     * in its result (PinRequestHelper), in the cell of [args].
     */
    private fun completeAddShortcut(data: Intent?, args: PendingRequestArgs) {
        if (args.requestCode != REQUEST_CREATE_SHORTCUT) {
            return
        }

        // TextLauncher: a result without data has no request.
        val info = PinRequestHelper.createWorkspaceItemFromPinItemRequest(
            activity, data?.let(PinRequestHelper::getPinItemRequest), 0,
        )
        if (info == null) {
            Log.e(TAG, "Unable to parse a valid shortcut result")
            return
        }
        // TextLauncher: one page and no folder to add to (the container is the desktop's).
        home.completeAddShortcut(info, args.cellX, args.cellY)
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
     * Launcher.completeAddAppWidget: the widget's item with the spans and cell of [itemInfo] (the
     * PendingAddWidgetInfo, or the flow's PendingRequestArgs made from it), its view, and its
     * size. TextLauncher: when there is no room for it, the widget is not added (Launcher3 would
     * add a page).
     */
    private fun completeAddAppWidget(appWidgetId: Int, itemInfo: ItemInfo, appWidgetInfo: AppWidgetProviderInfo) {
        val launcherInfo = LauncherAppWidgetInfo(appWidgetId, appWidgetInfo.provider)
        launcherInfo.spanX = itemInfo.spanX
        launcherInfo.spanY = itemInfo.spanY
        launcherInfo.minSpanX = itemInfo.minSpanX
        launcherInfo.minSpanY = itemInfo.minSpanY
        launcherInfo.user = appWidgetInfo.profile
        if (itemInfo is PendingAddWidgetInfo) {
            launcherInfo.sourceContainer = itemInfo.sourceContainer
        } else if (itemInfo is PendingRequestArgs) {
            launcherInfo.sourceContainer = itemInfo.widgetSourceContainer
        }
        if (!home.addWidget(launcherInfo, itemInfo.cellX, itemInfo.cellY)) {
            Toast.makeText(activity, R.string.out_of_space, Toast.LENGTH_SHORT).show()
            cancel(appWidgetId)
        }
    }

    companion object {
        /**
         * TextLauncher: the widget ids of the add flow in progress and of its result waiting for
         * the load, which are not ghosts (HomeLoaderHooks.removeGhostWidgets). Main thread only.
         */
        var pendingWidgetIds: Set<Int> = emptySet()
            private set

        private const val TAG = "Launcher"

        /** LauncherConstants.ActivityCodes */
        private const val REQUEST_CREATE_SHORTCUT = 1
        private const val REQUEST_CREATE_APPWIDGET = 5
        private const val REQUEST_BIND_APPWIDGET = 11
        private const val REQUEST_RECONFIGURE_APPWIDGET = 13

        /** LauncherConstants.SavedInstanceKeys */
        private const val RUNTIME_STATE_PENDING_REQUEST_ARGS = "launcher.request_args"
        private const val RUNTIME_STATE_PENDING_ACTIVITY_RESULT = "launcher.activity_result"
    }

    /**
     * PendingRequestArgs.forWidgetInfo and forIntent: the widget id of an add flow and the
     * container it came from, or the request code of a shortcut's activity, and the item it adds.
     * TextLauncher: no WidgetAddFlowHandler or intent in it (the provider is looked up by the
     * id), and it is saved as a Bundle rather than parceled.
     */
    private class PendingRequestArgs private constructor(
        val widgetId: Int,
        val widgetSourceContainer: Int,
        /** PendingRequestArgs.getRequestCode: 0 for a widget. */
        val requestCode: Int = 0,
    ) : ItemInfo() {
        fun toBundle(): Bundle = Bundle().apply {
            putInt(KEY_WIDGET_ID, widgetId)
            putInt(KEY_REQUEST_CODE, requestCode)
            putInt(KEY_SOURCE_CONTAINER, widgetSourceContainer)
            putInt(KEY_ITEM_TYPE, itemType)
            putInt(KEY_CONTAINER, container)
            putInt(KEY_CELL_X, cellX)
            putInt(KEY_CELL_Y, cellY)
            putInt(KEY_SPAN_X, spanX)
            putInt(KEY_SPAN_Y, spanY)
            putInt(KEY_MIN_SPAN_X, minSpanX)
            putInt(KEY_MIN_SPAN_Y, minSpanY)
            putParcelable(KEY_USER, user)
        }

        companion object {
            fun forWidgetInfo(appWidgetId: Int, info: ItemInfo): PendingRequestArgs {
                var sourceContainer = LauncherSettings.Favorites.CONTAINER_UNKNOWN
                if (info is PendingAddWidgetInfo) {
                    sourceContainer = info.sourceContainer
                } else if (info is PendingRequestArgs) {
                    // TextLauncher: a flow that goes on (bind, then configure) keeps its source.
                    sourceContainer = info.widgetSourceContainer
                }
                val args = PendingRequestArgs(appWidgetId, sourceContainer)
                args.copyFrom(info)
                return args
            }

            /** PendingRequestArgs.forIntent. TextLauncher: the intent is not kept. */
            fun forIntent(requestCode: Int, intent: Intent, info: ItemInfo): PendingRequestArgs {
                val args = PendingRequestArgs(-1, LauncherSettings.Favorites.CONTAINER_UNKNOWN, requestCode)
                args.copyFrom(info)
                return args
            }

            fun fromBundle(b: Bundle): PendingRequestArgs =
                PendingRequestArgs(
                    b.getInt(KEY_WIDGET_ID), b.getInt(KEY_SOURCE_CONTAINER), b.getInt(KEY_REQUEST_CODE),
                ).apply {
                    itemType = b.getInt(KEY_ITEM_TYPE)
                    container = b.getInt(KEY_CONTAINER)
                    cellX = b.getInt(KEY_CELL_X)
                    cellY = b.getInt(KEY_CELL_Y)
                    spanX = b.getInt(KEY_SPAN_X)
                    spanY = b.getInt(KEY_SPAN_Y)
                    minSpanX = b.getInt(KEY_MIN_SPAN_X)
                    minSpanY = b.getInt(KEY_MIN_SPAN_Y)
                    b.getParcelable(KEY_USER, UserHandle::class.java)?.let { user = it }
                }

            private const val KEY_WIDGET_ID = "widgetId"
            private const val KEY_REQUEST_CODE = "requestCode"
            private const val KEY_SOURCE_CONTAINER = "sourceContainer"
            private const val KEY_ITEM_TYPE = "itemType"
            private const val KEY_CONTAINER = "container"
            private const val KEY_CELL_X = "cellX"
            private const val KEY_CELL_Y = "cellY"
            private const val KEY_SPAN_X = "spanX"
            private const val KEY_SPAN_Y = "spanY"
            private const val KEY_MIN_SPAN_X = "minSpanX"
            private const val KEY_MIN_SPAN_Y = "minSpanY"
            private const val KEY_USER = "user"
        }
    }

    /** ActivityResultInfo, saved as a Bundle rather than parceled. */
    private class ActivityResultInfo(val requestCode: Int, val resultCode: Int, val data: Intent?) {
        fun toBundle(): Bundle = Bundle().apply {
            putInt(KEY_REQUEST_CODE, requestCode)
            putInt(KEY_RESULT_CODE, resultCode)
            putParcelable(KEY_DATA, data)
        }

        companion object {
            fun fromBundle(b: Bundle) = ActivityResultInfo(
                b.getInt(KEY_REQUEST_CODE),
                b.getInt(KEY_RESULT_CODE),
                b.getParcelable(KEY_DATA, Intent::class.java),
            )

            private const val KEY_REQUEST_CODE = "requestCode"
            private const val KEY_RESULT_CODE = "resultCode"
            private const val KEY_DATA = "data"
        }
    }
}
