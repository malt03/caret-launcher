package com.malt03.textlauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.malt03.textlauncher.icons.AppIconLoader
import java.util.concurrent.Executors

/**
 * Loads launchable apps of the personal profile only (docs/spec.md 2.1) and keeps them up to date.
 */
class AppRepository(private val context: Context, private val iconLoader: AppIconLoader) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(List<AppEntry>) -> Unit>()

    /** Null until the first load finishes. */
    var apps: List<AppEntry>? = null
        private set

    /** Called once the app list reflects a package event (the home screen's package tasks). */
    private val packageListeners = mutableListOf<(PackageEvent) -> Unit>()

    init {
        reload(null)
    }

    fun addListener(listener: (List<AppEntry>) -> Unit) {
        listeners += listener
        apps?.let(listener)
    }

    fun removeListener(listener: (List<AppEntry>) -> Unit) {
        listeners -= listener
    }

    /** Called on the main thread with each package event, once [apps] includes it. */
    fun addPackageListener(listener: (PackageEvent) -> Unit) {
        packageListeners += listener
    }

    fun removePackageListener(listener: (PackageEvent) -> Unit) {
        packageListeners -= listener
    }

    fun find(key: String): AppEntry? = apps?.firstOrNull { it.key == key }

    private fun isMine(changedUser: UserHandle): Boolean = changedUser == user

    /**
     * A package event from [ModelLauncherCallbacks]: as in Launcher3's package tasks, the icon
     * cache (PackageUpdatedTask: IconCache.updateIconsForPkg, appsRemoved: removeIconsForPkg) and
     * the app list (AllAppsList.updatePackage / removePackage) are updated first; then the
     * [packageListeners] apply it to their items.
     */
    fun onPackageEvent(event: PackageEvent) {
        if (!isMine(event.user)) return
        when (event.op) {
            PackageEvent.OP_ADD, PackageEvent.OP_UPDATE, PackageEvent.OP_REMOVE -> {
                var remaining = event.packages.size
                if (remaining == 0) return
                for (packageName in event.packages) {
                    iconLoader.updatePackage(packageName, event.user, removed = event.op == PackageEvent.OP_REMOVE) {
                        if (--remaining == 0) reload(event)
                    }
                }
            }
            else -> reload(event)
        }
    }

    fun reload() = reload(null)

    private fun reload(event: PackageEvent?) {
        executor.execute {
            val serial = userManager.getSerialNumberForUser(user)
            val loaded = launcherApps.getActivityList(null, user)
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    AppEntry(
                        key = AppEntry.keyOf(info.componentName, serial),
                        component = info.componentName,
                        user = user,
                        label = info.label?.toString().orEmpty(),
                        info = info,
                    )
                }
            mainHandler.post {
                apps = loaded
                listeners.toList().forEach { it(loaded) }
                if (event != null) packageListeners.toList().forEach { it(event) }
            }
        }
    }
}
