package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.Context
import com.android.launcher3.AppFilter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

/**
 * The all apps list as the home screen's ModelCallbacks.bindAllApplications gets it from
 * Launcher3's LauncherModel (LoaderTask.loadAllApps and the package tasks keep the AllAppsList on
 * the model thread): the AppInfos with their icons, titles and runtime flags, which the all apps
 * list, the search and the home screen look apps up in by ComponentKey. Main thread only.
 */
class AppRepository {
    private val listeners = mutableListOf<(Array<AppInfo>) -> Unit>()

    /**
     * The apps (AllAppsList.copyData: sorted by COMPONENT_KEY_COMPARATOR, as AllAppsStore keeps
     * them). Null until the first load finishes.
     */
    var apps: Array<AppInfo>? = null
        private set

    /** ModelCallbacks.bindAllApplications */
    fun bindAllApplications(apps: Array<AppInfo>) {
        this.apps = apps
        listeners.toList().forEach { it(apps) }
    }

    /** Called on the main thread with every new [apps]. */
    fun addListener(listener: (Array<AppInfo>) -> Unit) {
        listeners += listener
        apps?.let(listener)
    }

    fun removeListener(listener: (Array<AppInfo>) -> Unit) {
        listeners -= listener
    }

    /** AllAppsStore.getApp: the app of [key], if it is in the list. */
    fun getApp(key: ComponentKey): AppInfo? {
        val apps = apps ?: return null
        val index = apps.binarySearch(
            AppInfo().apply {
                componentName = key.componentName
                user = key.user
            },
            AppInfo.COMPONENT_KEY_COMPARATOR,
        )
        return if (index < 0) null else apps[index]
    }
}

/** AppFilter: TextLauncher also leaves itself out of the list. */
class LauncherAppFilter(private val context: Context) : AppFilter(context) {
    override fun shouldShowApp(app: ComponentName): Boolean =
        app.packageName != context.packageName && super.shouldShowApp(app)
}
