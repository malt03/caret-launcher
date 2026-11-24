package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.util.SparseArray
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelWriter

/**
 * The home layout (docs/spec.md 5.2), in the role of Launcher3's LauncherModel: its BgDataModel
 * ([bgDataModel]) holds every ItemInfo by id, on the main thread, and a folder's items are also
 * its FolderInfo's contents, as in Launcher3. [loadBlocking] reads them from the favorites table of
 * [LauncherDatabase] (LoaderTask and LoaderCursor). Every change goes through [writer]
 * (ModelWriter), which changes the in-memory model and writes the changed items to the database
 * one by one on the model thread; adding and removing shortcuts updates the system's pinned
 * shortcuts (BgDataModel.updateShortcutPinnedState). See HomeItemInfo.kt for the kinds of items.
 */
class HomeModel(
    private val context: Context,
    db: LauncherDatabase,
    /** Makes and keys the shortcut items. */
    private val shortcuts: ShortcutRepository,
    /** Makes the icons saved with the app shortcuts. */
    private val iconCache: IconCache,
) {
    /** LauncherModel.getModelDbController */
    val modelDbController = ModelDbController(context, db)

    val bgDataModel = BgDataModel()

    /**
     * LauncherModel.getWriter. The home screen changes the model only once it is loaded: no drag
     * starts and nothing is added while it loads (Tex-twu9).
     */
    val writer = ModelWriter(context, modelDbController, bgDataModel, BgDataModel.ModificationSource.ModelTask)

    /** LauncherModel.isModelLoaded: [bgDataModel] holds what was read from the database. */
    var isLoaded = false
        private set

    /**
     * TextLauncher: some app shortcuts were saved without their icons (before the icon column was
     * added); they are saved again once their icons are loaded.
     */
    var hasShortcutsWithoutIcons = false

    /**
     * LoaderTask.loadWorkspace: called on the model thread once, before the model is used. The
     * items of other users are left out (docs/spec.md 2.1), as are the rows that cannot be read
     * (LoaderCursor.markDeleted).
     */
    fun loadBlocking(): List<ItemInfo> {
        val userManager = context.getSystemService(UserManager::class.java)
        val myUser = Process.myUserHandle()
        val mySerial = userManager.getSerialNumberForUser(myUser)
        val loaded = mutableListOf<ItemInfo>()
        val folders = HashMap<Int, FolderInfo>()
        val folderItems = mutableListOf<Pair<Int, ItemInfo>>()
        var withoutIcons = false
        modelDbController.query(null, null, null, "${Favorites.CONTAINER}, ${Favorites.RANK}").use { c ->
            val cursor = Columns(c)
            while (c.moveToNext()) {
                val serial = c.getLong(cursor.profileId)
                // TextLauncher: the personal profile only.
                if (serial != mySerial) continue
                val itemType = c.getInt(cursor.itemType)
                val intent = if (c.isNull(cursor.intent)) null else parseIntent(c.getString(cursor.intent))
                val item: ItemInfo = when (itemType) {
                    Favorites.ITEM_TYPE_APPLICATION -> {
                        val component = intent?.component ?: continue
                        AppItemInfo(component, serial).also { it.title = c.getString(cursor.title) }
                    }
                    Favorites.ITEM_TYPE_DEEP_SHORTCUT -> {
                        val key = ShortcutKey.fromIntent(intent ?: continue, myUser)
                        val info = shortcuts.newItem(HomeShortcutKey(key.packageName, key.id, serial).key) ?: continue
                        // LoaderCursor.loadSimpleWorkspaceItem: the saved title, until the
                        // shortcut is looked up.
                        info.title = c.getString(cursor.title)
                        if (!info.title.isNullOrEmpty()) {
                            info.contentDescription = context.packageManager.getUserBadgedLabel(info.title!!, info.user)
                        }
                        val blob = if (c.isNull(cursor.icon)) null else c.getBlob(cursor.icon)
                        if (blob == null) withoutIcons = true
                        info.loadIcon(blob)
                        info
                    }
                    Favorites.ITEM_TYPE_FOLDER -> FolderInfo().also { it.options = c.getInt(cursor.options) }
                    Favorites.ITEM_TYPE_APPWIDGET -> {
                        val provider = if (c.isNull(cursor.appWidgetProvider)) null else c.getString(cursor.appWidgetProvider)
                        val component = provider?.let(ComponentName::unflattenFromString) ?: continue
                        WidgetItemInfo(c.getInt(cursor.appWidgetId), component).also {
                            it.options = c.getInt(cursor.options)
                            it.sourceContainer = c.getInt(cursor.appWidgetSource)
                        }
                    }
                    else -> continue
                }
                item.id = c.getInt(cursor.id)
                item.container = c.getInt(cursor.container)
                item.screenId = 0
                item.cellX = c.getInt(cursor.cellX)
                item.cellY = c.getInt(cursor.cellY)
                item.spanX = c.getInt(cursor.spanX)
                item.spanY = c.getInt(cursor.spanY)
                item.rank = c.getInt(cursor.rank)
                if (item.container == Favorites.CONTAINER_DESKTOP) {
                    loaded += item
                    if (item is FolderInfo) folders[item.id] = item
                } else {
                    folderItems += item.container to item
                }
            }
        }
        // LoaderTask: the items in folders, by rank; those whose folder is gone are left out.
        for ((container, item) in folderItems.sortedBy { it.second.rank }) {
            val folder = folders[container] ?: continue
            if (item !is WorkspaceItemInfo) continue
            item.rank = folder.contents.size
            folder.contents += item
        }
        hasShortcutsWithoutIcons = withoutIcons
        return loaded
    }

    /** LoaderCursor.parseIntent */
    private fun parseIntent(intentString: String): Intent? =
        runCatching { Intent.parseUri(intentString, 0) }
            .onFailure { Log.e(TAG, "Error parsing intent", it) }
            .getOrNull()

    /**
     * IconRequestInfo.loadIconFromDb / parseIconBlob: the icon saved with an app shortcut, which it
     * keeps when it cannot be looked up.
     */
    private fun WorkspaceItemInfo.loadIcon(blob: ByteArray?) {
        if (blob == null) return
        try {
            iconCache.iconFactory.use {
                bitmap = it.createIconBitmap(BitmapFactory.decodeByteArray(blob, 0, blob.size), /* isFullBleed */ false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode byte array for info $this", e)
        }
    }

    /**
     * BgDataModel.dataLoadComplete: [loaded] (the items on the home page, with their folders'
     * contents) become the model.
     */
    fun setLoaded(loaded: List<ItemInfo>) {
        val all = SparseArray<ItemInfo>()
        for (item in loaded) {
            all.put(item.id, item)
            if (item is FolderInfo) item.contents.forEach { all.put(it.id, it) }
        }
        bgDataModel.dataLoadComplete(all, "load")
        isLoaded = true
    }

    /**
     * The items on the home page (CONTAINER_DESKTOP), in the order of their ids; a folder's items
     * are its FolderInfo's contents.
     */
    val items: List<ItemInfo>
        get() = bgDataModel.itemsIdMap.filter { it.container == Favorites.CONTAINER_DESKTOP }

    /** Every item, including the ones in folders (BgDataModel.itemsIdMap). */
    fun allItems(): List<ItemInfo> = bgDataModel.itemsIdMap.toList()

    private var shortcutsSanitized = false

    /**
     * LoaderTask.sanitizeWidgetsShortcutsAndPackages: once the loaded shortcuts were looked up (and
     * the missing ones deleted), the system's pinned shortcuts are made to match the model. Once
     * per load.
     */
    fun sanitizeShortcutsOnce() {
        if (shortcutsSanitized) return
        shortcutsSanitized = true
        // Update pinned state of model shortcuts
        bgDataModel.updateShortcutPinnedState(context)
    }

    /** Whether [item] is in the model (and not a copy, or an item that was deleted). */
    fun contains(item: ItemInfo): Boolean = bgDataModel.itemsIdMap[item.id] === item

    /** The keys of every app shortcut on the home screen, also in folders. */
    fun shortcutKeys(): List<String> = allItems().filter { it.isDeepShortcut }.map(shortcuts::keyOf)

    /** The key an app or shortcut is saved by ([AppItemInfo.homeKey] or [HomeShortcutKey.key]). */
    fun keyOf(item: ItemInfo): String? = when {
        item is AppItemInfo -> item.homeKey
        item.isDeepShortcut -> shortcuts.keyOf(item)
        else -> null
    }

    /** LoaderCursor's column indices. */
    private class Columns(c: Cursor) {
        val id = c.getColumnIndexOrThrow(Favorites._ID)
        val title = c.getColumnIndexOrThrow(Favorites.TITLE)
        val intent = c.getColumnIndexOrThrow(Favorites.INTENT)
        val container = c.getColumnIndexOrThrow(Favorites.CONTAINER)
        val itemType = c.getColumnIndexOrThrow(Favorites.ITEM_TYPE)
        val cellX = c.getColumnIndexOrThrow(Favorites.CELLX)
        val cellY = c.getColumnIndexOrThrow(Favorites.CELLY)
        val spanX = c.getColumnIndexOrThrow(Favorites.SPANX)
        val spanY = c.getColumnIndexOrThrow(Favorites.SPANY)
        val rank = c.getColumnIndexOrThrow(Favorites.RANK)
        val options = c.getColumnIndexOrThrow(Favorites.OPTIONS)
        val icon = c.getColumnIndexOrThrow(Favorites.ICON)
        val appWidgetId = c.getColumnIndexOrThrow(Favorites.APPWIDGET_ID)
        val appWidgetProvider = c.getColumnIndexOrThrow(Favorites.APPWIDGET_PROVIDER)
        val appWidgetSource = c.getColumnIndexOrThrow(Favorites.APPWIDGET_SOURCE)
        val profileId = c.getColumnIndexOrThrow(Favorites.PROFILE_ID)
    }

    private companion object {
        const val TAG = "HomeModel"
    }
}
