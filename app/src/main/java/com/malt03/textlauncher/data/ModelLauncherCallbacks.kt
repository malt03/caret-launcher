package com.malt03.textlauncher.data

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.Log

/**
 * Launcher3's ModelLauncherCallbacks: the one LauncherApps.Callback of the app, which turns the
 * system's events into the model's package tasks ([PackageEvent], which [AppRepository] applies to
 * the app list and the icons before the home screen applies it to its items) and shortcut tasks
 * ([ShortcutRepository], ShortcutsChangedTask). TextLauncher: no install sessions or incremental
 * downloads.
 */
class ModelLauncherCallbacks(
    private val apps: AppRepository,
    private val shortcuts: ShortcutRepository,
) : LauncherApps.Callback() {

    override fun onPackageAdded(packageName: String, user: UserHandle) {
        Log.d(TAG, "onPackageAdded triggered for packageName=$packageName, user=$user")
        apps.onPackageEvent(PackageEvent(PackageEvent.OP_ADD, user, setOf(packageName)))
    }

    override fun onPackageChanged(packageName: String, user: UserHandle) {
        apps.onPackageEvent(PackageEvent(PackageEvent.OP_UPDATE, user, setOf(packageName)))
    }

    override fun onPackageRemoved(packageName: String, user: UserHandle) {
        Log.d(TAG, "onPackageRemoved triggered for packageName=$packageName, user=$user")
        apps.onPackageEvent(PackageEvent(PackageEvent.OP_REMOVE, user, setOf(packageName)))
    }

    override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        apps.onPackageEvent(PackageEvent(PackageEvent.OP_UPDATE, user, packageNames.toSet()))
    }

    override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
        apps.onPackageEvent(PackageEvent(PackageEvent.OP_SUSPEND, user, packageNames.toSet()))
    }

    override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        if (!replacing) {
            apps.onPackageEvent(PackageEvent(PackageEvent.OP_UNAVAILABLE, user, packageNames.toSet()))
        }
    }

    override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
        apps.onPackageEvent(PackageEvent(PackageEvent.OP_UNSUSPEND, user, packageNames.toSet()))
    }

    override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
        this.shortcuts.onShortcutsChanged(packageName, shortcuts, user)
    }

    private companion object {
        const val TAG = "LauncherAppsCallbackImpl"
    }
}

/**
 * A package event of [ModelLauncherCallbacks]: PackageUpdatedTask's OP_ADD and OP_UPDATE, and
 * PackageTaskFactory's appsRemoved, appsUnavailable, appsSuspended and appsUnsuspended.
 */
data class PackageEvent(val op: Int, val user: UserHandle, val packages: Set<String>) {
    companion object {
        const val OP_ADD = 1
        const val OP_UPDATE = 2
        const val OP_REMOVE = 3
        const val OP_UNAVAILABLE = 4
        const val OP_SUSPEND = 5
        const val OP_UNSUSPEND = 6
    }
}
