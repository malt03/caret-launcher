package com.malt03.textlauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
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
 * The app shortcuts placed on the home screen: looks them up and reports changes (the home model
 * keeps the system's pinned state in step, BgDataModel.updateShortcutPinnedState).
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

    /** The key a shortcut on the home screen is saved by, from ShortcutKey.fromItemInfo. */
    fun keyOf(info: ItemInfo): String {
        val key = ShortcutKey.fromItemInfo(info)
        return HomeShortcutKey(key.packageName, key.id, userSerial).key
    }

    /**
     * The home screen's item for a saved shortcut key, before the shortcut is looked up
     * (LoaderTask: the WorkspaceItemInfo is made from the saved intent). Null for a key of another
     * user, which TextLauncher does not show (docs/spec.md 2.1).
     */
    fun newItem(key: String): WorkspaceItemInfo? {
        val parsed = HomeShortcutKey.parse(key) ?: return null
        val user = userOf(parsed) ?: return null
        return WorkspaceItemInfo().apply {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
            intent = ShortcutKey.makeIntent(parsed.id, parsed.packageName)
            this.user = user
        }
    }

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
}
