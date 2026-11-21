package com.malt03.textlauncher.data

import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
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
) {
    val items = mutableListOf<ItemInfo>()
    private var nextId = 1

    /** Called on a background thread once, before the model is used. */
    fun loadBlocking(): List<ItemInfo> {
        val rows = db.loadHomeRows()
        val loaded = mutableListOf<ItemInfo>()
        val folders = HashMap<Long, FolderInfo>()
        for (row in rows.filter { it.container == HomeRow.CONTAINER_HOME }) {
            val item = when (row.type) {
                HomeRow.TYPE_APP -> row.appKey?.let(::AppItemInfo)
                HomeRow.TYPE_SHORTCUT -> row.appKey?.let(shortcuts::newItem)
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
            val item = if (row.type == HomeRow.TYPE_SHORTCUT) shortcuts.newItem(key) ?: continue else AppItemInfo(key)
            item.id = row.id.toInt()
            item.container = folder.id
            item.rank = folder.contents.size
            folder.contents += item
        }
        return loaded
    }

    fun setLoaded(loaded: List<ItemInfo>) {
        items.clear()
        items += loaded
        nextId = (allItems().maxOfOrNull { it.id } ?: 0) + 1
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
        val rows = mutableListOf<HomeRow>()
        for (item in items) {
            if (item.id == ItemInfo.NO_ID) item.id = newId()
            item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            rows += when (item) {
                is AppItemInfo -> row(item, HomeRow.TYPE_APP, HomeRow.CONTAINER_HOME, item.homeKey)
                is FolderInfo -> row(item, HomeRow.TYPE_FOLDER, HomeRow.CONTAINER_HOME, null)
                is WidgetItemInfo -> row(item, HomeRow.TYPE_WIDGET, HomeRow.CONTAINER_HOME, null)
                else -> if (item.isDeepShortcut) {
                    row(item, HomeRow.TYPE_SHORTCUT, HomeRow.CONTAINER_HOME, shortcuts.keyOf(item))
                } else {
                    continue
                }
            }
            if (item is FolderInfo) {
                item.contents.forEachIndexed { rank, content ->
                    if (content.id == ItemInfo.NO_ID) content.id = newId()
                    content.container = item.id
                    content.rank = rank
                    val type = if (content.isDeepShortcut) HomeRow.TYPE_SHORTCUT else HomeRow.TYPE_APP
                    rows += row(content, type, item.id.toLong(), keyOf(content) ?: return@forEachIndexed)
                }
            }
        }
        val shortcutKeys = shortcutKeys()
        ioExecutor.execute {
            db.replaceHomeRows(rows)
            // BgDataModel.addItems / removeItems update the pinned state after model changes.
            shortcuts.updatePinnedStateBlocking(shortcutKeys)
        }
    }

    private fun row(item: ItemInfo, type: Int, container: Long, key: String?): HomeRow = HomeRow(
        item.id.toLong(), type, container, item.cellX, item.cellY, item.spanX, item.spanY, item.rank, key,
        (item as? WidgetItemInfo)?.appWidgetId, (item as? WidgetItemInfo)?.provider,
    )
}
