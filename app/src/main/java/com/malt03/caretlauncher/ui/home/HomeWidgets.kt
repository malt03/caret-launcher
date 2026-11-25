package com.malt03.caretlauncher.ui.home

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
