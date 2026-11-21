package com.malt03.textlauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
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

    /** LauncherAppsCallbackImpl: the package's icons are updated first (PackageUpdatedTask). */
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = updatePackage(packageName, user, removed = true)
        override fun onPackageAdded(packageName: String, user: UserHandle) = updatePackage(packageName, user, removed = false)
        override fun onPackageChanged(packageName: String, user: UserHandle) = updatePackage(packageName, user, removed = false)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            // OP_UPDATE
            if (isMine(user)) packageNames.forEach { updatePackage(it, user, removed = false) }
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reloadIfMine(user)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = reloadIfMine(user)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = reloadIfMine(user)
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = Unit
    }

    init {
        launcherApps.registerCallback(callback, mainHandler)
        reload()
    }

    fun addListener(listener: (List<AppEntry>) -> Unit) {
        listeners += listener
        apps?.let(listener)
    }

    fun removeListener(listener: (List<AppEntry>) -> Unit) {
        listeners -= listener
    }

    fun find(key: String): AppEntry? = apps?.firstOrNull { it.key == key }

    private fun isMine(changedUser: UserHandle): Boolean = changedUser == user

    private fun reloadIfMine(changedUser: UserHandle) {
        if (isMine(changedUser)) reload()
    }

    private fun updatePackage(packageName: String, changedUser: UserHandle, removed: Boolean) {
        if (isMine(changedUser)) iconLoader.updatePackage(packageName, changedUser, removed, ::reload)
    }

    fun reload() {
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
            }
        }
    }
}
