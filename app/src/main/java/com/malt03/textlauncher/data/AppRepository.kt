package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.AppFilter
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.ItemInfoMatcher
import com.malt03.textlauncher.icons.AppIconLoader

/**
 * LauncherModel's app list: Launcher3's [AllAppsList] of the personal profile (docs/spec.md 2.1),
 * kept on the model thread (Executors.MODEL_EXECUTOR) by LoaderTask.loadAllApps and the package
 * tasks, as in Launcher3. Its AppInfos, with their icons, titles and runtime flags, are bound to the
 * main thread (ModelTaskController.bindApplicationsIfNeeded and bindAllApplications) as [apps],
 * which the all apps list, the search and the home screen look apps up in by ComponentKey.
 */
class AppRepository(private val context: Context, private val iconLoader: AppIconLoader) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(Array<AppInfo>) -> Unit>()

    /** Model thread only. TextLauncher: the launcher itself is not in the list. */
    private val allAppsList = AllAppsList(iconLoader.iconCache, LauncherAppFilter(context))

    /**
     * The apps (AllAppsList.copyData: sorted by COMPONENT_KEY_COMPARATOR, as AllAppsStore keeps
     * them); main thread only. Null until the first load finishes.
     */
    var apps: Array<AppInfo>? = null
        private set

    /** Called once the app list reflects a package event (the home screen's package tasks). */
    private val packageListeners = mutableListOf<(PackageEvent, Map<String, List<LauncherActivityInfo>>) -> Unit>()

    init {
        loadAllApps()
        // LauncherModel.onPackageIconsUpdated (CacheDataUpdatedTask) and, for every icon,
        // ModelInitializer.refreshAndReloadLauncher (the apps are loaded again).
        iconLoader.addChangeListener { packages ->
            if (packages == null) loadAllApps() else updateIconsAndLabels(packages)
        }
    }

    /** ModelCallbacks.bindAllApplications: called on the main thread with every new [apps]. */
    fun addListener(listener: (Array<AppInfo>) -> Unit) {
        listeners += listener
        apps?.let(listener)
    }

    fun removeListener(listener: (Array<AppInfo>) -> Unit) {
        listeners -= listener
    }

    /**
     * Called on the main thread with each package event once [apps] includes it, with the
     * launchable activities of the packages AllAppsList.updatePackage found (OP_ADD, OP_UPDATE).
     */
    fun addPackageListener(listener: (PackageEvent, Map<String, List<LauncherActivityInfo>>) -> Unit) {
        packageListeners += listener
    }

    fun removePackageListener(listener: (PackageEvent, Map<String, List<LauncherActivityInfo>>) -> Unit) {
        packageListeners -= listener
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

    private fun isMine(changedUser: UserHandle): Boolean = changedUser == user

    /**
     * LoaderTask.loadAllApps: the launchable activities as AppInfos, with their icons and section
     * names (TextLauncher: looked up one by one, not in bulk), then bound.
     */
    private fun loadAllApps() {
        MODEL_EXECUTOR.execute {
            allAppsList.clear()
            for (app in launcherApps.getActivityList(null, user)) {
                allAppsList.add(AppInfo(app, user), app)
            }
            allAppsList.getAndResetChangeFlag()
            // BaseLauncherBinder.bindAllApps: bound even when nothing is in the list.
            bindApplications(allAppsList.copyData(), null, emptyMap())
        }
    }

    /** CacheDataUpdatedTask's AllAppsList.updateIconsAndLabels for the icons updated in the cache. */
    private fun updateIconsAndLabels(packages: Set<String>) {
        MODEL_EXECUTOR.execute {
            allAppsList.updateIconsAndLabels(HashSet(packages), user)
            bindApplicationsIfNeeded(null, emptyMap())
        }
    }

    /**
     * A package event from [ModelLauncherCallbacks]: the app list part of Launcher3's package
     * tasks (PackageUpdatedTask, PackageTaskFactory), with the icon cache's, on the model thread;
     * then the [packageListeners] apply it to their items.
     */
    fun onPackageEvent(event: PackageEvent) {
        if (!isMine(event.user)) return
        val packages = event.packages
        MODEL_EXECUTOR.execute {
            val iconCache = iconLoader.iconCache
            val activitiesLists = HashMap<String, List<LauncherActivityInfo>>()
            when (event.op) {
                PackageEvent.OP_ADD, PackageEvent.OP_UPDATE -> {
                    val updated = packages.toMutableSet()
                    if (event.op == PackageEvent.OP_UPDATE) {
                        // PackageUpdatedTask: disabled packages are removed (appsRemoved).
                        val disabledPackages = updated.filter { !launcherApps.isPackageEnabled(it, event.user) }
                        updated.removeAll(disabledPackages.toSet())
                        for (packageName in disabledPackages) {
                            iconCache.removeIconsForPkg(packageName, event.user)
                            allAppsList.removePackage(packageName, event.user)
                        }
                    }
                    for (packageName in updated) {
                        iconCache.updateIconsForPkg(packageName, event.user)
                        activitiesLists[packageName] = allAppsList.updatePackage(context, packageName, event.user)
                    }
                }
                PackageEvent.OP_REMOVE -> for (packageName in packages) {
                    iconCache.removeIconsForPkg(packageName, event.user)
                    allAppsList.removePackage(packageName, event.user)
                }
                PackageEvent.OP_UNAVAILABLE -> for (packageName in packages) {
                    allAppsList.removePackage(packageName, event.user)
                }
                PackageEvent.OP_SUSPEND -> allAppsList.updateRuntimeFlags(
                    ItemInfoMatcher.ofPackages(packages, event.user), FlagOp.NO_OP.addFlag(FLAG_DISABLED_SUSPENDED),
                )
                PackageEvent.OP_UNSUSPEND -> allAppsList.updateRuntimeFlags(
                    ItemInfoMatcher.ofPackages(packages, event.user), FlagOp.NO_OP.removeFlag(FLAG_DISABLED_SUSPENDED),
                )
            }
            bindApplicationsIfNeeded(event, activitiesLists)
        }
    }

    /** ModelTaskController.bindApplicationsIfNeeded, then the package event's tasks. */
    private fun bindApplicationsIfNeeded(event: PackageEvent?, activitiesLists: Map<String, List<LauncherActivityInfo>>) {
        val data = if (allAppsList.getAndResetChangeFlag()) allAppsList.copyData() else null
        bindApplications(data, event, activitiesLists)
    }

    private fun bindApplications(
        data: Array<AppInfo>?,
        event: PackageEvent?,
        activitiesLists: Map<String, List<LauncherActivityInfo>>,
    ) {
        if (data == null && event == null) return
        mainHandler.post {
            if (data != null) {
                apps = data
                listeners.toList().forEach { it(data) }
            }
            if (event != null) packageListeners.toList().forEach { it(event, activitiesLists) }
        }
    }

    /** AppFilter: TextLauncher also leaves itself out of the list. */
    private class LauncherAppFilter(private val context: Context) : AppFilter(context) {
        override fun shouldShowApp(app: ComponentName): Boolean =
            app.packageName != context.packageName && super.shouldShowApp(app)
    }
}
