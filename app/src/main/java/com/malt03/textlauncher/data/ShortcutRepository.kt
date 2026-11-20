package com.malt03.textlauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.android.launcher3.shortcuts.ShortcutRequest

/**
 * An app shortcut placed on the home screen (docs/spec.md 5.3). Identified like Launcher3's
 * ShortcutKey (package and shortcut id) plus the user, as [AppEntry] keys are.
 */
data class HomeShortcutKey(val packageName: String, val id: String, val userSerial: Long) {
    val key: String get() = "$PREFIX$packageName/$id#$userSerial"

    companion object {
        private const val PREFIX = "shortcut:"

        fun isShortcutKey(key: String): Boolean = key.startsWith(PREFIX)

        fun parse(key: String): HomeShortcutKey? {
            if (!isShortcutKey(key)) return null
            val body = key.removePrefix(PREFIX)
            val serial = body.substringAfterLast('#').toLongOrNull() ?: return null
            val packageAndId = body.substringBeforeLast('#')
            val slash = packageAndId.indexOf('/')
            if (slash <= 0) return null
            return HomeShortcutKey(packageAndId.substring(0, slash), packageAndId.substring(slash + 1), serial)
        }
    }
}

/**
 * The app shortcuts placed on the home screen: looks them up, keeps the system's pinned state in
 * step with the home layout (BgDataModel.updateShortcutPinnedState), and reports changes.
 * Personal profile only (docs/spec.md 2.1).
 */
class ShortcutRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val userSerial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = Unit
        override fun onPackageAdded(packageName: String, user: UserHandle) = Unit
        override fun onPackageChanged(packageName: String, user: UserHandle) = Unit
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit

        /** ShortcutsChangedTask: the home screen's shortcuts may have changed or gone. */
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            if (user == this@ShortcutRepository.user) listeners.toList().forEach { it() }
        }
    }

    init {
        launcherApps.registerCallback(callback, mainHandler)
    }

    /** Called on the main thread when an app's shortcuts changed. */
    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun keyOf(shortcut: ShortcutInfo): String = HomeShortcutKey(shortcut.`package`, shortcut.id, userSerial).key

    fun userOf(key: HomeShortcutKey): UserHandle? = if (key.userSerial == userSerial) user else null

    /**
     * Looks up the shortcuts for [keys] (pinned, dynamic or from the manifest; a shortcut that was
     * just placed is pinned only after the next save). Missing ones are left out. Returns null
     * when the system could not be asked, as LoaderTask treats a failed query: nothing is known
     * to be gone then. Background thread.
     */
    fun loadBlocking(keys: Collection<String>): Map<String, ShortcutInfo>? {
        val result = HashMap<String, ShortcutInfo>()
        keys.mapNotNull(HomeShortcutKey::parse).filter { it.userSerial == userSerial }
            .groupBy { it.packageName }
            .forEach { (packageName, shortcutKeys) ->
                val shortcuts = ShortcutRequest(context, user)
                    .forPackage(packageName, shortcutKeys.map { it.id })
                    .query(ShortcutRequest.ALL)
                if (!shortcuts.wasSuccess()) return null
                shortcuts.forEach { result[keyOf(it)] = it }
            }
        return result
    }

    /** The app a shortcut belongs to: its activity, or else its package's first app. */
    fun appOf(shortcut: ShortcutInfo, apps: List<AppEntry>): AppEntry? =
        shortcut.activity?.let { activity -> apps.firstOrNull { it.key == AppEntry.keyOf(activity, userSerial) } }
            ?: apps.firstOrNull { it.component.packageName == shortcut.`package` && it.user == user }

    /**
     * BgDataModel.updateShortcutPinnedState: updates the deep shortcuts state in system to match
     * the home layout, pinning any missing shortcuts and unpinning any extra shortcuts.
     * Background thread.
     */
    fun updatePinnedStateBlocking(keys: Collection<String>) {
        // Collect all system shortcuts
        val result = ShortcutRequest(context, user)
            .query(ShortcutRequest.PINNED or ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY)
        if (!result.wasSuccess()) {
            return
        }

        // Map of packageName to shortcutIds that are currently in the system
        val systemMap: MutableMap<String, MutableSet<String>> = result
            .groupingBy { it.`package` }
            .foldTo(
                destination = mutableMapOf(),
                initialValueSelector = { _, element -> mutableSetOf(element.id) },
                operation = { _, accumulator, element -> accumulator.apply { add(element.id) } },
            )

        // Map of packageName to shortcutIds that are currently in our model
        val modelMap: Map<String, Set<String>> = keys.mapNotNull(HomeShortcutKey::parse)
            .filter { it.userSerial == userSerial }
            .groupBy({ it.packageName }, { it.id })
            .mapValues { it.value.toSet() }

        // Check for diff
        for ((key, modelShortcuts) in modelMap) {
            val systemShortcuts = systemMap.remove(key) ?: emptySet()

            // Do not use .equals as it can vary based on the type of set
            if (systemShortcuts.size != modelShortcuts.size || !systemShortcuts.containsAll(modelShortcuts)) {
                // Update system state for this package
                pinShortcuts(key, modelShortcuts.toList())
            }
        }

        // If there are any extra pinned shortcuts, remove them
        systemMap.keys.forEach { packageName -> pinShortcuts(packageName, emptyList()) }
    }

    private fun pinShortcuts(packageName: String, ids: List<String>) {
        try {
            launcherApps.pinShortcuts(packageName, ids, user)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to pin shortcuts", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to pin shortcuts", e)
        }
    }

    private companion object {
        const val TAG = "ShortcutRepository"
    }
}
