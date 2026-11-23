package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.os.Process
import android.os.UserManager
import android.util.Log
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.ContentWriter
import com.android.launcher3.util.Executors.MODEL_EXECUTOR

/**
 * The home layout (docs/spec.md 5.2), as Launcher3's BgDataModel holds it: the ItemInfos on the
 * home grid ([items]; a folder's items are its FolderInfo's contents), kept in memory on the main
 * thread. [loadBlocking] reads them from the favorites table of [LauncherDatabase] (LoaderTask and
 * LoaderCursor) and [save] writes them back as a whole after every change, each item with
 * ItemInfo.onAddToDatabase (ModelWriter), on the model thread as in Launcher3. See HomeItemInfo.kt
 * for the kinds of items.
 */
class HomeModel(
    private val context: Context,
    db: LauncherDatabase,
    /** Makes and keys the shortcut items, and keeps the system's pinned shortcuts in step. */
    private val shortcuts: ShortcutRepository,
    /** Makes the icons saved with the app shortcuts. */
    private val iconCache: IconCache,
) {
    /** LauncherModel.getModelDbController */
    val modelDbController = ModelDbController(db)

    val items = mutableListOf<ItemInfo>()

    /**
     * LauncherModel.isModelLoaded: [items] hold what was read from the database. Until then
     * nothing is saved, as Launcher3's model tasks wait for the load (Tex-twu9).
     */
    var isLoaded = false
        private set

    /**
     * While a drag is in progress, saves wait for its end: the dragged item is out of the model
     * meanwhile (a folder's contents lose it at the start), and Launcher3 writes nothing until the
     * drop (Folder.removeFolderContent does not change the database). See [deferSaves].
     */
    private var savesDeferred = false
    private var saveRequested = false

    /** The shortcut keys the system was last told to keep pinned; null until the first save. */
    private var pinnedKeys: Set<String>? = null

    /**
     * TextLauncher: some app shortcuts were saved without their icons (before the icon column was
     * added); the home screen is saved again once their icons are loaded.
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
                        AppItemInfo(AppEntry.keyOf(component, serial)).also { it.title = c.getString(cursor.title) }
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

    fun setLoaded(loaded: List<ItemInfo>) {
        items.clear()
        items += loaded
        isLoaded = true
    }

    /**
     * Holds [save] from a drag's start to its end (or saves at the end when one was asked for in
     * between), so that a drag is saved once, with the item where it was dropped.
     */
    fun deferSaves(deferred: Boolean) {
        if (savesDeferred == deferred) return
        savesDeferred = deferred
        if (!deferred && saveRequested) {
            saveRequested = false
            save()
        }
    }

    /** ModelDbController.generateNewItemId: the id for a new item. */
    fun newId(): Int = modelDbController.generateNewItemId()

    /** Every item, including the ones in folders. */
    fun allItems(): List<ItemInfo> = items.flatMap { if (it is FolderInfo) listOf(it) + it.contents else listOf(it) }

    /** The keys of every app shortcut on the home screen, also in folders. */
    fun shortcutKeys(): List<String> = allItems().filter { it.isDeepShortcut }.map(shortcuts::keyOf)

    /** The key an app or shortcut is saved by ([AppEntry.key] or [HomeShortcutKey.key]). */
    fun keyOf(item: ItemInfo): String? = when {
        item is AppItemInfo -> item.homeKey
        item.isDeepShortcut -> shortcuts.keyOf(item)
        else -> null
    }

    fun save() {
        if (!isLoaded) {
            Log.w(TAG, "Not saving the home screen before it is loaded")
            return
        }
        if (savesDeferred) {
            saveRequested = true
            return
        }
        // ModelWriter: each item's values (ItemInfo.onAddToDatabase), whose icon ContentWriter
        // flattens on the model thread.
        val writers = mutableListOf<ContentWriter>()
        fun add(item: ItemInfo) {
            if (item.id == ItemInfo.NO_ID) item.id = newId()
            item.screenId = 0
            val writer = ContentWriter(context)
            item.onAddToDatabase(writer)
            writer.put(Favorites._ID, item.id)
            writers += writer
        }
        for (item in items) {
            if (item !is AppItemInfo && item !is FolderInfo && item !is WidgetItemInfo && !item.isDeepShortcut) continue
            item.container = Favorites.CONTAINER_DESKTOP
            add(item)
            if (item is FolderInfo) {
                item.contents.forEachIndexed { rank, content ->
                    if (content !is AppItemInfo && !content.isDeepShortcut) return@forEachIndexed
                    if (content.id == ItemInfo.NO_ID) content.id = newId()
                    content.container = item.id
                    content.rank = rank
                    add(content)
                }
            }
        }
        // BgDataModel.addItems / removeItems update the pinned state when shortcuts are added or
        // removed; here, when the set of shortcut keys changed (Tex-536b).
        val shortcutKeys = shortcutKeys().toSet().takeIf { it != pinnedKeys }
        if (shortcutKeys != null) pinnedKeys = shortcutKeys
        MODEL_EXECUTOR.execute {
            modelDbController.newTransaction().use { t ->
                modelDbController.createEmptyDB()
                for (writer in writers) modelDbController.insert(writer.getValues(context))
                t.commit()
            }
            if (shortcutKeys != null) shortcuts.updatePinnedStateBlocking(shortcutKeys)
        }
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
