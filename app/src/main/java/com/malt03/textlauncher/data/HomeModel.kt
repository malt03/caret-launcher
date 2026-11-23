package com.malt03.textlauncher.data

import android.graphics.BitmapFactory
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.LauncherSettings
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import java.util.concurrent.Executor

/**
 * The home layout (docs/spec.md 5.2), as Launcher3's BgDataModel holds it: the ItemInfos on the
 * home grid ([items]; a folder's items are its FolderInfo's contents), kept in memory on the main
 * thread. [loadBlocking] reads them from [LauncherDatabase] (LoaderTask) and [save] writes them back
 * as a whole after every change (ModelWriter). See HomeItemInfo.kt for the kinds of items.
 */
class HomeModel(
    private val db: LauncherDatabase,
    private val ioExecutor: Executor,
    /** Makes and keys the shortcut items, and keeps the system's pinned shortcuts in step. */
    private val shortcuts: ShortcutRepository,
    /** Makes the icons saved with the app shortcuts, and tells the default icon apart. */
    private val iconCache: IconCache,
) {
    val items = mutableListOf<ItemInfo>()
    private var nextId = 1

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

    /** Called on a background thread once, before the model is used. */
    fun loadBlocking(): List<ItemInfo> {
        val rows = db.loadHomeRows()
        hasShortcutsWithoutIcons = rows.any { it.type == HomeRow.TYPE_SHORTCUT && it.icon == null }
        val loaded = mutableListOf<ItemInfo>()
        val folders = HashMap<Long, FolderInfo>()
        for (row in rows.filter { it.container == HomeRow.CONTAINER_HOME }) {
            val item = when (row.type) {
                HomeRow.TYPE_APP -> row.appKey?.let(::AppItemInfo)
                HomeRow.TYPE_SHORTCUT -> row.appKey?.let(shortcuts::newItem)?.also { it.loadIcon(row.icon) }
                HomeRow.TYPE_FOLDER -> FolderInfo().also { folders[row.id] = it }
                HomeRow.TYPE_WIDGET -> if (row.widgetId != null && row.provider != null) {
                    WidgetItemInfo(row.widgetId, row.provider)
                } else {
                    null
                }
                else -> null
            } ?: continue
            item.id = row.id.toInt()
            item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            item.cellX = row.cellX; item.cellY = row.cellY
            item.spanX = row.spanX; item.spanY = row.spanY
            loaded += item
        }
        for (row in rows.filter { it.container != HomeRow.CONTAINER_HOME }.sortedBy { it.rank }) {
            val folder = folders[row.container] ?: continue
            val key = row.appKey ?: continue
            val item = if (row.type == HomeRow.TYPE_SHORTCUT) {
                shortcuts.newItem(key)?.also { it.loadIcon(row.icon) } ?: continue
            } else {
                AppItemInfo(key)
            }
            item.id = row.id.toInt()
            item.container = folder.id
            item.rank = folder.contents.size
            folder.contents += item
        }
        return loaded
    }

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
        nextId = (allItems().maxOfOrNull { it.id } ?: 0) + 1
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

    /** ModelWriter's id for a new item. */
    fun newId(): Int = nextId++

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
        val rows = mutableListOf<HomeRow>()
        // ContentWriter.putIcon: an app shortcut's icon is saved with it, by row index.
        val icons = HashMap<Int, Pair<BitmapInfo, UserHandle>>()
        fun add(item: ItemInfo, row: HomeRow) {
            if (item is WorkspaceItemInfo && item.isDeepShortcut) icons[rows.size] = item.bitmap to item.user
            rows += row
        }
        for (item in items) {
            if (item.id == ItemInfo.NO_ID) item.id = newId()
            item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            add(item, when (item) {
                is AppItemInfo -> row(item, HomeRow.TYPE_APP, HomeRow.CONTAINER_HOME, item.homeKey)
                is FolderInfo -> row(item, HomeRow.TYPE_FOLDER, HomeRow.CONTAINER_HOME, null)
                is WidgetItemInfo -> row(item, HomeRow.TYPE_WIDGET, HomeRow.CONTAINER_HOME, null)
                else -> if (item.isDeepShortcut) {
                    row(item, HomeRow.TYPE_SHORTCUT, HomeRow.CONTAINER_HOME, shortcuts.keyOf(item))
                } else {
                    continue
                }
            })
            if (item is FolderInfo) {
                item.contents.forEachIndexed { rank, content ->
                    if (content.id == ItemInfo.NO_ID) content.id = newId()
                    content.container = item.id
                    content.rank = rank
                    val type = if (content.isDeepShortcut) HomeRow.TYPE_SHORTCUT else HomeRow.TYPE_APP
                    add(content, row(content, type, item.id.toLong(), keyOf(content) ?: return@forEachIndexed))
                }
            }
        }
        // BgDataModel.addItems / removeItems update the pinned state when shortcuts are added or
        // removed; here, when the set of shortcut keys changed (Tex-536b).
        val shortcutKeys = shortcutKeys().toSet().takeIf { it != pinnedKeys }
        if (shortcutKeys != null) pinnedKeys = shortcutKeys
        ioExecutor.execute {
            db.replaceHomeRows(rows.mapIndexed { index, row -> icons[index]?.let { (bitmap, user) -> row.withIcon(bitmap, user) } ?: row })
            if (shortcutKeys != null) shortcuts.updatePinnedStateBlocking(shortcutKeys)
        }
    }

    /** ContentWriter.getValues: the icon is not saved when it is the default one. */
    private fun HomeRow.withIcon(bitmap: BitmapInfo, user: UserHandle): HomeRow =
        // TextLauncher: nor while it is not loaded yet.
        if (bitmap.isLowRes || iconCache.isDefaultIcon(bitmap, user)) {
            this
        } else {
            copy(icon = GraphicsUtils.createDefaultFlatBitmap(bitmap))
        }

    private fun row(item: ItemInfo, type: Int, container: Long, key: String?): HomeRow = HomeRow(
        item.id.toLong(), type, container, item.cellX, item.cellY, item.spanX, item.spanY, item.rank, key,
        (item as? WidgetItemInfo)?.appWidgetId, (item as? WidgetItemInfo)?.provider,
    )

    private companion object {
        const val TAG = "HomeModel"
    }
}
