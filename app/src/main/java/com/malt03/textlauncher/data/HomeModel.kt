package com.malt03.textlauncher.data

import java.util.concurrent.Executor

/** Something placed on the home screen (docs/spec.md 5.2). */
sealed class HomeItem(val id: Long) {
    var cellX = 0
    var cellY = 0
    var spanX = 1
    var spanY = 1

    class App(id: Long, val appKey: String) : HomeItem(id)

    /** An app shortcut from the long-press menu (docs/spec.md 5.3), by its [HomeShortcutKey]. */
    class Shortcut(id: Long, val shortcutKey: String) : HomeItem(id)

    /**
     * A folder has no name (docs/spec.md 5.2). It holds apps ([AppEntry] keys) and app shortcuts
     * ([HomeShortcutKey] keys).
     */
    class Folder(id: Long, val itemKeys: MutableList<String>) : HomeItem(id)

    class Widget(id: Long, val appWidgetId: Int, val provider: String) : HomeItem(id)
}

/**
 * The home layout, kept in memory on the main thread and saved as a whole to
 * [LauncherDatabase] after every change.
 */
class HomeModel(
    private val db: LauncherDatabase,
    private val ioExecutor: Executor,
    /** Keeps the system's pinned shortcuts in step with the saved layout, on the IO thread. */
    private val updateShortcutPinnedState: (List<String>) -> Unit,
) {
    val items = mutableListOf<HomeItem>()
    private var nextId = 1L

    /** Called on a background thread once, before the model is used. */
    fun loadBlocking(): List<HomeItem> {
        val rows = db.loadHomeRows()
        val loaded = mutableListOf<HomeItem>()
        val folders = HashMap<Long, HomeItem.Folder>()
        for (row in rows.filter { it.container == HomeRow.CONTAINER_HOME }) {
            val item = when (row.type) {
                HomeRow.TYPE_APP -> row.appKey?.let { HomeItem.App(row.id, it) }
                HomeRow.TYPE_SHORTCUT -> row.appKey?.let { HomeItem.Shortcut(row.id, it) }
                HomeRow.TYPE_FOLDER -> HomeItem.Folder(row.id, mutableListOf()).also { folders[row.id] = it }
                HomeRow.TYPE_WIDGET -> if (row.widgetId != null && row.provider != null) {
                    HomeItem.Widget(row.id, row.widgetId, row.provider)
                } else {
                    null
                }
                else -> null
            } ?: continue
            item.cellX = row.cellX; item.cellY = row.cellY
            item.spanX = row.spanX; item.spanY = row.spanY
            loaded += item
        }
        for (row in rows.filter { it.container != HomeRow.CONTAINER_HOME }.sortedBy { it.rank }) {
            val key = row.appKey ?: continue
            folders[row.container]?.itemKeys?.add(key)
        }
        return loaded
    }

    fun setLoaded(loaded: List<HomeItem>) {
        items.clear()
        items += loaded
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        // Folder ids live in the same id space as their contents' rows.
        nextId += items.filterIsInstance<HomeItem.Folder>().sumOf { it.itemKeys.size }.toLong()
    }

    fun newId(): Long = nextId++

    /** The keys of every app shortcut on the home screen, also in folders. */
    fun shortcutKeys(): List<String> = items.flatMap {
        when (it) {
            is HomeItem.Shortcut -> listOf(it.shortcutKey)
            is HomeItem.Folder -> it.itemKeys.filter(HomeShortcutKey::isShortcutKey)
            else -> emptyList()
        }
    }

    fun save() {
        val rows = mutableListOf<HomeRow>()
        var contentId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        for (item in items) {
            rows += when (item) {
                is HomeItem.App -> HomeRow(item.id, HomeRow.TYPE_APP, HomeRow.CONTAINER_HOME, item.cellX, item.cellY, item.spanX, item.spanY, 0, item.appKey, null, null)
                is HomeItem.Shortcut -> HomeRow(item.id, HomeRow.TYPE_SHORTCUT, HomeRow.CONTAINER_HOME, item.cellX, item.cellY, item.spanX, item.spanY, 0, item.shortcutKey, null, null)
                is HomeItem.Folder -> HomeRow(item.id, HomeRow.TYPE_FOLDER, HomeRow.CONTAINER_HOME, item.cellX, item.cellY, item.spanX, item.spanY, 0, null, null, null)
                is HomeItem.Widget -> HomeRow(item.id, HomeRow.TYPE_WIDGET, HomeRow.CONTAINER_HOME, item.cellX, item.cellY, item.spanX, item.spanY, 0, null, item.appWidgetId, item.provider)
            }
            if (item is HomeItem.Folder) {
                item.itemKeys.forEachIndexed { rank, key ->
                    val type = if (HomeShortcutKey.isShortcutKey(key)) HomeRow.TYPE_SHORTCUT else HomeRow.TYPE_APP
                    rows += HomeRow(contentId++, type, item.id, 0, 0, 1, 1, rank, key, null, null)
                }
            }
        }
        nextId = maxOf(nextId, contentId)
        val shortcutKeys = shortcutKeys()
        ioExecutor.execute {
            db.replaceHomeRows(rows)
            // BgDataModel.addItems / removeItems update the pinned state after model changes.
            updateShortcutPinnedState(shortcutKeys)
        }
    }
}
